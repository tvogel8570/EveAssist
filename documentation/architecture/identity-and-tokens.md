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
- Disabling, deleting and inviting accounts call the Keycloak admin API with a service account that may manage users
  only. Each admin action writes an audit log row in the same transaction as the app's change.
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

Status: **proposed**.

The pages' security chain covers every path except `/api/**`, which is reserved for the later
[REST API](api.md) and its own chain for bearer tokens. A session cookie is never accepted on `/api/**`, and a bearer
token is never accepted on the pages. Status: **agreed** (part of the API groundwork).

## Linking a character

- Linking goes straight from the app to EVE SSO, separate from sign-in, with the authorization code flow, a random
  `state` and PKCE. The redirect comes back to the app.
- It uses its own EVE developer application, registered with the full scope set, separate from the application that
  Keycloak uses for "sign in with EVE". Signing in then never asks for scopes, and linking never creates a Keycloak
  identity.
- After the code exchange, the access token is validated as the EVE SSO documentation says. The JWKS URL is read from
  `https://login.eveonline.com/.well-known/oauth-authorization-server` and cached. `iss` must be
  `https://login.eveonline.com/` or `login.eveonline.com`, `aud` must contain both the client id and `"EVE Online"`,
  and `exp` is checked. The character id comes from `sub` (`CHARACTER:EVE:<id>`), the granted scopes from `scp` and
  the owner hash from `owner`.
- The `character` row is unique on the EVE character id, so a character that is on another account is refused.
- The owner hash is stored. When a later token carries a different owner hash, the character has been sold or
  transferred: it is marked as needing re-authorization and an alert is raised.

Status: **proposed** (separate application); the validation rules follow the EVE SSO documentation.

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
  which also works across several instances. Requests that arrive meanwhile wait for its result.
- After a 401, the token is refreshed once and the request retried once. There is no retry loop.
- When EVE SSO refuses the refresh token (`invalid_grant`), the character is marked as needing re-authorization, its
  stored data stays, and an alert is raised. Re-linking through EVE SSO clears the mark.
- Before each ESI request, the endpoint's scope is checked against the character's granted scopes. A missing scope
  marks the character as missing it, and only the features that need it ask for re-authorization.

Status: **agreed** (follows from the requirements).

## Admins

- ADMIN reaches only the `identity` module's admin pages: account fields and the names and ids of linked characters.
  ADMIN does not include USER, so the industry and market modules refuse an admin session.
- Unlinking a character deletes its token and ESI data through the same service as a user's own removal.

Status: **agreed** (follows from the requirements).

## Open questions

- Confirm a separate EVE developer application for character linking, as proposed above.
- Confirm Spring Session in Postgres.
