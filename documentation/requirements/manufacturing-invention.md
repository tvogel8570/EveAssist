# Manufacturing and Invention

Status: draft, interview in progress.

## Goals

Planning, tracking and profit analysis all have equal weight.

- **Plan**: choose one or more products and quantities, then get the full material tree, the jobs needed and their cost.
- **Track**: see running and finished jobs for all characters in one place, with alerts.
- **Analyse**: rank what is worth building, using the user's blueprints, skills, facilities and current prices.

## Profit ranking

- A toggle limits the ranking to products the user can build from their own blueprints (imported or hand-entered,
  plus what they can invent from them), or shows everything buildable in the SDE.
- The ranking can be sorted by volume-capped profit per day (see [Market and logistics](market-logistics.md)),
  margin % and profit per unit.
- Filtering by manufacturing group or market group comes later.

## Build tiers

All of these are in scope:

- Ammunition and consumables (charges, scripts, boosters, fuel and similar)
- T1 ships, modules and components
- T2 through invention: datacores, decryptors, success chance and T2 components
- T3 through reverse engineering: relics, strategic cruisers, T3 destroyers and subsystems
- Capital components, capital ships, Upwell structures and structure rigs

## Blueprints

- Owned BPOs and BPCs are imported from ESI for each character, with ME, TE and remaining runs. Corporation blueprints
  of the user's own corporations follow corporation assets (see
  [Pilot inventory](pilot-inventory.md#corporation-assets-of-your-own-corporations)), and sharing them is for the group phase.
- The user can also enter blueprints by hand, for example a blueprint they plan to buy or research, so it can be used in
  plans and profit analysis.
- Imported and hand-entered blueprints must be told apart.

## Facilities

- Builds happen in Upwell structures that the user or their corporation owns.
- A facility records its structure type, rigs, system (for security status and the system cost index) and tax rate.
- NPC stations and public structures are not needed for now.
- The user sets a default facility per activity or product group (for example components in a Raitaru, ships in an
  Azbel). A plan uses the defaults, and the user can change the facility of any job.

## Intermediates: build or buy

- Each intermediate in a plan (components, T2 components and similar) is marked build or buy.
- A default rule sets the first choice, for example "build if cheaper than buying". The user can override any item.
- Reaction products are always bought for now and priced from the market. Reaction planning comes later.

## Skills and implants

- Each character's skills are read from ESI.
- Each character's industry implants are read from ESI and stored. The stored set is updated only when the user asks,
  so jumping to another clone does not change plans.
- Plans assign each job to a character, and that character's skills and stored implants are used for time, cost,
  invention chance and slot counts.

## Plans

- A plan holds one or more products, each with a number of finished units. Shared intermediates are added up across
  the products.
- The tool works out the runs. When a job needs more runs than one BPC has, or would run past the job length limit, the
  tool splits it into several jobs, and the user can change the split.
- Surplus from rounding and batch sizes (extra components, extra units) is listed for each plan and credited at market
  value in its cost.
- **Blueprint levels in a plan.** A plan costs every product, and every part marked build, as built from a blueprint
  with ME 10 and TE 20. It is a plan, not a record of what the user owns, so it does not look at the user's blueprints.
  Other levels are for projects and for the facility and skill effects described above.
- **One job per item in a plan.** A plan assumes each built item is made in a single job, so the materials are rounded
  up once for all its runs. This is the cheapest case. A job that is split into several smaller ones rounds up on its
  own and uses more materials, and an item with a small base quantity gets no ME saving at all (a job never uses less
  than one of a material for each run). The page says so. In a project, each built item has its own job size and the
  materials are worked out per job.
- **Runs needed.** The plan has a section that lists, for each product and each part marked build, the total runs needed:
  the units the plan needs of it (for a product, the units asked for; for a part, the sum over everything that uses it)
  divided by the units one run makes, rounded up. Next to the runs it shows the units needed, the units made, the
  surplus and the time at TE 20 for all the runs. A plan shows no job count, since it assumes one job. Parts marked buy are not in this section, since
  they have no runs. They are listed with the materials to buy instead.

### Working with plans

Added 2026-11-10 at the developer's request.

- **New plan** can be started from the Plans list and from a plan's own page (a New button opens a small box for the
  name, because plans cannot be renamed yet).
- **Remove a product.** Each product row has a remove button. The plan is costed again at once, and a message says what
  was removed and offers **Undo**, which puts it back with the units it had and the prices the plan already had. A plan
  may be left with no products.
- **Units** are whole numbers from 1 to 1,000,000. Anything else is refused with the reason (a 0 says to use Remove), and
  the number the plan holds returns to the box, so the box never shows what the plan does not have.
- **Archive.** An archived plan is hidden from the Plans list until the user ticks **Show archived plans**, where it is
  marked and can be **restored**. It can still be opened and edited. Archiving is a status, not an edit, so it does not
  change the plan's version. It is offered on the list and on the plan's own page.
- **Delete.** A plan can be deleted from the list and from its own page, after a question that names the plan and says
  that its products and prices go with it. Delete cannot be undone, and the question points to archive as the way to keep it.
