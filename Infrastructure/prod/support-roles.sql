-- Support access to the production database: two login roles for a person looking at data, and fixing one small thing.
--
--   support_ro  reads an allowlist of tables. No writes, whatever the session asks for.
--   support_rw  reads the same, and may delete or update a few tables that a fix-up needs (the ESI cache, the fetch state, fetched assets).
--               Its transactions are read-only unless the session says BEGIN, SET TRANSACTION READ WRITE: a deliberate step, not a slip.
--
-- Neither role reads character_token (the encrypted refresh tokens) or spring_session* (a session id is the login cookie: whoever reads
-- it can act as that user). Neither can reach the keycloak database. The roles have no password: on RDS they are members of rds_iam and
-- log in with a 15 minute IAM token (documentation/runbooks/support-access.md, "Daily use").
--
-- THE ALLOWLIST IS DELIBERATE. A table is readable only if it is named below. A new migration's table stays invisible to support until
-- someone adds it here, or names it as excluded in SupportRolesIT: that test fails for a table that is neither.
--
-- Run it as the master user (a superuser in tests) against the eveassist database, and run it again after a migration adds a table:
--   psql -v ON_ERROR_STOP=1 -d eveassist -f support-roles.sql
-- It only creates what is missing and sets the same grants each time.
\set ON_ERROR_STOP on

SELECT format('CREATE ROLE %I LOGIN', r) FROM unnest(ARRAY['support_ro', 'support_rw']) r
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = r) \gexec

-- On RDS a role needs rds_iam to log in with an IAM token (and then cannot log in with a password). The role does not exist elsewhere.
SELECT format('GRANT rds_iam TO %I', r) FROM unnest(ARRAY['support_ro', 'support_rw']) r
WHERE EXISTS (SELECT FROM pg_roles WHERE rolname = 'rds_iam') \gexec

-- Safety nets that apply at every login, whatever the client does.
ALTER ROLE support_ro SET default_transaction_read_only = on;
ALTER ROLE support_rw SET default_transaction_read_only = on;
ALTER ROLE support_ro SET statement_timeout = '60s';
ALTER ROLE support_rw SET statement_timeout = '60s';
-- a forgotten BEGIN must not hold locks on production rows
ALTER ROLE support_ro SET idle_in_transaction_session_timeout = '5min';
ALTER ROLE support_rw SET idle_in_transaction_session_timeout = '2min';
ALTER ROLE support_ro CONNECTION LIMIT 5;
ALTER ROLE support_rw CONNECTION LIMIT 3;

-- support_rw can do whatever support_ro can
GRANT support_ro TO support_rw;

-- The master user on RDS is not a superuser: it may create roles but not grant on tables it does not own, so it is made a member of the
-- owners first, and the grants run as them (as 3-init-eveassist.sh does).
SELECT format('GRANT %I TO CURRENT_USER', r) FROM unnest(ARRAY['eveassist_owner', 'eveassist_sde']) r
WHERE NOT (SELECT rolsuper FROM pg_roles WHERE rolname = CURRENT_USER) AND NOT pg_has_role(CURRENT_USER, r, 'MEMBER') \gexec

SET ROLE eveassist_owner;

GRANT CONNECT ON DATABASE eveassist TO support_ro, support_rw;
GRANT USAGE ON SCHEMA public TO support_ro;

-- Readable. Not here, on purpose: character_token, spring_session, spring_session_attributes.
GRANT SELECT ON TABLE
    account,
    account_feature,
    character,
    character_asset,
    character_corporation,
    character_fetch,
    character_structure,
    contract_public,
    contract_public_fetch,
    contract_public_item,
    corporation,
    corporation_asset,
    corporation_division,
    corporation_fetch,
    esi_cache,
    flyway_schema_history,
    holder_asset,
    jita_fetch,
    jita_order,
    plan,
    plan_bought_part,
    plan_price,
    plan_product
TO support_ro;

-- Writable by support_rw, for fix-ups only: what ESI told us and what we stored from it, never an account, a plan or a token.
-- (Deleting an account stays with the application, which also revokes the tokens at EVE and deletes the Keycloak user.)
GRANT DELETE ON TABLE
    esi_cache,
    character_fetch,
    character_asset,
    character_structure,
    corporation_fetch,
    corporation_asset,
    corporation_division
TO support_rw;

GRANT UPDATE ON TABLE
    esi_cache,
    character_fetch,
    corporation_fetch
TO support_rw;

RESET ROLE;

-- the SDE is public game data
SET ROLE eveassist_sde;
GRANT USAGE ON SCHEMA sde TO support_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA sde TO support_ro;
RESET ROLE;
