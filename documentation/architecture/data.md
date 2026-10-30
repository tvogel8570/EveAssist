# Data and Ownership

Status: draft, interview in progress.

## Databases and schemas

One Postgres instance holds:

| Database / schema | Holds | Changed by |
|---|---|---|
| `eveassist` database, `app` schema | Accounts, characters, ESI data, plans, projects, alerts, audit log | Flyway migrations |
| `eveassist` database, `sde` schema | The SDE (Fuzzwork tables), read-only for the app's role | SDE import |
| `keycloak` database | Keycloak's own data | Keycloak |

The SDE sits in the app's database, as a schema, so that the import can swap a new version in with a schema rename in
one transaction, and so that queries can join SDE tables to app data when needed. The prototype keeps it in a separate
`sdeyaml` database, which the new app does not use. Status: **agreed**.

All times are stored as UTC `timestamptz`. Each user's time zone is applied only when a page shows them.

Records that appear in URLs (plans, projects, facilities, alerts and similar) have a UUID as their public id, alongside
their internal key. Internal keys never appear in URLs, pages or JSON, so that the later
[REST API](api.md) can use the same ids and nothing can be guessed by counting. Status: **agreed** (part of the API
groundwork).

## Ownership

- Every personal row carries the account that owns it, directly (`owner_account_id`) or through its character, which
  belongs to one account.
- ESI data (blueprints, jobs, skills, implants, orders, wallet transactions, contracts, standings) references its
  character with `ON DELETE CASCADE`. Removing or unlinking a character deletes its data in one statement, as the
  requirements say.
- Plans, projects, facilities, materials on hand, manual prices and settings belong to the account. Jobs in a plan
  reference a character with `ON DELETE SET NULL`, so they show as unassigned when the character is removed.
- Deleting an account deletes the Keycloak user, then the account row, and everything owned by it cascades.
- Reads go through one visibility check per module, given the viewer (the account, and in phase 2 its group
  memberships). Repositories take the viewer as a parameter, so a query without one does not compile.

Status: **agreed**.

## Sharing in groups (phase 2)

Added as new tables, without changing the personal ones:

- `group` (kind: corporation or coalition, corporation id for a corporation group), `group_member` (account, role,
  when it joined) and `group_character` (which of a member's characters take part).
- Shared blueprints are the blueprints of shared characters, minus the rows in `blueprint_withdrawal`.
- `group_facility` holds the group's structures, with the rigs, tax, name and system that managers enter.
- Corporation ESI data (corporation blueprints, jobs, structures) references the group, not a character.
- Job status passed from a blueprint owner to another member's plan is a narrow table (plan job, status, when), so the
  planner never reads the owner's ESI rows.
- The visibility check gains "shared with a group the viewer belongs to". No existing query changes shape.

For phase 3, Postgres row-level security can be turned on with the same owner columns, using a per-transaction setting
for the current account as a second line of defence.

Why not a tenant id on every row: groups span accounts, so no single tenant owns shared data, and a tenant id would
duplicate the owner. Why not a schema per tenant: sharing across groups and migrating thousands of schemas would be
hard.

Status: **agreed**.

## SDE import and versioning

The SDE comes from Fuzzwork's Postgres dump, as in the prototype (`Infrastructure/postgres`).

1. The app checks Fuzzwork once a day for a new dump and, when there is one, shows admins a banner on the admin
   pages. An admin starts the import (from the admin pages, or a command for dev). The import never starts by itself,
   so a broken or half-published dump does not reach users unseen.
2. The dump is restored into a staging schema.
3. The industry tables (blueprint materials, products, times, invention probabilities, required skills, and the type
   data costing uses) are compared with the live `sde` schema.
4. A `sde_version` row records the dump's name and date and when it was imported, and `sde_blueprint_change` rows record
   each changed blueprint and activity.
5. The staging schema is renamed to `sde` in one transaction, and the old one is kept until the next import.
6. Saved plans that use a changed blueprint are flagged for re-costing. Live projects keep their own copies of the
   quantities they were saved with, so they are not touched.

Each plan records the SDE version it was last costed with. Only the `sde` module reads SDE tables, so a change in
Fuzzwork's table layout is handled in one place.

Why Fuzzwork rather than CCP's own JSONL export: it is ready to restore into Postgres with the familiar table layout,
the prototype already uses it, and diffing it ourselves gives the change list that plan flagging needs.

Status: **agreed**.

## Costing in memory

The industry data a plan needs (blueprints, activities, materials, skills, type groups and volumes) is a few megabytes.
The `sde` module loads it into memory at startup and after each import, so costing a large plan, such as a capital ship
with every intermediate, walks in-memory structures and stays within a few seconds. Status: **proposed**.

## Open questions

None at present.
