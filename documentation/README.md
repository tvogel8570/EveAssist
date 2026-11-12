# EveAssist documentation

| Folder | What it holds |
|---|---|
| [`requirements/`](requirements/README.md) | Requirements drafts, gathered by interview |
| [`architecture/`](architecture/README.md) | How EveAssist is built, and why |
| [`sde/`](sde) | SDE schema reference |
| [`runbooks/`](runbooks) | Step-by-step operating procedures |

## Open issues

Tracked as GitHub Issues: https://github.com/tvogel8570/EveAssist/issues

## Frequently used URLs

### Dev

Start Infrastructure first (`docker compose up -d` in `Infrastructure/`), then App (`./mvnw spring-boot:run` in `App/`).
On Windows, `dev.eveassist.com` needs an entry in the Windows hosts file as well as WSL's `/etc/hosts`.

| What | URL | Login |
|---|---|---|
| EveAssist (App) | https://dev.eveassist.com:7444 | `dev-user` (a normal user) or `dev-admin` (also an admin). Password: `KC_DEV_USER_PASSWORD` in `Infrastructure/.env` |
| EveAssist plans | https://dev.eveassist.com:7444/plans | same as above |
| Keycloak admin console | https://dev.eveassist.com:8442/admin | a `master` realm admin. The bootstrap admin (`KC_BOOTSTRAP_ADMIN_USERNAME`) exists only until you create a named admin and delete it, so use the named one. `create-dev-users.sh` asks for that admin's user and password (see the note below) |
| Keycloak, `EveAssist` realm | https://dev.eveassist.com:8442/admin/master/console/#/EveAssist | same admin as above |
| Keycloak, user account page | https://dev.eveassist.com:8442/realms/EveAssist/account | `dev-user` or `dev-admin` |

#### Opening the dev links from another computer

App and Keycloak run in WSL on `Tim-Xoticpc`, whose WSL network the other computers cannot reach. The Ubuntu distro
has its own Tailscale node, `xoticpc-wsl-ts`, so the ports are reachable over the tailnet. Its tailnet address is the one that
`tailscale ip -4` prints in that distro (below: `<tailscale-ip>`).

1. On `Tim-Xoticpc`, start Keycloak (`docker compose up -d` in `Infrastructure/`) and App with the `local` profile added
   to `dev`, so that it listens on all addresses: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local` in `App/`,
   or `dev,local` as Active profiles in IntelliJ. With `dev` alone App only listens on `127.0.0.1`. Do not run it with
   `local` alone, which switches the dev profile off, and with it the TLS settings.
2. On the other computer, add `<tailscale-ip> dev.eveassist.com` to its hosts file (`/etc/hosts`, or
   `C:\Windows\System32\drivers\etc\hosts` on Windows, edited as administrator).
3. Open the links above. The ports in the URLs stay the same.

Keycloak's token issuer is `https://dev.eveassist.com:8442`, so the browser must reach both App and Keycloak under that
hostname. The distro joined with `tailscale up --accept-dns=false`, and its key expiry is disabled in the Tailscale
admin console. Anything on the tailnet can reach these ports.

Postgres is on `localhost:2345` (not a web page). Passwords are in `Infrastructure/.env`.

Notes:
- Signing in with an EVE Online account through EVE SSO also works in dev. It gives a normal user, not an admin.
- The dev users are created by `Infrastructure/keycloak/create-dev-users.sh`, which refuses to run unless `ENVIRONMENT=dev`.
- The Keycloak admin password in `.env` is only used when Keycloak first starts on an empty database. After that,
  change it in the admin console, not in `.env`.
- Passwords are never written in this file. See `CLAUDE.md` on secrets.
