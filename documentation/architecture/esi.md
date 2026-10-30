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
- A fetch task is one character and one kind of data (jobs, blueprints, skills, implants, orders, wallet transactions,
  contracts, standings and the rest). The client's cache rule decides whether ESI is called at all.
- After sign-in, if the user's setting is on, the app queues every task for every linked character that is not marked
  for re-authorization. The dashboard shows stored data at once, and a progress bar over SSE shows the fetch.
- With the setting off, a page queues the tasks it needs, and the freshness page has refresh buttons for one character
  or all of them.
- Tasks run only for users with a live session. When the session ends, the user's queued tasks are cancelled.
- Fetch state (last fetched, last result and the cache headers) is a row per character and kind of data. It backs the
  freshness page and the "stored data is N minutes old" labels, and it survives restarts. Queued tasks themselves are
  kept in memory only. After a restart, the user's next page or refresh queues them again.

Why not a job queue in Postgres (db-scheduler, JobRunr) or a separate worker with SQS: fetches matter only while the
user is signed in, so losing queued tasks at a restart costs nothing, and the advisory lock and the stored fetch state
already let several instances share the work.

Status: **agreed**.

## Corporation data (phase 2)

Corporation routes are fetched with a role-holder's token, only while that role-holder is signed in, through the same
client. The corporation id stands in for the character in the fetch state. Other members see the stored data with its
age.

## Jita prices

- Prices are public data, shared by all users. They are fetched on demand, when a plan is opened or the user asks for
  a refresh.
- For each item, the app reads `GET /markets/10000002/orders?type_id=<id>` (The Forge), keeps the orders at Jita 4-4
  (station 60003760), and works out the buy and sell price from the best orders that make up the user's share of the
  volume. The orders are stored once with their `Expires`. Each user's price comes from those stored orders, so users
  with different shares cost no extra ESI calls.
- Volume history comes from `GET /markets/10000002/history?type_id=<id>` and is stored per item, also within its cache
  time.
- System cost indices come from `GET /industry/systems`, fetched at most once per cache period for everyone.
- The `market-order` group allows 12,000 tokens per 15 minutes per IP address for all users together, so the shared
  store matters in phase 3. Opening a large plan fetches only the items whose stored orders have expired.

Status: **proposed** (follows from the requirements).

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

Flagged to the user when the architecture was drafted. The new ESI client avoids all of them.

- The prototype's `Api` sends `Cache-Control: no-cache` on every ESI request. The documentation says not to fetch
  before `Expires`, and that getting around the cache can get an application banned.
- Its User-Agent, `EveAssist/0.1 Where Am I`, has no contact email or URL.
- It sends no `X-Compatibility-Date`.
- The prototype's `Client` stores access and refresh tokens as plain bytes and decodes the JWT without checking its
  signature, issuer or audience.

## Open questions

- The concurrency limit per instance (8 is a first guess) and how early the client slows down against a bucket are to
  be tuned against real responses.
