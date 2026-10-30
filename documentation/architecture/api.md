# External REST API

Status: draft, interview in progress.

EveAssist may later offer a REST API for clients that other people write, and for users' own automation. The API is
**designed now and built later**: phase 1 builds none of it, but it does the few things that would be costly to
change once data and URLs exist. Status: **agreed**.

## Who would use it

- **A user's own automation**: a script, a spreadsheet or a Discord bot reading that user's jobs, plans, projects and
  alerts.
- **Third-party applications** that a user allows to read their EveAssist data, much as EVE SSO lets applications read
  ESI.
- **Corporation tooling** in the group phase, reading data shared with a group.

## Shape

- The API lives in the same application, in its own `api` module, under `/api/v1/`. Its controllers call the same
  services as the htmx pages, and return JSON.
- It starts read-only: jobs, plans and their costing, projects, alerts, freshness, and prices as EveAssist computes
  them. Writes, such as creating a plan or confirming a job match, come later if needed.
- The path carries the major version (`/api/v1/`). Fields are added without a new version; removing or changing one
  needs a new version, as ESI does with its compatibility dates.
- An OpenAPI description is generated from the code (for example with springdoc-openapi) and published with the API.
- Responses follow the conventions ESI users know: `ETag` and `If-None-Match`, `Expires` where data has a natural age,
  and paging for long lists.
- Each token has a rate limit, with the same kind of headers ESI uses, so that one client cannot slow down the site.
- Later, alerts can also be sent to a client's webhook, so that clients need not poll.

Status: **agreed** (designed now, built later); the details above are **proposed**.

## Authentication

Status: **agreed**.

- **Third-party applications** use OAuth 2.0 through Keycloak, which already issues EveAssist's tokens. Each application
  is a Keycloak client. The user signs in and consents to the scopes the application asks for, and can revoke it.
  Applications use the authorization code flow with PKCE.
- **A user's own scripts** use personal access tokens, made on the account page. Each one has a name, scopes and an
  optional expiry, can be revoked, and is shown once when made. Only a hash of it is stored.
- The API has its own Spring Security filter chain that accepts bearer tokens only. The pages' chain accepts the session
  cookie only. Neither accepts the other's credentials.
- A token resolves to the same viewer (the account, and its groups in phase 2) as a signed-in session, so the existing
  visibility check applies unchanged.
- There are no scopes for an admin to read ESI data, so the rule that admins never see it holds for the API too.

Why both kinds: OAuth is the right way for other people's applications to ask a user for access, but it is clumsy for a
user's own cron job or spreadsheet, which needs a token it can simply paste in.

## The API and ESI

Status: **agreed**.

The API serves stored data only, with its age. A request through the API never triggers an ESI fetch and never uses a
character's refresh token, even when the user is signed in elsewhere. The rule in
[ESI access and tokens](../requirements/esi-access.md), that character data is fetched only while the user is signed
in, therefore stays as it is. Job-finish alerts keep working while the user is away, because they are raised from
stored end times. Public data the app fetches for everyone, such as Jita prices, follows its usual rules.

## What phase 1 does for it

These cost little now and would be costly to retrofit:

- **Public ids**: plans, projects, facilities and other records that appear in URLs get a UUID as their public id.
  Database sequence numbers never appear in URLs, pages or JSON.
- **Plain services**: module services take and return plain objects, never pages, so a JSON controller can call them
  as they are (see [Module layout](README.md#module-layout)).
- **Two security chains**: the pages' chain is set up for `/**` except `/api/**`, which is left closed until the API
  exists.
- **Keycloak as the token issuer** for everything, so third-party clients need no second authorization server.

## CCP license

CCP's [Developer License Agreement](https://developers.eveonline.com/license-agreement) was read on 2026-10-29. Its
full text is not in the `esi-docs` clone, which only links to it. What it says that bears on the API:

- It licenses Game Data (the data CCP makes available, through ESI among others) to be used, shown and distributed
  "within an Application used for the Purpose". The purpose is an application that adds to players' enjoyment of EVE
  and is offered for non-commercial, non-profit use.
- The license is non-transferable and cannot be assigned without CCP's consent. Products based on EVE data may not be
  sold or licensed without a separate written agreement.
- An application may not track a player's information or activity without that player's express knowledge and
  consent. Privacy and data protection law apply as well.
- Nothing in it speaks directly of an application passing ESI data on to other applications or developers through an
  API of its own. CCP decides what falls inside the purpose, and can disable an application without notice when it
  judges that something does not.

What follows for EveAssist:

- **Personal access tokens** give a user's own scripts that user's own data, much as the data export does. The data
  stays with the user it belongs to, so this part is the safer one.
- **Third-party applications** reading a user's data through EveAssist are a grey area. The user's consent covers the
  tracking rule, but it is not clear that data handed to someone else's application is still used "within" EveAssist.
- The API is free, like the rest of EveAssist (see [Non-functional requirements](../requirements/non-functional.md#ccp-license)).

## Open questions

- How the API meets the license, in particular whether third-party applications are allowed, for example after asking
  CCP in writing. The developer decided that this is settled before the API is built, in whichever phase that is, and
  not now.
- Which phase builds the API, and whether personal access tokens come before third-party OAuth.
- Personal access tokens are not a built-in Keycloak feature. Whether the app issues and checks them itself (the
  proposed default), or they are mapped onto Keycloak offline tokens, is to be decided when the API is built.
