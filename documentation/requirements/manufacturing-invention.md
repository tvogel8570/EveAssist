# Manufacturing and Invention

Status: draft, interview in progress.

## Goals

Planning, tracking and profit analysis all have equal weight.

- **Plan**: choose a product and quantity, then get the full material tree, the jobs needed and their cost.
- **Track**: see running and finished jobs for all characters in one place, with alerts.
- **Analyse**: rank what is worth building, using the user's blueprints, skills, facilities and current prices.

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

## Intermediates: build or buy

- Each intermediate in a plan (components, T2 components and similar) is marked build or buy.
- A default rule sets the first choice, for example "build if cheaper than buying". The user can override any item.
- Reaction products are always bought for now and priced from the market. Reaction planning comes later.

## Skills

- Each character's skills are read from ESI.
- Plans assign each job to a character, and that character's skills are used for time, invention chance and slot counts.

## Invention

The tool helps with:

- **Decryptor choice**: compare decryptors, and no decryptor, by cost per successful run and per T2 unit built.
- **Run sizing**: how many attempts are needed to reach a target number of T2 units at the expected success chance.
- **BPC sourcing**: copy T1 BPCs from owned BPOs (including the copy jobs) or buy them.
- **Result tracking**: record actual successes and failures and compare them with the expected chance.

## Projects

- A saved plan becomes a live project.
- The project's jobs are matched to ESI industry jobs as they start and finish, and the project shows what is left.
- Plans do not read assets from ESI. The user records materials on hand by pasting a list copied from the game (for
  example an inventory window), and the project fills in quantities from it.

## Job slots

- The tool shows each character's used and free manufacturing, science and reaction slots and when running jobs finish.
- The user decides which character runs which job. The tool does not suggest assignments or build a timeline.

## Alerts

- Alerts are shown in the web app, on a dashboard and in a notification list.
- Email, Discord and other channels are not needed for now.

## Prices and fees

Prices, the price basis and fees are defined in [Market and logistics](market-logistics.md).

## Open questions

None at present.
