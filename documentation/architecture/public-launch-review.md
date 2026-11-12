# Public Launch Review

Status: **proposed**, written 2026-11-11 from the architecture docs and the code on branch `plan-changes`.

Purpose: the developer wants EveAssist on the public internet to get feedback. This reviews the agreed design
([Deployment](deployment.md)) against what exists in the repository, lists the information needed, and ranks the risks.
The two runbooks that follow from it are [Initial deployment](../runbooks/initial-deployment.md) and
[Deploying an update](../runbooks/update-deployment.md).

## Summary

The design is sound for a feedback launch: one EC2 instance, Compose, Caddy, RDS. **Almost none of the prod side
is built yet.** The repository has a dev-shaped `Infrastructure/compose.yaml`, no App image, no CI, no KMS key provider,
no account deletion or data export, no privacy page and no CCP notice. The first six items below must be done before
anyone else signs in. They also match the commitments the requirements make "from the first prod deployment that anyone
other than the operator can sign in to".

## Gaps between design and repository

| # | Gap | Where the design says it | State today | Blocks launch? | Your answer |
|---|---|---|---|---|---|
| 1 | App container image and a prod Compose file | deployment.md | Only `Infrastructure/{postgres,keycloak}/Dockerfile`. `compose.yaml` publishes Postgres on 2345, binds dev certs and sets `KC_HOSTNAME=https://${HOST}:8442`. | Yes |  |
| 2 | GitHub Actions build, test, push to ECR | deployment.md | No `.github/`. | Yes (or a documented manual build) |  |
| 3 | KMS-wrapped token data key | identity-and-tokens.md | Only `EnvDataKeyProvider`. The design says the KMS version is "a second implementation later". | Yes. A key in an environment variable on an internet host is the one decision that makes a breach reveal every refresh token. |  |
| 4 | Account deletion and data export | non-functional.md (Privacy), users.md | `AccountController` has only `GET /account`. No delete, no export. | Yes, by the requirements |  |
| 5 | Privacy policy, operator named, `security.txt`, CCP © line | non-functional.md, gain-trust.md | No template or message mentions them. | Yes. The CCP notice is a license term. |  |
| 6 | Forwarded headers behind Caddy | esi.md (Visitors) | No `server.forward-headers-strategy` or `X-Forwarded` handling. `PriceFetchLimits` keys on the remote address, so behind Caddy **all visitors count as one** and `/try` is throttled for everyone after 200 fetches an hour. Redirect URIs and secure-cookie handling also depend on it. | Yes |  |
| 7 | Prod profile | deployment.md | `application.yaml` has `dev` and a `!test & !measure` block only. The `prod` profile (no dev certificate, `server.address`, forwarded headers, `server.ssl` off behind Caddy) does not exist. | Yes |  |
| 8 | Keycloak behind a proxy | not described | Needs `KC_PROXY_HEADERS=xforwarded`, `KC_HOSTNAME` without port, HTTP on the internal port. Compose sets the dev port and TLS files. | Yes |  |
| 9 | Health endpoints | deployment.md | Actuator exposes `metrics` only, for ADMIN. "Health is not exposed until a deployment needs it." | Yes, for Compose health checks and the alarm. Expose only `/actuator/health` on a management port that is not published. |  |
| 10 | Keycloak brute-force protection, admin console exposure | identity-and-tokens.md | Realm has `bruteForceProtected: false`. The admin console is served by the same Keycloak that browsers reach. | Yes. Block `/admin/` and `/realms/master` from the internet at Caddy and reach them through an SSH tunnel. |  |
| 11 | Security headers, CSP | gain-trust.md ("strict CSP with every page") | None found in `App/src/main`. | Should. HSTS, `X-Content-Type-Options`, `Referrer-Policy`, frame protection at Caddy; CSP in the app. |  |
| 12 | SDE in RDS | CLAUDE.md | `load-sde.sh` is written for the local container. The app will not start without the `sde` schema. | Yes. Needs an RDS variant of the step. |  |
| 13 | Realm placeholders | keycloak-realm.md | Realm file uses `${authBaseUrl}` and `${authAdminUrl}` as well as the `.env` names. Confirm they are filled in prod. | Check |  |
| 14 | Spring Session, Flyway roles on RDS | data.md | `3-init-eveassist.sh` creates the databases and roles for the local Postgres. RDS needs the same, run once by hand with the master user. | Yes |  |
| 15 | Fetch log, scope page, admin audit view | gain-trust.md | Proposed, partly built (Characters page). | No, but the trust message to testers depends on them. |  |

