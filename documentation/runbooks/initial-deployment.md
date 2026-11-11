# Initial Deployment to Prod

Status: **draft**, written 2026-11-11 before anything below has been run. Steps marked **[to build]** depend on code or
files that do not exist yet; see [Public launch review](../architecture/public-launch-review.md) for the gap list.
Replace every `<placeholder>`. Never paste a secret into a command line that lands in shell history: use
`aws secretsmanager ... --secret-string file://` or the console.

Do this once. Later releases follow [Deploying an update](update-deployment.md).

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

1. **KMS**: create a symmetric key `eveassist-token-key`. Key policy: administrators manage it, the instance role (step 6)
   may only `Encrypt`, `Decrypt`, `GenerateDataKey`. **[to build]** the KMS `DataKeyProvider`. Generate the wrapped
   data key once with `aws kms generate-data-key-without-plaintext --key-id <alias> --key-spec AES_256`, store the
   ciphertext blob (base64) in Secrets Manager as `EVE_TOKEN_DATA_KEY_WRAPPED`.
2. **RDS**: PostgreSQL, the same major version as the dev image (check `Infrastructure/postgres/Dockerfile`), single-AZ,
   `db.t4g.small` or larger, storage encrypted, **not publicly accessible**, security group `eveassist-rds`, automated
   backups 7 days. Master password into Secrets Manager.
3. **AWS Backup**: weekly plan, keep four.
4. **ECR**: repository `eveassist-app`, plus `eveassist-keycloak` if you build the Keycloak image yourself. Lifecycle
   rule: keep the last 10 images.
5. **CloudWatch**: log group `/eveassist/prod`, retention 30 days.

## 5. Secrets Manager

One secret per concern, JSON key/value, so that rotation of one does not touch the others:

| Secret | Keys |
|---|---|
| `eveassist/db` | `EVEASSIST_OWNER_PASSWORD`, `EVEASSIST_APP_PASSWORD`, `EVEASSIST_SDE_PASSWORD`, `KC_DB_PASSWORD`, master password |
| `eveassist/keycloak` | `KC_BOOTSTRAP_ADMIN_USERNAME`, `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KC_APP_CLIENT_SECRET`, `KC_EVE_CLIENT_ID`, `KC_EVE_CLIENT_SECRET` |
| `eveassist/app` | `EVE_LINK_CLIENT_ID`, `EVE_LINK_CLIENT_SECRET`, `EVE_TOKEN_DATA_KEY_WRAPPED`, `ESI_CONTACT_EMAIL` |

Use long random values (`openssl rand -base64 36`) for every password. Do not reuse the dev values.

## 6. The instance

1. Ubuntu LTS or Amazon Linux, `t4g.medium` (4 GB; Keycloak needs about 1 GB, the app about 1 GB) in a public subnet,
   security group `eveassist-ec2`, the Elastic IP, encrypted root volume.
2. IAM instance role `eveassist-ec2` with only: pull from the two ECR repositories, read the three secrets, use the KMS
   key (see 4.1), write to the log group, SSM core (for Session Manager).
3. Install Docker Engine and the Compose plugin. Turn on automatic security updates.
4. Clone the repository, or copy only `Infrastructure/prod/` **[to build]**, to `/opt/eveassist`.

## 7. Prepare the database (once)

From the instance, with the RDS master password:

1. Create the databases and roles with `Infrastructure/postgres/sql/init/3-init-eveassist.sh`, as the README describes
   for an existing server. It creates `eveassist`, `keycloak` and their roles.
2. Load the SDE into the `eveassist` database's `sde` schema **[to build]**: a variant of `postgres/load-sde.sh` that
   restores `latest-postgresschema.pgdump` to RDS with `pg_restore --no-owner --role=eveassist_owner`. Download the dump
   from Fuzzwork first, and write down its date, because the update runbook needs it.
3. Check: `psql` as `eveassist_app` can `select count(*) from sde."invTypes"` (use the table names in
   `documentation/sde/sde-schema.sql`).

## 8. Compose for prod **[to build]**

`Infrastructure/prod/compose.yaml`, with these differences from the dev file:

- No published ports for the app or Keycloak, no Postgres container, no `extra_hosts`. Only Caddy publishes 80 and 443.
- `app`: the ECR image pinned by digest, `SPRING_PROFILES_ACTIVE=prod`, `server.ssl` off, listening on the Compose
  network, `server.forward-headers-strategy=framework`, `APP_BASE_URL=https://<domain>`,
  `KEYCLOAK_ISSUER_URI=https://auth.<domain>/realms/EveAssist`, `EVEASSIST_DB_URL` pointing at RDS with `sslmode=require`.
- `keycloak`: `KC_HOSTNAME=https://auth.<domain>`, `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`,
  `KC_HTTPS_*` removed, `start --optimized --import-realm`, `KC_DB_URL` pointing at RDS. Brute-force protection on in
  the realm file.
- `caddy`: `reverse_proxy app:7444` for `<domain>`; `reverse_proxy keycloak:8080` for `auth.<domain>`, but
  **`respond 404` for `/admin/*` and `/realms/master/*`**. Add HSTS, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY`.
- Logging driver `awslogs`, group `/eveassist/prod`.
- Health checks for `app` (`/actuator/health` on an unpublished management port) and Keycloak (existing check).
- A start script that reads the three secrets into environment variables for `docker compose up`, with no `.env` file
  left on disk.

## 9. First start

1. Build and push the images (manually the first time, `docker buildx build --platform linux/arm64 ...` for a Graviton
   instance, then `docker push`). Note the digest.
2. On the instance, run the start script. Order: Keycloak first. Keycloak imports the realm because its database is
   empty. The app runs Flyway on start.
3. Watch the logs until the app reports it started and Keycloak reports `Import finished successfully` and no `ERROR`.
4. Create the real admin in Keycloak through an SSH or SSM tunnel to the admin console (not through the internet):
   named admin user, MFA, then delete the bootstrap admin. Give your own EVE account the ADMIN role.

## 10. Verify (launch gate)

Do every item. A failed item stops the launch.

- [ ] `https://<domain>` serves the landing page with a valid certificate. `http://` redirects.
- [ ] `https://auth.<domain>/admin/` and `/realms/master` answer 404 from the internet.
- [ ] Sign in with EVE, return to the app, an `account` row exists, the session cookie is `Secure; HttpOnly; SameSite=Lax`.
- [ ] Link a character with the default scopes, see its assets, the Characters page lists the granted scopes.
- [ ] Look at `character_token` in the database: the refresh token is ciphertext, and `EVE_TOKEN_DATA_KEY_WRAPPED` is
      the only place the key appears (not in the logs, not in the environment of the app in plaintext).
- [ ] Remove the character. The token is revoked at EVE (the page confirms) and the row is gone.
- [ ] `/try`: two different browsers on two networks are limited separately (proves forwarded headers). Fetching more
      than the limit prints "not priced" and does not error.
- [ ] Delete the test account and export its data **[to build]**. Keycloak user and app rows are gone.
- [ ] Footer shows the CCP © notice, the operator name, the commit id. `/.well-known/security.txt` answers.
- [ ] The privacy policy page exists and matches what is stored.
- [ ] A CloudWatch log line shows with no tokens or secrets. Trigger a test ERROR and the alarm emails.
- [ ] **Restore test**: take an RDS snapshot, restore it to a new instance, start the stack against it on your dev
      machine or a second instance, sign in, see data. Delete the restore afterwards.
- [ ] `docker compose down && up -d` on the instance: everything comes back, sessions survive (they are in Postgres).

## 11. Go live

1. Tell the first testers (small audience), with the early-preview wording and the feedback address.
2. Watch for the first hour: app logs, Hikari pending connections (`/actuator/metrics`, signed in as admin), ESI errors.
3. Keep a rollback ready: `docker compose stop app` plus a maintenance page from Caddy.

## 12. After launch

- Record the instance id, the image digest, the SDE dump date and the RDS snapshot id in the deployment log
  (a plain file `documentation/runbooks/deploy-log.md`, no secrets).
- Calendar: restore test every six months, Keycloak and Postgres patch review monthly, certificate and domain expiry.
- Move the first-deploy steps that you improvised back into this file.
