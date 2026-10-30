# ESI Access and Tokens

Status: draft, interview in progress.

This covers how EveAssist uses the ESI tokens of linked EVE characters. Sign-in tokens (the Keycloak session and the
identity provider used to sign in) are separate and are covered in [Users, accounts and roles](users.md).

## Standards

- The [EVE developer documentation](https://developers.eveonline.com/docs/) is the authority for ESI and EVE SSO.
- ESI best practices and general OAuth 2.0 best practices are followed. Among them:
  - Access tokens (JWTs) are validated (signature, issuer, audience and expiry) and refreshed shortly before they
    expire, not after a request fails.
  - Only one refresh runs at a time for each character. Other requests for that character wait for its result.
  - The newest refresh token returned by EVE SSO is the one stored.
  - Refresh tokens are encrypted at rest and never logged or shown.
  - A failed request is not retried in a loop. After a 401, the token is refreshed once and the request retried once.
  - The scopes a character granted (the `scp` claim) are checked before a request, so calls that would get a 403 are
    not sent.
  - ESI cache times (`Expires`, `ETag` and `If-None-Match`), bucket rate limits (`Retry-After`) and the error limit
    (`X-ESI-Error-Limit-Remain` and `X-ESI-Error-Limit-Reset`) are respected. The app slows down before it reaches a
    limit.

## When character data is fetched

- Character data (jobs, blueprints, wallet, skills, implants, orders and the rest) is fetched only while the user is
  signed in. Nothing is fetched for signed-out users, and their refresh tokens are not used.
- By default, all of a user's characters are fetched right after sign-in, within ESI cache times. The user can turn
  this off, for example to sign in faster with many characters. With it off, data is fetched when a page needs it or
  the user asks for a refresh.
- The dashboard does not wait for the fetch after sign-in. It shows stored data at once, with its age, and the fetch
  runs in the background. A progress bar under the header shows how many characters are done, and each dashboard card
  is marked as updating until its data arrives. Then the card updates by itself. On a first sign-in, the cards are
  empty until the first fetch arrives.
- At the next sign-in, the stored refresh token of each character is used again, unless the character is marked as
  needing re-authorization.
- On sign-out, the access tokens the app holds are discarded. Refresh tokens are kept, encrypted, for the next
  sign-in.

## Freshness

- A freshness page shows, for each character and each kind of data, when it was last fetched (for example "Alt One:
  jobs 3 min ago, wallet 2 h ago").
- The user can ask for a refresh of one character or of all of them. A refresh still respects ESI cache times.

## Job finish alerts

- Since nothing is fetched while the user is signed out, a job is marked finished in the app when the end time from
  its last fetch passes. No ESI call is needed. The next fetch confirms it.
- The alert itself is described in [Manufacturing and invention](manufacturing-invention.md#alerts).

## Failures

- **Revoked or broken token**: when EVE SSO refuses a character's refresh token, the character is marked as needing
  re-authorization, as described in [Users, accounts and roles](users.md#eve-characters).
- **Missing scope**: when a later version needs a scope that a character did not grant, the character is marked as
  missing it. Features that do not need the scope keep working for that character, and only the features that do
  ask the user to re-authorize it.
- **ESI down or throttled**: stored data is still shown, with its last-fetched time, and a banner says that ESI is
  unavailable or throttled. The fetch is tried again later, within ESI's limits.

## Open questions

None at present.
