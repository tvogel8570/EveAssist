# Initial Deployment to Prod

Status: **done on 2026-11-12** except the last launch-gate item (step 10: the test ERROR that makes the alarm email arrive) and go-live (step 11).
What was improvised on the way is now written into the steps below, and the day-to-day commands are in [Common commands](common-commands.md).
The original wording follows. See [Public launch review](../architecture/public-launch-review.md) for the gap list.
Replace every `<placeholder>`. Never paste a secret into a command line that lands in shell history: use
`aws secretsmanager ... --secret-string file://` or the console.

Do this once. Later releases follow [Deploying an update](update-deployment.md).

## What exists (2026-11-11, `us-west-2`, account $EVEASSIST_ACCOUNT_ID)

No secrets here, only identifiers, and the repository is public, so the identifiers are written as variables: `$EVEASSIST_INSTANCE_ID` and so
on. Their values are in `Infrastructure/prod/ops.env` (git-ignored; see "Values" in [Common commands](common-commands.md)).

| Thing | Id |
|---|---|
| Security groups | `eveassist-ec2` `$EVEASSIST_EC2_SG` (80, 443 tcp, 443 udp; no SSH), `eveassist-rds` `$EVEASSIST_RDS_SG` |
| Elastic IP | `$EVEASSIST_ELASTIC_IP`, `$EVEASSIST_EIP_ALLOCATION`, on the instance |
| DNS (Route53 zone `$EVEASSIST_ZONE_ID`) | A `eveassist.net` and `auth.eveassist.net`, TTL 300 |
| Instance | `$EVEASSIST_INSTANCE_ID`, `t4g.medium`, Ubuntu 24.04 arm64, 30 GB gp3 encrypted, in `us-west-2a`, IMDSv2 required with hop limit 2 (containers need 2), no key pair, reached by SSM only |
| Instance role | `eveassist-ec2`, inline policy `eveassist-prod` plus `AmazonSSMManagedInstanceCore` |
| KMS | key `$EVEASSIST_KMS_KEY_ID`, alias `eveassist-token-key` |
| RDS | `eveassist-prod`, PostgreSQL 18.6, `$EVEASSIST_RDS_HOST`, subnet group `eveassist-prod` |
| ECR | `eveassist-app`, `eveassist-keycloak` (tags immutable, scan on push, keep 10) |
| Logs | `/eveassist/prod`, 30 days |
| Secrets | `eveassist/db`, `eveassist/keycloak`, `eveassist/app`, and RDS's own `rds!db-...` |
| SDE loaded | Fuzzwork build 3687575, 2026-11-11 |

The other VPC, the stopped `t2.micro` and the `EveAssist-dev-*` security groups in the account are from an earlier stack and
are not used.

## 0. Before you start

- [ ] Every item under "Information the developer must provide" in the review is answered.
- [ ] Gaps 1, 3 to 10, 12 and 14 of the review are merged to `development` and `./mvnw verify` passes.
- [ ] A bad launch can be undone: you know how to stop the stack and take the DNS record away (section 10).

## 1. AWS account basics

1. Root account: MFA on, no access keys. Create an IAM admin user (MFA) for daily use.
2. Set one region for everything: `<region>`.
3. Billing alarm at `<ceiling>` USD, emailing `<ops-email>`.
4. One VPC (the default is fine). Two security groups:
   - `eveassist-ec2`: inbound 80 and 443 from anywhere, 22 from `<your-ip>/32` only (or none, and use SSM Session
     Manager, recommended).
   - `eveassist-rds`: inbound 5432 from `eveassist-ec2` only.

## 2. Domain and DNS

1. Point `<domain>` and `auth.<domain>` (A records) at an Elastic IP that you allocate now, so a replaced instance keeps
   the address.
2. Keep TTL low (300 s) until launch day is over.

## 3. EVE developer applications

At https://developers.eveonline.com, create two applications and write their ids and secrets into Secrets Manager (step 5):

