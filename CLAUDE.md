# EveAssist

An out-of-game (OOG) industry tool for EVE Online, built in phases: single player first, then small groups and
corporations, then possibly public multi-tenant. Later phases must not require a rewrite of earlier ones.

The earlier prototype modules `Api/` (REST API, ESI and SDE) and `Client/` (Thymeleaf web UI) were removed from the
working tree. They are kept in git under the tag `prototype-api-client`. Read them with
`git ls-tree -r --name-only prototype-api-client -- Api Client`, `git show prototype-api-client:Api/<path>` and
`git grep <pattern> prototype-api-client -- Api Client`. They are reference only, not the basis for the requirements
in `documentation/requirements/`. To build or run them, ask the developer first, then use a worktree on the tag.

## Terms

The person working on EveAssist with Claude is the **developer**. Call them that in conversation, in this file, in
handoff notes, commit messages and PR descriptions. In `documentation/`, "user" means an end user of EveAssist, a
person who signs in to it (a player or an admin), and never the developer. When interview answers are written into
the requirements or architecture, say "the developer decided" or similar, not "the user decided".

## Layout

| Path | What it is |
|---|---|
| `App/` | EveAssist's application, being built: Spring Boot 4.1 / Java 25, Spring Modulith. See `documentation/architecture/`. |
| `Infrastructure/` | Docker Compose for Postgres and Keycloak (realm export in `keycloak/`). See `Infrastructure/README.md`. |
| `documentation/requirements/` | Requirements drafts, gathered by interview. Open questions are listed at the end of each file. |
| `documentation/sde/` | SDE schema reference. |
| `documentation/runbooks/` | Step-by-step operating procedures, such as loading a changed Keycloak realm. |
| `documentation/ai/hand-off/` | Fallback location for handoff notes (see below). |

## Code and architecture decisions

`documentation/architecture/` and the code must not drift apart. Code that departs from a **proposed** decision updates
the doc in the same change, and the PR description says so. Check with the developer before departing from an **agreed**
decision. Questions that code can answer are settled by writing the code rather than by more design in the docs.

## Build and test

- App: `./mvnw verify` in `App/`, which includes the architecture checks.
- Load the SDE for App once with `postgres/load-sde.sh` in `Infrastructure/`. App will not start without it.
- Run App with `./mvnw spring-boot:run` in `App/`, after `docker compose up -d` in `Infrastructure/`. It needs Keycloak at
  start-up. Open it at https://dev.eveassist.com:7444. On Windows, `dev.eveassist.com` also needs an entry in the Windows
  hosts file, because WSL's `/etc/hosts` does not apply there.
- Tests named `*IT` and Testcontainers-based tests need Docker running.
- Flyway migrations are named `VYYYYMMDD_HHMM__descriptive-name-with-dashes.sql` (UTC), and are never edited once merged.
  Test-only SQL has the marker `T_`. The rules are in `documentation/architecture/data.md`, and `MigrationNamingTest`
  checks them.
- App's browser tests use Playwright's Chromium, which it downloads on first run. On a new machine it may also need
  system libraries: `libnss3`, `libnspr4` and `libasound2t64`.
- Infrastructure: `docker compose up -d` in `Infrastructure/` (needs `.env`, `postgres/password.txt` and the
  Keycloak certificate files; see its README).

Local ports with the default profile: Postgres 2345 (5432 inside the compose network), Keycloak 8442 (HTTPS), App 7444 (HTTPS).

## ESI

The EVE developer documentation at https://developers.eveonline.com/docs/ is the authoritative source for how to work
with ESI (endpoints, scopes, SSO, caching, rate limits and errors). When the code, tests or real ESI responses differ
from it, tell the developer about the difference in the session where it is found. Don't quietly follow either one.

The site's source is cloned at `~/git/esi-docs` (https://github.com/esi/esi-docs). Read it there rather than fetching
the site: the pages are Markdown under `docs/`, for example `docs/services/esi/` and `docs/services/sso/`. Run
`git pull` in it first so it is current. If another computer has no clone, clone it to the same path. For the exact
fields of an endpoint, use ESI's OpenAPI spec at https://esi.evetech.net/meta/openapi.json.

