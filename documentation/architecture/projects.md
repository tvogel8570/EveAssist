# Projects: persistence and use cases

Status: **proposed** and settled with the developer on 2026-11-18, for review of the details before any migration is written (step 1 of
issue #62). Nothing here is built except the pure costing (`ProjectCosting`, `JobCosting`, `BlueprintSplitter`, see
[Project costing](data.md#project-costing)). The requirements are in [Projects](../requirements/projects.md) and
[Costing components](../requirements/costing-detail.md), and a worked example is in the
[sample walkthrough](../requirements/sample-project-cost-walkthrough.md).

This note decides what is stored, which use cases exist, and how a project stays correct while several things change under it. It
follows the plan's pattern on purpose (`Plan`, `PlanRepository`, `PlanEditing`, `PlanQueries`), so a reader who knows plans knows projects.

## Decisions (the developer, 2026-11-18)

| Question | Decision |
|---|---|
| Branch | Keep working on `projects-requirements` (PR #60 grows, and is squash-merged when the developer says so) |
| Products | The plan provides the **initial** products. While the project is a **Draft** its products can change, including ones the plan does not have |
| Facility table | In step 1, with typed percents; step 3 adds columns |
| Quotes | Stored as JSON snapshots |
| Prices | The project **copies the plan's prices at start and owns them** afterwards: it keeps its own price rows and its own price side (sell or buy) |
| JSON mapping | A hybrid. The **inputs** of a quote are stored through document records and mappers (they are read back into domain objects). The **outputs**, the costing and the trace, are stored as **display-only documents**, never turned back into domain objects |
| Lifecycle | **Once a project has been quoted it is no longer a Draft and cannot be edited.** *New version from this quote* makes it a Draft again, starting as a copy of that quote |
| Deleting a quote | **A single quote can be deleted** (rules below) |
| Deleting a facility | **Current design**: the items that used it fall back to the project's default facility |
| Adding a product to a Draft | Its prices are **fetched at once, only for the added product**. A product that is removed and added again is priced again at once |

## Four different "versions"

They are easy to confuse, so each has its own name in the tables and the code:

| Name | What it counts | Changes when |
|---|---|---|
| `project.version` | **edits** of the project, for optimistic concurrency (a stale edit is refused) | the developer edits the Draft. Costing and archiving do not change it |
| `costed_project_version` | the `project.version` a costing was made for | a costing is stored. It is what says a costing is out of date |
| `costed_sde_version` | which **SDE import** the costing used | the SDE is re-imported |
| `inputs_format_version`, `document_format_version` | the **layout of a stored JSON document** | a document's fields change meaning (see below). Unrelated to the other three |
| `number` on a quote | which quote of the project this is (1, 2, 3), never reused | a quote is issued |

## Shape

A **project** is an aggregate owned by one account, in one of two states: **Draft** (editable) or **Quoted** (locked). *Start project* copies a
plan into a new Draft, and the plan is not changed afterwards. *Issue quote* freezes the Draft as a numbered **quote** and locks the project.
*New version from this quote* copies that quote back into the project's tables and makes it a Draft again, and the earlier quote stays as sent.
**Archived** is a separate flag and does not change the state.

```
project ─┬─ product, bought part, item, item skill, price      the working inputs, in tables you can query
         ├─ costing (latest, replaced each time)  ── trace     the result of "Cost", for the Draft
         └─ quote 1, 2, ...  each with its costing ── trace    frozen snapshots, never edited
facility belongs to the account and is used by projects
```

Where it lives: the `industry` module, `internal/domain` (`Project` as an `@AggregateRoot`, with `ProjectId` public like `PlanId`),
`internal/application` (use cases and the repository port), `internal/persistence` (JDBC). The module may use `identity`, `sde` and
`market` today. Reading owned blueprints needs `inventory` and is step 5, so this step types them.

## Tables

Every table is account-owned through `project`, which references the account with `ON DELETE CASCADE`, so deleting an account removes it all
([Ownership](data.md#ownership)). Names follow the plan's.

| Table | Holds | Notes |
|---|---|---|
| `project` | `public_id`, `owner_account_id`, `name`, `version`, `status` (`draft` or `quoted`), `last_quote_number`, `source_plan_id` (set null if the plan is deleted), `archived_at`, the **settings** (ISK per hour, ISK per m³, contract fee, quote mode, margin %, premium %, quote step, price side, default split strategy and time bound), the draft's buyer name and note, and the **builder** (Industry and Advanced Industry levels, implant percent) | `version` rises on every edit, as a plan's does. Archiving and costing do not change it |
| `project_product` | `project_id`, `type_id`, `quantity` | copied from the plan at start. **Editable in a Draft**: add (any buildable type the SDE knows), remove, change the quantity |
| `project_bought_part` | `project_id`, `type_id` | a part with a recipe that is bought, copied from the plan, changeable in a Draft |
| `project_item` | one row per built item: `me`, `te`, `blueprint_cost_per_run`, `blueprint_kind` (`typed` now, `owned` in step 5), `facility_id` (null means the project's default), split strategy and time bound if they differ from the project's | the Draft's blueprint choices. Typed ME and TE are the first form |
| `project_item_skill` | `project_id`, `item_type_id`, `skill_type_id`, `level` | the builder's level in each time-saving skill the item's blueprint requires. The percent per level is read from the SDE, not stored |
| `project_price` | like `plan_price`: `type_id`, `source` (`market` or `manual`), `sell`, `buy`, `as_of` | copied from the plan at start, then **the project's own**, refreshed only when the user asks. Costing never reads the shared Jita orders |
| `facility` | account-owned: name, the eight terms of `FacilityTerms` (structure and rig material, time and cost percents, cost index, tax, SCC), and later the structure type, rigs and system | typed percents first. An item points at one, or the project's default. Deleting a facility sets `facility_id` to null |
| `project_costing` | the Draft's latest costing (`quote_id` null) and each quote's (`quote_id` set, and the project must be the quote's, by a composite foreign key): provenance (`costed_project_version`, `costed_sde_version`, `prices_as_of`, `costed_at`), `delivered_cost`, `quotable`, `document_format_version`, and one `jsonb` **display document** of the costing | one row with no quote per project (a partial unique index), replaced on each costing. A quote's row is never changed, and **the database refuses an update** of it |
| `project_trace` | `costing_id`, `document_format_version`, `jsonb` **display document** of the trace | **its own table**, with `ON DELETE CASCADE` from the costing, so that the 30-day purge of an archived project's traces **deletes rows and never modifies a frozen quote** |
| `project_quote` | `project_id`, `number`, `issued_at`, `expires_at`, `buyer_name`, `note`, the price and its mode, and the **inputs snapshot** (`jsonb`, with `inputs_format_version`) | frozen, and **the database refuses an update**. Quote numbers are 1, 2, 3 per project. Its costing points back at it (`project_costing.quote_id`), so deleting a quote deletes its costing and trace |

**Why quotes are snapshots and the Draft is tables.** The Draft changes all the time and is queried (the list shows a total, an archive filter, a
staleness check), so it is in tables. A quote never changes and is read as a whole, so a snapshot is enough and it avoids copying nine tables
for every quote. Because a quoted project is locked, its tables hold the same inputs as its latest quote until *New version from this quote*
replaces them with the chosen quote's snapshot. Older quotes exist only as snapshots.

## How the JSON is stored (the hybrid)

The domain records carry no Jackson annotations (the architecture rule `domainStaysPlain` forbids it, and its fixture `Jackson3AwarePlan` shows
the case), so the JSON shapes live in `persistence`.

- **Inputs snapshot (`project_quote.inputs_snapshot`).** Document records for the inputs only, about ten: the products, the bought parts, each
  item's build (typed terms or owned blueprints, the jobs, the split policy), the facility's values, the builder and its product skills, the
  settings, and the prices used. Two mappers, domain to document and back. Reading is through the domain constructors, so the values are checked
  on the way in, and a record whose rules tighten later needs an upcaster for the versions stored before. Its version is a column (below).
- **Outputs (`project_costing.costing`, `project_trace.document`).** Display documents built from `ProjectCost` and the job detail
  (`JobCost`, `MaterialUse`, `TimeBreakdown`, `InstallCostBreakdown`) by one mapper. They are read only by the page that shows them, which
  renders from the document, and are **never converted back to domain objects**. A change to the domain therefore does not break an old quote.
  Its version is a column too, and the page reads each version it has to.
- **The format version is a column, and it decides how a document is read** (the developer's point, 2026-11-18: even a tolerant reader needs the
  version to read an old document properly). `project_quote.inputs_format_version`, `project_costing.document_format_version` and `project_trace.document_format_version`
  are `NOT NULL` columns, the one authority; the JSON does not repeat them. A reader looks at the column **before** it parses the document.
- **What changes the format version.** A change that alters what a stored field *means* or removes one a reader needs: a rename, a unit (percent or
  fraction), a rounding rule, a split of one field into two. A new **optional** field does not, because an old document simply lacks it. Tolerant
  fields therefore cover additions only, and the version covers everything else.
- **A document class per format version.** Each version is its own record (`QuoteCostingV1`, `QuoteCostingV2`, and for the inputs `QuoteInputsV1`), written by
  its mapper and read straight back by Jackson with no further annotation. A new version adds a class and keeps the old one, and the page renders
  through one reader per version. Direct Jackson serialization of the domain records was considered and **not taken**: it saves only the writing mapper,
  since a reader per version is needed either way (checked with Jackson 3.1.7: a required or primitive field added to a record makes old JSON fail to load).
- **Retiring a version.** A reader may go only when no stored document has that version. A Flyway data migration converts the old documents to the
  current version first, and is itself tested against a stored example. Until then the reader stays.
- **Tests:** a stored example of every version that is still stored, loaded or rendered, so a change that breaks old data fails a test and not a
  customer. A new version comes with its example and with a test that its mapper writes what its reader reads.
- **Nothing is queried inside the JSON.** What a list needs (price, delivered cost, expiry, quotable) is a real column, copied out when the
  quote is written.

## Use cases

Primary ports in `internal/application`, each `@Transactional`, each taking the `Viewer` first, as the plan's do. An edit of a quoted project is
refused with `ProjectLockedException`.

| Use case | What it does | Result |
|---|---|---|
| `ProjectEditing.start(viewer, planId)` | copies the plan's products, bought parts and prices into a new Draft, with the viewer's default settings | the project id |
| `ProjectEditing.rename`, `archive`, `restore`, `delete` | like the plan's. Rename, archive and restore are allowed in either state, and **none of them changes the version** (the name is not an input of the costing, so a rename leaves a stored costing current). Delete removes the quotes, costings and traces | |
| `ProjectQuoting.deleteQuote(viewer, projectId, number)` | deletes one quote with its costing and trace (see the rules under Concurrency) | |
| `ProjectEditing.addProduct`, `removeProduct`, `changeQuantity` | Draft only, with an `expectedVersion`. A product the plan did not have is fine. **Adding fetches, at once, the prices of that product and of everything it needs that the project has no price for**, and leaves every price the project already has as it is. **Removing drops the prices that no remaining product needs**, so adding it back fetches them again at once | the new version |
| `ProjectEditing.setSettings`, `setBuilder`, `setItem`, `choosePartSource`, `setDefaultFacility` | Draft only, one edit, with an `expectedVersion`. A stale version is refused (`ProjectChangedException`), as a plan's is | the new version. Does **not** cost |
| `ProjectPrices.refresh(viewer, projectId)` | Draft only. Replaces the project's prices from the shared Jita orders, as `PriceRefresh` does for a plan | which prices changed |
| `ProjectCostings.cost(viewer, projectId)` | Draft only. Builds a `ProjectDefinition`, calls `ProjectCosting.cost` and `compareToLeastCost`, stores the Draft's costing and trace | the costing, with warnings and whether a quote can be issued |
| `ProjectQuoting.issue(viewer, projectId, command)` | needs a stored costing for the **current version** and `quotable`. Writes the next quote number with the snapshot, the buyer, the note and the expiry, and **locks** the project | the quote |
| `ProjectQuoting.newVersionFrom(viewer, projectId, number)` | replaces the tables' inputs with that quote's snapshot, bumps the version, and makes the project a **Draft** again. The quote stays as sent | the new version |
| `ProjectQueries.overviews`, `details`, `quote`, `quotes` | reads for the pages. `details` says a Draft's costing is **out of date** when its project version or SDE version differs, with the costing's age | views, never domain objects |

**Costing is on request only** (decided 2026-11-18): no use case costs as a side effect of an edit or of opening a project. A quoted project's
costing is the quote's, frozen. Showing current prices beside the frozen ones (a requirement) is a query that costs without storing, and is not part of step 1.

**What `cost` needs and where each part comes from.** It resolves the inputs through ports, so steps 2 to 5 replace one adapter each and the
costing does not change:

| Input | Port | In step 1 |
|---|---|---|
| Recipes, packaged volumes, a blueprint's time-saving skills | `sde` module | recipes exist. **Volumes and time skills are new SDE reads** (attribute 1982, `industryActivitySkills`) and belong to this step, because a project cannot be costed without them |
| Unit prices | the project's `project_price` | built |
| Adjusted prices | `AdjustedPriceService` in the `market` module, over the shared `adjusted_price` table ([Adjusted prices](esi.md#adjusted-prices)) | built (step 2). `ProjectEntry.cost` refreshes them first, and a job whose material has no adjusted price warns `MISSING_ADJUSTED_PRICE` and blocks a quote |
| Cost index, facility terms | the `facility` row | typed |
| Builder | the project's columns and `project_item_skill` | typed |
| Owned blueprints | none yet | typed ME and TE per item (`ProjectBuild.Typed`, cut at the 30-day limit and at a time bound, with the item's or the project's split policy); the splitter's `FromBlueprints` waits for step 5 |

## Relationships and deletes

| From → to | On delete |
|---|---|
| account → plan, project, facility | CASCADE. Deleting an account removes everything it owns |
| plan → project | **No cascade.** `source_plan_id` becomes null. A project is a copy and owns its prices, so deleting, archiving or editing its plan changes nothing |
| project → product, bought part, item, item skill, price, costing, quote | CASCADE. Deleting a project removes it all, quotes and traces included |
| costing → trace | CASCADE. The purge of an archived project's traces deletes trace rows and touches nothing else |
| facility → item's `facility_id` | SET NULL (decided), so the item falls back to the project's default. A quote is unaffected, since it holds the facility's values and not a reference |

**Archived** is `archived_at`. Archiving deletes nothing and changes no version. It only starts the 30 days after which the project's traces are
purged (a per-user preference later, see the [costing trace](../requirements/projects.md#costing-trace)). The purge is a background job, and the
only one in this module. A quote is deleted by `deleteQuote` or with its project.

## Concurrency and rules

- **Compare numbers by value.** The tables store `0` as `0.00`, and `BigDecimal.equals` tells scales apart, so a reloaded `ProjectSettings` does not `equals`
  the one that was saved. A use case that asks whether settings changed compares them by value (`compareTo`), never with `equals` on the records.
- **Optimistic versions**, as for plans: every edit has `WHERE version = ?`. Costing and archiving do not change `version`. A costing stored
  later than the edit it was made for cannot overwrite a newer one, because the row records `costed_project_version` and the write checks it.
- **A quote can only be issued from a costing of the current version.** If the Draft changed after the last costing, *issue* is refused with
  "cost it again". This is the one place a stale costing is never allowed.
- **A quoted project is locked.** Every edit, price refresh and costing is refused until *New version from this quote*.
- **A quote is never updated.** The only way to change one is a new quote. The application has no update for it, and a trigger makes the database refuse one for a quote
  and for a quote's costing. A migration that converts stored documents to a newer format version switches the trigger off for its own transaction.
- **Deleting a quote** deletes its row, its costing and its trace, and nothing else. Quote numbers are **never reused** (the project keeps a
  `last_quote_number`), so a number a buyer was given never means a different quote. If the project is **Quoted** and the deleted quote was the
  only one, the project becomes a **Draft** again, since no quote stands any more. In every other case the state is unchanged. *(Proposed:
  these follow from the decisions, and are the developer's to confirm.)*

## Migrations and tests

- **Built** (2026-11-18), named by CLAUDE.md's rule (`VYYYYMMDD_HHMM__name.sql`, UTC, never edited once merged), four migrations so each is one change:
  `V20261118_1940__facility`; `V20261118_1941__projects` (the project and its product, bought part, item, item skill and price tables);
  `V20261118_1942__project-quotes-and-costings` (quote, costing and trace); `V20261118_1943__freeze-project-quotes` (the trigger); and, for the adjusted prices
  that are not the project's own, `V20261118_2100__adjusted-prices`.
  New builders start with Industry and Advanced Industry at 0 and no implant, and a new item at ME 10 and TE 20, as a plan assumes.
- **Built:** `ProjectSchemaIT` shows what the tables enforce before any repository uses them: the cascade from an account, the plan's deletion leaving the
  project, a deleted facility falling back to the default, the frozen quote (it fails with the trigger migration taken out), one Draft costing, a costing
  that must agree with its quote's project, quote numbers, and the checks.
- **Built** (2026-11-18): the aggregate `Project` (with `ProjectConfig`, `ProjectItem`, `ProjectBuilder` and the lock) and `ProjectRepository` with its JDBC
  adapter, tested by `ProjectTest` and `JdbcProjectRepositoryIT`: create from a plan (the link is kept only for the viewer's own plan), the round trip of every
  input, a save replacing what was stored, a stale version refused, another account's project looking missing, a facility of another account refused, quoted
  and reopened states, archive, and delete. Removing the version check or the facility check makes exactly the tests for them fail.
- **Built** (2026-11-18): `ProjectEditing` (start from a plan, rename, archive, restore, delete, add, change and remove products, choose a part's source, set an item,
  set the config, replace prices) and `ProjectEntry` (add a product and refresh prices, the two that ask ESI, between two short transactions and never for a quoted
  project), tested by `ProjectEditingIT`. Removing a product drops the prices and items nothing else needs, so a re-added product is priced again at once, and adding
  one fetches only for items the project has no price for. Removing the fetch-only-missing rule, the pruning, or the quoted check makes the matching tests fail.
- **Built** (2026-11-18): `ProjectCostings.cost`, which costs a Draft on request and writes nothing. It reads the project, each item's facility (through
  `FacilityTermsLookup`), the SDE and the stored adjusted prices (through `AdjustedPriceService`), and hands them to `ProjectCosting`, returning a `ProjectCostResult` with the
  comparison against least cost, the version it was costed for, the SDE version and the age of the oldest price. The two SDE reads this needed are built:
  `unitVolume` (packaged volume where there is one) and `timeSkillsOf` (a blueprint's required skills that carry attribute 1982), checked against the real SDE
  (the Golem has three, Multispectrum Coating II two, a Vexor is hauled at its packaged 10,000 m³). An item with no facility chosen is costed with none and the result
  says so (`NO_FACILITY`, which blocks a quote). An item typed as owned is refused until step 5. **The adjusted prices come from step 2** (2026-11-18): `ProjectEntry.cost` first refreshes them from ESI's `/markets/prices` (only when ESI's hour is up, and a failure keeps the stored ones and is reported with their age), then costs. The result carries `adjustedPricesAsOf`. Tested by `ProjectCostingsIT` with numbers worked by hand
  from the fixture's recipes, and removing the product skills, the per-item facility, the warning or the price side makes the matching tests fail.
- **Not yet written:** the repository's methods for the Draft's costing, quotes and traces (with their tests: replacing the Draft's costing, and the trace
  purge leaving the quote as it was), the facility repository, and the use cases `cost`, `issue`, `newVersionFrom`, `deleteQuote` and the queries.
- Use case tests with a fake repository and fake ports: *issue refused on a stale costing*, *edit refused on a quoted project*, *new version from a
  quote* round trip, *add a product the plan did not have*.
- A document test per stored version, loading or rendering a stored example (see the version rules above).
- The account deletion test lists projects, facilities and their costings among what is gone.
- The architecture checks run as always; no new rule is expected.

## Support access

The support role (`support_ro`) reads an allowlist of tables, and a test fails for a table that is neither on it nor excluded on purpose. The project tables are
**excluded for now** (the developer decided on 2026-11-18): the privacy statement lists what the operator may look at, and it does not yet cover projects, facilities
or quotes, and a quote holds a buyer's name and note. Until that changes the operator cannot read a costing or its trace, which is what the trace is for. The decision
and the options (readable with the buyer's name and note in separate tables, readable in full, or stay excluded) are in issue #63.

## Open questions

None are open from the developer's list of 2026-11-18. The rules marked *proposed* above (deleting the only quote of a Quoted project returns it to a
Draft, and quote numbers are not reused) are to be confirmed, and nothing else is open on the JSON: the display documents with a mapper are kept.
