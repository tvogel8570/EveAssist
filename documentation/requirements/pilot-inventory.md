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
- **Which container.** An item in a container says which one in its place line, in the "how held" badge: the name the
  player gave the container, in quotes (`in "Mining gear"`), or else what kind it is (`in Station Container`). Each container
  is a line of its own, because each is a different place for the stack. A corporation's item in a container keeps its
  hangar as a second badge, so it reads `in "Ammo"` and `Production`. Only the container that holds the item directly is
  named. Ships (an item in a ship's hold, or fitted) keep their plain wording for now. The CSV has a Container column
  (name, or kind when there is no name). Names from the game are shown exactly as typed, spaces included (players use them to
  order their lists in the game), and so are the names of a corporation's hangars. Ships' names are kept the same way.
- **Items that are hard to find stand out.** An item "in deliveries" (waiting to be taken, for a character, a corporation, a
  capsuleer or a corporation goal) has its "how held" text in amber. An item "in asset safety", or "in an impounded
  office" (a corporation lost its office, and what was in it waits there), has it in red. These are the items most often
  looked for as lost, and nothing else is coloured. An impounded office is a folder like any other office, so it is not
  listed as an item. "In an impounded office" is its own entry in the How held filter, and the "not fitted or in ships"
  shortcut keeps it.
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

Container names are read with the assets and are refreshed only when the asset list changes, because ESI's asset list
does not carry names: a container renamed in game keeps its old name here until its items move. If ESI cannot give the
names, the page says what kind of container it is.

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
- **A source beside the characters.** In the browse view each corporation is one more holder, with its own colour in the
  bar (after the characters' colours). It is shown or hidden with the characters switch. The character filter lists the
  corporations too, and is then called "Character or corporation", so the user can keep only the corporation's items or
  leave them out by ticking the characters. The bar's caption says "holders" instead of "characters" while the account
  has a corporation. With the feature off, corporation items are neither shown nor counted, though they stay stored.
- **Where it is.** Corporation items sit in an office folder, in a hangar division. The office is not an item and is not
  listed. A place line shows the station or structure, the corporation as the holder, and the hangar's name (division 1
  to 7, as the corporation named it, or "Hangar 3" when it gave no name). Each corporation's hangar is a line of its
  own. Items in containers, ships and deliveries are marked as for character assets, and an item in a container takes
  the hangar of the container. The CSV export has a Hangar column.
- **Fetch.** Same rules as [Fetching](#fetching): when the page opens or Refresh is pressed, only while the user is
  signed in and the feature is on, within ESI cache times, and only the latest state is kept. If several characters are
  Directors, the first that ESI answers for is used. The status block lists each corporation with its state: up to
  date, updating, could not update, **no Director linked** (with the reason), or **Director needs authorization**.
- **Corporations come and go with the characters.** A corporation is listed when one of the account's characters that
  granted the roles permission is in it. When no such character is in it any more (it moved, or was removed), the
  corporation and its stored items are dropped at the next run. A failed lookup never drops one.
- **Structure names** are learned with the Director's token, as for a character's own items, and are available to all
  characters of the account.
- **Not yet.** Corporation blueprints, industry jobs and contracts (a later step, each with its own scope and role),
  corporation wallet and orders, and how many of a corporation's structures are named (the status block shows that for
  characters only). Assets in a corporation the account has no Director in.

## Fetching

- Assets are fetched after the page is on screen (it opens from stored data, and then asks for the fetch) or when the user
  presses Refresh, within ESI cache times, and only while the user is
  signed in ([ESI access and tokens](esi-access.md#when-character-data-is-fetched)).
- Stored data is shown first, with its age.
- Only the latest state of each character is kept. Each fetch replaces the character's rows. There is no history.

## Pasted list

The pasted list of materials on hand stays, as a manual supplement for items ESI has not shown yet, such as something
just bought.

## Not included

Items in transit (in contracts or sell orders) are not in ESI assets, and the developer decided not to add them.

## Open questions

Corporation assets: whether a corporation's stored items should be kept for a while when the last character known to be
in it leaves, instead of being dropped at the next run.

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
