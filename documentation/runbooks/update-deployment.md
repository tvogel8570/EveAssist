# Deploying an Update

Status: **draft**, written 2026-11-11. The steps assume the prod set-up of
[Initial deployment](initial-deployment.md). There is no staging environment, so the rehearsal on the *EC2 computer*
(step 4) is the staging.

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
| Keycloak or Postgres major upgrade | Own rehearsal with a restore of prod data (a *rehearsal database*) | Never in the same release as an app change |

## Terminology

These terms are written in italics throughout this runbook.

Computers:

1. *Development computer*: the computer that holds the local git repository, where you run `git`, `./mvnw`, `docker buildx` and the `aws`
   commands, and where `ops.env` lives.
2. *EC2 computer*: the computer on AWS that runs the *prod stack*. Its scripts and files are in `/opt/eveassist/Infrastructure/prod`.

Things that run or are stored on them:

3. *Prod stack*: the containers on the *EC2 computer* (app, Keycloak, Caddy) that `start.sh` starts. It serves real users.
4. *Prod database*: the RDS instance `eveassist-prod`.
5. *Snapshot*: the manual RDS snapshot `pre-vX-Y-Z` of the *prod database*, taken in step 3 (`$SNAPSHOT` in the commands).
6. *Rehearsal database*: the throwaway RDS instance `eveassist-rehearsal`, restored from the *snapshot* and deleted after the rehearsal.
   It holds real users' data.
7. *Rehearsal container*: the one throwaway container of the new app image that `rehearse-app.sh` runs against the *rehearsal database*.

"Instance" on its own always means an RDS instance. Say "*EC2 computer*" for the machine.

## If you lose your place

A dropped connection or a closed terminal loses your shell and its variables. Run this on the *development computer*; it only reads, and it
looks at what exists (local images, ECR, the *snapshot*, the *rehearsal database*, the tag) and says which section is next:

```bash
cd ~/git/EveAssist
./Infrastructure/prod/release-status.sh 0.3.0        # the release version
```

It cannot see the *EC2 computer*, so for sections 5 and 6 look at `images.env` and `sudo docker ps` there, and at the footer of the site.
Your shell history (`~/.bash_history`) shows the last commands you ran. To keep it useful, put `export HISTTIMEFORMAT='%F %T '` (times) and
`PROMPT_COMMAND='history -a'` (each command saved at once, not only when the shell exits) in `~/.bashrc`. Then set `VERSION` again and load
`ops.env` (section 2, steps 3 and 4) before you continue.

## 1. Release readiness

- [ ] The change is on `development`, `./mvnw verify` passes there (includes architecture checks and `MigrationNamingTest`).
- [ ] Documentation that the change touches is updated in the same PR (CLAUDE.md rule on architecture drift).
- [ ] No handoff section left in the PR description.
- [ ] Privacy review done. Run the `privacy-review` skill (`/privacy-review`, in Claude Code on the *development computer*) over everything this
      release adds: all commits since the last release, not only the newest PR. The skill reviews a PR or a branch against `origin/development`, so
      give it the release PR (`development` to `main`) if it is open, or tell it the range to review, from the last release tag to the tip of
      `development`:
      ```bash
      git fetch origin --tags
      git tag --list 'v*' --sort=-v:refname | head -1        # the last release; the range is <that tag>..origin/development
      ```
      The skill recommends; it does not edit the policy. Act on every recommendation before you build: a change to the privacy policy
      (`App/src/main/resources/templates/privacy.html`) is app code, so it goes through a PR into `development` and is part of the image you
      build in section 2. Stop on any write scope or anything that looks like an ad or a payment, because the license and the public commitment
      forbid them. It is not a legal review: take a recommendation that touches a legal duty to the developer.
- [ ] The result is noted for the deploy log (section 7): "privacy review: no change needed", or the pull request that changed the policy.
- [ ] Release notes written (what changed, any sign-out, any downtime).

## 2. Build and publish the image

