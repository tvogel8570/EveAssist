# Inventory

Status: proposed (added 2026-11-10 with the browse page). Covers the `inventory` module: how assets are stored for
browsing and how the page answers. The fetch is in [ESI access](esi.md#character-assets), the requirements are in
[Pilot inventory](../requirements/pilot-inventory.md).

## Where an item is, decided when it is stored

ESI lists an item with the id of what holds it, which may be a container or ship that is itself in the list. When a
character's assets are stored, `AssetClassifier` walks each chain to its top and stores, with the item:

- `root_location_id`: the station, solar system or structure at the top of the chain;
- `depth` and `container_type_id`: how deep it is, and the type of what holds it directly;
- `held`: `HANGAR`, `IN_CONTAINER`, `IN_SHIP`, `FITTED`, `DELIVERIES` or `ASSET_SAFETY`, from the location flag and the
  depth. Asset safety and deliveries are decided by the flag at any depth, because items inside an asset safety wrap
  are still in asset safety.

The walk never uses `location_type`, because real ESI types an item in a structure as `item` (see
[Character assets](esi.md#character-assets)). Doing this once at store time keeps every page request a plain query, and
means a chain is never walked for a user who only looks. Existing rows were dropped by the migration and are read again.

## One page, one address, two answers

`GET /inventory` is the whole page for a browser and only the list for htmx, told apart by the `HX-Request` header, with
`Vary: HX-Request` so that a cached page is never shown for a list or the other way round. The whole filter is in the
query string (`BrowseFilter`), and htmx pushes it, so a view can be reloaded, bookmarked and sent, and the back button
works. Only a full page visit starts a fetch. A filter change never does.

The filter is a form. Its hidden fields hold every chosen value. A column's list, opened by Add filter, holds that
column's choices in its own checkboxes, so while it is open the hidden fields for that column are left out (the `open`
field names it), and every answer brings the new hidden fields back out of band. Done, a chip's cross and a preset send
no open column, which closes the list. Values that do not parse are dropped, a value list is cut at 200 and a search at
100 characters, because the filter arrives in the address.

The list of values for a column leaves out that column's own filter, so a choice can be widened, and counts the item
types each value would leave under all the other filters, including the minimum total. It returns at most 500 values and
the list's own search runs in the database, so a long list (places, groups) stays reachable. A page lists at most 1,000
item types and then says so, instead of offering more.

## Queries

`BrowseQueries` is the only place with the SQL. Every query is limited to the ids of the viewer's own linked characters,
which `BrowseService` takes from the `characters` module's `LinkedCharacters`, so no query can reach another account. All
values are bound parameters, and the one part of the SQL the caller chooses, the sort, comes from a fixed list. A
structure's name is taken from `character_structure` for any character of the account, never another account. Its
region comes from the system stored with the name.

## The bar and its colours

Each character has one of 24 colours, from its place among the viewer's characters in the order they were first linked
(`linked_at`, then EVE id), so the colour is the same everywhere and linking a character never changes another's.
Removing one moves the colours of those linked after it, and more than 24 characters share colours. A bar is an inline SVG with a rectangle
for each character, and its colours are classes in `app.css` (`seg-0` to `seg-23`). The page writes no inline style, so
that a strict Content Security Policy (see [Gaining players' trust](../requirements/gain-trust.md)) will not block it.

## The CSV export

One line for each item, character, place and way of holding, honouring every filter including the minimum total. It
stops at 100,000 lines and then says so in a last line. Copy as text asks the server for the same filter and gives every
item type (up to 10,000), not only the page on screen. Every cell is quoted, and a cell that
starts with `=`, `+`, `-` or `@` (and is not a plain number) gets a quote in front, because names come from the game and
a spreadsheet would run such a cell as a formula.

## Not yet

- Shortfall against a plan or project, grouping by market group, value in ISK.
- Grouping characters by game account (EVE does not say which account a character is on, so the user would tag them).
- A time zone for the "last answer" times, which are in UTC.
- The status block's open or closed state is not kept across its two-second updates while a fetch runs.
