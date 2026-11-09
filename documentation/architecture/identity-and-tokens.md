# Identity and ESI Tokens

Status: draft, interview in progress.

Covers the requirements in [Users, accounts and roles](../requirements/users.md) and
[ESI access and tokens](../requirements/esi-access.md).

## Sign-in through Keycloak

- The app is an OIDC client of the Keycloak realm `EveAssist` (Spring Security `oauth2Login`, authorization code flow).
  Keycloak brokers EVE SSO, Google, Discord and Facebook. The app has no passwords and no login form of its own.
- The app keys its `account` row on the Keycloak user id (`sub`). Sign-in methods live only in Keycloak. The account
  page adds and removes them through Keycloak's account linking, so accounts are never merged by email.
- The roles USER and ADMIN come from Keycloak realm roles in the ID token and are mapped to Spring authorities.
  Group roles (phase 2) are stored in the app, per group, not in Keycloak, because they belong to one group each.
- Disabling and deleting accounts call the Keycloak admin API with a service account that may manage users only.
  There are no invites. Each admin action writes an audit log row in the same transaction as the app's change.
- Audit log rows hold the admin, the action, the account id, the account's display name at the time and when. They
  have no foreign key to the account, so they outlive its deletion, and a daily job deletes rows older than one year.
- The realm is kept in git with no users, secrets or signing keys, and is loaded as
  [its runbook](../runbooks/keycloak-realm.md) describes. A first sign-in always creates a new account: the realm's
  `eveassist first login` flow never matches an existing user, and duplicate emails are allowed. ADMIN accounts also hold USER, as a default role, and the app ignores USER when ADMIN is
  present.
- EVE SSO is brokered with Keycloak's OAuth v2 provider type, not OpenID Connect, because EVE SSO refuses the `openid`
  scope that Keycloak's OIDC type always asks for. Keycloak reads the user from EVE's `/v2/oauth/verify`, which EVE's
  metadata names as its user info endpoint but the EVE developer documentation does not describe.
- A sign-in with EVE is identified by `CharacterOwnerHash`, not the character id. When a character is sold, its owner
  hash changes, so the new owner gets a new account and cannot reach the seller's.
- The whole character name is kept in Keycloak's first name field, with last name empty. EVE names have no fixed form,
  so a full-name claim would split them at a space. The realm's profile has no character limits on names, for the same
  reason.
- Users see Keycloak's own sign-in page for now. The developer decided that an EveAssist sign-in page, with a button per
  provider that goes straight to it (`kc_idp_hint`), is built when it is needed, and a minimal Keycloak theme for the
  remaining pages comes later. The prod realm must not offer a password form: EveAssist keeps no passwords, and only
  dev has test users.
- The account row is created at the first sign-in, with the display name from that sign-in, and is not updated from
  Keycloak after that. The app reads the signed-in user's id from the session, and keeps no Keycloak access or refresh
  token after sign-in, because it never calls anything with them.
- The app does not ask Keycloak for `offline_access`. The prototype asked for it, but nothing runs for a user who is
  signed out, so the app needs no long-lived Keycloak token.

Status: **agreed** (follows from the requirements).

## Sessions

- The browser holds one session cookie: `HttpOnly`, `Secure`, `SameSite=Lax`. This is an essential cookie, so no
  consent banner is needed. CSRF protection is Spring Security's default.
- Sessions are stored in Postgres with Spring Session JDBC. Sessions then survive a restart, a second instance can be
  added later without sticky sessions, and the fetch coordinator can tell which users are signed in.
- Sign-out ends the Keycloak session (RP-initiated logout), deletes the app session, drops that user's access tokens
  from memory and cancels their queued fetches.

Status: **agreed**.

The pages' security chain covers every path except `/api/**`, which is reserved for the later
[REST API](api.md) and its own chain for bearer tokens. A session cookie is never accepted on `/api/**`, and a bearer
token is never accepted on the pages. Status: **agreed** (part of the API groundwork).

## Linking a character