Run this section on the *development computer*, in one shell, from the repository root. Every `aws` command in this runbook that runs there
names the profile (`--profile EveAssist-admin`), so it does not matter which profile is the default. The commands that run on the *EC2 computer*
(`start.sh` and the other scripts) use the instance role and never name a profile: the SSO profile does not exist there.

1. Build from the commit that is the tip of `development`, after `./mvnw verify` passed on it, and do not rewrite history afterwards: the
   footer shows the commit id, and the id must stay a commit that exists on GitHub (the first release's footer showed an id that a later
   history rewrite removed).
2. Confirm that `ops.env` exists on the *development computer*, is not world-readable, and has every value filled in. If it is missing, create it from
   `template_ops.env` as "Values" in [Common commands](common-commands.md) describes (the template gives the lookup command for each value).
   ```bash
   cd ~/git/EveAssist
   ls -l Infrastructure/prod/ops.env                                   # must exist; mode -rw------- is expected
   ./Infrastructure/prod/ops-env-values.sh  # no lines with DIFFER
   ```
3. Load it into this shell, then check that the values this release needs are set (a value is never printed, so the check is safe to paste
   into a ticket). Do this in the same shell you use for the rest of the runbook; a new terminal starts without them.
   ```bash
   set -a; . Infrastructure/prod/ops.env; set +a
   for v in EVEASSIST_ACCOUNT_ID EVEASSIST_INSTANCE_ID EVEASSIST_RDS_HOST EVEASSIST_RDS_SG; do
     [ -n "${!v}" ] && echo "ok       $v" || echo "MISSING  $v"
   done
   aws sts get-caller-identity --profile EveAssist-admin --query Account --output text | grep -qx "$EVEASSIST_ACCOUNT_ID" \
     && echo "ok       signed in to the account in ops.env" \
     || echo "WRONG ACCOUNT or not signed in: aws sso login --profile EveAssist-admin --use-device-code"
   ```
4. CI builds, tests and pushes the images to ECR **[to build]**. Until then, build locally for the *EC2 computer*'s CPU, tagging
   `<version>-<short commit>` (ECR tags are immutable, so a tag can be pushed only once):
   ```bash
   # The registry host: the account id from ops.env (section 2, step 3). The repositories eveassist-app and eveassist-keycloak sit directly under it.
   # The :? makes the line stop with an error if ops.env is not loaded, instead of building a host without the account id.
   ECR=${EVEASSIST_ACCOUNT_ID:?load ops.env first (section 2, step 3)}.dkr.ecr.us-west-2.amazonaws.com
   COMMIT=$(git rev-parse --short HEAD)
   # replace X.Y.Z with the release version, for example 0.3.0. Exported, so that release-status.sh ("If you lose your place") can see it.
   export VERSION=X.Y.Z
   ```
   
   ```bash
   # App
   (cd App && docker buildx build --builder eveassist-arm --platform linux/arm64 \
     --build-arg GIT_COMMIT=$COMMIT --build-arg APP_VERSION=$VERSION \
     -t $ECR/eveassist-app:$VERSION-$COMMIT --load .)
   ```

   **Keycloak: build it only when its image would change.** The image is made from three files only (`Dockerfile`, `.dockerignore` and the
   realm file `EveAssist-realm-export.json`: the build context holds nothing else). The tag of the newest Keycloak image in ECR ends with the
   commit it was built from, so compare those three files at that commit with `HEAD`. If nothing differs, do not rebuild: a rebuild of the same
   files can still give a different image (the `ubi9` base and `curl` are not pinned), and that image would never have been rehearsed. The
   running Keycloak image stays, and `KEYCLOAK_IMAGE` in `images.env` stays as it is. This assumes the newest Keycloak image in ECR is the one
   that runs; confirm that against `images.env` on the *EC2 computer* if you pushed one without deploying it.
   ```bash
   KC_PATHS="Infrastructure/keycloak/Dockerfile Infrastructure/keycloak/.dockerignore Infrastructure/keycloak/EveAssist-realm-export.json"
   KC_LAST=$(aws ecr describe-images --profile EveAssist-admin --repository-name eveassist-keycloak \
     --query 'sort_by(imageDetails[?imageTags!=`null`],&imagePushedAt)[-1].imageTags[0]' --output text)
   KC_COMMIT=${KC_LAST##*-}                                    # the tag is <version>-<commit>
   echo "newest Keycloak image in ECR: $KC_LAST (built from $KC_COMMIT)"
   git cat-file -e "$KC_COMMIT^{commit}" || echo "commit $KC_COMMIT is not in this clone: run git fetch, then repeat"
   if git diff --quiet "$KC_COMMIT" HEAD -- $KC_PATHS; then
     KC_BUILD=no;  echo "Keycloak unchanged since $KC_COMMIT: do NOT build it"
   else
     KC_BUILD=yes; echo "Keycloak CHANGED since $KC_COMMIT: build it"; git diff --stat "$KC_COMMIT" HEAD -- $KC_PATHS
   fi
   ```
   Build it only when the check said `KC_BUILD=yes` (a changed realm file also needs [keycloak-realm.md](keycloak-realm.md), because the
   import is skipped when the realm already exists):
   ```bash
   [ "$KC_BUILD" = yes ] && (cd Infrastructure/keycloak && docker buildx build --builder eveassist-arm --platform linux/arm64 \
     -t $ECR/eveassist-keycloak:$VERSION-$COMMIT --load .)
   ```
