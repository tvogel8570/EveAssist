#!/bin/sh
# Creates the dev-only users that test sign-in without an external identity provider: dev-user (USER, by default) and
# dev-admin (ADMIN too). Refuses to run outside dev, because EveAssist keeps no passwords anywhere else.
#
# It signs in to Keycloak's master realm as an administrator. The bootstrap admin of .env exists only until a named admin
# replaces it (delete it then, as the prod runbook says), so this does not use it. Set KC_DEV_ADMIN_USERNAME and
# KC_DEV_ADMIN_PASSWORD in the environment to run without being asked; otherwise it asks, and the password is not echoed
# or stored.
set -eu
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
[ "$ENVIRONMENT" = dev ] || { echo "ENVIRONMENT is not dev" >&2; exit 1; }

if [ -z "${KC_DEV_ADMIN_USERNAME:-}" ]; then
    printf 'Keycloak master-realm admin user: ' >&2
    read -r KC_DEV_ADMIN_USERNAME </dev/tty
fi
if [ -z "${KC_DEV_ADMIN_PASSWORD:-}" ]; then
    printf 'Password for %s: ' "$KC_DEV_ADMIN_USERNAME" >&2
    stty -echo </dev/tty
    read -r KC_DEV_ADMIN_PASSWORD </dev/tty
    stty echo </dev/tty
    printf '\n' >&2
fi
export KC_DEV_ADMIN_USERNAME KC_DEV_ADMIN_PASSWORD

docker exec -i -e KC_DEV_USER_PASSWORD -e KC_DEV_ADMIN_USERNAME -e KC_DEV_ADMIN_PASSWORD -e HOST eve-assist-keycloak bash <<'SH'
set -eu
kc=/opt/keycloak/bin/kcadm.sh
keytool -importcert -noprompt -alias server -file /etc/x509/https/tls.crt -keystore /tmp/dev-users-trust.p12 -storepass changeit -storetype PKCS12 >/dev/null 2>&1 || true
$kc config truststore --trustpass changeit /tmp/dev-users-trust.p12
$kc config credentials --server "https://$HOST:8442" --realm master --user "$KC_DEV_ADMIN_USERNAME" --password "$KC_DEV_ADMIN_PASSWORD"
for name in dev-user dev-admin; do
    if [ -z "$($kc get users -r EveAssist -q "username=$name" -q exact=true --fields id --format csv --noquotes)" ]; then
        $kc create users -r EveAssist -s "username=$name" -s enabled=true -s "firstName=$name" >/dev/null
    fi
    $kc set-password -r EveAssist --username "$name" --new-password "$KC_DEV_USER_PASSWORD"
done
$kc add-roles -r EveAssist --uusername dev-admin --rolename ADMIN
SH
echo "dev-user and dev-admin are ready; their password is KC_DEV_USER_PASSWORD in .env"
