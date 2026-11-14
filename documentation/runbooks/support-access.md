# Support access to the production database

How the developer looks at production data from IntelliJ's Database tool window without an administrator login, and what that access can and
cannot do. **Status (2026-11-12): designed and tested on a throwaway restored copy of production, through IntelliJ as well as `SupportRolesIT`: every check below passed. Set up on production on 2026-11-12, with the same checks passing there.**
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
4. For `support_rw`, set the console's transaction mode (**Tx**) to Manual and start each fix-up with `SET TRANSACTION READ WRITE;`, then commit or
   roll back deliberately. In Auto mode every statement is its own transaction: `SET TRANSACTION` then fails with `25P01` ("can only be used in
   transaction blocks") and the write after it is refused as read-only. In Auto mode, run `BEGIN; SET TRANSACTION READ WRITE; ...; ROLLBACK;` as
   one script instead.
5. IntelliJ opens one database session per console and table editor, and the roles have connection limits (`support_rw` 3, `support_ro` 5), so
   a few open tabs give `53300 too many connections for role`. Tick *Single session mode* in the data source's Options, or close idle sessions
   (Sessions list), or reconnect.
6. The SDE is in the `sde` schema, with mixed-case names that must be quoted: `SELECT count(*) FROM sde."invTypes";`. The browser shows it only
   after `sde` is ticked under the data source's Schemas.

## Setting it up on production

Done on 2026-11-12 (see `deploy-log.md`). The tunnel document and the role were created first, then, in this order, each step checked before the next:

1. A manual snapshot, `pre-support-roles`, as the way back.
2. IAM authentication turned on for the RDS instance (`modify-db-instance --enable-iam-database-authentication --apply-immediately`). The status
   passes through `configuring-iam-database-auth` for a minute or two; the instance stays available and the site keeps answering. Do not use
   `--output text` with `PendingModifiedValues`: the CLI cannot format it (the request still goes through).
3. `support-roles.sql` run as the master user through a tunnel from the developer's machine, twice. The database is private and the instance role
   has no standing read of the master secret, so the developer's admin profile reads it inside one subshell, never writing or printing it.
4. Real logins as each role with IAM tokens, and the permission checks below.

To repeat after a migration adds a table: decide whether support may read it, edit the script (or `EXCLUDED` in `SupportRolesIT`), and run step 3 again.

## Checks

Run them after a change to the script, a migration that adds a table, or a new IntelliJ setup. `SupportRolesIT` automates the database part
(`./mvnw verify`, needs Docker); these are the same checks done by hand through the real tunnel and IAM tokens. All of them passed on production
on 2026-11-12. Every write below is refused or rolled back.

Open the tunnel and a token first (see "Daily use"). Use the `support_ro` data source for A, E and F and the `support_rw` one for B to D. For the
write checks set the console's **Tx** to Manual and finish each with Rollback; in Auto mode `SET TRANSACTION READ WRITE` fails with `25P01` and the
write after it is refused as read-only, which is the design working but not what C and D test.

| | Do | Expect |
|---|---|---|
| A | Open or `SELECT * FROM` each of `character_token`, `spring_session`, `spring_session_attributes` | `permission denied for table ...` (42501) |
| B | As `support_rw`, edit a cell in `account` and submit | `cannot execute UPDATE in a read-only transaction` (25006) |
| C | As `support_rw`: `SET TRANSACTION READ WRITE; UPDATE account SET created_at = now();` then roll back. Also `DELETE FROM plan`, `DELETE FROM character`, `TRUNCATE esi_cache`, `DROP TABLE esi_cache` | `permission denied` for each |
| D | As `support_rw`: `SET TRANSACTION READ WRITE; DELETE FROM esi_cache WHERE cache_key = 'does-not-exist';` then roll back. Without the first statement the same `DELETE` must fail | 0 rows, no error. Without `SET TRANSACTION`: 25006 |
| E | As `support_ro`: `DELETE FROM esi_cache;`, then with `SET TRANSACTION READ WRITE;` first | `read-only transaction`, then `permission denied` |
| F | `SELECT count(*) FROM sde."invTypes";` (tick `sde` under the data source's Schemas to see it in the browser) | a number |

Also: an ordinary password for `support_ro` is refused, `support_ro` cannot connect to the `keycloak` database, and the role cannot start a session
with another SSM document or on another target (tested with an identity other than the one that owns the session).

To check from a shell instead of IntelliJ: `dbtoken` writes the token to `~/.pgpass`, which `psql` reads, so with the tunnel open
`psql "host=127.0.0.1 port=15432 dbname=eveassist user=support_ro sslmode=verify-ca sslrootcert=$HOME/.aws/rds-global-bundle.pem" -c 'select current_user'`
prints `support_ro`.
