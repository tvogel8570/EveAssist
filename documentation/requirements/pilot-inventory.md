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

Decided with the developer on 2026-11-10, from mockups (`documentation/mockups/inventory-browse-v3.html`).

- **A table of item types**: one row per item type with its **Total** first, then, when characters are shown, a single
  bar of who holds it, then the category and the number of places. A row opens to show where the item is: the place,
  its region, how it is held and which characters hold it. There is no list-and-detail layout and no layout grouped by
  category.
- **Characters are optional and on by default.** One switch shows or hides the bar. Each character has its own colour,
  the same in every bar. On a phone the Total stays beside the item's name, and the bar sits under it.
- **Many characters.** An industrialist may have 21 characters on 7 game accounts. The bar scales to any number, and the
  status line is one line ("21 characters: 19 up to date, 2 need authorization") that opens into a table. EVE does not
  say which game account a character is on, so grouping characters by account would need tags the user sets. That is
  not built.
- **Search and Add filter.** The search matches item, group and category. Add filter asks for a column and then shows
  its values to tick, each with the number of item types it would leave. The columns are category, group, character,
  where, region and how held, and a minimum total. **Filters from different columns all apply, and several values in
  one column mean any of them.** Each chosen filter is a chip that can be taken away, and the chips can be cleared at
  once.
- **How held** says whether an item is in a hangar, in a container, in a ship's hold, fitted to a ship, in deliveries or
  in asset safety. All of them count in the totals unless the filter leaves them out. A shortcut leaves out what is fitted
  or in ships.
- **The address is the filter.** A filtered, sorted view can be reloaded, bookmarked and sent, and the back button steps
  back through the filters.
- **Sort** by most units, name or category. A long list shows the first 100 item types and offers more.
- **Copy as text** (item and total, tab-separated) and **Export CSV** (one line for each item, character, place and
  way of holding) follow the filter.
- **Places.** A place is a station or solar system named from the SDE, or a structure named by any character of the
  account. A structure no character of the account can name shows as an unknown structure with its id.
- **Blueprints** are tagged BPC or BPO and say that ME and TE are not read yet.

Still wanted, later: shortfall against a plan or project, grouping by market group, and value in ISK.

A location tree like the in-game asset window is not wanted for now.

## Corporation assets of your own corporations

Added 2026-11-10. The developer's characters share corporation assets among themselves, so these assets matter in
phase 1. This is **not** the group phase: the data belongs to the one account, and nothing is shared with other
users. [Groups and corporations](groups.md) adds sharing later without changing what is stored here.

- **Read through a Director.** ESI gives a corporation's assets only to a character with the Director role
  (`GET /corporations/{id}/assets`, scope `esi-assets.read_corporation_assets.v1`). The hangar names come from
  `esi-corporations.read_divisions.v1`, also a Director route. The app learns which linked characters are Directors
  from `esi-characters.read_corporation_roles.v1`. A corporation whose linked characters include no Director cannot be
  read, and the page says so.
- **Its own feature.** The scopes above belong to a separate feature, turned on apart from character inventory, so a
  user who does not want the app to read corporation data never grants them (see
  [Gaining players' trust](gain-trust.md#scopes)).
- **A source beside the characters.** In the browse view each readable corporation is one more holder, with its own
  colour in the bar. It is shown or hidden with the characters switch, and a filter column **holder** (character or
  corporation) lets the user leave corporation assets out of, or keep only them in, the totals.
- **Where it is.** Corporation items sit in an office folder, in a hangar division. A row's place shows the corporation,
  the station or structure and the division's name (division 1 to 7, as the corporation named them). Items in
  containers, ships and deliveries are marked as for character assets.
- **Fetch.** Same rules as [Fetching](#fetching): only while a Director is signed in, within ESI cache times, the
  latest state only, and the other characters of the account see the stored data with its age. If several characters
  are Directors, any one of them may fetch it. The corporation id stands in for the character in the fetch state.
- **Structure names** learned for the corporation are available to all characters of the account, as for character
  assets.
- **Not yet.** Corporation blueprints, industry jobs and contracts (a later step, each with its own scope and role), and
  corporation wallet and orders. Assets in a corporation the account has no Director in.

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

Corporation assets: which characters count as the corporation's members when a character leaves it (the
corporation's stored data would then have no reader until another Director is linked), and whether the stored rows are
kept or dropped.

Deferred by the developer until the plan step is taken up:

- How the pasted list and ESI assets are added together without counting an item twice (for example the pasted lines
  expire at the next asset fetch, or are matched against it).
- Whether a project reserves from the combined pool in the same way as the earlier requirements describe.

Also open:

- **Structures whose name a character cannot learn.** ESI gives a player structure's name only to a character that may
  dock there, and refuses the rest with a plain 403. Against real data, one character had 15 structures: 1 was named, 10
  were refused and 4 were not yet asked, and the refused ones held only 14 items. The developer decided on 2026-11-09
  that a name learned by one character is used for all the characters of the same account (the same person), and never
  for another account. Still to decide: whether the user can name a structure by hand, as for facilities in
  [Manufacturing and invention](manufacturing-invention.md#facilities). Until then an unnamed structure shows as an
  unknown structure with its id.
- Whether blueprints with ME, TE and runs are merged into the inventory rows when the blueprint fetch exists.
