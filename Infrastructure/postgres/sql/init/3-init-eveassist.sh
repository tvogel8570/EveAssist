#!/bin/sh
# Creates the keycloak and eveassist databases and their roles. Safe to run again: it creates only what is missing and
# sets each role's password from the environment. The postgres user is kept for set-up and admin tasks.
# On a managed server (RDS) there is no superuser: run it with PGUSER set to the master user, plus PGHOST, PGPASSWORD
# and PGSSLMODE. Such a user may create roles but cannot act as them until it is made a member, so it is granted
# membership first, and the statements that need an owner run as that owner.
set -eu
admin="${PGUSER:-postgres}"

psql -v ON_ERROR_STOP=1 -U "$admin" -d postgres \
    -v keycloak_password="$KC_DB_PASSWORD" \
    -v owner_password="$EVEASSIST_OWNER_PASSWORD" \
    -v app_password="$EVEASSIST_APP_PASSWORD" \
    -v sde_password="$EVEASSIST_SDE_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', r) FROM unnest(ARRAY['keycloak_owner', 'eveassist_owner', 'eveassist_app', 'eveassist_sde']) r
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = r) \gexec

ALTER ROLE keycloak_owner PASSWORD :'keycloak_password';
ALTER ROLE eveassist_owner PASSWORD :'owner_password';
ALTER ROLE eveassist_app PASSWORD :'app_password';
ALTER ROLE eveassist_sde PASSWORD :'sde_password';

SELECT format('GRANT %I TO CURRENT_USER', r) FROM unnest(ARRAY['keycloak_owner', 'eveassist_owner', 'eveassist_sde']) r
WHERE NOT (SELECT rolsuper FROM pg_roles WHERE rolname = CURRENT_USER) \gexec

SELECT 'CREATE DATABASE keycloak OWNER keycloak_owner'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak') \gexec
SELECT 'CREATE DATABASE eveassist OWNER eveassist_owner'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'eveassist') \gexec

REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
REVOKE ALL ON DATABASE eveassist FROM PUBLIC;
GRANT CONNECT ON DATABASE eveassist TO eveassist_app, eveassist_sde;
-- the SDE import builds a staging schema, then renames it to sde
GRANT CREATE ON DATABASE eveassist TO eveassist_sde;
SQL

psql -v ON_ERROR_STOP=1 -U "$admin" -d eveassist <<'SQL'
-- application tables live in public, changed only by Flyway as eveassist_owner
SET ROLE eveassist_owner;
ALTER SCHEMA public OWNER TO eveassist_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO eveassist_app;
ALTER DEFAULT PRIVILEGES FOR ROLE eveassist_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO eveassist_app;
ALTER DEFAULT PRIVILEGES FOR ROLE eveassist_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO eveassist_app;

-- the SDE is read-only for the application
RESET ROLE;
SET ROLE eveassist_sde;
CREATE SCHEMA IF NOT EXISTS sde AUTHORIZATION eveassist_sde;
GRANT USAGE ON SCHEMA sde TO eveassist_app;
ALTER DEFAULT PRIVILEGES FOR ROLE eveassist_sde
    GRANT SELECT ON TABLES TO eveassist_app;
SQL
