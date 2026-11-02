#!/bin/sh
# Dev only, until the admin import exists: replaces the eveassist.sde schema with the SDE in postgres/backup.
# The dump's schema is called evesde. It is restored as eveassist_sde, so that role owns it and the default
# privileges from 3-init-eveassist.sh give the app read access. Run from Infrastructure/.
set -eu
dump=postgres/backup/latest-postgresschema.pgdump
version=$(basename "$dump")@$(date -u -r "$dump" +%Y-%m-%dT%H:%M:%SZ)

psql() { docker exec -i eve-assist-postgres psql -v ON_ERROR_STOP=1 -q -U postgres -d eveassist "$@"; }

psql <<'SQL'
DROP SCHEMA IF EXISTS evesde CASCADE;
CREATE SCHEMA evesde AUTHORIZATION eveassist_sde;
SQL

docker exec -i eve-assist-postgres pg_restore -U postgres -d eveassist --no-owner --no-privileges \
    --role=eveassist_sde --schema=evesde < "$dump"

psql -v version="$version" <<'SQL'
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
