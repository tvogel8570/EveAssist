# Deploying an Update

Status: **draft**, written 2026-11-11. The steps assume the prod set-up of
[Initial deployment](initial-deployment.md). There is no staging environment, so the rehearsal on the dev machine
(step 2) is the staging.

Downtime during a deploy is accepted. Do it at a quiet time and say so on the site if it will exceed a few minutes.

## Change types

| Change | Needs | Extra procedure |
|---|---|---|
| App code and templates only | Steps 1 to 7 | None |
| App with a Flyway migration | Steps 1 to 7, snapshot is mandatory | Migration rules below |
| Keycloak realm file or a Keycloak client secret | Step 5 replaced by [keycloak-realm.md](keycloak-realm.md) | Everyone is signed out |
| New or changed ESI calls or scopes | Add: concurrency check first | [concurrency-tuning.md](concurrency-tuning.md), `esi.md`. New scopes need the EVE application updated ("Adding an ESI scope" below) and every character re-authorized. |
| Caddyfile only | Put the file in place **without replacing its inode**, then `sudo docker restart <project>-caddy-1` | `caddy reload` does not see a replaced file; see "Changing the Caddyfile" below |
| New SDE (after an EVE patch) | SDE reload section | Plans using old type ids may change |
| Keycloak or Postgres major upgrade | Own rehearsal with a restore of prod data | Never in the same release as an app change |

## 1. Release readiness

- [ ] The change is on `development`, `./mvnw verify` passes there (includes architecture checks and `MigrationNamingTest`).
- [ ] Documentation that the change touches is updated in the same PR (CLAUDE.md rule on architecture drift).
- [ ] No handoff section left in the PR description.
- [ ] Privacy and CCP checks: does the change store a new kind of data, add a third-party request, add a write scope or
      anything that looks like an ad or a payment? If yes, update the privacy policy first, and stop on any write scope or
      payment, because the license and the public commitment forbid them.
- [ ] Release notes written (what changed, any sign-out, any downtime).

## 2. Rehearse

1. Restore the snapshot of step 4 to a private, temporary RDS instance, as "Restoring the database" below describes, with
   `--backup-retention-period 0 --no-deletion-protection` (a throwaway copy of real data must not make backups of its own, and must be
   deletable). Do not copy a dump of prod to the dev machine: the privacy policy says the data is kept at AWS, and a file on a laptop is also
   in its backups and disk images. On the dev machine use the dev database with generated data.
2. On the instance, run the new build against the copy: `sudo ./rehearse-app.sh <endpoint of the copy> <new app image@sha256:...>`
   (`Infrastructure/prod/rehearse-app.sh`; copy it to the instance like the other scripts). It starts one throwaway container of the new image on
   the Compose network, memory capped at 900 MB, with Docker's own log driver (so its lines cannot trip the prod alarms) and no published
   port, waits up to 3 minutes for the health endpoint, prints the Flyway and start-up lines, and removes the container. Check `free -m`
   first: stop if less than about 1,200 MB is available, because the prod stack keeps running. **A pass** is health `UP`,
   "Successfully validated N migrations", and either "No migration necessary" or the new migration applied in the time you expect.
   The copy holds the same linked characters and tokens as prod, so the rehearsal may make one round of ESI calls for them.
3. What this does not test: the Keycloak image (to rehearse a Keycloak change, start its image the same way against the copy's `keycloak`
   database: the 2026-11-12 release skipped that on purpose), and anything seen in a browser. Those are checked after the deploy (step 6).
4. Time the migration. A table rewrite on a large table (assets, price rows, contracts) is the thing to look for.
   More than a couple of minutes means a different migration (add column nullable, backfill in batches, then constrain
   in a later release).
5. Delete the throwaway database (`--skip-final-snapshot --delete-automated-backups`) and check that no instance, snapshot or retained backup
   of it remains. It holds real users' data, so do not keep it.

### Migration rules

- Forward only, never edit a merged migration (`documentation/architecture/data.md`).
- Make each release safe to run on the **previous** image for one deploy: add before you use, remove only in a later
  release (expand then contract). Then rolling the image back does not need a database restore.
