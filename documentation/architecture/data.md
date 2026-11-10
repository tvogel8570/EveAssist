# Data and Ownership

Status: draft, interview in progress.

## Databases and schemas

One Postgres instance holds:

| Database / schema | Holds | Changed by |
|---|---|---|
| `eveassist` database, `public` schema | Accounts, characters, ESI data, plans, projects, alerts, audit log | Flyway migrations |
| `eveassist` database, `sde` schema | The SDE (Fuzzwork tables), read-only for the app's role | SDE import |
| `keycloak` database | Keycloak's own data | Keycloak |

The developer decided that application tables use the `public` schema rather than a schema of their own.

### Database roles

Status: **agreed**. The `postgres` superuser is kept for set-up and admin tasks, and each job has its own role:

| Role | Used by | Can |
|---|---|---|
| `keycloak_owner` | Keycloak | Owns the `keycloak` database. It cannot connect to `eveassist`. |
| `eveassist_owner` | Flyway | Owns the `eveassist` database and its `public` schema, so it can change tables. |
| `eveassist_app` | The running app | Reads and writes rows in `public`, and reads `sde`. It cannot change tables. |
| `eveassist_sde` | SDE import | Owns `sde`, and can create the staging schema that replaces it. It cannot touch `public`. |

`Infrastructure/postgres/sql/init/3-init-eveassist.sh` creates the roles and databases, and can be run again.

The SDE sits in the app's database, as a schema, so that the import can swap a new version in with a schema rename in
one transaction, and so that queries can join SDE tables to app data when needed. The prototype keeps it in a separate
`sdeyaml` database, which the new app does not use. Status: **agreed**.

All times are stored as UTC `timestamptz`. Each user's time zone is applied only when a page shows them.

Records that appear in URLs (plans, projects, facilities, alerts and similar) have a UUID as their public id, alongside
their internal key. Internal keys never appear in URLs, pages or JSON, so that the later
[REST API](api.md) can use the same ids and nothing can be guessed by counting. Status: **agreed** (part of the API
groundwork).

## Migrations and test data

Status: **agreed** with the developer (2026-11-08), to be tried and changed if it does not hold up.

**Migrations** are Flyway files in `App/src/main/resources/db/migration`, named
`VYYYYMMDD_HHMM__descriptive-name-with-dashes.sql`, with the time in UTC when the migration is created.

- Flyway reads `V20261108_1530` as version 20261108.1530. The first six migrations were numbered `V1` to `V6` and were
  renamed to timestamps (taken from their commit times) on 2026-11-08, before any database but the developer's dev
  database had applied them.
- A timestamp avoids two branches both creating the next number. If a later migration is merged first, the older one
  is stamped again on rebase. `outOfOrder` stays off everywhere, so Flyway refuses to start otherwise.
- A merged migration is never edited, because Flyway checks its checksum. A change is a new migration.
- Migrations only go forward, with no undo scripts, and each one makes one change.
- Postgres can roll back DDL, and Flyway runs each migration in a transaction, so a failed migration leaves the
  database as it was. `CREATE INDEX CONCURRENTLY` is an exception.
