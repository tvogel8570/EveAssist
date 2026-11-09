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
- **Failures**: 5xx and timeouts are retried later with back-off, never in a loop. Stored data stays on screen with its
  age, and a banner says that ESI is down or throttled.

Status: **agreed** (follows from the requirements and the ESI documentation).

## Background fetches

- Fetches run inside the app on virtual threads. A per-instance limit (a semaphore, for example 8 calls at once) keeps
  a sign-in with many characters from flooding ESI. A refresh of one character holds that character's Postgres
  advisory lock, so two fetches of the same character never overlap, also with several instances.
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
user is signed in, so losing queued tasks at a restart costs nothing, and the advisory lock and the stored fetch state
already let several instances share the work.

**Exception: public shared data.** The list of public contracts is read by a background loader that needs no signed-in
user, because the data is public and shared by all users, and a user who opens the page should find it ready. The
developer decided this on 2026-11-09. The loader starts a minute after start-up and then 30 minutes after each run
ends (the list is cached for 30 minutes), reads batches of 500 contracts until none are waiting, and stops when a batch
makes no progress (ESI throttling or down) or the app is stopping. A Postgres session advisory lock (key `0x45410001`)
keeps two instances, or a run that is still going, from reading together. It is switched off with
`eveassist.market.contracts.background=false`, and the batch size, first delay and interval can be set under
`eveassist.market.contracts`. It uses the same client, so the simultaneous-call limit and the error-limit pause apply.

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
  list and `GET /contracts/public/items/<id>` for the lines of one contract. The list is read through its pages and cached
  like other routes. A contract's lines never change, so the client does not cache that route: the app stores them and
  asks once per contract. A 204 means the contract has expired or been accepted, and the app drops it. ESI also answers 403 "Contract accepted by player, requires authorization" for a contract accepted after the list was cached, which the OpenAPI spec does not say, and the app treats it the same. Contracts up to
  100 m³ are kept, newest first, and each batch reads up to 500 sets of lines (see [Background fetches](#background-fetches)). At the time of writing The Forge's
  list has about 35 pages. ESI's OpenAPI spec names no rate-limit group for these routes and the responses carry no
  rate-limit headers, so only the error limit applies. The prices are worked out from the stored rows (see
  [Contract prices](../requirements/market-logistics.md#contract-prices)).
- System cost indices come from `GET /industry/systems`, fetched at most once per cache period for everyone.
- The `market-order` group allows 12,000 tokens per 15 minutes per IP address for all users together, so the shared
  store matters in phase 3. A refresh of a large plan fetches only the items whose stored orders have expired.

Why ESI rather than Fuzzwork's market aggregates: the aggregates are fixed at the best 5% of volume, while the
requirements make the share a user setting, and they would add a third-party service with no uptime promise.

Status: **agreed** (follows from the requirements).

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
