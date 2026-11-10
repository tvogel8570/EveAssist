# Refresh token behaviour at EVE SSO

Written 2026-11-10. EveAssist stores each linked character's refresh token, which never expires unless the player (or
EveAssist) revokes it. After characters that were linked again were refused at their first refresh, the question is what
EVE does when a character is authorized a second time and when a token is revoked. The answer decides whether EveAssist
can leave a replaced token valid at EVE (it should not: see [Gaining players' trust](../../requirements/gain-trust.md))
and in which order it should revoke and link. Background: `documentation/architecture/identity-and-tokens.md`, Revocation.

`refresh-token-experiment.sh` asks EVE directly, with EveAssist's linking application, outside the app.

## Before you run it

- **Stop EveAssist.** The callback address must fail to load in your browser, so that the app does not take the code. The
  address bar still shows `...?code=...&state=...`, and you paste it into the script. The code is short-lived and works once.
- **Pick the test character.** Use one that is not linked in EveAssist, or one you do not mind linking again, because if
  revocation works per grant this run ends EveAssist's access to that character too.
- Needs `curl`, `jq`, `base64` and `openssl`. It reads `EVE_LINK_CLIENT_ID`, `EVE_LINK_CLIENT_SECRET` and `APP_BASE_URL`
  from `Infrastructure/.env` (or the file in `ENV_FILE`), without running that file.
- It asks for one read-only scope, `esi-assets.read_assets.v1`.

## What it does

| Step | Question |
|---|---|
| 1 | Authorize (T1) and refresh it. Does EVE rotate the refresh token? |
| 2 | Authorize the same character again (T2), revoking nothing. Do T1 and T2 both still refresh? |
| 3 | If both work: revoke T1 only. Does T2 survive? (If a second authorization already ended T1, this step is skipped.) |
| 4 | Revoke what is left, then authorize again (T3). Does revoke-then-authorize give a working token? |
| 5 | What does EVE answer to revoking an already revoked token, and to a made-up one? |

It prints HTTP statuses and EVE's error words only, never a token or secret, and writes the same lines to
`report-<time>.txt` here (ignored by git). Credentials and tokens go to `curl` through a private temporary config file, not
the command line. When it ends, or fails, it revokes every token it made that may still be valid. Afterwards, look at CCP's
third-party applications page and revoke EveAssist for the test character if it is still listed.

## Reading the result

| Result | Meaning for EveAssist |
|---|---|
| Step 2: T1 refused after T2 was issued | A new authorization replaces the old token. Nothing stale is left, and re-linking needs no revoke. |
| Step 2: both work, step 3: T2 refused after T1 was revoked | Revocation is per grant, as suspected. Revoke the old token when the player presses Re-authorize and before the new authorization, which is the order step 4 tests. |
| Step 2: both work, step 3: T2 still works | Revocation is per token. The first explanation was wrong, so the refusals have another cause and the revoke on re-link can go back. |
| Step 1: a different refresh token is returned | EVE rotates tokens. EveAssist must store every returned token, and a failed save loses the character's access. |
| Step 4: T3 refused | Revoke-then-authorize also fails, and the flow needs another design. |

Send the report file back, or paste its lines.
