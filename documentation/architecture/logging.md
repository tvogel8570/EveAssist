# Log context (MDC) and correlation

Status: **proposed, a separate work stream, not started.** The developer asked for it to be written down on 2026-11-09
and not built yet. Nothing here changes the code today. The plan below is what to start from, and the open questions
are what to settle first.

Covers how every log line gets a correlation id and the other attributes needed to follow one request, one background
run or one ESI call through the logs. The rules about what logs may never contain are in
[Deployment and operations](deployment.md#monitoring), and still apply.

## Why

Today a log line says what happened and in which class, and not for whom, in which request or in which run. With
several requests, the background contract loader, ESI fetches and event listeners writing at once, that is not enough
to answer "what happened to this request" or "which run logged this warning". The contract loader's start and stop
lines are the first that would be worth tying together by a run id.

## What MDC is, and what goes wrong with it

MDC (mapped diagnostic context) is a per-thread map that the logging framework adds to every line the thread writes.
It is thread-local, so it follows a thread and nothing else. Two things matter for this app:

- Tomcat's request threads are pooled, so a value left behind leaks into the next request unless it is cleared in a
  `finally`.
- Work that moves to another thread does not take the map with it. That is most of this app's background work:
  `Thread.ofVirtual()` runs, the `newVirtualThreadPerTaskExecutor()` inside a refresh, and event listeners that run
  after the publishing transaction commits. Each hop has to carry the context on purpose.

## The attributes

| Key | Set where | Value | Notes |
|---|---|---|---|
| `requestId` | the first web filter | A generated id for each HTTP request | Also returned in the `X-Request-Id` response header, so a person can quote it. An incoming `X-Request-Id` is used only when it matches a strict pattern (letters, digits and hyphens, 8 to 64 characters) and comes from the proxy; otherwise one is generated. This keeps a caller from writing arbitrary text into the logs. |
| `accountId` | a filter after authentication | The account's public id (the UUID in `AccountId`) | The id only. Never an email address, a display name, a session id or a token, because a person's data is deleted with their account (see [Ownership](data.md#ownership)). |
| `characterId` | code that works for one character | The EVE character id | For fetches and token refreshes, which belong to a character. |
| `task` and `runId` | the start of a background run | A fixed task name such as `contract-loader`, and a short id for this run | One id ties a run's start line, its batch lines and its stop line together. |
| `esiRoute` and `esiRequestId` | the ESI client | The route key, and ESI's `X-Esi-Request-Id` response header | ESI sends that header on responses. It is what CCP can look up, so it belongs in the log for every failed call and in any question asked of the developer community. |
| `eventId` | an event listener | The id of the event being handled | See the open question on events below. |

The keys are constants in one small class (for example `LogContext`), which is the only code that calls `MDC.put`. That
gives one allow-list, a length cap on values, and one place for an ArchUnit rule to check.

## How the context travels

- **Web requests**: a filter sets `requestId` first, before Spring Security, so that even a rejected sign-in is logged
  with an id. The `accountId` is added once the request is authenticated. Both are removed in a `finally`.
- **Background runs**: the code that starts the run sets `task` and `runId` as its first act on the run's own thread
  and clears them in a `finally`. `ContractLoader` is the first example.
- **Threads started from a thread that has context**: a helper wraps a task (`LogContext.wrap(runnable)`) so it copies
  the map in and clears it out. The virtual-thread executor inside `ContractPrices.refresh` and the ESI calls it makes
  are the cases to cover first. The alternative is the context-propagation library, if the app adopts Micrometer
  Tracing (see below).
- **Event listeners**: see the open question below.

## Format

- **Dev**: the plain text pattern, with the correlation values in a fixed place so that a line stays readable, for
  example `[requestId accountId]`.
- **Prod**: structured JSON on the console (Spring Boot has built-in structured logging formats), so that CloudWatch
  Logs Insights can filter and group by `requestId`, `accountId` or `task` without parsing text. The existing
  CloudWatch metric filters, which count ERROR lines and ESI failures, would be rewritten for JSON when this is built.

## Hand-written ids or Micrometer Tracing

Spring Boot can fill `traceId` and `spanId` into the MDC itself when Micrometer Tracing is on the classpath, and it
has a property for placing them in the log pattern. That would replace the home-made `requestId` with the standard
`traceId`, and would handle propagation across threads. The cost is a dependency and a tracer with no exporter, which
is more than the app needs today. This is the first thing to decide when the work starts. If the app later adds
distributed tracing, the choice should not have to be undone, so the keys in the table above should be able to stay as
they are next to `traceId`.

## Tests

- A web test checks that a response carries `X-Request-Id`, that a valid incoming id is kept, that an invalid one is
  replaced, and that the context is gone after the request.
- A test checks that the MDC is empty after a background run, including when it ends with an exception.
- A test checks that a task wrapped for a new thread logs with the original context.
- An ArchUnit rule allows `MDC.put` only in `LogContext`.
- No test asserts on the exact JSON, only on the keys.

## Not in this work stream

Metrics, distributed tracing exporters, log retention and shipping (CloudWatch stays as it is), and anything that adds
personal data to the logs.

## Open questions

- Micrometer Tracing, or a hand-written `requestId` (see above).
- **Events.** The Modulith event publication registry delivers an event again after a crash, with no request behind it.
  [Code structure](code-structure.md#events) says events carry ids and the few values a listener needs. Should an event
  also carry the `requestId` that caused it, or should a listener only log its own `eventId`?
- **Server-sent events.** A page keeps one stream open for as long as it is open. Is one id per connection enough, with
  the alert or fetch as the line's subject?
- Should the `requestId` be shown to the person on an error page so that they can quote it?
- Which prod fields go into the CloudWatch metric filters after the move to JSON, and who updates the alarms.