- Linking goes straight from the app to EVE SSO, separate from sign-in, with the authorization code flow and a random
  `state`. The redirect comes back to the app. The app is a confidential client with a secret, so it uses the secret
  (HTTP Basic at the token endpoint) and not PKCE: the EVE documentation presents the two as alternatives, PKCE being
  for clients that cannot keep a secret. The first build changed this from "state and PKCE" on 2026-11-09.
  **PKCE comes back if EveAssist ever has a client that cannot keep a secret**: a React or other single-page front
  end, a mobile or desktop app, or anything that runs the linking flow itself against the [REST API](api.md) (which
  already uses PKCE for third-party applications). Then the authorization request adds `code_challenge` and
  `code_challenge_method=S256`, and the token request sends `code_verifier` and `client_id` in place of the secret. The
  EVE developer application for such a client is registered as a separate public application, not by weakening this
  one. Until then the server-rendered pages keep the secret flow.
- It uses its own EVE developer application, registered with every scope that any feature uses, separate from the
  application that Keycloak uses for "sign in with EVE". Each authorization request asks only for the scopes of the
  features that are turned on. Signing in then never asks for scopes, and linking never creates a Keycloak
  identity.
- After the code exchange, the access token is validated as the EVE SSO documentation says. The JWKS URL is read from
  `https://login.eveonline.com/.well-known/oauth-authorization-server` and cached. `iss` must be
  `https://login.eveonline.com/`, `https://login.eveonline.com` or `login.eveonline.com` (the EVE documentation's text
  names the first and last, and its own validation example accepts the second, found 2026-11-09), `aud` must contain both the client id and `"EVE Online"`,
  and `exp` is checked. The character id comes from `sub` (`CHARACTER:EVE:<id>`), the granted scopes from `scp` and
  the owner hash from `owner`.
- The `character` row is unique on the EVE character id, so a character that is on another account is refused.
- The owner hash is stored. When a later token carries a different owner hash, the character has been sold or
  transferred: it is marked as needing re-authorization and an alert is raised.

Status: **agreed** (separate application); the validation rules follow the EVE SSO documentation.

## Storing refresh tokens

- Only refresh tokens are stored. Access tokens stay in memory (a cache in the `characters` module, keyed by
  character) and are dropped at sign-out or when they expire.
- Each refresh token is encrypted in the app with AES-256-GCM before it is written. Each token gets a random 96-bit
  nonce, and the character id and key id are the associated data, so a ciphertext copied to another row does not
  decrypt.
- The data key is wrapped by an AWS KMS key. At startup the app asks KMS to unwrap it once and holds it in memory. In
  dev the data key comes from `env.properties`, which is not committed, as `CLAUDE.md` describes.
- Each stored token records the id of the key that encrypted it. To rotate, a new data key is added and used for new
  writes. EVE SSO returns a new refresh token at every refresh, and the newest one is stored, so old ciphertexts
  are replaced as characters are used. Tokens left on the old key are re-encrypted by a one-off job before that key
  is retired.
- Refresh tokens are never logged, never put in exceptions, and never included in the data export.

Why in the app rather than pgcrypto: the key never travels to the database, so it cannot show up in query logs or
`pg_stat_statements`, and a database backup on its own reveals no tokens. Why not KMS for every token: it would mean a
KMS call for every refresh and a stand-in for KMS in dev.

Status: **agreed**.

## Refreshing access tokens