| Application | Scopes | Callback |
|---|---|---|
| Sign-in (Keycloak) | `publicData` only | `https://auth.<domain>/realms/EveAssist/broker/eve/endpoint` |
| Character linking | every read-only scope any feature uses (see `Feature` in the `characters` module) | `https://<domain>/characters/link/callback` |

Check that no write scope is selected. The public commitment is read-only.

## 4. Data and key services

1. **KMS**: a symmetric key with the alias `eveassist-token-key`. The default key policy lets IAM decide, so access is
   the instance role's policy (step 6): **`Decrypt` only**, on this key only, with the condition
   `"StringEquals": {"kms:EncryptionContext:purpose": "eveassist-refresh-tokens"}`. The app's only KMS call is that unwrap at start
   (`KmsDataKeyProvider`), so the role cannot encrypt, generate data keys or decrypt for another purpose. Narrowed on 2026-11-12, see the
   deploy log. Making the wrapped data key below needs `kms:GenerateDataKey` and so your admin profile, from the dev machine, not the
   instance. Make the wrapped data
   key once, with the purpose that the app checks on every unwrap, and store the base64 text in Secrets Manager as
   `EVE_TOKEN_DATA_KEY_WRAPPED` without printing it:
   `aws kms generate-data-key-without-plaintext --profile EveAssist-admin --key-id alias/eveassist-token-key --key-spec AES_256 --encryption-context purpose=eveassist-refresh-tokens --query CiphertextBlob --output text`.
   Check it by unwrapping it and counting the bytes (32), and by trying a wrong purpose (refused). Losing the key or this
   blob makes every stored refresh token unreadable, so the key is never deleted and the blob lives in Secrets Manager.
