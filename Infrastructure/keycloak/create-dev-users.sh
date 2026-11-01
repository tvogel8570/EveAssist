#!/bin/sh
# Creates the dev-only users that test sign-in without an external identity provider: dev-user (USER, by default) and
# dev-admin (ADMIN too). Refuses to run outside dev, because EveAssist keeps no passwords anywhere else.
set -eu
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
[ "$ENVIRONMENT" = dev ] || { echo "ENVIRONMENT is not dev" >&2; exit 1; }

docker exec -i -e KC_DEV_USER_PASSWORD -e KC_BOOTSTRAP_ADMIN_USERNAME -e KC_BOOTSTRAP_ADMIN_PASSWORD -e HOST eve-assist-keycloak bash <<'SH'
set -eu
kc=/opt/keycloak/bin/kcadm.sh
keytool -importcert -noprompt -alias server -file /etc/x509/https/tls.crt -keystore /tmp/dev-users-trust.p12 -storepass changeit -storetype PKCS12 2>/dev/null || true
$kc config truststore --trustpass changeit /tmp/dev-users-trust.p12
$kc config credentials --server "https://$HOST:8442" --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"
for name in dev-user dev-admin; do
    if [ -z "$($kc get users -r EveAssist -q "username=$name" -q exact=true --fields id --format csv --noquotes)" ]; then
        $kc create users -r EveAssist -s "username=$name" -s enabled=true -s "firstName=$name" >/dev/null
    fi
    $kc set-password -r EveAssist --username "$name" --new-password "$KC_DEV_USER_PASSWORD"
done
$kc add-roles -r EveAssist --uusername dev-admin --rolename ADMIN
SH
echo "dev-user and dev-admin are ready; their password is KC_DEV_USER_PASSWORD in .env"
