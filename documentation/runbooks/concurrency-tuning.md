# Concurrency Tuning

Which symptoms suggest changing a concurrency setting, and what to change. The values below are first guesses, set
without load measurements. Change one at a time and watch the symptom, because they affect each other.

## What limits what

- **Hikari pool** (`spring.datasource.hikari.maximum-pool-size`, 10): how many database connections the app holds.
  Every repository call, ESI cache lookup (`EsiCache.find`/`save`) and session read takes one for the length of its
  statement or transaction. No connection is held while ESI is being asked.
- **ESI simultaneous calls** (`eveassist.esi.max-concurrent`, 8): EveAssist's own cap on HTTP calls to ESI in flight.
  It is not an ESI rule. It is held only around the HTTP call.
- **Tomcat** (`server.tomcat.threads.max`, 200 by default): how many requests are handled at once while Tomcat runs on
  platform threads. It has no effect if virtual threads are enabled for requests.
- **Virtual threads for requests** (`spring.threads.virtual.enabled`, **not enabled**): the fan-out in `MarketService`
  and `ContractPrices` and the contract loader already use virtual threads in code, and do not depend on this flag.

The database's own limit, `max_connections` in Postgres (default 100), is the ceiling for the pool. Check it before
raising the pool, and keep room for Flyway, Keycloak's own database and admin sessions.

## Symptoms and actions

| Symptom | Likely cause | What to do |
|---|---|---|
| Log lines `HikariPool-1 - Connection is not available, request timed out after 30000ms`, or requests that stall for about 30 s and then fail | The pool is used up. Check first that nothing holds a connection across an ESI call, which would be a bug. | Raise `maximum-pool-size` in steps (10 → 15 → 20), staying well below Postgres `max_connections`. Look at the Hikari metrics `hikaricp.connections.pending` and `hikaricp.connections.usage` if they are exposed. |
| `hikaricp.connections.pending` above 0 for long stretches while `hikaricp.connections.active` sits at the maximum, but no timeouts | Requests wait for a connection but still succeed. Latency is lost, not correctness. | Same as above, if the wait shows in page times. A pool that is full for a moment during a burst of ESI cache lookups is normal and needs no change. |
| `Connection is not available ... (total=10, active=10, idle=0, waiting=19)` when the inventory page opens, with many `Fetching the assets of a character failed: CannotGetJdbcConnectionException` lines and a request that fails in an ordinary query | Every character's assets were stored at once: each storing transaction holds a connection while its rows are replaced. Seen 2026-11-10 with 20 characters. `AssetFetcher.MOST_CONCURRENT_FETCHES` (3) now limits it | Lower `MOST_CONCURRENT_FETCHES`, or raise the pool with it. Check it stays well below the pool size, because token refreshes (see the next row) and requests need connections too. |
| `Connection is not available ...` right after a restart or a long idle time, on a page that asks for tokens for many characters | Every token refresh holds a connection for its advisory lock and needs a second one. Seen 2026-11-10 with 20 characters. `CharacterAccessTokens.MOST_CONCURRENT_REFRESHES` (2) now limits this | If it returns, check that `MOST_CONCURRENT_REFRESHES` times two is still at most the pool size, and that no new code holds a connection while asking for another. Do not just raise the pool. |
| Pool at maximum, but Postgres shows the connections `idle in transaction` (`pg_stat_activity`) | A transaction is held open across slow work. A larger pool only hides it. | Find and fix the code. Do not raise the pool. |
| Postgres `FATAL: sorry, too many clients already` | Pool size times the number of app instances is above `max_connections`. | Lower the pool, or raise `max_connections`, or put a pooler (PgBouncer) in front. |
| A price refresh or contract load is slow, and ESI answers arrive in small batches while the CPU is idle | The ESI cap is what limits it. | Raise `eveassist.esi.max-concurrent` a little (8 → 12), only if the error-limit headers (`X-ESI-Error-Limit-Remain`) and rate-limit headers show room. |
| ESI answers 420 or 429, or `X-ESI-Error-Limit-Remain` runs low, or the client reports it is waiting for a bucket | EveAssist is asking ESI too fast. | Lower `eveassist.esi.max-concurrent` (8 → 4). See `documentation/architecture/esi.md` for the limits. |
| `Connection is not available` errors that begin only after raising `max-concurrent` | More ESI calls in flight means more simultaneous cache lookups and saves, which need connections. | Lower `max-concurrent` again, or raise the pool together with it. |
| Under many simultaneous page loads, requests queue and the log or metrics show Tomcat's threads (`tomcat.threads.busy`) at the maximum of 200, while the CPU and the Hikari pool both have room | Requests are blocked in I/O, for example ESI or slow queries, and use up Tomcat's platform threads. | This is the case for enabling `spring.threads.virtual.enabled=true`. Raise the Hikari pool at the same time, because many more requests may then reach the database together. Run with `-Djdk.tracePinnedThreads=full` once and check for pinning. |
| After enabling virtual threads, throughput drops or stalls, and a thread dump or a JDK Flight Recorder recording shows virtual threads pinned | A native call or a library pins its carrier thread. On Java 25, `synchronized` no longer pins. | Find the pinning code with the JFR event `jdk.VirtualThreadPinned`. Turn the flag off again if it cannot be fixed. |
| After enabling virtual threads, the JVM exits while only scheduled work remains | Virtual threads are daemon threads. Tomcat normally keeps the JVM alive, so this appears only without a web server. | Set `spring.main.keep-alive=true`. |

