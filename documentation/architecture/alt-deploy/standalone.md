# Standalone Deployment: Discussion

Status: discussion only. Nothing here is agreed, and nothing here changes the decisions in the
[architecture](../README.md).

EVE Online is known for in-game scams and theft, and many players will not trust a hosted tool with their characters'
ESI tokens. This file looks at a standalone EveAssist: installed and run by one player on their own computer, for all
the characters on their EVE accounts. It first summarizes the UI, stack and deployment decisions made so far, then
lists the options for a standalone build.

## Decisions so far

### Stack (agreed)

- One Spring Boot application in a new module, `App/`, on Java 25 and Spring Boot 4.1, written from scratch. Module
  boundaries inside it are enforced: `identity`, `characters`, `esi`, `sde`, `industry`, `market`, `alerts`, `groups`
  and later `api`.
- Keycloak signs users in and brokers EVE SSO, Google, Discord and Facebook. Characters are linked through a separate
  EVE SSO application that the app calls directly.
- Postgres holds the app's data, the SDE as a schema, Keycloak's database and Spring Session.
- Background ESI fetches run in-process on virtual threads, with a per-character Postgres advisory lock. Alerts reach
  open pages over SSE, and `LISTEN`/`NOTIFY` passes them between instances.
- Refresh tokens are encrypted with AES-256-GCM. The data key is wrapped by AWS KMS, and in dev it is a value in
  `env.properties`.

### UI (agreed)

- Pages are rendered on the server with Thymeleaf, with htmx, Tailwind and daisyUI, built by npm inside the Maven
  build. Small pieces of JavaScript, such as SortableJS for drag and drop, are allowed.
- Vaadin was rejected because it keeps every open tab's UI in server memory. React was rejected because it needs a
  second toolchain and the types and rules written again in TypeScript.
- The htmx UI must have: a test that renders every template, one shared htmx set-up (CSRF, `HX-Redirect` on an expired
  session, visible errors, `hx-sync`), tests at 50 ms and 150 ms of latency, row-level updates of large plans, and
  `th:text` only for data.

### Deployment (agreed)

- Prod runs at `eveassist.net` on one EC2 instance with Docker Compose (the app, Keycloak, and Caddy with Let's
  Encrypt), with RDS Postgres in a single availability zone. It costs roughly $30–45 a month.
- GitHub Actions builds the image and pushes it to ECR. The instance pulls with its IAM role.
- Secrets come from AWS Secrets Manager. The KMS key may be used only by the instance's IAM role.
- RDS keeps seven daily backups with point-in-time recovery, and AWS Backup keeps four weekly ones. A restore is tested
  every six months.
- Logs and alarms are in CloudWatch. Actuator and Keycloak health endpoints are checked.
- To scale, a load balancer replaces Caddy, and the app may move to ECS.

The requirements say that prod runs in AWS, that there is no staging environment, that availability is best effort,
and that the design must handle a few thousand users in the public multi-tenant phase without a rewrite.

### Where the decisions depend on AWS

KMS, Secrets Manager, RDS with AWS Backup, ECR with IAM-role pulls, and CloudWatch. Compose with Caddy, the
Postgres-based scaling and the dev set-up do not depend on AWS.

## What EVE SSO and ESI allow

Checked in the ESI docs (`docs/services/sso/index.md` and `docs/services/esi/rate-limiting.md` in `esi/esi-docs`):

- **Authorization Code with PKCE** is meant for "mobile and desktop applications that cannot securely store the client
  secret". A standalone build can ship a public client ID and no secret.
- **Rate limits** apply per application ID and character ID pair, so many installs sharing one client ID do not share
  a limit for a character. Unauthenticated routes are limited per source IP.
- The redirect URL must exactly match one registered with the EVE application. The docs do not say whether
  `http://localhost:<port>` is allowed. Existing desktop tools use a fixed localhost port, so it very likely is. This is
  to be confirmed by registering a test application.

## What a standalone build drops

| Hosted piece | Standalone replacement |
|---|---|
| Keycloak and its sign-in options | None. The computer's owner is the only user, and characters are linked directly through EVE SSO with PKCE. |
| KMS-wrapped data key | The OS keychain (Windows Credential Manager, macOS Keychain, libsecret), or a master password with Argon2 |
| Secrets Manager | Not needed, because there is no secret |
| RDS, AWS Backup, restore tests | A local database file and an export or backup button |
| Caddy, TLS, ECR, CloudWatch | A server bound to `127.0.0.1` only, and a local log file |
| Spring Session in Postgres | Sessions in memory |

