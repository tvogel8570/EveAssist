#!/bin/bash
# Prod only. Prepares the RDS server from the prod instance. Usage:
#   RDS_HOST=<endpoint> RDS_SECRET_ARN=<arn of the RDS-managed master secret> ./db-setup.sh init
#   RDS_HOST=<endpoint> ./db-setup.sh sde
# Only init needs the master user, and so the master secret: the instance role may read it only while init runs (grant it
# for that, then take it away). sde connects as eveassist_sde, which owns the sde schema and may create schemas in the
# database (3-init-eveassist.sh grants that), with the password from the Secrets Manager secret eveassist/db.
#
# init  creates the keycloak and eveassist databases and roles (postgres/sql/init/3-init-eveassist.sh, run as the
#       master user), with the role passwords from the Secrets Manager secret eveassist/db.
# sde   downloads the SDE schema dump from Fuzzwork and replaces the sde schema in eveassist, like postgres/load-sde.sh
#       does for the dev container, as the eveassist_sde role. Stop the app first when this is a reload.
#
# Secrets are read here with the instance role and passed to the container through a private temp file, never on a
# command line. The postgres client is the same image as the dev database, so its version matches the server.
set -euo pipefail

: "${RDS_HOST:?set RDS_HOST to the RDS endpoint}"
IMAGE=postgres:18.6-alpine3.24
HERE=$(cd "$(dirname "$0")" && pwd)
INIT_SCRIPT="$HERE/../postgres/sql/init/3-init-eveassist.sh"
SDE_URL=https://www.fuzzwork.co.uk/dump/latest-postgresschema.pgdump
SDE_DIR=${SDE_DIR:-/opt/eveassist/sde}

secret() { aws secretsmanager get-secret-value --secret-id "$1" --query SecretString --output text; }

umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# the connection settings for the container that runs the PostgreSQL client: who connects, and with which password
connection() {
    {
        echo "PGHOST=$RDS_HOST"
        echo "PGUSER=$1"
        echo "PGSSLMODE=require"
        echo "PGPASSWORD=$2"
    } > "$work/env"
}

case "${1:-}" in
init)
    : "${RDS_SECRET_ARN:?set RDS_SECRET_ARN to the RDS master secret ARN}"
    connection eveassist_master "$(secret "$RDS_SECRET_ARN" | jq -r .password)"
    ;;
sde)
    connection eveassist_sde "$(secret eveassist/db | jq -r .EVEASSIST_SDE_PASSWORD)"
    ;;
esac

case "${1:-}" in
init)
    secret eveassist/db | jq -r '"KC_DB_PASSWORD=\(.KC_DB_PASSWORD)\nEVEASSIST_OWNER_PASSWORD=\(.EVEASSIST_OWNER_PASSWORD)\nEVEASSIST_APP_PASSWORD=\(.EVEASSIST_APP_PASSWORD)\nEVEASSIST_SDE_PASSWORD=\(.EVEASSIST_SDE_PASSWORD)"' >> "$work/env"
    docker run --rm --env-file "$work/env" -v "$INIT_SCRIPT:/init.sh:ro" "$IMAGE" sh /init.sh
    echo "Databases and roles are in place."
    ;;
sde)
    mkdir -p "$SDE_DIR"
    dump="$SDE_DIR/latest-postgresschema.pgdump"
    curl -fL --retry 3 -o "$dump" "$SDE_URL"
    build=$(curl -fsSL https://www.fuzzwork.co.uk/dump/latest/ | grep -oE 'sdeyaml_postgresschema_[0-9]+_[0-9]+_[0-9]+\.pgdump' | head -1 || true)
    version="${build:-latest-postgresschema.pgdump}@$(date -u -r "$dump" +%Y-%m-%dT%H:%M:%SZ) sha256:$(sha256sum "$dump" | cut -c1-16)"
    echo "SDE version: $version"
    client() { docker run --rm -i --env-file "$work/env" -v "$dump:/sde.pgdump:ro" "$IMAGE" "$@"; }

    client psql -v ON_ERROR_STOP=1 -q -d eveassist <<'SQL'
DROP SCHEMA IF EXISTS evesde CASCADE;
CREATE SCHEMA evesde AUTHORIZATION eveassist_sde;
SQL
    client pg_restore -d eveassist --no-owner --no-privileges --role=eveassist_sde --schema=evesde /sde.pgdump
    client psql -v ON_ERROR_STOP=1 -q -d eveassist -v version="$version" <<'SQL'
BEGIN;
DROP SCHEMA IF EXISTS sde CASCADE;
ALTER SCHEMA evesde RENAME TO sde;
GRANT USAGE ON SCHEMA sde TO eveassist_app;
SET ROLE eveassist_sde;
CREATE TABLE sde.sde_version (name text NOT NULL, imported_at timestamptz NOT NULL DEFAULT now());
INSERT INTO sde.sde_version (name) VALUES (:'version');
COMMIT;
SQL
    echo "Loaded $version"
    ;;
*)
    echo "usage: db-setup.sh init|sde" >&2
    exit 2
    ;;
esac
