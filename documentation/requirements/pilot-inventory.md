# Pilot Inventory

Status: draft, interview in progress. Added 2026-11-09 at the developer's request, ahead of the earlier roadmap.

Pilot inventory shows what the user's characters own, read from ESI, and later feeds plans and projects with it.

## Goals

- **First, browse**: see what every linked character holds and where, and search it.
- **Then, plan**: show for a plan or project what is needed, held and missing.

## Departs from earlier requirements

[Manufacturing and invention](manufacturing-invention.md#projects) says plans do not read assets from ESI and that the
user records materials on hand by pasting a list. The developer decided that assets are read from ESI. The pasted list
stays as a manual supplement (see [Pasted list](#pasted-list)). That section is updated when the plan step is built.

## Linking characters

Inventory needs linked characters, which are not built yet. The minimal linking is built first, as
[Users, accounts and roles](users.md#eve-characters) and
[Identity and ESI tokens](../architecture/identity-and-tokens.md#linking-a-character) describe, with no throw-away
parts. Inventory asks for two scopes, both read-only:

- `esi-assets.read_assets.v1`: the character's assets.
- `esi-universe.read_structures.v1`: names of player-owned structures.

## What is shown

- All items of all linked characters, in all locations: ships, containers, fitted modules and cargo included.
- Blueprints (BPOs and BPCs) are shown too, without ME, TE and runs until the blueprint fetch exists.
- Each row says where the item is. NPC stations and systems are named from the SDE. Structures are named through ESI.
- Fitted modules, cargo and container contents are marked as such.

## Browse view

The first view is totals by item with a search box: one row per item type, with the total across characters, expandable
to character and location. The developer expects significant changes to it later, so it is built to be changed.
Wanted:

- **Filters**: by character, region, system or station, and by market group or category.
- **Sorting and grouping**, and **export** (for example as text).
- **Shortfall**: for a plan or project, the materials needed, held and missing.

A location tree like the in-game asset window is not wanted for now.

## Fetching

- Assets are fetched when the user opens the page or presses Refresh, within ESI cache times, and only while the user is
  signed in ([ESI access and tokens](esi-access.md#when-character-data-is-fetched)).
- Stored data is shown first, with its age.
- Only the latest state of each character is kept. Each fetch replaces the character's rows. There is no history.

## Pasted list

The pasted list of materials on hand stays, as a manual supplement for items ESI has not shown yet, such as something
just bought.

## Not included

Items in transit (in contracts or sell orders) are not in ESI assets, and the developer decided not to add them.

## Open questions

Deferred by the developer until the plan step is taken up:

- How the pasted list and ESI assets are added together without counting an item twice (for example the pasted lines
  expire at the next asset fetch, or are matched against it).
- Whether a project reserves from the combined pool in the same way as the earlier requirements describe.
- Corporation assets: left to the group phase.

Also open:

- **Follow-up, flagged 2026-11-09: structures whose name a character cannot learn.** ESI gives a player structure's name
  only to a character that may dock there, and refuses the rest with a plain 403. Against real data, one character had
  15 structures: 1 was named, 10 were refused and 4 were not yet asked. The refused ones held only 14 items. For now the
  browse view shows such a location as an unknown structure with its id, and a name learned by one character is never
  shown for another. To decide later: whether a name learned by one of the user's own characters may be used for their
  other characters (it is the same user's data, but a character that cannot dock would then see a name that ESI withheld
  from it), and whether the user can name a structure by hand, as for facilities in
  [Manufacturing and invention](manufacturing-invention.md#facilities).
- Whether blueprints with ME, TE and runs are merged into the inventory rows when the blueprint fetch exists.
