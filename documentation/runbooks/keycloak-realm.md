# Loading a Changed Realm File

`Infrastructure/keycloak/EveAssist-realm-export.json` is the realm's source of truth, and it is edited by hand. It holds
no users, secrets or signing keys. Secrets are `${...}` placeholders filled in from `Infrastructure/.env` during import.

Keycloak imports the file on its own only when the realm does not exist yet. On a new `keycloak` database, starting
Keycloak is all that is needed. For a database that already has the realm, use the steps below. They replace the realm
with the file in git and keep its users, their ids (`sub`, which `account` rows are keyed on), roles and linked sign-in
methods.

Use these steps also after changing a value in `.env` that the realm uses, such as `KC_EVE_CLIENT_ID`,
`KC_EVE_CLIENT_SECRET` or `KC_APP_CLIENT_SECRET`.

## What it costs

- Keycloak is down for about a minute.
- Everyone's Keycloak session ends, and signing keys are generated again. Sign-in tokens already issued stop being valid.
- Changes made in the admin console are lost unless they were copied into the file first. Compare before you start, if
  anyone may have made some.
- A user who holds a role that the new file removes makes the import fail. Remove the role from those users first.

## Steps

Run these in `Infrastructure/`. `keycloak/export/` is ignored by git, and must start empty.

1. Back up the database:

   ```bash
   docker exec eve-assist-postgres pg_dump -U postgres -Fc keycloak > keycloak/export/keycloak-before-import.pgdump
   ```

2. Stop Keycloak:

   ```bash
   docker compose stop eve-assist-keycloak
   ```

3. Export the realm and its users. This writes `EveAssist-realm.json` and `EveAssist-users-0.json` (more files with many
   users):

   ```bash
   docker compose run --rm eve-assist-keycloak-export
   ```

4. Replace the exported realm with the file from git. The users files stay:

   ```bash
   cp keycloak/EveAssist-realm-export.json keycloak/export/EveAssist-realm.json
   ```

5. Import. The log must end with `Import finished successfully` and show no `ERROR`:

   ```bash
   docker compose run --rm eve-assist-keycloak-export import --optimized --dir /opt/keycloak/data/export --override true
   ```

6. Start Keycloak, then sign in and check that the account still has its data in EveAssist:

   ```bash
   docker compose up -d eve-assist-keycloak
   ```

7. **Delete everything in `keycloak/export/`.** The export holds password hashes, secrets and private signing keys, and
   the backup holds the same.

## If it goes wrong

Before deleting the backup, restore it into a new database:

```bash
docker compose stop eve-assist-keycloak
```

```bash
docker exec eve-assist-postgres dropdb -U postgres keycloak
```

```bash
docker exec eve-assist-postgres createdb -U postgres -O keycloak_owner keycloak
```

```bash
docker exec -i eve-assist-postgres pg_restore -U postgres --no-owner --role=keycloak_owner -d keycloak < keycloak/export/keycloak-before-import.pgdump
```

Then run `3-init-eveassist.sh` as `Infrastructure/README.md` describes, to put back the database's access rules, and
start Keycloak.

## Keeping dev and prod on one Keycloak version

The version is pinned to a patch release in one place, `Infrastructure/keycloak/Dockerfile` (`FROM quay.io/keycloak/keycloak:26.8.2`),
and dev and prod both build from that file. Do not use a floating tag such as `26.8`: on 2026-11-11 dev ran 26.8.1 from an
old image while prod pulled 26.8.2, which started to reject admin API tokens without an audience, and nothing showed it until
the first prod start. To take a patch release, change the pin, rebuild both images (`docker compose build eve-assist-keycloak`
in `Infrastructure/` for dev, and the prod steps in the update runbook), and write the version in the deploy log. After a
rebuild, check that the dev log says the same `Keycloak 26.x.y` as prod.

## A client that calls the admin API needs the `kc-admin-api` scope

From Keycloak 26.8.2 a call to the admin REST API is refused with 401 unless the token carries the `kc-admin-api` scope, which adds the
API's own audience (the realm's admin URL) through the built-in "Admin API Audience" mapper. Keycloak gives that scope to its own
`admin-cli` and `security-admin-console` clients; any other client, such as the app's `eveassist-admin` service account, must have it
as a default client scope. Giving the client a generic audience mapper for `realm-management` does not work, and neither do role
claims, which the API does not read from the token. Keycloak's log says why only at debug level
(`KC_LOG_LEVEL=info,org.keycloak.services:debug`): "No audience in the token", then "Expected audience not available in the token".
The scope's definition is in the realm file, and it needs Keycloak 26.8.2 or newer, which is another reason the version is pinned.

## Moving to a newer Keycloak

An old realm file is put through Keycloak's upgrade steps at every import. A file from Keycloak 21 failed one of them
under 26.8, logging `Failed to migrate json representation` while the import still reported success. So when Keycloak
moves to a new major version, make the file again rather than carrying the old one forward:

1. Start the new version with an empty database, create a realm named `EveAssist`, and export it with `--users skip`.
2. Copy over EveAssist's settings: the `USER` and `ADMIN` roles, `USER` in place of `offline_access` in the default
   roles, the `eveassist-app` client, the `eve` identity provider, the `eveassist first login` flow, the
   optional profile fields, and `duplicateEmailsAllowed` true with `loginWithEmailAllowed` false.
3. Remove `org.keycloak.keys.KeyProvider` from `components`, because that export contains private keys.
