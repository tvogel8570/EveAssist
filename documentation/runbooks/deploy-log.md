# Deploy Log

One entry per build and deploy of prod, newest last. No secrets. The procedure is in [Initial deployment](initial-deployment.md) and
[Deploying an update](update-deployment.md).

## 2026-11-12: first build, not yet deployed

- Commit `a13eb6a` on branch `prod-deployment`. Both images built for `linux/arm64` on the developer's x86 machine (the Keycloak one under
  QEMU emulation) and pushed to ECR in `us-west-2`.
- App: `$EVEASSIST_ACCOUNT_ID.dkr.ecr.us-west-2.amazonaws.com/eveassist-app:0.1.0-a13eb6a`, digest
  `sha256:436225ac5ba22480a82aa16d1ecdb1a3129f159f74638db895d73f1260f29316`.
- Keycloak: `$EVEASSIST_ACCOUNT_ID.dkr.ecr.us-west-2.amazonaws.com/eveassist-keycloak:0.1.0-a13eb6a`, digest
  `sha256:6362f3bd77c2f6537639b697936b32a4bd2d23e1f8241ce08db1ffa02c9de2c6`. It carries the realm file of that commit.
- SDE in RDS: Fuzzwork build 3687575, 2026-11-11.
- RDS instance `eveassist-prod`, EC2 `$EVEASSIST_INSTANCE_ID`. No RDS snapshot taken yet.

## 2026-11-12: first start on the instance

- Started with `start.sh` after an RDS snapshot `pre-first-start`. Running now: App `0.1.0-a13eb6a` (digest `sha256:436225ac…`) and
  Keycloak `0.1.0-6813893` (digest `sha256:d1cbf6db…`, Keycloak 26.8.2 on the commit that added the `kc-admin-api` scope).
- Public checks passed from the internet: pages answer, certificates are Let's Encrypt for both names, `/admin/` and `/realms/master` answer
  404, security headers are present, `security.txt` shows the public contact.
- Found and fixed on the first start:
  - The app crashed once because Keycloak's `/health/live` turned healthy before the realm import finished. The prod health check now asks
    for the realm's discovery document.
  - The app's health check ran under `CMD-SHELL` (dash, no `/dev/tcp`) and could never pass. It now runs as `bash -c`.
  - Keycloak 26.8.2 refuses admin API calls unless the token has the `kc-admin-api` scope. Dev ran 26.8.1, which did not, and hid it. The
    realm file now gives the `eveassist-admin` client that scope, and Keycloak is pinned to 26.8.2 for dev and prod.
- The `EveAssist` realm was deleted and imported again from the image three times while this was fixed. At that point it held only the
  service account: no users, no EVE logins, no sessions.
- Not yet done: the first EVE sign-in, ADMIN role for the developer's account, a named Keycloak admin with the bootstrap admin removed and
  brute-force protection on the `master` realm, a character link, the account deletion on the live stack, the per-visitor limit check from two
  networks, the restore test, and the CloudWatch alarm.

## 2026-11-12: first sign-in and Keycloak admin

- First EVE sign-in on the live site worked: one `account` row, one Keycloak person with an `eve` identity, no warnings or errors in the
  Keycloak or app logs, and the session cookie is `Secure; HttpOnly; SameSite=Lax`. Sign-in goes through the whole chain: app, Keycloak, EVE
  and back, with the callback `https://auth.eveassist.net/realms/EveAssist/broker/eve/endpoint`.
- The Keycloak `master` realm now has a named admin with the `admin` role and a password the developer typed at kcadm's own prompt. The
  bootstrap admin `kcbootstrap` was deleted, and brute-force protection is on for the `master` realm too (failure factor 10, no permanent
  lockout). It was done with kcadm in an SSM shell, because a Windows browser cannot reach the plugin's loopback port (see the initial
  runbook, step 9). The bootstrap values stay in Secrets Manager only because Compose requires them to start.
- No SSM session logging is configured (the account has no `SSM-SessionManagerRunShell` document, so shell sessions leave no transcript,
  only the session history). Worth enabling if more than one person gets shell access.
- Not yet done: a character link, the account deletion on the live stack, the per-visitor `/try` limit from two networks, the restore test, and
  the CloudWatch alarm.

## 2026-11-12: character link, export and account deletion on the live stack