## Progress on the gaps (2026-11-11)

Built on branch `prod-deployment`: gaps 1 (files written; images not yet pushed), 3 (KMS provider, checked live), 4 (export and
deletion, including Keycloak's `eveassist-admin` service account), 5 (privacy policy, footer with the CCP notice, operator and commit,
`security.txt`), 6, 7, 8, 9, 10, 11 (headers at Caddy; the app's CSP is still open), 12 and 14. Still open: gap 2 (CI; a manual build for the
first release), gap 11's CSP, gap 13 (checked at the first start) and gap 15. The SDE version is not yet in the footer, because `identity`
may not depend on `sde`.

## Information the developer must provide

Collect these before starting the initial runbook. None are secrets to commit; secrets go to Secrets Manager.

1. **Domain.** `eveassist.net` is the design's choice. Registrar, DNS host, who controls it. Decide the Keycloak host:
   `auth.eveassist.net` (recommended, separate origin) or a path on the same host.
   1. `eveassist.net`
   2. Route53
   3. Route53
   4. me
   5. `auth.eveassist.net`
2. **AWS.** Account id, region, whether root has MFA, a billing alarm, and whether a separate IAM admin user exists.
   Pick the region once, because RDS, KMS, ECR and Secrets Manager must share it.
   1. account = $EVEASSIST_ACCOUNT_ID
   2. Region = us-west-2
   3. yes
   4. yes
   5. yes
3. **Two EVE developer applications** (https://developers.eveonline.com): one for Keycloak's "sign in with EVE", one
   for character linking with every planned read-only scope. For each, the production callback:
     - Keycloak broker: `https://auth.<domain>/realms/EveAssist/broker/eve/endpoint`
   - Linking: `https://<domain>/characters/link/callback`
   1. available
4. **Operator contact email** for ESI's `User-Agent` and for CCP. A mailbox that is watched (CCP can suspend access
   after three unanswered emails in a row).
   1. cass@vogelware.com 
5. **Other sign-in providers.** The realm has only the `eve` provider. Decide whether Google, Discord, Facebook go live
   at launch (each needs its own OAuth client and consent screen). Recommendation: EVE only at launch.
    1. only eve online
6. **Audience and cap.** Who gets the link, and whether to cap accounts. Nothing in the app limits sign-ups.
    1. I have list of people to invite.  no cap
7. **Privacy policy text and operator name** to show on the site.
    1. help me creaate this
8. **Budget ceiling** and an email for CloudWatch and billing alarms.
    1. done and aws alert setup
9. **Source visibility.** gain-trust.md promises a public repository and CI-built images. Decide whether the repo is
   public by launch. If it is not, drop that sentence from the pages.
    1. drop sentence

## Risks and mitigations

Ranked by what hurts most. "Owner" is the work item in the gaps table.