- A migration that cannot be made backward compatible is a "restore to roll back" release. Say so in the notes and take
  the snapshot in step 4 immediately before, not hours earlier.

## 3. Build and publish the image

1. Build from the commit that is the tip of `development`, after `./mvnw verify` passed on it, and do not rewrite history afterwards: the
   footer shows the commit id, and the id must stay a commit that exists on GitHub (the first release's footer showed an id that a later
   history rewrite removed).
2. CI builds, tests and pushes the images to ECR **[to build]**. Until then, build locally for the instance's CPU, tagging
   `<version>-<short commit>` (ECR tags are immutable, so a tag can be pushed only once):
   - App, in `App/`: `docker buildx build --builder eveassist-arm --platform linux/arm64 --build-arg GIT_COMMIT=$(git rev-parse --short HEAD) -t <ecr>/eveassist-app:X.Y.Z-<commit> --load .`
   - Keycloak, in `Infrastructure/keycloak/`: the same without the build argument, tagged `eveassist-keycloak:X.Y.Z-<commit>`.
3. Before pushing, check what is inside, without running anything: both images are `arm64`, the app image's `GIT_COMMIT` is the commit, the jar in
   it (`docker create`, `docker cp ...:/app/app.jar`) has the same SHA-256 as `App/target/app-*.jar` from the verify run, and the realm file in the
   Keycloak image equals the one in git.
4. Push: `aws ecr get-login-password | docker login --username AWS --password-stdin <ecr>`, then `docker push` both. Read each **digest** back from
   ECR (`aws ecr describe-images`). The instance pins digests, not tags, so what runs is exactly what was tested.