5. Before pushing, check what is inside, without running anything: the image is `arm64`, the app image's `GIT_COMMIT` is the commit, and the jar in
   it has the same SHA-256 as `App/target/app-*.jar` from the verify run. If you built Keycloak, its image is `arm64` too and its realm file
   equals the one in git.
   ```bash
   docker image inspect --format '{{.Architecture}}' $ECR/eveassist-app:$VERSION-$COMMIT
   docker image inspect --format '{{json .Config.Env}}' $ECR/eveassist-app:$VERSION-$COMMIT | tr ',' '\n' | grep GIT_COMMIT
   CID=$(docker create $ECR/eveassist-app:$VERSION-$COMMIT)
   docker cp $CID:/app/app.jar /tmp/image-app.jar && docker rm $CID
   sha256sum /tmp/image-app.jar App/target/app-*.jar        # the two hashes must match
   ```
   Only if you built Keycloak:
   ```bash
   if [ "$KC_BUILD" = yes ]; then
     docker image inspect --format '{{.Architecture}}' $ECR/eveassist-keycloak:$VERSION-$COMMIT
     KCID=$(docker create $ECR/eveassist-keycloak:$VERSION-$COMMIT)
     docker cp $KCID:/opt/keycloak/data/import/EveAssist-realm-export.json /tmp/image-realm.json && docker rm $KCID
     diff /tmp/image-realm.json Infrastructure/keycloak/EveAssist-realm-export.json && echo "realm file equals git"
   fi
   ```
6. Push, then read each **digest** back from ECR. The *EC2 computer* pins digests, not tags, so what runs is exactly what was tested.
   ```bash
   aws ecr get-login-password --profile EveAssist-admin | docker login --username AWS --password-stdin $ECR
   docker push $ECR/eveassist-app:$VERSION-$COMMIT
   aws ecr describe-images --profile EveAssist-admin --repository-name eveassist-app \
     --image-ids imageTag=$VERSION-$COMMIT --query 'imageDetails[0].imageDigest' --output text
   ```
   Only if you built Keycloak:
   ```bash
   if [ "$KC_BUILD" = yes ]; then
     docker push $ECR/eveassist-keycloak:$VERSION-$COMMIT
     aws ecr describe-images --profile EveAssist-admin --repository-name eveassist-keycloak \
       --image-ids imageTag=$VERSION-$COMMIT --query 'imageDetails[0].imageDigest' --output text
   fi
   ```

## 3. Snapshot

Run this section on the *development computer*, with `ops.env` loaded (section 2, steps 2 and 3).