The business modules (`industry`, `market`, `esi`, `sde` and others) are unchanged.

## Earning players' trust

This matters more than the choice of stack.

- **Open source, with releases built by GitHub Actions** from tagged commits, ideally reproducible, so anyone can check
  a download against the source.
- **Signed installers.** Without signing, Windows SmartScreen and macOS Gatekeeper show warnings that undercut trust.
  An Apple developer account costs $99 a year. Windows signing costs roughly $100–400 a year, or about $10 a month with
  Azure Trusted Signing.
- **A short, published list of the hosts the app contacts**: `login.eveonline.com`, `esi.evetech.net` and the SDE
  source. No telemetry, and no update server beyond GitHub releases. Players can check this with a firewall.
- **Least scopes**: each scope is asked for only when the feature that needs it is turned on.
- **Tokens never leave the computer.**
- **Own EVE application**: a setting for a player's own client ID, for the most cautious players.

## Distribution options

| Option | Implications |
|---|---|
| **A. Installer with a bundled Java runtime (`jpackage`)** that starts the app and opens the browser at `http://localhost:<port>`, with a tray icon | Keeps Java 25, Spring Boot and every agreed decision. Installers are about 80–120 MB and the app uses about 300 MB of RAM. One build per OS, as a GitHub Actions matrix. |
| **B. Docker Compose file for self-hosting** | Easy for technical players, a hurdle for others. Docker Desktop on Windows is heavy. Also serves a corporation that runs its own server. |
| **C. Runnable JAR** | Simplest to build, but the player must install Java 25. Fine as an extra download beside A. |
| **D. GraalVM native image** | Fast start and small memory, but Spring Boot native images are brittle, especially with Thymeleaf reflection and JDBC drivers. Better added later. |

## UI options

| Option | Implications |
|---|---|
| **1. The agreed Thymeleaf and htmx UI in the player's own browser** | No UI work is lost. Latency on localhost is near zero, which removes htmx's weak spot. The local server must be protected from other local processes and from DNS rebinding: bind to `127.0.0.1`, check the `Host` header, keep CSRF, and use a random per-launch token in the first URL. |
| **2. The same UI in a desktop window** (Tauri, Electron or JavaFX WebView) around the local server | Feels like an app. Tauri and Electron add a second toolchain. JavaFX WebView's WebKit is old, and Tailwind and daisyUI may render poorly in it. |
| **3. Native JavaFX UI** | A true desktop app, but the agreed UI is discarded and there is no way back to hosted. Tables and drag and drop work well, but styling is much more work. |
| **4. Electron or Tauri with React** | The reasons React was rejected still apply, with a desktop shell added on top. |

## Local database options

| Option | Implications |
|---|---|
| **Embedded Postgres** (zonky `embedded-postgres` binaries, run by the app) | Keeps advisory locks, `LISTEN`/`NOTIFY`, the SDE schema swap and one SQL dialect shared with hosted. Adds about 30 MB and a child process to manage. Moving to a new Postgres major version needs a dump and restore. |
| **SQLite** | One small file, easy to back up. Fuzzwork publishes an SQLite SDE too. Loses advisory locks (Java locks are enough in one process), and two SQL dialects must be kept if hosted stays. |
| **H2** | Pure Java, but its Postgres mode is imperfect and its files are more easily corrupted by a crash than SQLite's. Not recommended. |

## The deciding question

Does the standalone build replace the hosted plan, or is it a second way to deploy the same code?

- **Replace**: the simplest build, and it fits a single player. Groups and corporations (phase 2) would mean each
  corporation runs its own server, and the public multi-tenant phase effectively goes away.
- **Second mode of one codebase**: a `local` profile (PKCE, keychain, no Keycloak) beside a `hosted` profile. Phases 2
  and 3 stay open, and a corporation can self-host the hosted profile with option B. It costs an abstraction around
  identity and token storage, and testing both profiles. Embedded Postgres keeps that cost low.

Suggested direction, not decided: a second mode, with distribution A, UI 1 and embedded Postgres. The player installs
a signed app that opens the existing htmx UI in their browser, and almost nothing agreed so far is lost.
