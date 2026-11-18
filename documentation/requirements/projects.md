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

## Lifecycle

Decided with the developer on 2026-11-17.

- **Start project** on a plan copies it. The plan is not changed, and one plan can have many projects.
- **Draft.** The project can be changed freely: blueprints, facilities, builder, rates, price basis, margin. It costs
  again on demand, and prices change only when the user asks (see [Market and logistics](market-logistics.md#price-source)).
- **Quoted.** *Issue quote* freezes the costing and the price as a numbered version (quote 1, 2, …) with its date. A
  quoted version cannot be edited. *New version from this quote* makes a Draft that starts as a copy of it, so the earlier
  quote stays as it was sent. Current prices are shown beside the frozen ones so the drift is visible.
- Archive and delete work as for plans (see [Working with plans](manufacturing-invention.md#working-with-plans)).

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
  says what is missing instead of guessing.
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
   summed, because one builder runs them one after another for this purpose.
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
- **Market-referenced.** Quote starts from a Jita sell or buy price (or a manual price) plus a premium or discount %. The
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

## Later

- Matching ESI industry jobs to the project's jobs, reservations of materials on hand and actual profit from sales, as
  in [Manufacturing and invention](manufacturing-invention.md#projects) and
  [Market and logistics](market-logistics.md#contracts).
- Hauling quotes from Red Frog Freight and similar tools.
- Slot-aware lead time and a delivery date to quote.
- Buyer-supplied materials.
- Invention, copy and research jobs inside a project (see [Invention](manufacturing-invention.md#invention)).

## Open questions

- **Time value.** The developer needs more information before deciding whether to use it and at what rate. Until then
  it is a project setting that defaults to 0, so it changes nothing. What it is: an ISK-per-hour charge for the builder's
  time, so a slow build is not shown as cheaper than a fast one just because its materials are cheaper.
- **Job size per item.** The default is one job up to the job-length limit, then split. The developer expects this to
  change.
- **Where the project's pages sit** in the menu, and how a project is started from the Blueprints page ("Load into
  project" is shown but off there, see [Blueprints page](manufacturing-invention.md#blueprints-page)).
- **The currency format** on the shareable text.
- **A facility's structure and rig bonus table**: where it lives, and how it is kept right after patches.
- **Wormhole index, details left open:** how old a typed index may be before the page warns (a number of days); whether the
  same field may also override an index ESI does list, for a what-if; and whether the facility page offers a small helper
  that works the index out from the two numbers in a job's cost hover (the index line in ISK and the estimated item value),
  so the user does not divide by hand; and whether the app derives it from the user's own industry jobs instead (see above).
