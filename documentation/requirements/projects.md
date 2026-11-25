# Projects

Status: draft, interview in progress (first two rounds done 2026-11-17).

A **project** takes a [plan](manufacturing-invention.md#plans) and makes it concrete: it binds the plan's built items to
specific blueprints, a facility and a builder, adds taxes, fees, hauling and time, and ends in a **delivered cost** and a
**price to quote to a buyer**. A plan is a reusable recipe costed at ME 10 / TE 20. A project is one real job of work.

This replaces the thinner sketch under [Projects in Manufacturing and invention](manufacturing-invention.md#projects).
Tracking (matching ESI industry jobs, reservations, actual profit from sales) is not dropped, but it comes after the
quote and is not specified here yet (see [Later](#later)).

## Page layout

Decided with the developer on 2026-11-17, from `documentation/mockups/project-page.html` (layout C): the project page has
the work on the left (settings, facilities, jobs, delivered cost) and the **quote card on the right, always in view**, so
a change to a margin, a blueprint or a rate shows its effect on the price at once. On a narrow screen the quote card moves
below the work. Layouts A (one long page) and B (tabs) were not chosen.

### The first slice of the page

Decided with the developer on 2026-11-19. The developer reviewed the formulas ([Costing components](costing-detail.md)) and will change
nothing until they can compare **worked examples** with the game's own screens, so the first slice is the one that makes that comparison
possible, and the rest of the editing follows it.

- **Comparison first.** A **Projects list** with *Start from a plan*, and a **project page** that shows the stored costing job by job
  **in the shape of the game's Industry window**: the materials, the *Job duration modifiers* hover and the cost hover (estimated item value,
  system cost index line, structure role bonus, gross cost, SCC surcharge, total), the totals, and the quote card. Editing is kept small: a typed
  facility form, the ME and TE of each item, and *Cost it*. The rest of the controls from layout C come in later slices.
- **Menu.** The page has **its own "Projects" link beside Plans** in the main menu.
- **How a job's costing is laid out** is shown as three alternatives in `documentation/mockups/project-compare.html` for the developer to
  choose from: **A** cards that mirror the game's window and its two hovers, **B** a table with a "game shows" column where the developer types
  what the game shows and the page marks every line that differs, and **C** a copyable text block laid out like the hovers. The mockup uses two
  real readings: a wormhole job (typed index) and a Dodenvale job (the index from ESI, which differs from the game's by a few ISK).
- **What each line must let the developer see** (from [Costing trace](#costing-trace)): the exact product before rounding beside the used
  quantity, the time with its modifier lines, the index and where it came from (typed or ESI, and its age), and the estimated item value. The
  window rounds each cost line to whole ISK and adds the rounded gross cost and taxes, so the page shows the total the way the window does, with the
  exact total beside it.
- **The comparison is a diagnostic, not a core screen.** Decided with the developer on 2026-11-20. It is most useful while the formulas
  and logic are being validated, and in normal operation it is for troubleshooting a difference from the game. The finished project page is
  built around the work and the quote card (see [Page layout](#page-layout)). The comparison is a secondary view of a job (a link or a
  collapsed section such as "Check against the game"), not a main tab or a permanent column, and it should not take space from the core page.
- **Both A and B are built, later.** Decided with the developer on 2026-11-20: layout **A** (cards that mirror the game's window) and layout
  **B** (the table with a "game shows" column) are both built, as two views of the same comparison. Building them waits until the primary
  project screens (the Projects list, the project page and the quote card) are designed. This changes the "Comparison first" order above: the
  comparison is no longer the first thing built, only a thing that will be built.
- **The game values are typed.** The game cannot copy a job as text: only the list of possible blueprints below the job can be copied, and it
  is not the data compared. So the Game column is typed, per line. Reading a pasted screenshot in the browser (OCR) is a possible later step,
  with a confirm step, and the app's own expected number can be used to match a misread such as `2.723` to `2,723`. It is not built until typing
  proves painful.

### The primary screens

Mocked up on 2026-11-25 in `documentation/mockups/projects-list.html` and `documentation/mockups/project-page-primary.html`, and
reviewed with the developer.

- **The Projects list** has a table and a card layout (both kept). Each project shows its status, whether its costing is out of date and why,
  the quote total, the **margin** (not the profit, which is on the quote card), the buyer and the days to expiry. Filters by status, a search
  box, and archived projects hidden unless asked for.
- **Cost it runs by itself when a project is started** from a plan. Afterwards it is a button, and the page asks for it when the costing is out of
  date or something was changed since.
- **A quote can only be issued from a current costing.** Issuing is blocked while the costing is out of date or has unsaved changes.
- **Delete quote is always available** on an issued or expired quote (see [Lifecycle](#lifecycle)). An expired quote is kept as issued, and the way
  forward is a new version, costed with today's prices.
- **The quote card is for the user only.** No preview of what the buyer sees. What is sent to the buyer is the copied text or the CSV.
- **The comparison views are linked from a collapsed "Check against the game" section** of the project page, not shown by default.

**Built in three slices** (agreed with the developer on 2026-11-25), on branch `projects-ui`:

1. **Done: the list and the project page.** `/projects` (table and cards, the state filter, archived on request, *Start from a plan* which costs at
   once) and `/projects/{id}`: settings and builder, the facilities the account has, the items with typed ME, TE, facility, blueprint cost and
   time skills, the warnings, the jobs, the delivered cost and the quote card (issue, new version, delete). **Differences from the mockup:**
   the reasons a costing is out of date are the three the backend knows (the project was edited, the game data changed, a facility changed),
   not the example "prices are 3 days old"; an issued quote can have a new version made from any quote in the list; the list has no search
   box yet; and the margin is a saved setting, so changing it needs Cost it again before a quote can be issued (decided on 2026-11-25).
2. **Next: facilities.** Adding, changing and deleting a facility, with its use cases (the version bump on delete is a known gap in
   [Projects: persistence and use cases](../architecture/projects.md)).
3. **After that:** the quote text and CSV, and the comparison views A and B.

Jita split (see [Price basis](market-logistics.md#price-basis)) follows as its own change after these pages.

## Lifecycle

Decided with the developer on 2026-11-17, and made exact on 2026-11-18 (the lock, the products and the prices).

- **Start project** on a plan copies it. The plan is not changed, and one plan can have many projects. The plan provides the project's
  **initial** products and prices. After that the project is its own: a change to the plan, or deleting it, does not reach the project.
- **Draft.** The project can be changed freely: **its products (including ones the plan does not have)**, blueprints, facilities, builder, rates,
  price side, margin. It has **its own prices**, copied from the plan at start. It costs again on demand, and prices change only when the user asks
  (see [Market and logistics](market-logistics.md#price-source)).
- **Quoted.** *Issue quote* freezes the costing and the price as a numbered version (quote 1, 2, …) with its date. **Once a project has been
  quoted it is no longer a Draft and cannot be edited**: no change, no price refresh, no costing. *New version from this quote* makes it a Draft
  again, starting as a copy of that quote, so the earlier quote stays as it was sent. Current prices are shown beside the frozen ones so the drift
  is visible.
- **A single quote can be deleted** (the developer decided on 2026-11-18), and quote numbers are not reused.
- Archive and delete work as for plans (see [Working with plans](manufacturing-invention.md#working-with-plans)), in either state. Archiving
  changes nothing else. The tables, use cases and what happens on a delete are in [Projects: persistence and use cases](../architecture/projects.md).

## What the project is made of

| Input | Where it comes from | What it changes |
|---|---|---|
| Products and units | the plan | everything |
| Blueprint per built item | the Blueprints page, or a typed ME/TE | materials, time, jobs |
| Facility | the project's facility records (see [Facilities](#facilities)) | materials, time, job cost, tax |
| Builder | one character | time (skills, implants) |
| Price basis | account, plan, project (inheriting) | material cost, product value |
| Hauling rate, contract fee, time value, margin | account defaults, project may change | delivered cost, quote |

### Blueprints

Decided with the developer on 2026-11-17: **pick per built item, from owned blueprints.**

- Every built item in the project, the products and the parts marked build, gets one blueprint. It is chosen from the
  blueprints on the [Blueprints page](manufacturing-invention.md#blueprints-page) (BPO or BPC, with its ME, TE and
  runs left). The project starts with the best match (the highest ME, then TE, with enough runs), shows which it
  picked, and the user changes it where needed.
- The user can instead type an ME and TE for an item, for a blueprint they do not own yet or for a what-if. Such an item
  is marked "not owned", like a hand-entered blueprint (see [Blueprints](manufacturing-invention.md#blueprints)).
- A BPC with fewer runs than the item needs makes the job split: the BPC's runs first, then the next blueprint chosen
  for the rest. A job never uses more runs than its blueprint has. Where no owned blueprint covers the runs, the project
  says what is missing instead of guessing. The rules are in [The splitter](#the-splitter).
- The project's job size per built item is the project's own choice (a plan assumes one job per item). Materials are
  rounded per job (see [Plans](manufacturing-invention.md#plans)).
- **Each job is independent** (the developer decided on 2026-11-17, for the first version). A BPO is always available to
  every job that uses it, so two jobs on the same original do not wait for each other and nothing is reserved. A BPC's
  runs are still limited, as above.
- **Blueprint cost per run.** A BPC bought or invented, or a copy made, adds its price per run built. A BPO adds nothing
  by default; the user may set a per-run depreciation for it. Prices for bought BPCs come from
  [contract prices](market-logistics.md#contract-prices).

### Facilities

Decided with the developer on 2026-11-17: **facility records entered by hand.** This matches the existing
[Facilities](manufacturing-invention.md#facilities) section, and it is needed anyway because ESI does not give a
structure's rigs.

- A facility records its name, structure type (Raitaru, Azbel, Sotiyo, and the Athanor and Tatara refineries are not
  needed), rigs, solar system and the facility tax rate.
- The system's security band (high, low, null/wormhole) comes from the system, and it scales the rig bonus.
- The **system cost index** for the activity is read from ESI (`GET /industry/systems`, public) for every system ESI lists,
  and the **adjusted prices** for the estimated item value from `GET /markets/prices`. Neither is typed in, **except the
  index of a wormhole system**, which ESI does not give (see below).
- A default facility is set per activity or product group, and any job can use another.
- **Wormhole systems: the cost index is entered by hand** (see the next section).
- The structure's and the rigs' bonus values change with game patches, so they live in a table that can be updated
  without code, and the values are checked against the game and the
  [EVE developer documentation](https://developers.eveonline.com/docs/) before use. Not hard-coded in this file.

#### System cost index of a wormhole system

Added 2026-11-18, at the developer's request. **Requirement: a facility in a wormhole system takes its system cost index
as a value the user types in.**

- **Why.** ESI's `GET /industry/systems` lists no wormhole system: on 2026-11-18 it held 5,485 systems with ids from
  30,000,001 to 30,100,084, and wormholes start at 31,000,000. The developer confirmed that CCP keeps them out because
  industry in wormholes would then be trackable by hostile players. The game's own job window still uses an index for a
  wormhole system, so a project can only cost a job there if the user supplies it. (Facts and readings:
  [Costing components](costing-detail.md#34-job-install-cost).)
- **When it applies.** For any facility whose system ESI's list does not contain, which in practice is every wormhole
  system (ids from 31,000,000, known from the SDE). For every other system the index comes from ESI and is not typed.
- **The field.** The index is a percent that keeps **at least five decimals** (for example 0.56835). The game's window
  shows it rounded to two (0.57%), but its job cost hover shows the index line in ISK and the estimated item value, and
  the index is the first divided by the second (24,885 ÷ 4,374,740 = 0.56883%), so the exact value can be worked out
  from a job the user has open. Rounding it to two decimals moves the index line of a wormhole job by 0.29% for 0.57 against 0.56835 and by at most about 0.9% near that index, which is 0.035% and at most about 0.1% of the job's install cost, since the SCC surcharge is most of it (the index line is about 12% of the total).
- **Per activity.** The index belongs to an activity (manufacturing, copying, invention, research, reaction). The first
  version needs manufacturing only, and the others come with the work that needs them.
- **It is a value at a moment.** The typed index is stored with the date it was entered, and the facility and the costing
  show its age. It does not move much: two readings six hours apart differed by 0.085%, and a high-sec system moved by the
  same factor, so a recent value is a good one, but it is not live. A quote keeps the index it was issued with (see
  [Lifecycle](#lifecycle)).
- **A missing index is a gap.** A job at a wormhole facility with no typed index cannot be costed. The costing lists the
  gap like an unpriced material, and does not treat the index as zero.
- **Another source, proposed and not decided.** The index can be worked back from a job the user's characters or corporation have already installed in that system: ESI's industry jobs give each job's `cost`,
  and the formula in [Costing components](costing-detail.md#34-job-install-cost) turns it into an index. It would let the app fill in or refresh the typed value. **Not adopted**, for two reasons found on
  2026-11-18: it is unverified whether ESI's `cost` includes the 4% SCC surcharge, and a wormhole amplifies any error in the estimated item value about 8 times (an EIV off by 0.1% gives an index off by 0.8%),
  while the game's own job window gives the exact index at once. Industry jobs are not read by the app yet. Until it is decided, the index is typed.
- **The costing does not change.** The job costing already takes the index as an input in percent
  (`FacilityTerms.systemCostIndexPercent`); only where the value comes from differs.

### Builder

Decided with the developer on 2026-11-17: **one builder for the whole project**, a character chosen on the project.

- The builder's skills and stored implants (see [Skills and implants](manufacturing-invention.md#skills-and-implants))
  set the time of every job.
- Skills and implants change time, not material cost. They do change cost through the **time value** (below).
- Slot use is not checked in a project. Lead time across slots is not needed for now.

## How the cost is worked out

Steps 1, 2 and 4 are built as `JobCosting` (with `JobTerms`, `FacilityTerms` and `BuilderTerms`) in the `industry`
module. Every bonus and rate is an input there, as a percent, and the plan's ME 10 / TE 20 rounding runs through the same
code (`JobTerms.PLAN`). Its tests check the arithmetic by hand. The rules were compared with real jobs in the game
on 2026-11-18, and the formulas and the readings are in [Costing components](costing-detail.md). Rig scaling, the facility tax,
invention and copying are not yet read in the game.

Each step is its own line in the project, so a buyer's or the developer's question ("why is this 40 M?") can be answered
by reading down the page.

1. **Materials, per job.** The blueprint's base quantity, reduced by the blueprint ME, then by the facility's ME bonus and
   its rigs (these multiply, they do not add), rounded once per job (to two decimals half up, then up) and never below one per run. Valued at the price
   basis. Material on hand is valued at market price (see [plan decisions](manufacturing-invention.md#plans)).
2. **Job install cost, per job.** The estimated item value (from the blueprint's *base* materials at adjusted prices, not
   from the reduced quantities) times the system cost index, reduced by the structure's job cost bonus, plus the
   facility tax and the SCC surcharge. Copy, research and invention jobs have their own formulas, which come with
   invention.
3. **Blueprint cost per run** (see above).
4. **Time.** Job time from blueprint TE, builder skills and implants, facility TE bonus and rigs, rounded to the nearest second. Total job time is
   summed, because one builder runs them one after another for this purpose. The page also shows the **single longest job**
   (the developer asked for it on 2026-11-18), a stand-in for the time with several builder slots. It is a lower bound, not
   a lead time: it ignores that a job waits for the parts it uses.
5. **Time value.** The project has an ISK per hour (default from the account, default 0). Total time times that rate is
   a cost line, so a build that saves materials by taking many hours is seen as it is.
6. **Hauling.** ISK per m³ times inbound m³ (materials still to buy) plus outbound m³ (what is built). Volumes are from
   the SDE, with packaged volume for ships. See [Logistics](market-logistics.md#logistics).
7. **Surplus.** Extra units left over by rounding and batch size are credited at market value, as a plan does.
8. **Contract fee.** Decided with the developer on 2026-11-17: every project is delivered by contract, so **sales tax is
   0 and there is no percentage broker fee**. A flat fee of **10,000 ISK** per project stands in for the contract's
   cost. It is a setting (account default, project may change), so it can be changed if the game's cost differs. The
   selling character's skills and standings are not used, and a project has no selling character.

**Delivered cost** is steps 1 to 8 less what the surplus is credited. Since the fee is a fixed amount and does not
depend on the price, it is part of the cost and not a deduction from the price, so the margin can be worked out
directly from it.

### Hauling now, and later

- A flat rate per m³ in project settings, with an account default. This is enough for the first version.
- **Later**, the project can hand its inbound and outbound lists to other tools, for example Red Frog Freight
  (https://red-frog.org/) to price a courier, and take their quote back as the hauling cost. Nothing is built for
  that now, but the hauling cost is its own line with a source ("flat rate" or "typed in") so another source can be
  added without changing the rest.

## The quote

Decided with the developer on 2026-11-17: **both ways of pricing, switchable per project.**

- **Cost plus margin.** Quote = delivered cost divided by (1 − target margin %), set on the project. The Jita
  price of the product is shown beside it as a check.
- **Market-referenced.** Quote starts from a Jita sell, buy or split price (or a manual price) plus a premium or discount %. The
  project shows its profit against the delivered cost.
- The page shows the one in use as the quote and the other as a comparison.
- A quote has a **buyer name** and a **note** (free text), a date and an expiry. It does not link to the in-game
  contract.
- **The margin is a margin on the price** (the developer decided on 2026-11-17): a 20% margin means the profit is 20% of
  the quote, so the quote is the delivered cost divided by 0.8. A markup on cost is not offered, and the field is
  labelled "Margin on price" so the two are not confused.
- It shows the **price per unit and the total**. The margin and the profit are only for the developer's eyes.
- **Shareable text and CSV** can be copied or downloaded, to put in a contract description or a message. It does not
  show the cost breakdown.
- Buyer-supplied materials, where the buyer provides some inputs and the quote is a build fee only, are not needed for
  now (the developer did not pick them). The cost lines are built so that a material can be marked "supplied" later.

## First costing slice: decisions

Decided with the developer on 2026-11-18, for `ProjectCosting`, a pure domain class (no database, UI or ESI reads; every
input is a value, as with `JobCosting`):

- **Job sizes are an input.** The caller gives the runs of each job per built item, one job per item by default. A planner
  that splits an item across BPCs with too few runs comes later as its own piece.
- **Surplus is credited at the project's price basis**, the same unit price the item would have been bought at.
- **Incomplete cost blocks the quote.** The Draft shows the cost with a warning listing every unpriced material and every
  missing adjusted price. *Issue quote* is refused until every line is priced, so a frozen quote never hides a gap.
- **Rounding.** The delivered cost and the profit use exact amounts. The quote is rounded **up** to a step, a setting that
  defaults to 1,000 ISK, so the profit shown is slightly above the margin asked for.
- Not in this slice: persistence and the page, reading ESI, material on hand, the structure and rig table, invention and
  copying, and reading a blueprint's time-bonus skills from the SDE (taken as input).

## The splitter

Decided with the developer on 2026-11-18, built as `BlueprintSplitter`, `SplitPolicy` and `ProjectBuild.FromBlueprints`. It shares the
runs an item needs among the blueprints the developer owns, and cuts them into jobs. (A **job** is one industry job of several runs.)

**Two strategies, and a time bound that goes with either.** Splitting an item into more jobs can use slightly more material, because
every job rounds its materials up on its own, but it lets the jobs run in parallel slots and the project finish sooner. So the
developer chooses how to trade the two.

| Scenario | Rule |
|---|---|
| 1. **Least cost** (the default) | The best ME first, then the best TE, a copy before an original on a tie. A blueprint's runs go into **one job**, cut only at the 30-day limit (full jobs, the remainder last). The least material for the runs. |
| 2. **Time bound** (a limit set on top of either strategy) | The developer gives the longest time any job may take. Each blueprint's runs go into the **fewest jobs that each fit under it**, divided **evenly** so the jobs finish together. A bound that limits nothing (longer than the 30 days, or than one job already is) changes nothing. |
| 3. **Copies first** | Every copy first, **the one with the most runs first**, down to the fewest, using only as many runs of a copy as are needed. Then the **originals** finish what the copies cannot, the best ME first. Spends the copies the developer has before the originals. |

Scenario 2 on its own is Least cost with a time bound, and it combines with scenario 3. The developer asked to be told if this gets too complex.

- **What each splits.** A copy gives at most the runs it has left, an original as many as are still needed, and a copy with no runs is
  skipped. Where the blueprints cannot cover the runs, the jobs make too few units and the project shows a warning that blocks the quote.
- **Job length.** A job has at most `ceil(30 days ÷ seconds of one run)` runs. The developer remembers that the limit applies to every
  job of more than one run, so one run is always allowed even when it takes longer than 30 days. **To be confirmed in the game**
  ([costing details](costing-detail.md#314-maximum-runs-of-a-job)): the readings were of originals only.
- **When one run is already longer than the time bound,** each job is one run and the project says the bound could not be met
  (`TIME_BOUND_NOT_MET`, a notice that does not block a quote).
- **An original is always flagged.** A project that uses a blueprint original gets a notice (`USES_ORIGINAL`) on that item, because an
  original is always available and is easy to use without meaning to. It does not block a quote.
- **What the choice costs.** For scenarios 2 and 3 the project shows the cost against scenario 1 for the same project
  (`ProjectCosting.compareToLeastCost`): for each cost line the extra amount, the delivered cost's increase, how much shorter the
  **longest job** is and how the **total time** changes, and the extra cost for each hour the longest job is shortened. The page can say
  "the longest job is 2 d 1 h shorter for 184,000 ISK more". Splitting leaves the install cost unchanged (it comes from the base
  materials), so the increase is the extra rounding of materials, and in scenario 3 also the different blueprints' ME and price per run.
  A negative figure is a saving. The cost per hour is shown only when the longest job is shorter.
- The hand-built path stays: the jobs of an item can be given (`FixedJobs`), or a blueprint under typed terms (`Typed`, cut at the 30-day limit and at a time bound like any other, a blueprint that
  is not owned, or a what-if).

## Costing trace

Decided with the developer on 2026-11-18: the trace for troubleshooting a difference between the game and the app is **saved with the
costing and shown on the page** (not a DEBUG log). Logs carry only the project id and a costing id. The developer also asked that it be
**cleaned up**, both for table size and when an account is deleted.

**Why logs are not enough.** Every difference found so far was settled by comparing specific values with the game's Industry window
(the material quantities, the cost index line, the role bonus, the skills line, the rounding of a 2,722.005). A count of jobs and
warnings cannot show which of those differs, and a trace that lacks the prices' times cannot be reproduced an hour later.

**What a trace holds**, one per costing, in the shape of the game's window so the two can be read line against line:

| Group | Values |
|---|---|
| Inputs per job | blueprint kind, ME, TE, runs; the facility (structure bonuses, rig bonuses as scaled, index and its source, tax, SCC); the builder's levels, product skills and implant; the recipe's base quantities and base time |
| Data behind them | each material's adjusted price and unit price with their times; the SDE version; the app's commit id |
| Outputs per job | each material's exact product before rounding and its quantity; the time with its modifier lines (blueprint TE, skills and implants, structure); the EIV; the install cost as the window shows it (job fee, structure role bonus, SCC, tax, total) |
| Project | demand per part, the runs and split chosen, the warnings and notices |

`JobCost` does not yet carry the install cost lines or the unrounded material products, so the domain output grows to hold them.

**Lifecycle** (the three answers are the developer's, 2026-11-18; the rest is proposed to confirm):

- **Account deletion.** The trace belongs to a project, and the project to the account, with `ON DELETE CASCADE` as every other
  account table has it (see [Data](../architecture/data.md)). A test deletes an account that has traces and checks none are left.
- **Table size.** One JSON document per costing, not a row per job, so a project of nine jobs is one row of a few kilobytes.
  A Draft keeps only its **latest** trace and replaces it each time it is costed. An issued quote keeps its trace for as long as
  the quote exists, because the quote is frozen and its trace explains it. Deleting a project, or a quote version, deletes its trace.
- **Archived projects.** Their traces are removed **30 days** after archiving and the quotes stay. The 30 days is a setting, and is
  to become **a preference of each user** when preferences are written (until then one account-wide default).
- **Who can see it.** **Every user** can open the trace page of their own projects.
- **Account data export.** The traces are **not** in it (the developer decided).
- **What it must not hold:** tokens or secrets, and no names of the buyer or the characters, since the trace needs only type ids and numbers.
  The buyer's name and the note belong to the quote.

## Later

- Matching ESI industry jobs to the project's jobs, reservations of materials on hand and actual profit from sales, as
  in [Manufacturing and invention](manufacturing-invention.md#projects) and
  [Market and logistics](market-logistics.md#contracts).
- Hauling quotes from Red Frog Freight and similar tools.
- Slot-aware lead time and a delivery date to quote.
- Buyer-supplied materials.
- Choosing the cheapest split by trying the combinations, instead of the best ME first. Invention, copy and research jobs inside a project (see [Invention](manufacturing-invention.md#invention)).

## Open questions

- **Time value.** The developer needs more information before deciding whether to use it and at what rate. Until then
  it is a project setting that defaults to 0, so it changes nothing. What it is: an ISK-per-hour charge for the builder's
  time, so a slow build is not shown as cheaper than a fast one just because its materials are cheaper.
- **Job size per item.** The default is one job up to the job-length limit, then split. The developer expects this to
  change.
- **How a project is started from the Blueprints page** ("Load into project" is shown but off there, see
  [Blueprints page](manufacturing-invention.md#blueprints-page)). Where the project's pages sit in the menu is decided (a link beside Plans).
- **How Janice's Jita split is worked out: settled on 2026-11-25** from three appraisals at Jita 4-4 compared with ESI's market orders (`oEBWih`,
  2 Vexor; `e2iCSD`, 19 rows read from a screenshot; `f0LcqZ`, 50 Vexor). The split is the plain midpoint of Janice's buy and sell totals. Each
  total is **the best buy or best sell price at the time of Janice's data, times the quantity**, with no walking of the order book: 50 Vexor are
  priced at 10,920,000 each although the best sell order held only 8 units. In `e2iCSD`, 6 buy and 11 sell prices equal ESI's best now, 7 more were
  the best until orders posted after Janice's 05:30 UTC data, and the rest are orders since filled or removed. EveAssist's split follows the same
  rule, so that it can be checked against a Janice appraisal. **A consequence to decide:** for a large quantity this understates what buying that
  quantity would really cost, because the best order is usually small. Whether the project's material cost should walk the book is not decided here.
- **Whether layout C** (the copyable text block in `documentation/mockups/project-compare.html`) is built too. A and B are decided.
- **The currency format** on the shareable text.
- **A facility's structure and rig bonus table**: where it lives, and how it is kept right after patches.
- **Wormhole index, details left open:** how old a typed index may be before the page warns (a number of days); whether the
  same field may also override an index ESI does list, for a what-if; and whether the facility page offers a small helper
  that works the index out from the two numbers in a job's cost hover (the index line in ISK and the estimated item value),
  so the user does not divide by hand; and whether the app derives it from the user's own industry jobs instead (see above).