- An access token is refreshed when less than 60 seconds of it are left, not after a request fails.
- Only one refresh runs at a time for each character. The refresh holds a Postgres advisory lock on the character id,
  which also works across several instances. Requests that arrive meanwhile wait for its result. Sign-in and refreshing are
  deliberately not background tasks (see [Background tasks](code-structure.md#background-tasks)): they run in the
  request, and the lock is held only for the one call to EVE SSO, which times out after 30 seconds.
- After a 401, the token is refreshed once and the request retried once. There is no retry loop.
- When EVE SSO refuses the refresh token (`invalid_grant`), the character is marked as needing re-authorization, its
  stored data stays, and an alert is raised. Re-linking through EVE SSO clears the mark.
- Before each ESI request, the endpoint's scope is checked against the character's granted scopes. A missing scope
  marks the character as missing it, and only the features that need it ask for re-authorization.

Status: **agreed** (follows from the requirements).

## First build: linking for pilot inventory

Added 2026-11-09 for [Pilot inventory](../requirements/pilot-inventory.md). It builds the parts above that inventory
needs, in their final shape, and none of the rest (no KMS, no admin unlink, no owner-hash alert). Status: **proposed**.

**Module and tables.** The `characters` module holds `character` (account, EVE character id unique, name, owner hash,
granted scopes, state: active or needs re-authorization, linked at), `character_token` (character, encrypted refresh
token, nonce, key id, replaced at each refresh) and `account_feature` (account, feature, turned on at). Deleting a
character cascades to its token and, later, to its ESI data.

**Features and scopes.** A `Feature` enum in `characters` maps each feature to its read-only scopes. The first is
`INVENTORY`: `esi-assets.read_assets.v1` and `esi-universe.read_structures.v1`. The account turns features on and off
(the developer decided on 2026-11-09 to build the toggle now). An authorization request asks for the union of the
scopes of the turned-on features. Turning a feature on marks each linked character that lacks its scopes as needing
them, and the Characters page offers re-authorization. The EVE developer application is registered with the scopes of
every planned feature, which is allowed because a request may ask for fewer than the registration lists.

**The Characters page.** A page of its own, with a card per character (portrait, name, state, granted scopes, Remove),
a Link a character button and the feature toggles. It grows into the scope page and fetch log in
[Gaining players' trust](../requirements/gain-trust.md).

**Dropping permissions.** A token's scopes are fixed when the player consents, and the EVE documentation describes no way
to narrow them. Turning a feature off therefore only stops using it and changes what the next link asks for. The
character's card lists the scopes it granted that no turned-on feature uses as "not in use", and offers **Drop unused
permissions**: EveAssist revokes the refresh token at EVE, deletes its own copy, marks the character as needing
re-authorization (its stored data stays) and sends the browser straight on to link it again with only the scopes the
turned-on features need. The developer chose this on 2026-11-09 over revoking automatically when a feature is turned
off, which one click could turn into re-linking every character.

Revocation uses the `revocation_endpoint` in EVE SSO's metadata (`https://login.eveonline.com/v2/oauth/revoke`),
authenticated like the token endpoint, with `token` and `token_type_hint=refresh_token`. The EVE documentation pages do
not describe it (found 2026-11-09). The same call is made when a character is **removed** and when it is **linked
again**, so the old token does not stay valid at EVE after our copy is gone. Our own deletion never waits for EVE: when
EVE does not confirm, the page says the old access may still be valid and where to revoke it by hand. A refused link
(a character on another account) never revokes anything, because only the owner's own token is looked up.

**Portraits.** Pages load nothing from third parties, so the server downloads a character's 128 px portrait from EVE's
image server once, when the character is linked or re-linked, and stores it in the `character` row (about 10 KB). The
page serves it from `GET /characters/{id}/portrait`, to the owner only, with a private one-day cache header. The image
server is not ESI, takes no token and sends nothing about the user. When it does not answer, the card shows initials.
The portrait is deleted with the character. The developer chose this on 2026-11-09 over a cache fetched on demand.

**The flow.** `GET /characters/link` creates a random `state`, keeps it in the session, and redirects to
`https://login.eveonline.com/v2/oauth/authorize`. `GET /characters/link/callback` checks `state`, exchanges the code
once (the state is used up by the first callback), with the client secret, validates the token as described under
[Linking a character](#linking-a-character), and stores the character and the encrypted refresh token. A character on
another account is refused with a message. Linking a character already on this account renews its token and scopes.
It is a normal request, not a background task. The client id and secret are `EVE_LINK_CLIENT_ID` and
`EVE_LINK_CLIENT_SECRET`.

**Encryption in dev.** `EVE_TOKEN_DATA_KEY` (32 random bytes, base64) in `Infrastructure/.env` is the data key. The
code reads it through a `DataKeyProvider` with the key id. The KMS version is a second implementation later, so no
stored format changes.

**How `esi` gets a token.** `esi` must not depend on `characters`, so it owns a small port, `AccessTokens`
(`accessToken(characterId, requiredScope)`), which `characters` implements. It refreshes under the advisory lock when
fewer than 60 seconds are left, refuses a call whose scope the character lacks, and marks the character as needing
re-authorization on `invalid_grant`. `EsiClient` gets authenticated methods that take a character id, with the same
cache, limits and error handling as the public ones, the cache keyed by character and route. Assets are the first.

**Tests.** Unit tests cover the token rules with a fake EVE SSO. An integration test covers the callback, with the
refusal of a character on another account and the cascade on removal. Template tests cover the Characters page.

## Admins

- ADMIN reaches only the `identity` module's admin pages: account fields and the names and ids of linked characters.
  ADMIN does not include USER, so the industry and market modules refuse an admin session.
- Unlinking a character deletes its token and ESI data through the same service as a user's own removal.

Status: **agreed** (follows from the requirements).

## Open questions

None at present.