- An archived plan the user has not asked to see is not counted as "no plans yet": a list that is empty only because
  everything is archived says so.

### Trying a plan without an account

Decided with the developer on 2026-11-10. A visitor who has not signed in can build and cost a plan, so a new player can
see what EveAssist does before creating an account.

- **Its own page.** It is separate from the signed-in plan page, and it has no list of plans. The costing, ME 10 and
  TE 20, the build or buy choice, the runs needed and the total are the same as for a signed-in plan, and the two pages
  share the parts that show the results.
- **Nothing is saved on the server.** The plan is kept in the browser's own storage for that site. It survives closing
  the browser, but it is lost if the visitor clears the site's data or uses another browser or computer, and the page
  says so. The address is not used to hold the plan, because it is too short for a large plan.
- **No save, rename, archive, delete or list.** The visitor can start a new plan, which clears the page.
- **At most 10 products** in a plan, so that one visitor cannot ask for an unlimited amount of work.
- **Prices.** The page uses the prices the server already holds, however old, and asks for an item's price only when
  the server holds none. **Refresh prices** is not offered. The page shows how old the prices are and warns when they
  are stale.
- **Limits on fetching prices for visitors.** ESI allows 12,000 tokens per 15 minutes for the market orders of the whole
  app (see [ESI](../architecture/esi.md#jita-prices)), so every user shares one allowance, and a fresh fetch costs 2
  tokens. A plan of ten large ships needs about 100 to 150 items fetched when nothing is stored, and most plans need
  under 50. The starting limits, to be tuned against real usage, are:
  - **One visitor** (identified by network address) can have at most **200 items fetched per hour**. Beyond that the
    page uses stored prices only and lists the items it could not price.
  - **All visitors together** can have at most **1,500 items fetched per 15 minutes**, a quarter of the allowance. The
    rest is kept for signed-in users. Beyond it, visitors get stored prices only, with the same list.
  - An item whose price is already stored costs nothing against either limit.
- **No characters.** The page reads nothing about the visitor's characters, so it has no materials on hand.
- **Taking a plan into an account** when the visitor signs in is wanted but not required at first.

## Research

- ME/TE research jobs on BPOs are tracked (running jobs, slots and finish times) but not planned. The tool does not
  weigh research time against its payoff. Copy jobs for invention are planned, as described under Invention.

## Invention

The tool helps with:

- **Decryptor choice**: compare decryptors, and no decryptor, by cost per successful run and per T2 unit built.
- **Run sizing**: how many attempts are needed to reach a target number of T2 units at the expected success chance.
- **BPC sourcing**: copy T1 BPCs from owned BPOs (including the copy jobs) or buy them.
- **Result tracking**: record actual successes and failures and compare them with the expected chance.
- **Leftover runs**: invented BPCs whose runs a project does not use stay available as blueprints for later plans. Their
  share of the invention cost goes with them.

## Reverse engineering

- Relic quality (intact, malfunctioning, wrecked), with its run count and success chance.
- Decryptor choice, compared the same way as for invention.
- T3 strategic cruiser and T3 destroyer hulls and subsystems as outputs.
- Relics are priced from the market like other materials. Relic availability is not tracked.

## Projects

- A saved plan becomes a live project.
- Expected cost and profit are frozen when the project is saved. Current prices are shown beside them so the user can
  see the drift.
- The tool suggests matches between the project's jobs and ESI industry jobs (by character, blueprint and runs) as they
  start and finish. The user confirms or changes each match. The project shows what is left.
- Plans do not read assets from ESI. The user records materials on hand by pasting a list copied from the game (for
  example an inventory window).
- Materials on hand are one shared pool. Each project reserves what it uses, so the same item is not counted for two
  projects.
- Each project has one setting for when it ends: when all its units are sold (from wallet transactions and confirmed
  contract sales), or when the user closes it.
- Actual profit uses real sales (wallet transactions and confirmed contract sales, see
  [Market and logistics](market-logistics.md#contracts)) against the frozen expected material cost. Purchases are not
  matched to projects.

## SDE updates

- When a patch changes a blueprint, live projects keep the quantities they were saved with.
- Saved plans that use a changed blueprint are flagged, and the user re-costs them.
- SDE updates are done during an outage, so no open screen has to change while one is applied.

## Job slots

- The tool shows each character's used and free manufacturing, science and reaction slots and when running jobs finish.
- The user decides which character runs which job. The tool does not suggest assignments or build a timeline.

## Alerts

- Alerts are raised when a job finishes and when a project is blocked (its next job cannot start because materials or a
  BPC are missing). Contract alerts are described in [Market and logistics](market-logistics.md#contracts).
- Alerts are shown in the web app, on a dashboard and in a notification list.
- Email, Discord and other channels are not needed for now.

## Prices and fees

Prices, the price basis and fees are defined in [Market and logistics](market-logistics.md).

## Open questions

- Updating the stored prices in the background, so that a price is not old when someone opens a plan, is worth
  considering. It would help signed-in plans and the plan without an account. Not decided.
- How old a price must be before a plan warns that it is stale. The same age could serve both kinds of plan. The page
  without an account uses 24 hours for now.
