# EveAssist

An out-of-game (OOG) industry tool for EVE Online, built in phases: single player first, then small groups and
corporations, then possibly public multi-tenant. Later phases must not require a rewrite of earlier ones.

The code in `Api/` and `Client/` is a prototype written to try out ESI and the SDE. It is **not** the basis for the
requirements in `documentation/requirements/`. Check with the user before building new features on top of it.

## Layout

| Path | What it is |
|---|---|
| `Api/` | Spring Boot 4.1 / Java 25 REST API. Reads the SDE (`sdeyaml` database) with JDBC and calls ESI. OAuth2 resource server. |
| `Client/` | Spring Boot 3.2 / Java 17 web UI: Thymeleaf with the layout dialect, Tailwind CSS (built by npm through `frontend-maven-plugin`), Flyway, JPA. Signs in through Keycloak. |
| `Infrastructure/` | Docker Compose for Postgres and Keycloak (realm export in `keycloak/`). See `Infrastructure/README.md`. |
| `documentation/requirements/` | Requirements drafts, gathered by interview. Open questions are listed at the end of each file. |
| `documentation/sde/` | SDE schema reference. |
| `documentation/ai/hand-off/` | Fallback location for handoff notes (see below). |

## Build and test

- Client: `./mvnw verify` in `Client/`. Api: `./mvnw verify` in `Api/`. Each module has its own Maven wrapper.
- Api tests tagged `esi-live` call the real, public ESI, so the Api build needs network access. Offline, skip them
  with `./mvnw verify -DexcludedGroups=esi-live`.
- Tests named `*IT` and Testcontainers-based tests need Docker running.
- Client front end: `npm run build` or `npm run watch` in `Client/`.
- Infrastructure: `docker compose up -d` in `Infrastructure/` (needs `.env`, `postgres/password.txt` and the
  Keycloak certificate files; see its README).

Local ports with the default profile: Postgres 2345 (5432 inside the compose network), Keycloak 8442 (HTTPS), Api 8085 (8043 with TLS),
Client 7443 (9443 with TLS).

## ESI

The EVE developer documentation at https://developers.eveonline.com/docs/ is the authoritative source for how to work
with ESI (endpoints, scopes, SSO, caching, rate limits and errors). When the code, tests or real ESI responses differ
from it, tell the user about the difference in the session where it is found. Don't quietly follow either one.

The site's source is cloned at `~/git/esi-docs` (https://github.com/esi/esi-docs). Read it there rather than fetching
the site: the pages are Markdown under `docs/`, for example `docs/services/esi/` and `docs/services/sso/`. Run
`git pull` in it first so it is current. If another computer has no clone, clone it to the same path. For the exact
fields of an endpoint, use ESI's OpenAPI spec at https://esi.evetech.net/meta/openapi.json.

## Secrets

Real secrets are never committed. Each module has a `template_*` file with empty values to copy:
`template_env.properties` → `env.properties`, `template_password.txt` → `password.txt`, and template certificates
and keystores. `.gitignore` already covers the real filenames. Don't put secrets in code, commits, PR descriptions or
handoff notes.

## IntelliJ

Shared `.idea` settings are tracked and user-specific ones are ignored (see the comments in `.gitignore`).
Keep tracked `.idea` files free of machine-specific paths and credentials, because the project is opened on several
computers.

## Worktrees

Use git worktrees only when the user asks for one. Otherwise work in the main checkout, on its current branch. This
also applies to new Claude sessions: start them without a worktree unless the user asks for one.

## Working across computers

The user works on this repository from several computers with the same directory layout, connected over Tailscale.
Git is the source of truth. Work branches come off `development`, and their PRs target `development`.
Claude's memory and session history stay on each machine, so anything the next machine
needs has to be in git or in a handoff note.

- `/handoff` pushes the branch and writes a handoff note into the branch's **draft PR** description, between
  `<!-- handoff:start -->` and `<!-- handoff:end -->`. On `development` or `main`, or without `gh`, it writes to
  `documentation/ai/hand-off/HANDOFF.md` instead.
- `/resume-handoff` on the other machine checks out the branch, reads the note and summarizes where things stand.
- To read a handoff note without the skill, find the branch's draft PR (`gh pr list --draft --head <branch>`), read
  its description with `gh pr view <number> --json body`, and take the text between the two markers. If the branch has
  no draft PR, read `documentation/ai/hand-off/HANDOFF.md`.
- Before marking a draft PR ready for review, and always before it is merged, remove the handoff section from its
  description: everything from `<!-- handoff:start -->` through `<!-- handoff:end -->`, markers included. Keep the
  rest of the description, and update it with `gh pr edit <number> --body-file <file>`. Then check that no marker is
  left.
- Lasting project knowledge (conventions, decisions that hold beyond one branch) belongs in this file, not in a
  handoff note.