## Secrets

Real secrets are never committed. Each module has a `template_*` file with empty values to copy:
`template_env.properties` → `env.properties`, `template_password.txt` → `password.txt`, and template certificates
and keystores. `.gitignore` already covers the real filenames. Don't put secrets in code, commits, PR descriptions or
handoff notes.

The repository is public, so deployment identifiers (AWS account id, instance id, RDS endpoint, security group ids and the like) are not
committed either. Runbook commands refer to them as `$EVEASSIST_INSTANCE_ID` and so on; the values are in `Infrastructure/prod/ops.env`
(git-ignored, copy it from `template_ops.env` and load it with `set -a; . Infrastructure/prod/ops.env; set +a`). Real values never go into
documentation, scripts, commits or issues, and neither do the developer's own IP addresses.

`App/` has no secrets file of its own. It imports `Infrastructure/.env`, so run it with `App/` as the working directory.
In prod the same names come from real environment variables.

## IntelliJ

Shared `.idea` settings are tracked and user-specific ones are ignored (see the comments in `.gitignore`).
Keep tracked `.idea` files free of machine-specific paths and credentials, because the project is opened on several
computers.

The tracked `.idea` files do not import `App/` as a Maven project. On a computer where IntelliJ doesn't recognise it,
open View → Tool Windows → Maven, click `+` and pick `App/pom.xml`.

## Worktrees

Use git worktrees only when the developer asks for one. Otherwise work in the main checkout, on its current branch. This
also applies to new Claude sessions: start them without a worktree unless the developer asks for one.

## Working across computers

The developer works on this repository from several computers with the same directory layout, connected over Tailscale.
Git is the source of truth. Work branches come off `development`, and their PRs target `development`.
Claude's memory and session history stay on each machine, so anything the next machine
needs has to be in git or in a handoff note.

Development runs in the Ubuntu WSL distro on `Tim-Xoticpc`, in `~/git/EveAssist`. Other computers reach it with the
`xoticpc-wsl` SSH profile in their own `~/.ssh/config`, which jumps through Windows SSH on `tim-xoticpc` to `sshd` on
port 2222. Docker there is Docker Engine inside the distro, not Docker Desktop. Files that git ignores (see Secrets) and
the database are not in the clone, so a new host has to copy them from the current one.

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

## Releasing to main

Decided with the developer on 2026-11-08, corrected on 2026-11-12 after reading the GitHub rules. Every commit on `development` is kept on
`main`, with the same ids.

- **The route to `main` is a pull request.** The ruleset "primary branches" applies to both `development` and `main`, has no bypass for
  anyone, and requires pull requests, forbids force pushes and forbids deletion. A direct push is refused, even a fast-forward. Once
  `./mvnw verify` passes on `development`: open a PR from `development` to `main` and merge it with **"Create a merge commit"** (the
  ruleset also allows squash and rebase, which are wrong here, see below). Then bring `main` back with a PR from `main` to `development`,
  also a merge commit, so the two stay level (their trees are identical, the ids differ by the merge commits).
- **Tag the commit that was built, not the merge.** The image footer shows the commit id, so `git tag -a vX.Y.Z <built commit> -m "..."`
  goes on that commit, which stays reachable from `main` through the merge. Tag only after the deploy was checked, so that `main` never
  claims a release that did not run. Tags `v*` are protected by the ruleset "release tags" (no deletion, no update, no bypass): to correct a
  wrong tag, remove that ruleset first.
- **Never squash or rebase `development` into `main`.** Either gives the commits new ids, so the two branches diverge,
  PR links point at commits that are not on `main`, and the next release conflicts. Feature PRs into `development` are
  a separate choice from this one.
- Build the release images from the tip of `development` after verify, and do not rewrite history afterwards: the footer shows the commit
  id, and the id must stay a commit that exists on GitHub (see `documentation/runbooks/update-deployment.md`, step 3).