- Two characters were linked with all five read-only scopes (both features on). ESI answered: 2,878 asset rows were stored. The two stored
  refresh tokens are 40 bytes of AES-GCM ciphertext with a 12-byte nonce each, not readable as text, and name the key
  `kms-<12 hex digits>`, which is what the wrapped key in Secrets Manager gives. The app log held no JWT, `refresh_token`, `access_token`,
  `Bearer`, `client_secret` or `eat_` string, and no warnings or errors.
- KMS is asked once per app start, not per token: CloudTrail shows seven `Decrypt` calls with the purpose `eveassist-refresh-tokens` (one per
  start this evening) and none while the two tokens were encrypted at link time. Only the link window was covered by CloudTrail when checked;
  its delivery lags by minutes.
- The data export held the account, both characters without any token, the features, and no plans.
- The account was deleted from the account page. Checked against a baseline taken just before (1 account, 2 characters, 2 tokens, 2,878 assets,
  2 features, 1 corporation, 1 Keycloak user with 1 EVE identity): every row is gone, no orphans, no sessions, the Keycloak user is gone, and
  the realms are intact. This was the first live run of the real `KeycloakAdmin` with the `kc-admin-api` scope. The app logged no warning, so
  EVE confirmed both revocations. EVE's own page for third-party applications is the place to see it from the player's side.
- Still open: the per-visitor `/try` limit from two networks, the restore test, and the CloudWatch alarm.

## 2026-11-12: Keycloak stops keeping EVE's tokens, and monitoring

- A sign-in that asks for no scopes still gets a refresh token from EVE, and Keycloak kept it (`storeToken` was on). Nothing in EveAssist reads it
  and Keycloak never revokes an upstream token (no revoke code in its broker package, checked in the 26.8.2 sources), so a deleted account's
  token was dropped without being revoked at EVE. `storeToken` and `addReadTokenRoleOnCreate` are now off in the realm file and, set in place with
  kcadm, in the live realm. Checked after deleting the account and signing in again: the new EVE identity has no stored token and the user has no
  `read-token` role. EVE keeps a consent entry for the sign-in application on the player's third-party applications page, which only the player can
  remove (the deletion page and the privacy policy say so).
- On the player's EVE page, the linked characters no longer list EveAssist after a deletion, so the token revocation is confirmed from EVE's side.
- Monitoring, from `Infrastructure/prod/alarms.sh` (run with `ALERT_EMAIL` set; the address is not in the repository): metric filters in the namespace
  `EveAssist/Prod` for ERROR lines, failed ESI fetches (the app logs a WARN with "failed", not the HTTP status, so 420 and 429 are not counted apart)
  and Caddy's upstream errors; alarms for each, for the instance status check and for the CPU credit balance; and a Route 53 health check of
  `/privacy` with its alarm in us-east-1. Route 53's checkers in five regions all answered 200.
- The billing alarm's email subscription had never been confirmed, so it would not have emailed anyone. Both subscriptions were sent again and
  must be confirmed by clicking the link in each message.

## 2026-11-12: Caddy allowlist for the auth host

- The auth host's Caddy rules changed from a denylist (`/admin*`, `/realms/master*`, `/metrics*` answer 404) to an allowlist: only
  `/realms/EveAssist*` and `/resources/*` go to Keycloak, everything else answers 404 from Caddy. Before this, `/` was proxied and
  redirected to `http://localhost:8080/admin/`.
- The Caddyfile was copied to the instance by hand in an SSM shell (the branch is not pushed). `caddy reload` said `config is unchanged`,
  because the single-file bind mount still held the old inode after `mv`. `docker restart` on the Caddy container fixed it. The lesson is
  in "Changing the Caddyfile" of the update runbook.
- Checked from the internet: `/`, `/x`, `/admin/`, `/realms/master` and `/metrics` answer 404 from Caddy; the realm's discovery document,
  keys and account page answer 200; the app and `/privacy` answer 200.
- The developer signed in with EVE through the allowlist: the login page was styled and the sign-in completed.
- Still open: the restore test.

## 2026-11-12: `docker compose down` and start again

- `docker compose -p eveassist down` (run from a directory without a Compose file, so it needs no secrets) removed the three containers and the
  network, keeping the volumes. `sudo ./start.sh` brought everything back: Keycloak healthy after 16 s, the app healthy about 45 s after the
  start, same image digests as before, the same Let's Encrypt certificate (not reissued, valid to 2027-02-10).
- Checked from the internet: `auth.` answers 404 for `/`, `/x`, `/admin/`, `/realms/master` and `/metrics`, and 200 for the realm's discovery
  document; the app, `/privacy` and `security.txt` answer 200. A browser tab signed in before the restart was still signed in after it, so
  sessions survive (they are in Postgres).