- Take a manual RDS *snapshot* and wait until it is `available`. It is named `pre-v` plus the version with the dots turned into hyphens (version
  `0.2.0` gives `pre-v0-2-0`), because RDS names may contain only letters, digits and single hyphens. The name and the date by which it must go
  are worked out from `VERSION` of section 2, step 4 (set it again if this is a new shell). Tag the *snapshot* with that date. `--no-cli-pager` keeps the AWS CLI from opening its output in `less`, which would swallow the next lines of a pasted block:
  ```bash
  SNAPSHOT=pre-v${VERSION:?set VERSION first (section 2, step 4)}
  SNAPSHOT=${SNAPSHOT//./-}
  DELETE_BY=$(date -d '+7 days' +%F)
  aws rds create-db-snapshot --profile EveAssist-admin --no-cli-pager --db-instance-identifier eveassist-prod --db-snapshot-identifier $SNAPSHOT \
    --tags Key=purpose,Value=release-$SNAPSHOT-delete-by-$DELETE_BY \
    --query 'DBSnapshot.[DBSnapshotIdentifier,Status]' --output text
  aws rds wait db-snapshot-available --db-snapshot-identifier $SNAPSHOT --profile EveAssist-admin
  ```
- Optional for code-only releases, mandatory when a migration is included.
- A manual *snapshot* never expires: delete it after the release is accepted (section 7, step 5).

## 4. Rehearse on a copy of prod

Needs the app digest pushed in section 2 (step 6) and the *snapshot* of section 3. Do not go on to the deploy until it passed. A release with no
migration can skip the rehearsal only by an explicit decision, which you note in the deploy log (the 2026-11-12 release skipped the Keycloak part
that way).

1. Restore the *snapshot* of section 3 to the *rehearsal database*, a private, temporary RDS instance, as "Restoring the database" below describes, with
   `--backup-retention-period 0 --no-deletion-protection` (a throwaway copy of real data must not make backups of its own, and must be
   deletable). Do not copy a dump of prod to the *development computer*: the privacy policy says the data is kept at AWS, and a file on a laptop is also
   in its backups and disk images. On the *development computer* use the dev database with generated data.
   From the *development computer*, with `ops.env` loaded (`set -a; . Infrastructure/prod/ops.env; set +a`):
   ```bash
   aws rds restore-db-instance-from-db-snapshot --profile EveAssist-admin --no-cli-pager --db-instance-identifier eveassist-rehearsal \
     --db-snapshot-identifier $SNAPSHOT --db-instance-class db.t4g.small --db-subnet-group-name eveassist-prod \
     --vpc-security-group-ids "$EVEASSIST_RDS_SG" --no-publicly-accessible --no-multi-az \
     --backup-retention-period 0 --no-deletion-protection \
     --query 'DBInstance.[DBInstanceIdentifier,DBInstanceStatus]' --output text
   aws rds wait db-instance-available --db-instance-identifier eveassist-rehearsal --profile EveAssist-admin
   aws rds describe-db-instances --profile EveAssist-admin --db-instance-identifier eveassist-rehearsal \
     --query 'DBInstances[0].[Endpoint.Address,PubliclyAccessible]' --output text
   ```
2. On the *EC2 computer*, run the new build against the *rehearsal database*. `Infrastructure/prod/rehearse-app.sh` starts the *rehearsal container* of the new
   image on the Compose network, memory capped at 900 MB, with Docker's own log driver (so its lines cannot trip the prod alarms) and no
   published port, waits up to 3 minutes for the health endpoint, prints the Flyway and start-up lines, and removes the *rehearsal container*. Copy it
   to the *EC2 computer* like the other scripts. Check `free -m` first: stop if less than about 1,200 MB is available, because the *prod stack*
   keeps running. **A pass** is health `UP`, "Successfully validated N migrations", and either "No migration necessary" or the new
   migration applied in the time you expect. The *rehearsal database* holds the same linked characters and tokens as prod, so the rehearsal may make one
   round of ESI calls for them.
   ```bash
   free -m
   cd /opt/eveassist/Infrastructure/prod
   sudo ./rehearse-app.sh <endpoint of the rehearsal database> <account id>.dkr.ecr.us-west-2.amazonaws.com/eveassist-app@sha256:<digest>
   ```