2. **RDS**: PostgreSQL 18.6 (the dev image's version), single-AZ in the instance's zone, `db.t4g.small`, 20 GB gp3 that grows
   to 100, storage encrypted, **not publicly accessible**, security group `eveassist-rds`, automated backups 7 days,
   deletion protection on. Use `--manage-master-user-password`: RDS makes the master password and keeps it in its own secret
   (`rds!db-...`), so nobody handles it. The instance role has no standing permission to read that secret (removed on 2026-11-12, see the
   deploy log): add it to the role's `ReadOwnSecrets` statement only for `db-setup.sh init` or a check as the master user, then take it away.
3. **AWS Backup**: weekly plan, keep four.
4. **ECR**: repository `eveassist-app`, plus `eveassist-keycloak` if you build the Keycloak image yourself. Lifecycle
   rule: keep the last 10 images.
5. **CloudWatch**: log group `/eveassist/prod`, retention 30 days.

## 5. Secrets Manager

One secret per concern, JSON key/value, so that rotation of one does not touch the others:

| Secret | Keys |
|---|---|
| `eveassist/db` | `EVEASSIST_OWNER_PASSWORD`, `EVEASSIST_APP_PASSWORD`, `EVEASSIST_SDE_PASSWORD`, `KC_DB_PASSWORD` (the master password is in RDS's own secret) |
| `eveassist/keycloak` | `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KC_APP_CLIENT_SECRET`, `KC_ADMIN_CLIENT_SECRET` (the account-deletion service account; Keycloak and the app both need it), `KC_EVE_CLIENT_ID`, `KC_EVE_CLIENT_SECRET` |
| `eveassist/app` | `EVE_LINK_CLIENT_ID`, `EVE_LINK_CLIENT_SECRET`, `EVE_TOKEN_DATA_KEY_WRAPPED`, `ESI_CONTACT_EMAIL` |

Non-secret settings (domain names, the RDS endpoint, the KMS key alias) are in `Infrastructure/prod/prod.env` on the instance. The repository has only
`Infrastructure/prod/template_prod.env` (copy it, then fill in `RDS_HOST`); the real file is git-ignored.

Make the random values on the machine and load them with `--secret-string file://` from a private temporary file that is
shredded afterwards, so that no value is printed or reaches shell history.

Passwords are 40 letters and digits from `openssl rand` (no `/`, `+` or `=`, which break URLs and shell). Do not reuse the dev values.

## 6. The instance

1. Ubuntu LTS or Amazon Linux, `t4g.medium` (4 GB; Keycloak needs about 1 GB, the app about 1 GB) in a public subnet,
   security group `eveassist-ec2`, the Elastic IP, encrypted root volume.
2. IAM instance role `eveassist-ec2` with only: pull from the two ECR repositories, read the three secrets, use the KMS
   key (see 4.1), write to the log group, SSM core (for Session Manager).
3. Install Docker Engine and the Compose plugin. Turn on automatic security updates.
4. Copy `Infrastructure/prod/` and `Infrastructure/postgres/sql/init/` to `/opt/eveassist/Infrastructure/` (the layout `db-setup.sh` expects).
   The instance has no git checkout: the files were copied by hand through SSM (base64 in a shell command is enough for the small ones). The
   directory is owned by root, so every later write there (`Caddyfile`, `prod.env`) needs `sudo`, and `ssm-user` can only read it.
   `images.env` (the pinned digests) exists on the instance only.

## 7. Prepare the database (once)

From the instance, using `Infrastructure/prod/db-setup.sh`. It reads every password from Secrets Manager itself, so none is
typed or passed on a command line, and it runs the PostgreSQL client from the same image as the dev database. Set
`RDS_HOST` (the endpoint). `init` also needs `RDS_SECRET_ARN` (the RDS-managed master secret's ARN), because it is the only step that
connects as the master user; `sde` connects as `eveassist_sde`, which owns the `sde` schema, with the password from `eveassist/db`.

1. `./db-setup.sh init` creates `eveassist`, `keycloak` and their roles with `postgres/sql/init/3-init-eveassist.sh`, run
   as the master user. The master user is not a superuser, so the script grants itself the roles first. It is safe to run
   again.
2. `./db-setup.sh sde` (as `eveassist_sde`, so it needs no master secret and works again for every SDE reload) downloads Fuzzwork's
   latest schema dump, restores it into the `sde` schema and records the build in
   `sde.sde_version`. Write down that build and date, because the update runbook needs it. Load the same dump into the dev
   database before launch, so that what was tested is what runs.
3. Check as `eveassist_app` that `select count(*) from sde."invTypes"` returns rows, and that an `INSERT` into it is refused.

The instance role may not call `rds:DescribeDBInstances` (it is meant to be narrow), so a command on the instance cannot look up the
endpoint or the master secret's ARN: pass them in, as `RDS_HOST` and (for `init`) `RDS_SECRET_ARN` above.

The master secret is needed only for `init` (once) and for a restore check as the master user. The instance role's permission to read it is
therefore temporary: add it to the role's `ReadOwnSecrets` statement before `init`, and remove it afterwards. Everything else, including
the SDE reload, uses the roles in `eveassist/db`.

## 8. Compose for prod

Built: `Infrastructure/prod/` has `compose.yaml`, `Caddyfile`, `template_prod.env`, `images.env.template`, `template_ops.env` and `start.sh`; the images
come from `App/Dockerfile` and `Infrastructure/keycloak/Dockerfile`. What it does, and why:

- Only Caddy publishes ports (80, 443 and 443/udp). The app and Keycloak publish none, and there is no Postgres container.
  Keycloak's port 8080 is published on the instance's loopback address only, for the operator's tunnel.
- `app`: the ECR image pinned by digest, `SPRING_PROFILES_ACTIVE=prod`, plain HTTP on the Compose network,
  `server.forward-headers-strategy=native` (not `framework`: only the native Tomcat valve also replaces the remote
  address, which `/try` limits visitors by), health on management port 8081 that nothing publishes.
  The data key is `EVE_TOKEN_DATA_KEY_WRAPPED` with `EVE_KMS_KEY_ID`; the prod profile refuses `EVE_TOKEN_DATA_KEY`.
- `keycloak`: `KC_HOSTNAME=https://auth.<domain>`, `KC_HOSTNAME_ADMIN=http://localhost:8080` (the admin console is served
  there only, so a blocked public URL cannot break its own sign-in), `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`,
  `start --optimized --import-realm` with the realm baked into the image. Brute-force protection is on in the realm file.
- `caddy`: `reverse_proxy app:7444`, and on the auth host an allowlist: only `/realms/EveAssist*` and `/resources/*` go to
  `keycloak:8080`, **everything else (`/admin*`, `/realms/master*`, `/metrics*`, any new path) answers 404**, with HSTS, `X-Content-Type-Options: nosniff`, `Referrer-Policy` and `X-Frame-Options: DENY`.
  The Caddy container has both public names as network aliases, so the app reaches Keycloak by the issuer's own name inside
  the network, with a valid certificate. Certificates are in a volume that is kept, because Let's Encrypt limits issuing.
- Logging driver `awslogs`, group `/eveassist/prod`. Memory limits: Keycloak 1.5 GB, app 1.25 GB.
- Health checks: Keycloak on its management port, the app on 8081 (the image has no `curl`, so bash opens the socket).
- `start.sh` reads the three secrets into the environment of that one process, logs in to ECR and runs
  `docker compose up -d`. No `.env` file with secrets is left on disk.

Done 2026-11-12: the images were built for `linux/arm64`, pushed to ECR and pinned by digest in `images.env`, and `Infrastructure/prod/` was
copied to the instance (step 6.4).

Two things to know about the single-file mount of `Caddyfile`: a replaced file is not seen by the running container, so follow "Changing the
Caddyfile" in [update-deployment.md](update-deployment.md). And `docker compose down` run inside the stack's directory needs every secret,
because Compose reads `compose.yaml` and its `${VAR:?}` checks first: run `cd ~ && sudo docker compose -p eveassist down` instead (see
[Common commands](common-commands.md)).

## 9. First start

Open the shell with `prod` or `prod stack` from [Common commands](common-commands.md) (bash, and `sudo` for Docker). In plain `sh` a multi-line
paste goes wrong. Planned restarts raise the alarm `eveassist-prod-upstream-unreachable`; silence it first (same file, "Alarms").

1. Build and push the images (manually the first time, `docker buildx build --platform linux/arm64 ...` for a Graviton
   instance, then `docker push`). Note the digest.
2. On the instance, run the start script. Order: Keycloak first. Keycloak imports the realm because its database is
   empty. The app runs Flyway on start.
3. Watch the logs until the app reports it started and Keycloak reports `Import finished successfully` and no `ERROR`.
   `start.sh` prints Docker's warning `Your credentials are stored unencrypted in '/root/.docker/config.json'` (link
   https://docs.docker.com/go/credential-store/) before `Login Succeeded`. It is expected and harmless here: the ECR login token
   expires after 12 hours, the file is readable only by root, and no lasting registry credential exists (the instance pulls with
   its IAM role). To silence it, install `docker-credential-ecr-login` and set it as `credsStore` or `credHelpers` in that
   file, which also makes the login step unnecessary.
4. Create the real admin in Keycloak, not through the internet. The console works at `http://localhost:8080/admin/` through an SSM
   port-forward (`aws ssm start-session --profile EveAssist-admin --target <instance-id> --document-name AWS-StartPortForwardingSession --parameters '{"portNumber":["8080"],"localPortNumber":["8080"]}'`),
   but only if the browser runs where the plugin's loopback port is visible. From WSL with a **Windows browser** it is not: the plugin
   listens on WSL's `127.0.0.1` only, and WSL forwards to Windows only ports that listen on all interfaces. In that case do the same steps
   with `kcadm` in an SSM shell (the commands are in `Infrastructure/prod/admin-with-kcadm.md`): create a named admin with the `admin` role, set its password at
   kcadm's own prompt, check that it can sign in, turn on brute-force protection for the **master** realm (the realm file configures only
   `EveAssist`), then delete the bootstrap admin. Optional: an authenticator app for the named admin, which needs the console.
   Do **not** give your player account the ADMIN role: an admin account is separate and does not use the industry features
   (users.md), and nothing in the app needs ADMIN at launch, because metrics come through the tunnel. Make a second account for it when the admin area exists.
5. Metrics (heap, Hikari pool) are on the app's management port, not behind an admin sign-in: forward port 8081 the same way
   and open `http://localhost:8081/actuator/metrics`.
   From WSL with a Windows browser use `curl` in the WSL terminal instead of the browser, for the same reason as above.

## 10. Verify (launch gate)

Ticked items were checked on the live stack on 2026-11-12 (see the deploy log). Unticked items have no recorded check yet.

Do every item. A failed item stops the launch.

- [x] `https://<domain>` serves the landing page with a valid certificate. `http://` redirects.
- [x] `https://auth.<domain>/admin/`, `/realms/master`, `/metrics` and `/` answer 404 from the internet (the auth host is an allowlist).
- [x] Sign in with EVE, return to the app, an `account` row exists, the session cookie is `Secure; HttpOnly; SameSite=Lax`.
- [x] Link a character with the default scopes, see its assets, the Characters page lists the granted scopes.
- [x] Look at `character_token` in the database: the refresh token is ciphertext, and `EVE_TOKEN_DATA_KEY_WRAPPED` is
      the only place the key appears (not in the logs, not in the environment of the app in plaintext).
- [x] Remove the character. The token is revoked at EVE (the page confirms) and the row is gone.
- [x] `/try`: two different browsers on two networks are limited separately (proves forwarded headers). Fetching more
      than the limit prints "not priced" and does not error.
      Done 2026-11-12 without reaching the limit (it is high): the header chain was checked from two networks with a throwaway echo
      upstream, and the app's half by `ProdProfileIT`. See the deploy log.
- [x] Export the test account's data (account page, "Download my data"): the file has the account, characters, features and plans and no token.
      Then delete the account (account page, "Delete my account"): the token is revoked at EVE, the app rows are gone, the Keycloak user is gone
      (the admin console through the tunnel shows no such user), and the home page says it was deleted. This is the first use of the
      `eveassist-admin` service account against real Keycloak: if it says the sign-in record remains, check `KC_ADMIN_CLIENT_SECRET`,
      that the realm import created the client, and that the app can reach `http://keycloak:8080`.
- [x] Footer shows the CCP © notice, the operator name and the commit id (built with `--build-arg GIT_COMMIT=...`, otherwise it says `unknown`).
      `/.well-known/security.txt` answers with the contact and a future expiry.
- [ ] The privacy policy page (`/privacy`) matches what is stored. Read it against the schema once more if a migration added a table since it was written
      (last updated 2026-11-11). It says there are no access logs, which holds only while the Caddyfile has no `log` directive.
- [x] A CloudWatch log line shows with no tokens or secrets. Trigger a test ERROR and the alarm emails. Done 2026-11-12: a test ERROR line reached
      CloudWatch and put `eveassist-prod-errors` into ALARM, and CloudWatch recorded a successful notification to the alert topic (see the deploy log).
- [x] **Restore test**: take an RDS snapshot, restore it to a new instance, start the stack against it on your dev
      machine or a second instance, sign in, see data. Delete the restore afterwards.
- [x] `docker compose down && up -d` on the instance: everything comes back, sessions survive (they are in Postgres). Done 2026-11-12, see the deploy log.

## 11. Go live

1. Tell the first testers (small audience), with the early-preview wording and the feedback address.
2. Watch for the first hour: app logs, Hikari pending connections (`/actuator/metrics` through the tunnel to port 8081, see step 9), ESI errors.
3. Keep a rollback ready: `docker compose stop app` plus a maintenance page from Caddy.

## 12. After launch

- The deployment log (`deploy-log.md`, no secrets) holds the instance id, the image digests, the SDE dump date and the restore test. Add the
  go-live entry when it happens.
- Recurring tasks and their next dates are in [update-deployment.md](update-deployment.md), "Recurring tasks". Put them in your calendar.
- The steps improvised during the first deploy are now part of this file (steps 6.4, 7, 8, 9, and the SSM and alarm commands in
  [Common commands](common-commands.md)).