- About one request in 25 from the developer's WSL machine timed out, also against Cloudflare and AWS sites, so it is that machine's network
  and not the server.
- Still open: the restore test.

## 2026-11-12: `/try` per-visitor limit, the header chain

- The limit itself could not be reached from outside: a plan holds at most 10 products, and one visitor fetches only prices that are not
  already in the shared cache, so about 60 product adds as one visitor did not leave any item unpriced.
- A throwaway `traefik/whoami` container on the Compose network, behind a temporary Caddy route `/_probe` (removed afterwards, the Caddyfile
  is back to the committed one), showed what Caddy sends to its upstream. From two networks (network A and network B) the upstream
  got each computer's own public address in `X-Forwarded-For`; a forged `X-Forwarded-For`, `-Proto` and `-Host` from the client were all
  replaced. `RemoteAddr` was Caddy's address on the Docker network (172.18.0.3).
- Not checked live: that Tomcat (`server.forward-headers-strategy=native`) turns that header into the request's remote address for requests
  from the Docker subnet. That is covered by `ProdProfileIT.theVisitorsAddressIsTheOneCaddyForwarded` (prod profile, `X-Forwarded-For` in, the filter sees that address
  as the remote address) and by Tomcat's default trusted proxies, which include `172.16.0.0/12`, the Docker network. The gate item is closed on the
  two together, not on a live run of the limit.
- The Caddyfile was changed in place with `tee` (same inode), so `caddy reload` was enough and Caddy was not restarted.

## 2026-11-12: restore test

- A manual snapshot of `eveassist-prod` (`restore-test-20261112`) was restored to a new private instance `eveassist-restore-test` (`db.t4g.small`,
  the `eveassist-prod` subnet group and security group, not publicly accessible). The restore took about 10 minutes.
- Checked from the instance with SQL as the master user: Flyway at `20261110.2008` with 20 migrations (the repository's latest migration is
  `V20261110_2008__plan-archive.sql`), SDE build 3687575 with 53,925 `invTypes` rows, the one account, the 26 expected tables including
  `spring_session*`, and in the `keycloak` database the realms `master` and `EveAssist` with 3 users.
- The whole stack was then run on the copy by changing `RDS_HOST` in `prod.env` (kept as a backup, restored afterwards): Flyway validated 20
  migrations and changed nothing, the app was healthy after 12 s, Keycloak and the app both held their connection pools on the copy
  (`keycloak_owner -> keycloak: 3`, `eveassist_app -> eveassist: 10`), sign-in with EVE worked through the restored Keycloak realm, and the
  developer's data was as expected. A sign-in tab that had been left open was no longer signed in, which fits Keycloak's 30 minute idle
  timeout and not the restore.
- Switched back to `eveassist-prod`, checked the URLs of the app, checked the site from the internet, signed in again. The restored instance and
  its snapshot were deleted (no final snapshot). The backup `prod.env` copy was removed. The snapshots `pre-first-start` and
  `eveassist-final-snapshot` were left alone.
- Learned: the instance role may not call `rds:DescribeDBInstances` (by design), so a script on the instance must be given the master secret's
  ARN. The stack's directory is owned by root, so changing `prod.env` needs `sudo`.
- Launch gate: the restore test, the `/try` item and the restart check are done. Items without a recorded check yet: removing a single character
  (the account deletion revoked both tokens, but a plain character removal was not run on its own), the privacy policy read against the
  schema, and a test ERROR that makes the alarm email arrive.