If none of these fit and the app is slow, do not change settings first. Look at where the time goes: a thread dump
(`jcmd <pid> Thread.dump_to_file`), the Hikari metrics, and the slow-query log in Postgres.

## Reading the metrics

Added 2026-11-10. The app exposes a few metrics through Spring Boot Actuator, on its own address and port, for a signed-in
user with the admin role only. Nothing else of the actuator is exposed (no health, environment or heap dump), and an
ordinary user or a signed-out visitor is refused. Open them in the browser while signed in as an admin, or ask for the
list at `/actuator/metrics`:

| Metric (`/actuator/metrics/<name>`) | What it says |
|---|---|
| `hikaricp.connections.pending` | Requests or tasks waiting for a database connection. Above 0 for long stretches means the pool is the limit. |
| `hikaricp.connections.active`, `hikaricp.connections.max` | Connections in use and the pool size. Active sitting at max is the same signal. |
| `eveassist.asset.fetch.waiting` | Characters whose assets are queued behind the `AssetFetcher.MOST_CONCURRENT_FETCHES` turns. |
| `eveassist.asset.fetch.turnaround` | Time from a character being queued to its fetch being done. Read the mean and the max. |

The numbers are for the running instance since it started, and are not stored anywhere.

## When to look again

The values here were set for one player with about 20 characters and have not been measured under load. The user counts
below are estimates, and a metric reaching its trigger matters more than the count. Both caps, `MOST_CONCURRENT_FETCHES`
and `MOST_CONCURRENT_REFRESHES`, are shared by every user of one instance, so the queue lengthens with each user.

| When | Why | What to do |
|---|---|---|
| About 5 to 10 users on at once, each with 10 or more characters | The fetch queues overlap, and a user who opens Inventory after sign-in waits behind other people's fetches. | Raise the pool and `MOST_CONCURRENT_FETCHES` together (for example pool 20, fetches 5). Keep fetches plus refreshes well below half the pool. |
| About 50 users (the small group phase) | Sign-in bursts, such as a corporation logging in at the start of an operation, outgrow one pool and per-instance caps. | Move to a shared work queue with priorities, instead of each page start kicking its own fetches. Check Postgres `max_connections` against pool size times the number of instances. Consider virtual threads for requests (see above). |
| A few thousand users (the public phase) | Fetching on page open stops making sense. | Fetch in a background scheduler that refreshes recently active accounts within ESI cache times, so the page only reads stored data. ESI's error and rate limits, not the pool, become the limit. This also changes the rule that nothing is fetched for signed-out users, so check [Gaining players' trust](../requirements/gain-trust.md) first. |
| Any time: `hikaricp.connections.pending` above 0 for long stretches, or any `Connection is not available` line | The pool is the limit. | See the symptoms table above. |
| Any time: `eveassist.asset.fetch.turnaround` regularly above about 30 seconds | The fetch caps are the limit, not the pool. | Raise `MOST_CONCURRENT_FETCHES` with the pool, or move to the shared queue above. |
| Any time: Postgres connections `idle in transaction` | A transaction is held across slow work, which is a bug. | Fix the code. Do not raise the pool. |

## What does not need tuning now

- Tomcat's 200 threads, while the app is a single player or a small group. Nothing in the app's current load comes
  near it.
- The virtual-thread fan-out in `MarketService.currentPrices` and `ContractPrices.refresh`. Their size is set by the
  number of items asked for, and `max-concurrent` caps the ESI calls they make.

Add a row here when a new symptom is seen and understood, with what fixed it.