3. What this does not test: the Keycloak image (to rehearse a Keycloak change, start its image the same way against the `keycloak`
   database of the *rehearsal database*: the 2026-11-12 release skipped that on purpose), and anything seen in a browser. Those are checked after the deploy (step 6).
4. Time the migration. A table rewrite on a large table (assets, price rows, contracts) is the thing to look for.
   More than a couple of minutes means a different migration (add column nullable, backfill in batches, then constrain
   in a later release).
5. From the *development computer*, delete the *rehearsal database* and check that no instance, snapshot or retained backup of it remains. It holds real users' data, so do
   not keep it.
   ```bash
   aws rds delete-db-instance --profile EveAssist-admin --no-cli-pager --db-instance-identifier eveassist-rehearsal \
     --skip-final-snapshot --delete-automated-backups
   aws rds wait db-instance-deleted --db-instance-identifier eveassist-rehearsal --profile EveAssist-admin
   # each of these must show nothing for eveassist-rehearsal
   aws rds describe-db-instances --profile EveAssist-admin --query 'DBInstances[].DBInstanceIdentifier'
   aws rds describe-db-snapshots --profile EveAssist-admin --query 'DBSnapshots[].DBSnapshotIdentifier'
   aws rds describe-db-instance-automated-backups --profile EveAssist-admin --query 'DBInstanceAutomatedBackups[].DBInstanceIdentifier'
   ```

### Migration rules

- Forward only, never edit a merged migration (`documentation/architecture/data.md`).
- Make each release safe to run on the **previous** image for one deploy: add before you use, remove only in a later
  release (expand then contract). Then rolling the image back does not need a database restore.
- A migration that cannot be made backward compatible is a "restore to roll back" release. Say so in the notes and take
  the *snapshot* in section 3 immediately before, not hours earlier.

## 5. Deploy

Start only when the rehearsal (section 4) passed and the *rehearsal database* is deleted.

On the *EC2 computer* (`/opt/eveassist/Infrastructure/prod`; the directory is owned by root, so write with `sudo`):

0. Optional, for a restart that takes more than a few seconds: silence `eveassist-prod-upstream-unreachable` (and `eveassist-prod-site-down`
   for a long one), so that a planned restart does not send alert emails. The commands are in [Common commands](common-commands.md),
   "Alarms". Switch them back on in step 6.

1. Put the new app digest into `images.env` (the file exists on the *EC2 computer* only), and the new Keycloak digest too if you built Keycloak. Commit nothing secret.
   ```bash
   cd /opt/eveassist/Infrastructure/prod
   sudo cp images.env images.env.bak-<running version, dots as hyphens, e.g. v0-2-0>   # the rollback target
   sudo nano images.env
   ```
2. Start the app. The script loads the three secrets from Secrets Manager, logs in to ECR and runs `docker compose up -d`, which
   pulls the pinned image. Do not run `docker compose` by hand in this directory: Compose needs every secret, and exporting them yourself
   is what `start.sh` exists to avoid. Flyway runs at start. Caddy is not touched, and neither is Keycloak unless you built it.
   ```bash
   sudo ./start.sh app
   # if Keycloak was built:  sudo ./start.sh app keycloak
   ```
3. Follow the log until the app reports started. Any Flyway error, stop and go to "Rolling back".
   ```bash
   sudo docker logs -f eveassist-app-1
   ```

## 6. Verify

0. If you silenced alarms in step 5, switch them back on and run the check at the end of "Alarms" in [Common commands](common-commands.md).

- [ ] The footer shows `build <VERSION> (<commit id>)`, for example `build 0.3.0 (76dede5)`: the version is what users and testers can name, the commit id is for you.
- [ ] Health endpoint is up, the CloudWatch alarm is not firing.
- [ ] Sign in with an existing account, open Characters and Inventory. Existing sessions survive an app restart (they
      are in Postgres), so a user who stays signed in is a good sign.