- Checked from the internet afterwards: `http://` answers 308 to `https://` for both names, and the footer shows the operator, the early-preview
  wording, the privacy and `security.txt` links, `build a13eb6a` (the running image's commit) and the CCP © notice.

## 2026-11-12: removing a single character

- The developer removed one linked character from the Characters page on the live site, and its inventory data was removed with it. The
  page showed the plain green message ("The character is removed, with its saved access and stored data."), not the revoke-failed warning that
  `CharactersController` adds when EVE does not accept the revocation, so the token was revoked at EVE. Not yet tested: removing the character that is also the sign-in
  character (issue #24).


## 2026-11-12: alert delivery

- The alarm tests showed the chain up to SNS working (CloudWatch recorded each publish to `eveassist-alerts` as successful, and the upstream alarm's
  emails arrived three times). All alerts, and the billing alarm, go to one address that is hosted at Microsoft 365. While Outlook had an outage
  the same afternoon, the error alarm's emails could not be read. The developer decided not to add a second subscriber, so a Microsoft outage can hide an
  alarm: the instance status check, the site-down health check and the CloudWatch console are the other places to look.

## 2026-11-12: the instance role's KMS access narrowed

- The `TokenKey` statement of the instance role's inline policy went from `kms:Encrypt`, `kms:Decrypt` and `kms:GenerateDataKey` to `kms:Decrypt`
  only, with the condition `kms:EncryptionContext:purpose = eveassist-refresh-tokens`. The app's only KMS call is that `Decrypt` at start-up
  (checked in `KmsDataKeyProvider`). The previous policy was saved before the change, to put back if the app could not start.
- Tested: after the change the app container was restarted. CloudTrail shows the instance role's `Decrypt` with that purpose at the app's start,
  78 seconds after the `PutRolePolicy` call, without an error, and the site answered 200 on every checked address. A deliberate `kms:Encrypt`
  from the instance with the same context was refused with `AccessDenied`.
  The app showed `(healthy)` after the restart, and the developer signed in and the Characters page of a linked character loaded, which
  shows that the stored refresh tokens still decrypt.
- Not changed: anyone holding the role (any container, through the metadata service) can still do the one `Decrypt` that the app does, so this
  removes abilities, it does not protect the key against code running on the instance. The `db-setup.sh` SDE load now runs as `eveassist_sde`;
  removing the role's standing read of the RDS master secret is the next, separate step.

## 2026-11-12: the instance role no longer reads the RDS master secret

- `db-setup.sh sde` now connects as `eveassist_sde` (password from `eveassist/db`), so only `init` needs the master user. The init script already gave that
  role `CREATE` on the database. Tested first against a throwaway Postgres with the project's own init script: a first load and a replacement load
  work as that role, the application role can read the result and cannot write to it. The new file was copied to the instance by hand (the old one
  is kept as `db-setup.sh.bak` there).
- The `ReadOwnSecrets` statement of the instance role's inline policy lost the `rds!db-...` resource, so it reads only `eveassist/*`. The previous
  policy was saved before the change.
- Checked on the instance: the script prints its usage and exits 2 when no mode is given, `eveassist_sde` logs in and shows `CREATE` on the
  database and all 53,925 rows of `invTypes`, `describe-secret` on the master secret is refused with `AccessDeniedException`, and `eveassist/app` is
  still readable. The site answered 200 on every checked address. Not run: a real SDE reload, which waits for the next EVE patch and will be the
  first real use of this path.
- The role still lets any container on the instance do the one `Decrypt` the app does (step above). The `manage-users` service account in Keycloak
  is unchanged, by decision, until an admin area exists.

## 2026-11-12: the error alarm test is accepted

- The test ERROR line (written to the app container's standard output at 18:11:28 UTC) reached CloudWatch Logs, `eveassist-prod-errors` went from OK to
  ALARM at 18:12:00 UTC and back to OK at 18:24:00 UTC, and CloudWatch recorded each notification to `eveassist-alerts` as successfully executed. Through the
  same topic and subscription the `eveassist-prod-upstream-unreachable` alarm's emails arrived on three occasions the same day.
- The developer's mailbox, hosted at Microsoft 365, was unreachable for part of the afternoon (a Microsoft Outlook disruption), and the two
  `eveassist-prod-errors` emails themselves were not seen. The developer accepted the item on that evidence: the alarm fired, AWS sent the
  notifications, and the topic and the subscription demonstrably deliver.

## 2026-11-12: go-live, first testers

- The developer invited the first 2 testers (initial runbook, step 11). The state at that point: image digests as in the first start (app
  `0.1.0-a13eb6a`, Keycloak `0.1.0-6813893`), every alarm `OK` with its actions on (none was left silenced), no ERROR lines in the 30 minutes before, and
  the site, `/try` and the realm answering 200.
- The launch gate was closed except one item, which stays open on purpose: the privacy policy page was read against the schema and what it
  left out is now written into it, but four sentences still do not match what the code does (issues #25 to #28: corporation data after a character is
  removed, the 10-hour session limit, `esi_cache` rows, data kept after a feature is turned off).
- Nobody watches the first hours by hand: the developer chose to wait for the testers' feedback, which may take a couple of days because they
  are volunteers. The safety net is the alarms (errors, ESI failures, upstream errors, instance status, CPU credits, the site-down health check and
  the billing alarm), which email one Microsoft-hosted mailbox. A watch that I (Claude) started was stopped at the developer's request.
- Running code is the build of commit `a13eb6a`. The repository's `prod-deployment` branch, with the fixes made since (the `start.sh` secrets check,
  the Keycloak token retry, the runbooks), is not pushed and not deployed.

## 2026-11-12: release v0.2.0 (build 3ec2628), the first update of prod

- **What was built:** the tip of `development`, commit `3ec2628` (the merge of pull request #37), after `./mvnw verify` passed on it (BUILD SUCCESS,
  236 unit tests, 404 integration tests, 2 skipped live-service tests). Both images were built for `linux/arm64` on the developer's machine and checked
  before pushing: the jar in the app image has the same SHA-256 as the jar from the verify run, `GIT_COMMIT=3ec2628` is in it, and the realm file in the
  Keycloak image equals the one in git. Tags `0.2.0-3ec2628` (immutable in ECR):
  - App: `$EVEASSIST_ACCOUNT_ID.dkr.ecr.us-west-2.amazonaws.com/eveassist-app:0.2.0-3ec2628`, digest
    `sha256:a9efc8993956abc03f3560b4058e6218065ac30f4b7480c51c3d78443f5ff969` (read back from ECR).
  - Keycloak: `$EVEASSIST_ACCOUNT_ID.dkr.ecr.us-west-2.amazonaws.com/eveassist-keycloak:0.2.0-3ec2628`, digest
    `sha256:c20bd8558f0a46b7ab3eb0dc86b087676e50420b274dd6d776be8e722f77002e`.
- **What changed compared with the running build** (`563b081` here, the equivalents of the old ids `a13eb6a` and `6813893`): no migration. App: the Keycloak
  service-account token is replaced and the deletion tried once more when Keycloak refuses the cached one, a changed message, and the privacy policy
  additions. Keycloak image: the realm file now has `storeToken` and `addReadTokenRoleOnCreate` off, which the live realm already had from the earlier
  `kcadm` change, so nothing is imported again. On the instance: the new `start.sh` (stops when a secret cannot be fetched).
- **Snapshot and rehearsal** (update runbook steps 4 and 2): snapshot `pre-v0-2-0` (the first name tried, `pre-v0.2.0`, was refused because RDS names
  cannot contain dots). It was restored to `eveassist-rehearsal`: private, the `eveassist-rds` group, no deletion protection, no backups. The new app image
  ran against it as one throwaway container (`rehearse-app.sh`, 900 MB cap, Docker's log driver): health `UP`, 20 migrations validated, "No migration
  necessary", started in 19.6 s, 2,091 MB of host memory still available. The Keycloak image was not rehearsed, by decision. The copy was then deleted,
  and a check found no instance, snapshot, retained backup or security group of it.
- **Deploy** (about 21:25 UTC, by the developer from a clip): new `start.sh` copied in place (checked by checksum against the repository), `images.env`
  changed to the two digests (the old file kept as `images.env.bak-v0-1-0`), `sudo ./start.sh app keycloak`. Keycloak logged "Realm 'EveAssist' already
  exists. Import skipped" (no re-import, nobody signed out), the app validated 20 migrations, needed none and started in 12.8 s. No ERROR line in the
  prod log group afterwards. The footer shows `build 3ec2628` (browser and curl), the privacy page carries the new wording, `/`, `/try`, `/privacy`,
  `security.txt` and the realm's discovery document answer 200, `/admin/` on the auth host answers 404, and the developer signed in.
- **Alarm:** `eveassist-prod-upstream-unreachable` went to ALARM at 21:26:55 UTC, because Caddy could not reach the restarting containers. It was not
  silenced beforehand: the developer ran the deploy before that was done. The developer cannot read the alert mailbox while Outlook is down and
  accepts the alarms as right. The deploy's own checks do not depend on it.
- **GitHub rules found on the way:** `main` is covered by the ruleset "primary branches" (shared with `development`, no bypass), which requires pull
  requests and forbids force pushes and deletion, so the fast-forward push in `CLAUDE.md` could never have worked. `CLAUDE.md` now says a release goes by
  pull request with a merge commit. A second ruleset, "release tags", protects `v*` tags from deletion and change.
- **Still to do:** the tag `v0.2.0` on `3ec2628`, the pull requests to `main` and back, and deleting the snapshot `pre-v0-2-0` once the release is
  accepted, by 2026-11-19. Not obtained: the Hikari pending-connections reading (the one command printed an empty line).

