#!/usr/bin/env bash
# Finds out, against the real EVE SSO, how refresh tokens behave when a character is authorized again and when a token
# is revoked. See README.md before running it. It prints HTTP statuses and EVE's error words only, never a token, and it
# revokes every token it made before it ends.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$HERE/../../../Infrastructure/.env}"
SCOPE="esi-assets.read_assets.v1"
TOKEN_URL="https://login.eveonline.com/v2/oauth/token"
REVOKE_URL="https://login.eveonline.com/v2/oauth/revoke"
AUTHORIZE_URL="https://login.eveonline.com/v2/oauth/authorize"

for tool in curl jq base64 openssl; do
  command -v "$tool" >/dev/null || { echo "Needs $tool" >&2; exit 2; }
done

# A key from the properties-style env file, without sourcing it, so nothing in it is run.
env_value() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//"; }
[[ -r "$ENV_FILE" ]] || { echo "Cannot read $ENV_FILE (set ENV_FILE=...)" >&2; exit 2; }
CLIENT_ID="$(env_value EVE_LINK_CLIENT_ID)"
CLIENT_SECRET="$(env_value EVE_LINK_CLIENT_SECRET)"
BASE_URL="$(env_value APP_BASE_URL)"; BASE_URL="${BASE_URL:-https://dev.eveassist.com:7444}"
REDIRECT="$BASE_URL/characters/link/callback"
[[ -n "$CLIENT_ID" && -n "$CLIENT_SECRET" ]] || { echo "EVE_LINK_CLIENT_ID / EVE_LINK_CLIENT_SECRET missing in $ENV_FILE" >&2; exit 2; }

WORK="$(mktemp -d)"; chmod 700 "$WORK"
REPORT="${REPORT:-$HERE/report-$(date -u +%Y%m%d-%H%M%S).txt}"
declare -A VALID   # token name -> 1 while we believe it is still valid at EVE, so cleanup revokes it

say() { echo "$*" | tee -a "$REPORT"; }

# POST a form to EVE with the application's credentials. The credentials and the token go through a curl config file
# that only we can read, so they never show in the process list. Prints the HTTP status; the body is left in $WORK/body.
post() {
  local url="$1"; shift
  local cfg="$WORK/curl.cfg"
  : > "$cfg"; chmod 600 "$cfg"
  printf 'user = "%s:%s"\n' "$CLIENT_ID" "$CLIENT_SECRET" >> "$cfg"
  for field in "$@"; do printf 'data-urlencode = "%s"\n' "$field" >> "$cfg"; done
  curl -s -o "$WORK/body" -w '%{http_code}' -K "$cfg" "$url"
  rm -f "$cfg"
}

why() { jq -r '[.error // empty, .error_description // empty] | join(": ")' "$WORK/body" 2>/dev/null; }

