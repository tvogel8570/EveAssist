# ESI Access

Status: draft, interview in progress.

Covers how the app calls ESI, how fetches run while a user is signed in, prices, and alerts. The EVE developer
documentation is the authority (see `CLAUDE.md`); the rules below follow it and the requirements in
[ESI access and tokens](../requirements/esi-access.md).

## The ESI client

One client in the `esi` module makes every ESI call. Feature modules ask it for a resource and never build ESI
requests themselves.

- **User-Agent**: `EveAssist/<version> (<contact email>; +<repository URL>)`. The docs strongly prefer an email and an
  app name with a version. The contact email comes from configuration.
- **Compatibility date**: every request sends `X-Compatibility-Date` with a fixed date in the code. The date is moved
  forward on purpose, after the app has been checked against ESI's changes. Without it, ESI answers as of its oldest
  version.
- **Caching**: for each character (or none, for public data) and each route, the client stores `ETag`, `Expires` and
  `Last-Modified`. It does not call a route again before `Expires`, and when it does it sends `If-None-Match`.
  A 304 costs one rate-limit token instead of two and only updates the fetched time. The client never sends
  `Cache-Control: no-cache`.
- **Rate limits**: the client tracks `X-Ratelimit-Remaining` per rate-limit group and character. It spreads requests
  and slows down well before the bucket is empty. After a 429 it waits for `Retry-After` before using that bucket
  again. Group limits are read from the OpenAPI spec's `x-rate-limit` (for example `char-industry` 600 and
  `char-wallet` 150 tokens per 15 minutes).
- **Error limit**: for routes without bucket limits, the client watches `X-ESI-Error-Limit-Remain` and
  `X-ESI-Error-Limit-Reset` and pauses every call when few errors are left. 4xx responses cost five tokens on
  bucket-limited routes, so the scope check before a call matters.
- **Pagination**: paged routes are read through `X-Pages`. Every page must carry the same `Last-Modified`, or the whole
  set is read again later.
