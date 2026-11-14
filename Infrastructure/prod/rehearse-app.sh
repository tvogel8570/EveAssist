#!/bin/bash
# Rehearsal of a release on a restored copy of the database. On a pass it prints the APP_IMAGE line for images.env. Usage: sudo ./rehearse-app.sh <db endpoint> <image@sha256:...>
set -euo pipefail
COPY_HOST=${1:?usage: rehearse-app.sh <endpoint of the restored database> <app image with digest>}
IMAGE=${2:?usage: rehearse-app.sh <endpoint of the restored database> <app image with digest>}
cd "$(dirname "$0")"
set -a; . ./prod.env; set +a
for secret in eveassist/db eveassist/keycloak eveassist/app; do
    text=$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text)
    [ -n "$text" ] && [ "$text" != None ] || { echo "rehearse-app.sh: secret $secret came back empty" >&2; exit 1; }
    exports=$(printf '%s' "$text" | jq -r 'to_entries[] | "export \(.key)=\(.value | @sh)"')
    [ -n "$exports" ] || { echo "rehearse-app.sh: secret $secret holds no values" >&2; exit 1; }
    eval "$exports"
done
unset text exports
aws ecr get-login-password | docker login --username AWS --password-stdin "${IMAGE%%/*}" >/dev/null 2>&1
free -m | awk 'NR==2{print "host memory: " $7 " MB available before the rehearsal"}'
docker rm -f rehearsal-app >/dev/null 2>&1 || true
export EVEASSIST_DB_URL="jdbc:postgresql://${COPY_HOST}:5432/eveassist?sslmode=require"
docker run -d --name rehearsal-app --network eveassist_default --memory 900m --memory-swap 900m \
    -e SPRING_PROFILES_ACTIVE=prod -e APP_BASE_URL="https://${DOMAIN}" \
    -e KEYCLOAK_ISSUER_URI="https://${AUTH_DOMAIN}/realms/EveAssist" -e KEYCLOAK_ADMIN_URL=http://keycloak:8080 \
    -e EVEASSIST_DB_URL -e EVEASSIST_APP_PASSWORD -e EVEASSIST_OWNER_PASSWORD -e KC_APP_CLIENT_SECRET -e KC_ADMIN_CLIENT_SECRET \
    -e EVE_LINK_CLIENT_ID -e EVE_LINK_CLIENT_SECRET -e ESI_CONTACT_EMAIL -e EVE_TOKEN_DATA_KEY_WRAPPED -e EVE_KMS_KEY_ID \
    -e AWS_REGION="$AWS_DEFAULT_REGION" "$IMAGE" >/dev/null
echo "rehearsal container started against ${COPY_HOST}; waiting for health (up to 3 minutes)"
status=down
for i in $(seq 1 36); do
    sleep 5
    docker ps --filter name=rehearsal-app --filter status=running -q | grep -q . || { echo "the container stopped"; break; }
    if docker exec rehearsal-app bash -c "exec 3<>/dev/tcp/127.0.0.1/8081 && printf 'GET /actuator/health HTTP/1.0\r\n\r\n' >&3 && grep -q '\"status\":\"UP\"' <&3" 2>/dev/null; then status=UP; break; fi
done
echo "health: $status"
echo "--- Flyway and start-up lines (the app's own log, no secrets):"
docker logs rehearsal-app 2>&1 | grep -E "Flyway|Successfully validated|No migration necessary|up to date|Started EveAssist|ERROR|APPLICATION FAILED" | cut -c1-220 | tail -12
free -m | awk 'NR==2{print "host memory: " $7 " MB available with the rehearsal running"}'
docker rm -f rehearsal-app >/dev/null 2>&1
echo "rehearsal container removed"
# The result. Only a rehearsal with health UP gives images.env lines to deploy; read "Successfully validated N migrations" and
# "No migration necessary" (or the migration you expect) in the lines above as well: that is the rest of a pass.
if [ "$status" != UP ]; then
    echo "health was $status: this is NOT a pass. Do not deploy; read the lines above."
    exit 1
fi
echo
echo "--- For images.env (update runbook, deploy, step 1). Copy the old file first: sudo cp images.env images.env.bak-<running version>"
[ -r images.env ] && grep -E '^(APP|KEYCLOAK)_IMAGE=' images.env | sed 's/^/# now in images.env: /'
echo "APP_IMAGE=${IMAGE}"
case "$IMAGE" in *@sha256:*) ;; *) echo "# warning: the image has no @sha256 digest; images.env pins digests, not tags" ;; esac
echo "# KEYCLOAK_IMAGE stays as it is, unless you built a new Keycloak image for this release"