| Risk | Impact | Mitigation |
|---|---|---|
| **Token key and tokens leak.** Refresh tokens are read-only scopes, but they reveal assets and intel, and EVE players are targeted. | Loss of trust, possible CCP action | KMS-wrapped key (gap 3). Data key never in an env var in prod. Read-only scopes only. RDS not publicly accessible, security group allows the instance only. Encrypted RDS storage. Publish incident disclosure within one week (agreed). |
| **Keycloak admin console on the internet** with a weak or default bootstrap admin. | Full account takeover | Block at Caddy, tunnel for admin. Long random bootstrap password, delete the bootstrap admin after creating a named one, enable brute-force protection, MFA for the admin. |
| **`/try` abused** as a free ESI proxy or to burn the shared ESI allowance. It is unauthenticated, CSRF-exempt and calls ESI. | ESI error limit or ban for the application, which also takes down signed-in users | Fix gap 6 first. The developer decided on 2026-11-11 to use standard Caddy, which has no rate limiting. So there is no per-IP request limit at launch: only `PriceFetchLimits`. Add a cap on `state` size and an alarm on 429s. If `/try` is flooded, add a per-IP request limit in the app, or switch the proxy (nginx has `limit_req`). The 1,500 per 15 minutes global cap is a quarter of the market allowance, so it is a backstop, not a defence. |
| **ESI rate limits with real users.** Concurrency (8) and pool (10) are untested under load. | Slow pages, ESI 420, token refresh failures | Run the load checkpoint in esi.md and concurrency-tuning.md before inviting more than a handful. Start with a small audience (item 6). |
| **Single instance, single AZ, no staging.** | Downtime (accepted) or a bad deploy with no rehearsal | Say best effort on the site. Take an RDS snapshot before each deploy. Rehearse every deploy on the dev machine against a copy of prod data where migrations are involved. |
| **Irreversible migration plus bad release.** Flyway migrations never edit and the image rolls back but the schema does not. | Cannot roll back | Expand/contract migrations. Snapshot before deploy. Restore is the rollback of last resort and is tested (see Backups). |
| **Backups untested.** | Data loss on the first real failure | Do the restore test once before launch, not after six months. |
| **Keycloak realm drift.** Admin-console changes are lost at the next import. | Silent config loss | Treat the file in git as the only source. Follow keycloak-realm.md, which also signs everyone out. |
| **Secrets in the wrong place.** `.env`, `password.txt`, the Keycloak key files. | Leak | Secrets Manager, fetched at container start. `.gitignore` already covers filenames. Never `scp` secrets into a shell history. |
| **CCP license breach.** Missing © notice, any ad or donation link, tracking without consent. | CCP disables the application without notice | Gap 5 before launch. No analytics, no third-party fonts or scripts. |
| **Privacy law (GDPR) for EU testers.** Accounts hold name and email, plus character data. | Complaint, fine | Gap 4 and 5. Retention rules written down. Say where data is stored (the AWS region). |
| **SDE out of date after an EVE patch.** | Wrong recipes and costs | Update procedure in the update runbook. Note the SDE version on the site footer. |
| **Cost run-away** from CloudWatch logs, NAT, snapshots, data transfer. | Surprise bill | Billing alarm at the ceiling. No NAT gateway (instance in a public subnet, locked down by security group). Log retention 30 days. |
| **Domain or certificate lapse.** Let's Encrypt renewal fails behind a firewall. | Site down, sign-in broken | Caddy needs ports 80 and 443 open. Alarm on certificate expiry (the health check over HTTPS). Keep the registrar on auto-renew. |
| **Feedback intake.** Testers have nowhere to report. | Wasted launch | A link on the page to the GitHub issues or a mailbox. Decide before launch. |

## Recommendation on scope

A feedback launch needs less than the full design on day one:

- Keep the agreed shape (EC2, Compose, Caddy, RDS), because switching later costs more than building it now.
- Launch **EVE sign-in only**, a **small announced audience**, and say **"early preview, data may be reset"** on the site.
- Do **not** launch before gaps 1, 3 to 10 and 12 are closed. Gap 2 (CI) can be a documented manual build and push for
  the first release, then CI before the second.
- If the KMS provider is too much for the first week, the fallback is the key in Secrets Manager, read at start and
  held in memory. It is weaker (the instance role can read the key directly) but never in an image or in git. Record it
  as a known deviation in the PR.

## Open questions

- Is the instance in a public subnet with a locked-down security group (cheaper, recommended) or behind a NAT?
- Does the developer want the first release to ship `/try` only, with sign-in turned on a week later after feedback?
  That would remove most of the token and privacy risk from the first week.
pro