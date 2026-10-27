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
  are added in the group phase.
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
- Each project has one setting for when it ends: when all its units are sold (from wallet transactions), or when the
  user closes it.
- Actual profit uses real sales from wallet transactions against the frozen expected material cost. Purchases are not
  matched to projects.

## SDE updates

- When a patch changes a blueprint, live projects keep the quantities they were saved with.
- Saved plans that use a changed blueprint are flagged, and the user re-costs them.

## Job slots

- The tool shows each character's used and free manufacturing, science and reaction slots and when running jobs finish.
- The user decides which character runs which job. The tool does not suggest assignments or build a timeline.

## Alerts

- Alerts are raised when a job finishes and when a project is blocked (its next job cannot start because materials or a
  BPC are missing).
- Alerts are shown in the web app, on a dashboard and in a notification list.
- Email, Discord and other channels are not needed for now.

## Prices and fees

Prices, the price basis and fees are defined in [Market and logistics](market-logistics.md).

## Open questions

None at present.
