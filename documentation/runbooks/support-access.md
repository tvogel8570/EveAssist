# Support access to the production database

How the developer looks at production data from IntelliJ's Database tool window without an administrator login, and what that access can and
cannot do. **Status (2026-11-12): designed and tested on a throwaway restored copy of production. Not yet set up on production itself.**
The production steps are in "Setting it up on production".

## Design

| Piece | What it is |
|---|---|
| Tunnel | An SSM Session document, `EveAssist-RdsTunnel`, with the database host and port written into it. A caller can choose only the local port, so it cannot forward anywhere else. |
| IAM role | `EveAssist-support-ro`, assumable by the account's SSO administrator role. It may start a session on the instance with that one document, end its own sessions, and `rds-db:connect` as `support_ro`. Nothing else. |
| Database login | IAM authentication: a 15 minute token from `aws rds generate-db-auth-token`, used as the password. Neither database role has a password. |
| `support_ro` | Reads an allowlist of tables and the SDE. Never writes. |
| `support_rw` | Reads what `support_ro` reads. May `DELETE` from the fix-up tables (`esi_cache`, `character_fetch`, `character_asset`, `character_structure`, `corporation_fetch`, `corporation_asset`, `corporation_division`) and `UPDATE` `esi_cache`, `character_fetch`, `corporation_fetch`. Its transactions are read-only until the session runs `SET TRANSACTION READ WRITE`. |

Neither role reads `character_token` (encrypted refresh tokens) or `spring_session*` (a session id is a login cookie). Neither can reach the
`keycloak` database. Both have a statement timeout (60 s), an idle-in-transaction timeout, and a connection limit.

**The allowlist is deliberate.** A table added by a migration is invisible to support until it is named in `Infrastructure/prod/support-roles.sql`
(then run the script again) or in `EXCLUDED` of `SupportRolesIT`. That test fails for a table that is in neither, so the decision cannot be forgotten.
IntelliJ still lists the excluded tables, because it lists the whole catalog. Opening one gives `permission denied`.

The privacy policy must say that the operator can read account data for support (tokens and sessions excluded). See the GitHub issue for it.

## Files

- `Infrastructure/prod/support-roles.sql`: the roles and grants. Safe to run again.
- `Infrastructure/prod/support-access/template_*.json`: the tunnel document, the role's trust policy and its permission policy. The identifiers are
  variables (see `Infrastructure/prod/template_ops.env`); render with `envsubst` after loading `ops.env`.
- `App/src/test/java/com/eveassist/SupportRolesIT.java`: runs the script on the real migrations and logs in as each role.

## Daily use

The helpers `dbtunnel` and `dbtoken` are bash functions in `~/.bashrc` on the machine where IntelliJ's backend runs (the WSL distro on remote
development, since the tunnel, `~/.aws` and `~/.pgpass` must be where the JDBC driver runs). Their source is kept by the developer in a clip; they
are not in the repository.

1. Terminal A: `dbtunnel` (admin profile for now, `dbtunnel EveAssist-support-ro` once that profile exists). Leave it running.
2. Terminal B: `dbtoken support_ro EveAssist-admin` (or `support_rw`). The token is valid for 15 minutes of new connections.
3. IntelliJ data source: host `127.0.0.1`, port `15432`, database `eveassist`, authentication "pgpass", user `support_ro` (underscore, not hyphen),
   SSL mode `verify-ca` with the RDS global bundle (`~/.aws/rds-global-bundle.pem`). Run `dbtoken` again before each new connection.
4. For `support_rw`, set the console's transaction mode to Manual and start each fix-up with `SET TRANSACTION READ WRITE;`, then commit or roll back
   deliberately.

## Setting it up on production

Not done yet. In order, each step checked before the next:

1. Create the tunnel document from its template (`aws ssm create-document --document-type Session`).
2. Create the role with the rendered trust policy, attach the rendered permission policy.
3. Enable IAM authentication on the RDS instance (a change that applies immediately, no restart). Record it in `deploy-log.md`.
4. Run `support-roles.sql` as the master user, from the instance, since the database is private.
5. Check with the steps below, then add a deploy-log entry.

## Checks

- A login as `support_ro` can `SELECT` from `account` and the SDE, and `permission denied` on `character_token`, `spring_session`,
  `spring_session_attributes`.
- A `DELETE` as `support_ro` fails with `read-only transaction`, even after `SET TRANSACTION READ WRITE` (then `permission denied`).
- As `support_rw`, a plain `DELETE FROM esi_cache` fails; after `SET TRANSACTION READ WRITE` it works; `UPDATE account` and `DELETE FROM plan`
  are `permission denied`.
- The role cannot start a session with another document, or on another target.

`SupportRolesIT` automates the database part (`./mvnw verify`, needs Docker).
