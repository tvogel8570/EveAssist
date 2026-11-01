## Infrastructure

Create postgres and keycloak docker instances

## Prerequisites

### postgres

- ensure `password.txt` exists under `postgres` directory
- ensure `postgres/backup/latest-postgresschema.pgdump` is the latest sdeyaml export
  from [Fuzzwork](https://www.fuzzwork.co.uk/dump/)

### keycloak-signed certificate and private key exist and are named properly

    - `keycloak/dev.eveassist.com.crt`
    - `keycloak/dev.eveassist.com.key`

### databases and roles

`postgres/sql/init/3-init-eveassist.sh` creates the `keycloak` and `eveassist` databases and their roles
(see `documentation/architecture/data.md`). Set the passwords in `.env` first; `template_.env` lists them.

A new volume runs the script on its first start, after the image is rebuilt with `docker compose build`. To run it
against an existing volume, without a rebuild:

```bash
set -a; . ./.env; set +a; docker exec -i -e KC_DB_PASSWORD -e EVEASSIST_OWNER_PASSWORD -e EVEASSIST_APP_PASSWORD -e EVEASSIST_SDE_PASSWORD eve-assist-postgres sh < postgres/sql/init/3-init-eveassist.sh
```

### keycloak realm

Keycloak imports `keycloak/EveAssist-realm-export.json` when the realm does not exist yet. To load a changed file into
a realm that has users, follow `documentation/runbooks/keycloak-realm.md`.

For dev, `keycloak/create-dev-users.sh` creates `dev-user` and `dev-admin`, with the password in `KC_DEV_USER_PASSWORD`.
It refuses to run unless `ENVIRONMENT` is `dev`.