5. Tag the release **after** the deploy has been checked: `git tag -a vX.Y.Z <the built commit> -m "..."` (the annotation names the footer
   id, both digests and anything the images do not show, such as a live realm setting that the image's realm file lacks). Tags `v*` are protected
   by the GitHub ruleset "release tags" (no deletion, no update, no bypass): to correct a wrong tag, remove that ruleset first.
   Releasing to `main` follows CLAUDE.md (a pull request with a merge commit).

## 4. Snapshot

- Take a manual RDS snapshot named `pre-vX-Y-Z` (for v0.2.0: `pre-v0-2-0`) and wait until it is `available`. RDS names may contain only letters, digits and
  single hyphens, so the dots of the version become hyphens. Tag it with the date by which it must go:
  `aws rds create-db-snapshot --db-instance-identifier eveassist-prod --db-snapshot-identifier pre-vX-Y-Z --tags Key=purpose,Value=release-vX-Y-Z-delete-by-<date>`.
- Optional for code-only releases, mandatory when a migration is included.
- **A manual snapshot never expires.** It keeps every account that exists on the day, including accounts users delete later, and the privacy
  policy promises that deleted data leaves the backups within 28 days. Delete the snapshot when the release is accepted, and within 7 days
  at the latest (`aws rds delete-db-snapshot --db-snapshot-identifier pre-vX-Y-Z`). The monthly task "list manual snapshots" below catches
  one that was forgotten.

## 5. Deploy

On the instance (`/opt/eveassist/Infrastructure/prod`; the directory is owned by root, so write with `sudo`):

0. Optional, for a restart that takes more than a few seconds: silence `eveassist-prod-upstream-unreachable` (and `eveassist-prod-site-down`
   for a long one), so that a planned restart does not send alert emails. The commands are in [Common commands](common-commands.md),
   "Alarms". Switch them back on in step 6.

1. Put the new digest into `images.env` (`sudo nano images.env`; the file exists on the instance only). Commit nothing secret.
2. `sudo ./start.sh app`. The script loads the three secrets from Secrets Manager, logs in to ECR and runs `docker compose up -d`, which
   pulls the pinned image. Do not run `docker compose` by hand in this directory: Compose needs every secret, and exporting them yourself
   is what `start.sh` exists to avoid. Flyway runs at start. Keycloak and Caddy are not touched.
3. `sudo docker logs -f eveassist-app-1` until it reports started. Any Flyway error, stop and go to "Rolling back".

## 6. Verify

0. If you silenced alarms in step 5, switch them back on and run the check at the end of "Alarms" in [Common commands](common-commands.md).

- [ ] The footer shows the new commit id.
- [ ] Health endpoint is up, the CloudWatch alarm is not firing.
- [ ] Sign in with an existing account, open Characters and Inventory. Existing sessions survive an app restart (they
      are in Postgres), so a user who stays signed in is a good sign.
- [ ] Open the specific feature changed, in its normal and error cases.
- [ ] `/try` still answers and its limits still count per visitor.
- [ ] Log lines: no new ERROR pattern, no `Connection is not available`, no ESI 420 or 429 in the first 15 minutes.
- [ ] `hikaricp.connections.pending` is near 0 (`/actuator/metrics/` on management port 8081, through the tunnel in the initial runbook, step 9).

## 7. Record

Append to `documentation/runbooks/deploy-log.md`: date, version, image digest, snapshot id, anything surprising.
Tell testers what changed, and if they must re-authorize characters.

## Rolling back

| Situation | Action |
|---|---|
| App starts but is wrong, **no migration** ran, or the migration was backward compatible | Put the previous digest back, `docker compose up -d app`. |
| Flyway failed halfway | Do **not** start the app on the half-migrated database. Restore the `pre-vX-Y-Z` snapshot to a new RDS instance (see "Restoring the database"), point the stack at it, start the previous digest. Switch the endpoint, then delete the damaged instance after a week. |
| Data was damaged by the release | Same as above. Point-in-time recovery gives a time just before the deploy. Tell users that changes since then are lost. |
| Keycloak realm import broke sign-in | Follow "If it goes wrong" in [keycloak-realm.md](keycloak-realm.md). |

After any rollback, write what happened in the deploy log and fix it forward in the next release. Do not edit a merged
migration. **If you silenced alarms in step 5, switch them back on now**, also when the deploy failed and you never reached step 6
(the commands and the check are under "Alarms" in [Common commands](common-commands.md)): the site-down alarm is the only outage alert
that does not depend on the instance.

### Restoring the database

For a rollback and for the six-monthly restore test. A restore brings back the database as it was at the snapshot, **including accounts that
users deleted since**. The privacy policy says deleted data is gone from live systems, so the steps below delete them again before the site
is used.

1. Stop the app so that nobody writes while you work: `sudo docker compose -p eveassist stop app` (from a directory without a Compose file).
   Visitors get an error page until it is back. Anything written to the live database after the snapshot is lost in a rollback, and anything
   written during a restore *test* (including an account deletion) goes to the copy and is lost when it is deleted, so do the test when
   nobody is expected, or with the app stopped.
2. Before you restore, save the list of account ids of the live database (ids only, no names): `select public_id from account order by 1`,
   with the PostgreSQL client container as `db-setup.sh` uses it.
3. Restore with these settings (all of them matter; the values are in `ops.env`, see [Common commands](common-commands.md)):
   ```bash
   aws rds restore-db-instance-from-db-snapshot --db-instance-identifier eveassist-restore \
     --db-snapshot-identifier pre-vX-Y-Z --db-instance-class db.t4g.small --db-subnet-group-name eveassist-prod \
     --vpc-security-group-ids "$EVEASSIST_RDS_SG" --no-publicly-accessible --no-multi-az --backup-retention-period 7
   ```
   Without `--vpc-security-group-ids` the instance gets the VPC's default group, and without `--no-publicly-accessible` it can be reachable
   from the internet, which the privacy policy says it is not (the subnets here route to an internet gateway). For a test or a rehearsal use
   `--backup-retention-period 0 --no-deletion-protection` instead of 7 and delete it afterwards (`--skip-final-snapshot --delete-automated-backups`);
   for a rollback that becomes production keep 7 and also add `--deletion-protection`, and compare the result with
   `aws rds describe-db-instances --db-instance-identifier eveassist-prod` (parameter group, encryption, backup window).
4. When it is `available`, check `PubliclyAccessible` is `false` and the security group is `eveassist-rds`.
5. Compare its account ids with the list from step 2. An id that is in the restore and not in the list was deleted after the snapshot: delete
   that account again (`delete from account where public_id = '...'` cascades through the account's tables; delete its Keycloak user with
   kcadm, see `Infrastructure/prod/admin-with-kcadm.md`), and its sessions (`delete from spring_session where principal_name = '<keycloak sub>'`; the Keycloak subject is the session's principal name). Write the number of accounts you re-deleted in
   the deploy log (no ids, no names).
6. For a rollback: set `RDS_HOST` in `prod.env` (`sudo`) to the new endpoint and restart the whole stack (`cd ~ && sudo docker compose -p
   eveassist down`, then `sudo ./start.sh`), because Keycloak and the app both read it. For a test: do the same, check sign-in and your data,
   then put the old value back and restart the stack the same way, and delete the restored instance and any snapshot you took for it.

## Special updates

### SDE reload (after an EVE patch)

1. Download the new dump from Fuzzwork. Record its date.
2. Rehearse on a restored copy of prod inside AWS, as in step 2. Check that blueprint and type ids the plans use still exist.
3. Snapshot (step 4).
4. Stop the app. Load the new `sde` schema as the initial runbook step 7.2 does. The load replaces the schema, so
   do it in a transaction or load to a new schema name and swap. The App will not start without it.
5. Start the app, open a plan, check a known blueprint's materials against the game.

### Rotating a secret

- App or Keycloak client secret, EVE application secret, DB password: change it in Secrets Manager, then the other side
  (EVE application, Keycloak realm via keycloak-realm.md, or `ALTER ROLE`), then restart the affected container. Change
  and restart within one window, because the two sides must agree.
- Token data key: add a new key id, run the one-off re-encryption job, retire the old key only when no row uses it
  ([identity-and-tokens.md](../architecture/identity-and-tokens.md#storing-refresh-tokens)).

### Adding an ESI scope

1. Add it to the character-linking EVE application's registered scopes first (a request may ask for fewer than the
   registration lists, never more).
2. Deploy the code. The feature toggle asks for it only when turned on.
3. Confirm it is read-only. A write scope is not allowed (gain-trust.md).

### Recurring tasks

| Task | When | Next |
|---|---|---|
| Restore test (snapshot, restore, start the stack on it, delete): follow "Restoring the database", with the app stopped or when nobody is expected | every 6 months | by 2027-05-12 |
| List manual RDS snapshots and delete the ones that are not needed (they never expire, and the privacy policy promises 28 days): `aws rds describe-db-snapshots --snapshot-type manual` | monthly | 2026-12-12 |
| Check that no alarm was left silenced (the check under "Alarms" in [Common commands](common-commands.md)) | monthly | 2026-12-12 |
| OS security updates, and Keycloak, Caddy and Postgres patch review | monthly | 2026-12-12 |
| Certificates: Caddy renews them by itself. The current ones end 2027-02-10, so look at the dates in January | yearly check | 2027-01-15 |
| Domain `eveassist.net`: auto-renew is on, expires 2027-08-04 | yearly check | 2027-07-01 |
| `security.txt` expiry (the app sets it; it ends 2027-11-11) | yearly check | 2027-10-15 |

### Patching the host and containers

Monthly: OS security updates, then `docker compose pull` for Keycloak and Caddy on a quiet day, restart one service at a
time. A Keycloak major version follows "Moving to a newer Keycloak" in keycloak-realm.md and is rehearsed on a restored copy.

## Changing the Caddyfile

Compose mounts `Caddyfile` into the Caddy container as a single file, and Docker pins such a mount to the file's inode. Replacing
the file (`mv`, `cp` over a new file, an editor that saves by renaming) leaves the container reading the old content, and
`caddy reload` then says `config is unchanged`. Do this instead:

1. On the instance, keep a copy (`cp Caddyfile Caddyfile.bak`), write the new file, and `diff` the two.
2. `docker exec <project>-caddy-1 caddy validate --config /etc/caddy/Caddyfile` only checks what the container sees, so it cannot
   prove the new file is the one in use. Restart the container with `docker restart <project>-caddy-1` (1 to 3 seconds of dropped
   connections; certificates are in a volume, sessions in Postgres).
3. Check that the container sees the change (`docker exec <project>-caddy-1 grep -c <something new> /etc/caddy/Caddyfile`) and test
   from outside. Use `docker exec`, not `docker compose exec`: Compose needs the variables `start.sh` sets and fails without them.