- [ ] Open the specific feature changed, in its normal and error cases.
- [ ] `/try` still answers and its limits still count per visitor.
- [ ] Log lines: no new ERROR pattern, no `Connection is not available`, no ESI 420 or 429 in the first 15 minutes.
- [ ] `hikaricp.connections.pending` is near 0 (`/actuator/metrics/` on management port 8081, through the tunnel in the initial runbook, step 9).

## 7. Record

Only after step 6 passed. Run on the *development computer*, in the shell of section 2 (the commands need `VERSION` and `COMMIT`; in a new shell set
them again to the release version and the short commit that was built).

1. Tag the release. The annotation names the footer id, the app digest, the Keycloak digest (or "Keycloak unchanged since <commit>") and anything
   the images do not show, such as a live realm setting that the image's realm file lacks. Tags `v*` are protected by the GitHub ruleset
   "release tags" (no deletion, no update, no bypass): to correct a wrong tag, remove that ruleset first. Tag only a deploy that was checked, so
   that `main` never claims a release that did not run.
   ```bash
   git tag -a v$VERSION $COMMIT -m "..."
   git push origin v$VERSION
   ```
2. Release to `main` as CLAUDE.md describes: a pull request from `development` with a merge commit, then a pull request from `main` back to `development`.
3. Append to `documentation/runbooks/deploy-log.md`: date, version, image digest, snapshot id, the result of the privacy review (section 1), anything surprising.
4. Tell testers what changed, and if they must re-authorize characters.
5. Delete the *snapshot* when the release is accepted, and within 7 days at the latest. **A manual *snapshot* never expires.** It keeps every
   account that exists on the day, including accounts users delete later, and the privacy policy promises that deleted data leaves the backups
   within 28 days. The monthly task "list manual snapshots" below catches one that was forgotten. If you skipped the *snapshot* (a code-only
   release), skip this step.
   ```bash
   SNAPSHOT=pre-v${VERSION:?set VERSION first}
   SNAPSHOT=${SNAPSHOT//./-}
   aws rds delete-db-snapshot --profile EveAssist-admin --no-cli-pager --db-snapshot-identifier $SNAPSHOT
   ```

## Rolling back

First confirm that the previous image still exists in ECR. The repositories keep only the last 10 images (a lifecycle rule, left as it is on
purpose: storage costs pennies), and an image that was pushed and never deployed counts too, so an old digest can have expired. List what is
there with "Container images in ECR" in [Common commands](common-commands.md) and look for the digest that `images.env` held before the deploy
(the old file is kept as `images.env.bak-<version>`, see the deploy log). If it is gone, the rollback is a rebuild of that commit, which gives a
different image, so treat it as a new release: rehearse it.

| Situation | Action |
|---|---|
| App starts but is wrong, **no migration** ran, or the migration was backward compatible | Put the previous digest back, `docker compose up -d app`. |
| Flyway failed halfway | Do **not** start the app on the half-migrated database. Restore the `pre-vX-Y-Z` *snapshot* to a new RDS instance (see "Restoring the database"), point the *prod stack* at it, start the previous digest. Switch the endpoint, then delete the damaged instance after a week. |
| Data was damaged by the release | Same as above. Point-in-time recovery gives a time just before the deploy. Tell users that changes since then are lost. |
| Keycloak realm import broke sign-in | Follow "If it goes wrong" in [keycloak-realm.md](keycloak-realm.md). |

After any rollback, write what happened in the deploy log and fix it forward in the next release. Do not edit a merged
migration. **If you silenced alarms in step 5, switch them back on now**, also when the deploy failed and you never reached step 6
(the commands and the check are under "Alarms" in [Common commands](common-commands.md)): the site-down alarm is the only outage alert
that does not depend on the *EC2 computer*.

### Restoring the database

For a rollback and for the six-monthly restore test. A restore brings back the database as it was at the snapshot, **including accounts that
users deleted since**. The privacy policy says deleted data is gone from live systems, so the steps below delete them again before the site
is used.