- **Timeouts**: a call gives up after 5 seconds to connect and 30 seconds for the response, set with
  `spring.http.clients.connect-timeout` and `read-timeout`. A call that gives up is a failure like any other. Without
  them a stalled connection would hold one of the simultaneous-call permits for ever. The values are a first guess, to
  be checked at the checkpoint under [Background fetches](#background-fetches).
- **Failures**: 5xx and timeouts are retried later with back-off, never in a loop. Stored data stays on screen with its
  age, and a banner says that ESI is down or throttled.

Status: **agreed** (follows from the requirements and the ESI documentation).

## Background fetches

- Fetches run inside the app on virtual threads, written as described in
  [Background tasks](code-structure.md#background-tasks). A per-instance limit (a semaphore, for example 8 calls at
  once) keeps a sign-in with many characters from flooding ESI. A refresh of one character holds an in-memory flag for
  that character, so two fetches of the same character never overlap within the instance. This replaces a Postgres
  advisory lock per character, which would have held a connection for the whole fetch (the developer decided this on
  2026-11-09).
- The ESI documentation sets no limit on simultaneous calls. It limits calls over time (bucket limits per rate-limit
  group, and the error limit), says to spread requests rather than burst constantly, and allows an occasional burst.
  The limit of 8 is EveAssist's own choice, to keep the burst after sign-in polite. It is not an ESI rule.
- **Checkpoint before deploying a feature that adds or changes ESI calls**: run its fetches in dev with several
  characters and check, from the response headers, how close each rate-limit group's `X-Ratelimit-Remaining` and the
  `X-ESI-Error-Limit-Remain` get to zero. Then tune the simultaneous-call limit and how early the client slows down,
  and record the values chosen and why.
- A fetch task is one character and one kind of data (jobs, blueprints, skills, implants, orders, wallet transactions,
  contracts, standings and the rest). The client's cache rule decides whether ESI is called at all.
- After sign-in, if the user's setting is on, the app queues every task for every linked character that is not marked
  for re-authorization. The dashboard shows stored data at once and does not wait. Fetch progress is pushed over SSE
  to a progress bar under the header, and each dashboard card is swapped in by htmx when its data has been fetched.
- With the setting off, a page queues the tasks it needs, and the freshness page has refresh buttons for one character
  or all of them.
- Tasks run only for users with a live session. When the session ends, the user's queued tasks are cancelled.
- Fetch state (last fetched, last result and the cache headers) is a row per character and kind of data. It backs the
  freshness page and the "stored data is N minutes old" labels, and it survives restarts. Queued tasks themselves are
  kept in memory only. After a restart, the user's next page or refresh queues them again.

Why not a job queue in Postgres (db-scheduler, JobRunr) or a separate worker with SQS: fetches matter only while the
user is signed in, so losing queued tasks at a restart costs nothing, and the stored fetch state is in the database. One
instance is expected; a second would need work to be claimed in the database (see
[Background tasks](code-structure.md#background-tasks)).

**Exception: public shared data.** The list of public contracts is read by a background loader that needs no signed-in
user, because the data is public and shared by all users, and a user who opens the page should find it ready. The
developer decided this on 2026-11-09. The loader starts a minute after start-up and then every 30 minutes (the list is
cached for 30 minutes). Each run is on its own virtual thread, so the scheduler's single thread stays free for other
scheduled work such as the alert sweep. A run reads batches of 500 contracts until none are waiting, and stops when a
batch makes no progress (ESI throttling or down) or the app is stopping. A start that finds a run still going does
nothing (see [Background tasks](code-structure.md#background-tasks) for the flag and for what a second instance would
need). It is switched off with `eveassist.market.contracts.background=false`, and the batch size, first delay and interval can be
set under `eveassist.market.contracts`. It uses the same client, so the simultaneous-call limit and the error-limit
pause apply.

Status: **agreed**.

## Corporation data (phase 2)

Corporation routes are fetched with a role-holder's token, only while that role-holder is signed in, through the same
client. The corporation id stands in for the character in the fetch state. Other members see the stored data with its
age.

## Jita prices

- Prices are public data, shared by all users. They are fetched on demand, and only when the user asks: the plan
  editor's Refresh prices button, or adding a product whose items the plan has no price for. Opening a plan never
  fetches, because a plan keeps the prices it was costed with so that it can be kept as quoted. The developer decided
  this on 2026-11-08. A refresh asks ESI about every item the plan could need, bought or built, runs the calls on
  virtual threads under the per-instance limit, and keeps the old price of any item ESI could not answer, listing it in
  a warning. The calls happen between two short transactions, never inside one.
- For each item, the app reads `GET /markets/10000002/orders?type_id=<id>` (The Forge), keeps the orders at Jita 4-4
  (station 60003760), and works out the buy and sell price from the best orders that make up the user's share of the
  volume. The orders are stored once (`jita_order`, with `jita_fetch` for when each item was last confirmed current, and
  `esi_cache` for the ESI client's `ETag` and `Expires`). Each user's price comes from those stored orders, so users
  with different shares cost no extra ESI calls. The price a plan uses is copied into the plan when it is taken (see
  [Plan prices](data.md#plan-prices)).
- Volume history comes from `GET /markets/10000002/history?type_id=<id>` and is stored per item, also within its cache
  time.
- Blueprint copies have no market, so their price comes from public contracts: `GET /contracts/public/10000002` for the
  list and `GET /contracts/public/items/<id>` for the lines of one contract. The list is read through its pages and
  cached like other routes. A contract's lines never change, so the client does not cache that route: the app stores
  them and asks once per contract. A 204 means the contract has expired or been accepted, and the app drops it. ESI also
  answers 403 "Contract accepted by player, requires authorization" for a contract accepted after the list was cached,
  which the OpenAPI spec does not say, and the app treats it the same. Contracts up to 100 m³ are kept, newest first,
  and each batch reads up to 500 sets of lines (see [Background fetches](#background-fetches)). ESI's OpenAPI spec names
  no rate-limit group for these routes and the responses carry no rate-limit headers, so only the error limit applies.
  The prices are worked out from the stored rows (see
  [Contract prices](../requirements/market-logistics.md#contract-prices)).

  **The list is capped (found 2026-11-09, not in ESI's documentation).** A region's list holds at most 35 pages of 1,000
  contracts, so 35,000, sorted by contract id ascending. For a region with more public contracts than that, the
  **newest** are cut off: The Forge's list showed contracts issued up to 5.5 hours before the request, while smaller
  regions showed minutes. Page 36 is a 404, and the spec lists no parameter that changes this: only `page` and the
  conditional-request headers. Eleven plausible parameters (`after`, `before`, `from_id`, `order`, `limit`, `since` and
  others) were tried against real ESI and all were ignored. EVE Ref's snapshots are built from the same list and show
  the same 35,000. So a contract appears in our data only when an older one expires or is accepted, and prices lag by
  hours. Contracts issued and accepted inside that lag are never seen, and they are the well-priced ones, so the prices
  lean high. Probing contract ids above the list's last id is no workaround: ids are shared by every region and
  contract type, so most probes would be errors against the error limit.
- System cost indices come from `GET /industry/systems`, fetched at most once per cache period for everyone.
- The `market-order` group allows 12,000 tokens per 15 minutes per IP address for all users together, so the shared
  store matters in phase 3. A refresh of a large plan fetches only the items whose stored orders have expired.

Why ESI rather than Fuzzwork's market aggregates: the aggregates are fixed at the best 5% of volume, while the
requirements make the share a user setting, and they would add a third-party service with no uptime promise.

Status: **agreed** (follows from the requirements).

## Character assets

Status: **proposed** (added 2026-11-09 with the asset fetch).

- **Route and scope**: `GET /characters/{character_id}/assets` needs `esi-assets.read_assets.v1` and is in the
  `char-asset` group (1,800 tokens per 15 minutes per character). The client reads it through its pages like other
  routes, caches it per character and route (`ETag`, `Expires`), and sends the character's access token as a bearer
  token. The token comes from the `AccessTokens` port, which the `characters` module implements (see
  [Refreshing access tokens](identity-and-tokens.md#refreshing-access-tokens)). The scope is checked before the call,
  so a character that did not grant it costs no request. After a 401 the token is forgotten and the request is made
  once more with a fresh one, never in a loop.
- **When it runs**: opening the inventory page, or its Refresh button, queues one task per character that can be
  fetched, on virtual threads. ESI's cache time decides whether ESI is asked at all, so opening the page again costs
  nothing while its copy is current. A character already being fetched is skipped (an in-memory flag per character). The
  page shows stored data at once and polls a status block every two seconds while a task runs.
- **Storage**: each fetch replaces the character's rows in one transaction (`character_asset`, no history), after the
  ESI call and never inside it. `character_fetch` records when ESI last answered, when the rows last changed, how many
  rows there are and the last problem. A failed fetch keeps the stored rows and their age.
- **Found 2026-11-09, against real data (not in ESI's documentation)**: the OpenAPI spec lists `location_type` as
  `station`, `solar_system`, `item` or `other` and says nothing more. For items in a player structure, real ESI answers
  `item`, not `other`, with the structure's id (above 1,000,000,000,000) as `location_id`. One character had 176 items in
  15 structures, all typed `item`. So a top-level item is found by its `location_id` not being the `item_id` of another
  item in the same list, never by `location_type`. NPC stations (about 60,000,000 to 64,000,000) and solar systems
  (about 30,000,000 to 33,000,000) can be named from the SDE, and a structure id (above 1,000,000,000,000) needs ESI.
  Blueprint copies have `is_blueprint_copy` true. Originals have no value, like every other item, so a blueprint
  original is told by the SDE's blueprint category. The `Deliveries` and `AssetSafety` flags also occur in real data.
- **Structure names**: `GET /universe/structures/{structure_id}` needs `esi-universe.read_structures.v1`, is asked
  with the character's token and has no rate-limit group in the OpenAPI spec, so only the error limit applies. ESI
  answers 200 with a name only for a character that may dock there, and 403 (or 404 for a structure that is gone), which
  the spec does not list. Every 403 counts toward the error limit, so after storing a character's assets (and also when
  ESI said nothing changed, to finish an earlier run) the app asks about the structures where that character has
  top-level items and nothing current is known: at most 25 per run, new ones first, stopping after 5 refusals. A name is
  trusted for 7 days, a refusal for 1 day and a "not found" for 7 days. A failure (throttled, down, no token) stops the
  run and is not kept. Answers are stored per character (`character_structure`), not shared, because only a character
  with docking access can learn a name, and they go with the character. Status: **proposed**.
- **Not yet**: the checkpoint under [Background fetches](#background-fetches) for the `char-asset` group.

## Alerts

- Every alert is a row in Postgres: account, kind (job finished, project blocked, contract accepted or completed,
  contract expiring, character needs re-authorization, ESI unavailable), what it is about, when it was raised and
  when it was read. The dashboard and the notification list read these rows, so no alert is lost while the user is
  away.
- Job-finish and contract-expiry alerts need no ESI call. A sweep every minute raises them for jobs whose stored end
  time has passed and for contracts that will expire within the user's warning time. The sweep is idempotent, so a
  restart only delays it.
- While a page is open, the browser keeps one server-sent events (SSE) stream to the app. New alerts and fetch
  progress are pushed on it. When the page loads, it reads the stored alerts first, so nothing is missed between
  streams.
- With one instance, alerts reach the stream through an in-process event. With several instances later, Postgres
  `LISTEN`/`NOTIFY` passes them between instances.

Why SSE: the browser only receives, so WebSocket is more than needed. Polling would delay alerts and progress and cost
requests. SSE works with server-rendered pages (htmx's SSE extension) and with a SPA.

Status: **agreed**.

## Differences found between the prototype and the ESI documentation

Flagged to the developer when the architecture was drafted. The new ESI client avoids all of them.

- The prototype's `Api` sends `Cache-Control: no-cache` on every ESI request. The documentation says not to fetch
  before `Expires`, and that getting around the cache can get an application banned.
- Its User-Agent, `EveAssist/0.1 Where Am I`, has no contact email or URL.
- It sends no `X-Compatibility-Date`.
- The prototype's `Client` stores access and refresh tokens as plain bytes and decodes the JWT without checking its
  signature, issuer or audience.

## Open questions

- The concurrency limit per instance (8 is a first guess) and how early the client slows down against a bucket are to
  be tuned against real responses, at the checkpoint under [Background fetches](#background-fetches).
  **Checkpoint for market prices, 2026-11-08**: 60 real items (the materials of blueprints, from the dev SDE), 8 calls
  at a time with a new connection each, took about 4 s. All answered 200 with `X-Pages: 1`, no 429 and no error
  headers. Each cost 2 tokens, so 120 of the 12,000 in the `market-order` bucket, and the lowest `X-Ratelimit-Remaining`
  was 11,880. The limit of 8 stays for now. Character data will need its own checkpoint, because its buckets are far
  smaller (for example 150 tokens for wallet routes).
- The response to a request sent with `X-Compatibility-Date: 2026-08-18` carries `x-compatibility-date: 2020-01-01`.
  This is probably because the orders route has no newer version. It is not confirmed with ESI's documentation.
- The OpenAPI spec lists `Cache-Control`, `ETag`, `Last-Modified` and `X-Pages` as headers of the orders route but not
  `Expires`. The real response sends `Expires`, as the documentation describes, so the client uses it and falls back to
  `max-age`.
