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

1. On the dev machine, restore the latest prod RDS snapshot dump into a throwaway database (use the restore steps of
   the initial runbook), point a local run of the new build at it, and let Flyway migrate it.
2. Open the pages the change touches, plus sign-in, Characters, Inventory and `/try`.
3. Time the migration. A table rewrite on a large table (assets, price rows, contracts) is the thing to look for.
   More than a couple of minutes means a different migration (add column nullable, backfill in batches, then constrain
   in a later release).
4. Delete the throwaway database. It holds real users' data, so do not keep it.

### Migration rules

- Forward only, never edit a merged migration (`documentation/architecture/data.md`).
- Make each release safe to run on the **previous** image for one deploy: add before you use, remove only in a later
  release (expand then contract). Then rolling the image back does not need a database restore.
- A migration that cannot be made backward compatible is a "restore to roll back" release. Say so in the notes and take
  the snapshot in step 4 immediately before, not hours earlier.

## 3. Build and publish the image

1. Tag the release: `git tag -a vX.Y.Z -m "..."` on the commit on `development` (releasing to `main` follows CLAUDE.md:
   fast-forward push, never squash).
2. CI builds, tests and pushes `eveassist-app` to ECR **[to build]**. Until then, build locally with
   `./mvnw verify` and `docker buildx build --platform linux/arm64 -t <ecr>/eveassist-app:vX.Y.Z --push .`.
3. Note the **image digest**. The instance pins digests, not tags, so what runs is exactly what was tested.

## 4. Snapshot

- Take a manual RDS snapshot named `pre-vX.Y.Z` and wait until it is `available`.
- Optional for code-only releases, mandatory when a migration is included.

## 5. Deploy

On the instance (`/opt/eveassist`):

0. Optional, for a restart that takes more than a few seconds: silence `eveassist-prod-upstream-unreachable` (and `eveassist-prod-site-down`
   for a long one), so that a planned restart does not send alert emails. The commands are in [Common commands](common-commands.md),
   "Alarms". Switch them back on in step 6.

1. Edit the pinned digest in the Compose file (or the env file the start script reads). Commit nothing secret.
2. `docker compose pull app`
3. `docker compose up -d app`. Flyway runs at start. Keycloak, Caddy and Postgres are not touched.
4. `docker compose logs -f app` until it reports started. Any Flyway error, stop and go to "Rolling back".

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
| Flyway failed halfway | Do **not** start the app on the half-migrated database. Restore the `pre-vX.Y.Z` snapshot to a new RDS instance, point the stack at it, start the previous digest. Switch the endpoint, then delete the damaged instance after a week. |
| Data was damaged by the release | Same as above. Point-in-time recovery gives a time just before the deploy. Tell users that changes since then are lost. |
| Keycloak realm import broke sign-in | Follow "If it goes wrong" in [keycloak-realm.md](keycloak-realm.md). |

After any rollback, write what happened in the deploy log and fix it forward in the next release. Do not edit a merged
migration.

## Special updates

### SDE reload (after an EVE patch)

1. Download the new dump from Fuzzwork. Record its date.
2. Rehearse on dev with a copy of prod, as in step 2. Check that blueprint and type ids the plans use still exist.
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
| Restore test (snapshot, restore, start the stack on it, delete) | every 6 months | by 2027-05-12 |
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