1. Stop the app so that nobody writes while you work: `sudo docker compose -p eveassist stop app` (from a directory without a Compose file).
   Visitors get an error page until it is back. Anything written to the live database after the snapshot is lost in a rollback, and anything
   written during a restore *test* (including an account deletion) goes to the copy and is lost when it is deleted, so do the test when
   nobody is expected, or with the app stopped.
2. Before you restore, save the list of account ids of the *prod database* (ids only, no names): `select public_id from account order by 1`,
   with the PostgreSQL client container as `db-setup.sh` uses it.
3. From the *development computer*, restore with these settings (all of them matter; the values are in `ops.env`, see [Common commands](common-commands.md)):
   ```bash
   aws rds restore-db-instance-from-db-snapshot --profile EveAssist-admin --no-cli-pager --db-instance-identifier eveassist-restore \
     --db-snapshot-identifier pre-vX-Y-Z --db-instance-class db.t4g.small --db-subnet-group-name eveassist-prod \
     --vpc-security-group-ids "$EVEASSIST_RDS_SG" --no-publicly-accessible --no-multi-az --backup-retention-period 7
   ```
   Without `--vpc-security-group-ids` the instance gets the VPC's default group, and without `--no-publicly-accessible` it can be reachable
   from the internet, which the privacy policy says it is not (the subnets here route to an internet gateway). For a test or a rehearsal use
   `--backup-retention-period 0 --no-deletion-protection` instead of 7 and delete it afterwards (`--skip-final-snapshot --delete-automated-backups`);
   for a rollback that becomes production keep 7 and also add `--deletion-protection`, and compare the result with
   `aws rds describe-db-instances --profile EveAssist-admin --db-instance-identifier eveassist-prod` (parameter group, encryption, backup window).
4. When it is `available`, check `PubliclyAccessible` is `false` and the security group is `eveassist-rds`.
5. Compare its account ids with the list from step 2. An id that is in the restore and not in the list was deleted after the snapshot: delete
   that account again (`delete from account where public_id = '...'` cascades through the account's tables; delete its Keycloak user with
   kcadm, see `Infrastructure/prod/admin-with-kcadm.md`), and its sessions (`delete from spring_session where principal_name = '<keycloak sub>'`; the Keycloak subject is the session's principal name). Write the number of accounts you re-deleted in
   the deploy log (no ids, no names).
6. For a rollback: set `RDS_HOST` in `prod.env` on the *EC2 computer* (`sudo`) to the new endpoint and restart the whole *prod stack* (`cd ~ && sudo docker compose -p
   eveassist down`, then `sudo ./start.sh`), because Keycloak and the app both read it. For a test: do the same, check sign-in and your data,
   then put the old value back and restart the stack the same way, and delete the restored instance and any snapshot you took for it.

## Special updates

### SDE reload (after an EVE patch)

1. Download the new dump from Fuzzwork. Record its date.
2. Rehearse on a *rehearsal database* inside AWS, as in section 4. Check that blueprint and type ids the plans use still exist.
3. Take the *snapshot* (section 3).
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
| List manual RDS snapshots and delete the ones that are not needed (they never expire, and the privacy policy promises 28 days): `aws rds describe-db-snapshots --profile EveAssist-admin --snapshot-type manual` | monthly | 2026-12-12 |
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

1. On the *EC2 computer*, keep a copy (`cp Caddyfile Caddyfile.bak`), write the new file, and `diff` the two.
2. `docker exec <project>-caddy-1 caddy validate --config /etc/caddy/Caddyfile` only checks what the container sees, so it cannot
   prove the new file is the one in use. Restart the container with `docker restart <project>-caddy-1` (1 to 3 seconds of dropped
   connections; certificates are in a volume, sessions in Postgres).
3. Check that the container sees the change (`docker exec <project>-caddy-1 grep -c <something new> /etc/caddy/Caddyfile`) and test
   from outside. Use `docker exec`, not `docker compose exec`: Compose needs the variables `start.sh` sets and fails without them.
