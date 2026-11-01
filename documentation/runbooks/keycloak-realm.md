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

## Moving to a newer Keycloak

An old realm file is put through Keycloak's upgrade steps at every import. A file from Keycloak 21 failed one of them
under 26.8, logging `Failed to migrate json representation` while the import still reported success. So when Keycloak
moves to a new major version, make the file again rather than carrying the old one forward:

1. Start the new version with an empty database, create a realm named `EveAssist`, and export it with `--users skip`.
2. Copy over EveAssist's settings: the `USER` and `ADMIN` roles, `USER` in place of `offline_access` in the default
   roles, the `eveassist-app` client, the `eve` identity provider, the `eveassist first login` flow, the
   optional profile fields, and `duplicateEmailsAllowed` true with `loginWithEmailAllowed` false.
3. Remove `org.keycloak.keys.KeyProvider` from `components`, because that export contains private keys.
