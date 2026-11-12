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