# The character a JWT access token is for (id and name only), read without verifying: it is for our own eyes.
who() {
  local payload
  payload="$(cut -d. -f2 <<<"$1" | tr '_-' '/+')"
  while (( ${#payload} % 4 )); do payload+="="; done
  base64 -d <<<"$payload" 2>/dev/null | jq -r '"\(.name) (\(.sub | sub("CHARACTER:EVE:"; "")))"' 2>/dev/null
}

authorize() {  # authorize <name>: the player authorizes in a browser, we exchange the code and keep the tokens
  local name="$1" state url pasted code status
  state="$(openssl rand -hex 8)"
  url="$AUTHORIZE_URL?response_type=code&client_id=$CLIENT_ID&redirect_uri=$(jq -rn --arg v "$REDIRECT" '$v|@uri')&scope=$SCOPE&state=$state"
  echo
  echo "Authorize the test character ($name). Open this address in a browser where you are signed in to EVE:"
  echo "  $url"
  echo "EveAssist must be STOPPED: the browser will then fail to load the callback, and its address bar still shows"
  echo "  $REDIRECT?code=...&state=..."
  read -r -p "Paste that whole address (or just the code): " pasted
  code="$(sed -E 's/.*[?&]code=([^&]+).*/\1/' <<<"$pasted")"
  status="$(post "$TOKEN_URL" "grant_type=authorization_code" "code=$code")"
  if [[ "$status" != 200 ]]; then say "  $name: exchange refused, HTTP $status $(why)"; return 1; fi
  jq -r .refresh_token "$WORK/body" > "$WORK/$name.refresh"; chmod 600 "$WORK/$name.refresh"
  local who_now; who_now="$(who "$(jq -r .access_token "$WORK/body")")"
  VALID[$name]=1
  say "  $name: authorized, character $who_now"
  CHARACTER_OF[$name]="$who_now"
}
declare -A CHARACTER_OF

refresh() {  # refresh <name> [label]: does EVE still honour it, and does it hand back a different refresh token?
  local name="$1" label="${2:-$1}" status sent got
  sent="$(cat "$WORK/$name.refresh")"
  status="$(post "$TOKEN_URL" "grant_type=refresh_token" "refresh_token=$sent")"
  if [[ "$status" == 200 ]]; then
    got="$(jq -r '.refresh_token // empty' "$WORK/body")"
    if [[ -z "$got" ]]; then say "  refresh $label: works (HTTP 200), no refresh token returned"
    elif [[ "$got" == "$sent" ]]; then say "  refresh $label: works (HTTP 200), same refresh token returned"
    else say "  refresh $label: works (HTTP 200), a DIFFERENT refresh token returned (rotation)"; printf '%s' "$got" > "$WORK/$name.refresh"; fi
    return 0
  fi
  say "  refresh $label: REFUSED, HTTP $status $(why)"
  VALID[$name]=0
  return 1
}

revoke() {  # revoke <name> [label]
  local name="$1" label="${2:-$1}" status
  status="$(post "$REVOKE_URL" "token_type_hint=refresh_token" "token=$(cat "$WORK/$name.refresh")")"
  say "  revoke $label: HTTP $status $(why)"
  VALID[$name]=0
}

cleanup() {
  echo
  echo "Cleaning up: revoking every token this run made that may still be valid."
  for name in "${!VALID[@]}"; do
    if [[ "${VALID[$name]}" == 1 && -f "$WORK/$name.refresh" ]]; then revoke "$name" "$name (cleanup)"; fi
  done
  rm -rf "$WORK"
  echo "Report (no tokens in it): $REPORT"
  echo "Also look at CCP's third-party applications page and revoke EveAssist for the test character if it is still listed."
}
trap cleanup EXIT

: > "$REPORT"
say "Refresh token experiment, $(date -u +%Y-%m-%dT%H:%M:%SZ), scope $SCOPE, redirect $REDIRECT"
echo
echo "Use a character that is NOT linked in EveAssist, or one you are happy to link again afterwards:"
echo "if revocation works per grant, this run ends EveAssist's access to that character too."
read -r -p "Is EveAssist stopped, and have you picked the character? [y/N] " ok
[[ "$ok" == y || "$ok" == Y ]] || exit 1

say ""
say "Step 1: authorize once (T1). Does refreshing it hand back a new refresh token?"
authorize T1 || exit 1
refresh T1

say ""
say "Step 2: authorize the SAME character again (T2) without revoking anything. Does T1 still work?"
authorize T2 || exit 1
if [[ "${CHARACTER_OF[T1]}" != "${CHARACTER_OF[T2]}" ]]; then say "  Different characters: start again."; exit 1; fi
refresh T1 "T1 (after T2 was issued)"; t1_ok=$?
refresh T2 "T2"; t2_ok=$?

say ""
if [[ $t1_ok -eq 0 && $t2_ok -eq 0 ]]; then
  say "Step 3: both work. Revoke T1 only. Does T2 survive?"
  revoke T1
  refresh T2 "T2 (after T1 was revoked)"
  refresh T1 "T1 (after its own revocation)"
else
  say "Step 3 skipped: a second authorization already ended the first token, so nothing stale is left behind."
fi

say ""
say "Step 4: revoke what is left, then authorize again (T3). Does revoke-then-authorize give a working token?"
for name in T1 T2; do
  if [[ "${VALID[$name]:-0}" == 1 ]]; then revoke "$name"; fi
done
authorize T3 || exit 1
refresh T3

say ""
say "Step 5: what does EVE answer to revoking something already revoked, and to nonsense?"
printf '%s' "$(cat "$WORK/T1.refresh")" > "$WORK/T1again.refresh"; revoke T1again "T1 again"
printf 'not-a-real-token' > "$WORK/junk.refresh"; revoke junk "a made-up token"

say ""
say "Done. Cleanup revokes T3. Send the report back."
