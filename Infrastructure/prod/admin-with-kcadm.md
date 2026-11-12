# Named Keycloak admin without a browser (prod), using kcadm through an SSM shell

Why not the console: the tunnel's local port is only on WSL's loopback address, and your Windows Chrome cannot reach it, even at
127.0.0.1. Everything that works from Windows listens on all interfaces. The console must also be opened at exactly
`localhost:8080`. `kcadm` needs none of that, and it asks for your new password on your own screen.

Stop the browser tunnel first (Ctrl+C in the terminal that runs `aws ssm start-session ... AWS-StartPortForwardingSession`).

## 1. Open a shell on the instance (a WSL terminal)

```bash
AWS_PROFILE=EveAssist-admin AWS_REGION=us-west-2 aws ssm start-session --target "$EVEASSIST_INSTANCE_ID"
```

## 2. In that shell, pick your admin user name and run kcadm inside Keycloak's container

> `sudo` clears your environment, so a plain `-e ADMIN_USER` would pass an empty name ("User name is missing"). Each command below
> therefore gives the value explicitly: `-e ADMIN_USER="$ADMIN_USER"`. Set `ADMIN_USER` once in the shell first.

Replace `YOUR-NAME` (a user name for you; it will be a master-realm administrator). The bootstrap login uses the container's own
environment, so no password is typed or shown for it.

```bash
export ADMIN_USER=YOUR-NAME
sudo docker exec -it -e ADMIN_USER="$ADMIN_USER" eveassist-keycloak-1 sh -c '
K=/opt/keycloak/bin/kcadm.sh
$K config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" \
  && $K create users -r master -s username="$ADMIN_USER" -s enabled=true \
  && $K add-roles -r master --uusername "$ADMIN_USER" --rolename admin \
  && echo "created $ADMIN_USER with the admin role"'
```

## 3. Set your password (it prompts; type a long one; nothing is printed)

```bash
sudo docker exec -it -e ADMIN_USER="$ADMIN_USER" eveassist-keycloak-1 sh -c '
/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null \
  && /opt/keycloak/bin/kcadm.sh set-password -r master --username "$ADMIN_USER"'
```

## 4. Prove that you can sign in as yourself (it prompts for your new password)

```bash
sudo docker exec -it -e ADMIN_USER="$ADMIN_USER" eveassist-keycloak-1 sh -c '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user "$ADMIN_USER"'
```

Expected: `Logging into http://localhost:8080 as user YOUR-NAME of realm master`. If it says invalid credentials, repeat step 3.
Only continue when this worked.

## 5. Turn on brute-force protection for the master realm, then delete the bootstrap user

```bash
sudo docker exec -it -e ADMIN_USER="$ADMIN_USER" eveassist-keycloak-1 sh -c '
K=/opt/keycloak/bin/kcadm.sh
$K config credentials --server http://localhost:8080 --realm master --user "$ADMIN_USER" \
  && $K update realms/master -s bruteForceProtected=true -s failureFactor=10 -s permanentLockout=false \
  && echo "brute-force protection is on" \
  && ID=$($K get users -r master -q username="$KC_BOOTSTRAP_ADMIN_USERNAME" --fields id --format csv --noquotes) \
  && [ -n "$ID" ] && $K delete users/$ID -r master && echo "bootstrap user deleted"'
```

## 6. Leave the shell (`exit`), then tell me

I will check from the database that the bootstrap user is gone, that your user exists, and that brute-force protection is on,
and record it in the deploy log. The bootstrap values stay in Secrets Manager because Compose still requires them, but they no
longer open anything. An authenticator app (OTP) for your user needs the console; leave it for later.