- The SDE is not a migration: its tables come from Fuzzwork and are loaded by the import (see
  [SDE import and versioning](#sde-import-and-versioning)).

**Tests** build their database from the same production migrations, in a Testcontainer set up by the same init script
as the real one (see `PostgresTestDatabase`). One container serves the whole run, and tests run as the limited
`eveassist_app` role. Test data is added on top, never by changing the schema.

- Test-only SQL lives in `App/src/test/resources/db/test`, which only the `test` profile loads. Its files carry the
  marker `T_` (`VYYYYMMDD_HHMM__T_descriptive-name.sql`, or `afterMigrate__T_descriptive-name.sql`). Data goes into
  `afterMigrate` or repeatable files, which Flyway runs after every versioned migration, so a later schema change
  cannot run underneath it. Nothing under `src/main` has the marker.
- A test migration never drops or relaxes a constraint, because tests would then pass against a schema that production
  does not have.
- **Pre-loaded data** is read-only reference data: the small SDE fixture and similar. Its ids avoid real ones, or use
  the real values, so that it cannot contradict real data. A test never changes it.
- **A test creates what it changes**, under unique keys (a new account per test, ids from a range kept for tests), so
  tests do not depend on each other or on empty tables, and never assert on global counts. Tests that cannot
  use unique keys, such as browser tests whose requests commit on the server's own thread, remove their rows in
  `@AfterEach`.
- **Constraints count in success measures.** For a constraint that matters (foreign key, unique key, check,
  `ON DELETE CASCADE`), one test shows an invalid row is rejected, and the normal tests pass with it on.

**Checks that keep the rules** (run by `./mvnw verify`):

- `MigrationNamingTest` checks every file name, that the stamp is a real date and not in the future, and that the
  marker `T_` is in the test location only.
- `MigrationsIT` applies only the production migrations to an empty database, validates them, and confirms that nothing
  is left pending. A migration that works only with test data fails here.
- A migration is tried by `./mvnw verify` before the app applies it to the dev database. An empty database does not
  prove it works on the dev data, for example a new `NOT NULL` column. If that bites, a rehearsal script (dump the dev
  database, restore it in a container, run the pending migrations) is the next step.

## Ownership

- Every personal row carries the account that owns it, directly (`owner_account_id`) or through its character, which
  belongs to one account.
- ESI data (blueprints, jobs, skills, implants, orders, wallet transactions, contracts, standings) references its
  character with `ON DELETE CASCADE`. Removing or unlinking a character deletes its data in one statement, as the
  requirements say.
- Plans, projects, facilities, materials on hand, manual prices and settings belong to the account. Jobs in a plan
  reference a character with `ON DELETE SET NULL`, so they show as unassigned when the character is removed.
- Deleting an account deletes the Keycloak user, then the account row, and everything owned by it cascades.
- Reads and writes are filtered in their SQL, given the viewer (the signed-in account, whose group memberships are looked
  up in the SQL in phase 2). Repositories take the viewer as a parameter, so a call without one does not compile (see
  [Pages, the API and the facade](code-structure.md#pages-the-api-and-the-facade)).

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

Until the admin import is built, `Infrastructure/postgres/load-sde.sh` loads the dump into `sde` for dev, with no comparison
or flagging.

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

The developer decided that SDE updates are done during an outage. No screens are open while the data changes, so
nothing is pushed to pages when an import finishes. Saved plans still need updating, through the flags in step 6.

Each plan records the SDE version it was last costed with. Only the `sde` module reads SDE tables, so a change in
Fuzzwork's table layout is handled in one place.

Why Fuzzwork rather than CCP's own JSONL export: it is ready to restore into Postgres with the familiar table layout,
the prototype already uses it, and diffing it ourselves gives the change list that plan flagging needs.

Status: **agreed**.

## Costing in memory

The industry data a plan needs (blueprints, activities, materials, skills, type groups and volumes) is a few megabytes.
The `sde` module loads it into memory at startup and after each import, so costing a large plan, such as a capital ship
with every intermediate, walks in-memory structures and stays within a few seconds. The import swaps the in-memory
copy at the same time as the schema. Status: **agreed**.

### Last-costed total

The developer decided that each plan stores its last-costed total, with enough provenance to know when to recalculate.
SQL cannot work out a total, because costing runs in memory, and costing every plan to draw a list would be too slow.
Status: **agreed**. The columns and rules below are **proposed**.

| Column on `plan` | Holds | Plan needs recalculating when |
|---|---|---|
| `total_cost` | The total from the last costing | |
| `costed_plan_version` | The plan's `version` when costed | It differs from the plan's `version` |
| `costed_sde_version` | The SDE version used (the version each plan already records, see [SDE import](#sde-import-and-versioning)) | It differs from the live SDE version |
| `prices_as_of` | The age of the oldest price used | |
| `costed_at` | When the costing ran | Another input changed after this time: skills, implants, facilities, cost indices, manual prices or settings |

- **A price never marks a plan out of date.** A plan keeps its own prices (see [Plan prices](#plan-prices)) and they
  change only when the user asks, so a plan is out of date only when its version or the SDE version differs from the one
  it was costed with. The earlier `plan_costed_item` table, which compared items with the shared prices, was dropped.
- A plan edit costs the plan anyway, so it stores all of these in the same transaction as the edit. Opening a plan stores
  them too if the stored total is out of date. Nothing recalculates in the background (see
  [Work with no signed-in person](code-structure.md#work-with-no-signed-in-person)).
- Only the total is stored. Line-level results are still calculated when a plan is opened.
- A recalculation that is not an edit does not change the plan's `version`, so it does not cause a conflict for someone
  editing the plan. Its update has `WHERE version = ?` with the version it loaded, so an older result cannot overwrite a
  newer one.
- The list shows the stored total with its age, marked if it is out of date. It does not recalculate to draw the list.
- **Measured** (with the earlier check that also joined `plan_costed_item`): the list query took 4.7 ms for an account
  with 100 plans, among 100,000 plans, 1,000,000 product rows and 5,000,000 costed items on Postgres 18, using
  `plan_owner_ix`. The check now reads only the plan row, so it should be cheaper. It has not been measured again.

### Plan prices

Status: **agreed** with the developer (2026-11-08), so that a plan can be kept as quoted to a customer.

| Table | Holds |
|---|---|
| `plan_price` | One row per plan and item: `sell`, `buy` (null when that side has no orders), `as_of`, and `source` (`market` now, `manual` for a price the user sets, see issue #8) |
| `jita_order` | The orders at Jita 4-4 as ESI last gave them, shared by all users, replaced per item when ESI answers |
| `jita_fetch` | When each item's orders were last confirmed current |
| `esi_cache` | The `ETag`, `Last-Modified` and `Expires` ESI gave for each route and parameter set |

- A plan costs with `plan_price` only, never with the shared orders. The rows are written with the plan, in the same
  transaction as an edit or a refresh.
- A refresh replaces the prices of every item ESI answered for, keeps those it did not, and drops prices for items the
  plan no longer needs. It increases the plan's `version`, like any edit.
- Adding a product copies in prices for items the plan has none for, and leaves the others alone.
- A plan holds a price for everything its products could need, bought or built, so buying or building a part never
  needs a new one.
- `prices_as_of` on `plan` is the age of the oldest price among the items that are bought.

### Public contracts

Status: **proposed**. Public data shared by all users, so these tables have no owner and no viewer.

| Table | Holds |
|---|---|
| `contract_public` | A public item exchange contract of up to 100 m³ with a price: region, price, issued and expiry time, start location, volume, whether its lines have been read, and two flags, `has_bpo` and `has_abyssal` (an original is a blueprint line, with ME or TE, that is not a copy; ESI sends it without `runs`), for contracts set aside because they hold a blueprint original or an Abyssal item |
| `contract_public_item` | The lines of a contract, read once: type, quantity, whether the issuer gives or asks for it, and for blueprint copies the ME, TE and runs. Removed with the contract |
| `contract_public_fetch` | When each region's list was last confirmed current |

- A refresh makes the stored contracts match ESI's list: contracts that are gone or expired are deleted, and new ones are
  added with their lines pending. Contracts already stored are not touched.
- Prices are worked out from these rows when asked and are not stored.

## Open questions

- How the SDE outage runs, given that step 1 has an admin start the import from the admin pages: the site stays up for
  admins only, or the app is stopped and the import is run as a command. The developer chose to decide this later.
