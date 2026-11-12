#!/bin/bash
# Prod only. Starts the stack, or applies a changed image or setting. Run it from anywhere on the instance:
#   /opt/eveassist/Infrastructure/prod/start.sh [docker compose up arguments, e.g. app]
# The three Secrets Manager secrets become environment variables of this process only, so no .env file with secrets
# exists on disk. The images to run come from images.env (see images.env.template).
set -euo pipefail
cd "$(dirname "$0")"

# prod.env and images.env are plain NAME=value files; exporting them lets compose.yaml substitute them
set -a
. ./prod.env
. ./images.env
set +a

for secret in eveassist/db eveassist/keycloak eveassist/app; do
    # Fetch first, then eval: the exit status of eval "$(...)" is that of the evaluated text, which is 0 for an empty string, so a
    # failed call (expired role, throttling, a missing secret) would pass unnoticed and the stack would start without that secret
    # (compose.yaml only refuses the variables marked :?; EVE_LINK_CLIENT_ID and _SECRET fall back to empty).
    text=$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text)
    [ -n "$text" ] && [ "$text" != None ] || { echo "start.sh: secret $secret came back empty, not starting" >&2; exit 1; }
    # @sh quotes each value, so a secret may hold any character
    exports=$(printf '%s' "$text" | jq -r 'to_entries[] | "export \(.key)=\(.value | @sh)"')
    [ -n "$exports" ] || { echo "start.sh: secret $secret holds no values, not starting" >&2; exit 1; }
    eval "$exports"
done
unset text exports

aws ecr get-login-password | docker login --username AWS --password-stdin "${APP_IMAGE%%/*}"
docker compose up -d --remove-orphans "$@"
docker compose ps
