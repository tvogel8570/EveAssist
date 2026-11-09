# Code Structure

Status: draft, proposed except where a section says otherwise.

Covers how the code inside `App/` is organized: how the modules from [Module layout](README.md#module-layout) are
enforced, how they talk to each other, and how the code inside one module is layered. Everything here is
**proposed** unless a section says otherwise. It follows from the agreed module layout and is listed for review under
[Open questions](#open-questions).

## Two questions, two tools

Spring Modulith and hexagonal (or clean) architecture answer different questions, so EveAssist uses both:

| | Spring Modulith | Hexagonal / clean |
|---|---|---|
| Question | Which business modules exist, and what may each one see of the others? | How does a module's domain logic stay independent of the web, the database and ESI? |
| Cuts the code | Vertically, by business capability | Horizontally, by technical concern |
| Rule | A module uses only another module's public API, and there are no cycles | Dependencies point inward, and the domain knows nothing about adapters |
| Enforced by | A test that runs `ApplicationModules.verify()` | ArchUnit rules, through jMolecules |

Modulith is the main structure, because boundaries between features are what decay first in a growing monolith, and
because clean boundaries are what let later phases (groups, a possible standalone mode, a possible split into
services) be added without a rewrite. Hexagonal layering is used inside the modules where it pays for itself, not
everywhere.

## Packages

Each module is a top-level package under the application's base package. Layers live **inside** a module, never as
top-level packages, because a shared top-level `adapters` or `domain` package would become a module that everything
depends on.

```
eveassist/
    EveAssistApplication.java
    industry/                  ← module; classes here are its public API
        IndustryService.java
        PlanSummary.java       ← plain objects the API takes and returns
        JobFinished.java       ← event other modules may listen to
        internal/
            domain/
            application/
            web/               ← htmx controllers, forms, and web models where a screen needs one
            persistence/
    market/
        ...
    esi/
        ...
```

- The module's base package is its public API: services, the plain objects they take and return, and its events.
- The plain objects and events are Java records: immutable snapshots with no business logic (see [Records](#records)).
  The service maps its domain model or rows to them at the module boundary, so other modules never hold a domain
  object. A service is a concrete class, and becomes an interface only for the reasons
  in [Service interfaces](#service-interfaces), such as tests that need a fake for the `esi` module's API (see
  [ESI as a boundary](#esi-as-a-boundary)). Modulith itself works the same either way: this is EveAssist's choice,
  not a Modulith rule.
- Everything under `internal` is hidden from other modules. Modulith's test fails if another module reaches into it.
- Where a module needs to expose more than its base package, it declares a named interface (`@NamedInterface`) rather
  than making classes public by accident.
- Modules depend only on modules they declare (`@ApplicationModule(allowedDependencies = ...)`), so a new dependency
  is a visible change in review.

### Records

The data types and events of a module's API are records. This is EveAssist's convention, not something Java or
Modulith requires, and an ArchUnit rule enforces it (see [Verification](#verification)).

- **No business logic** does not mean no code. A record may have a compact constructor that checks its input (no null
  id, no negative quantity), read-only accessors derived from its own fields (`isFinished()`, `totalCost()`), and
  static factory methods. It does not change state, call services or hold entities.
- **Records are only shallowly immutable.** A collection field can still be changed by whoever holds the list that
  was passed in. The compact constructor therefore copies every collection into an immutable one, with `List.copyOf`,
  `Set.copyOf` or `Map.copyOf`. The copy cannot be changed by anyone, and later changes to the original do not reach
  it. `List.copyOf` also rejects null elements.

  ```java
  public record PlanDetails(PlanId id, String name, List<PlanLine> lines) {
      public PlanDetails {
          lines = List.copyOf(lines);
      }
  }
  ```

- Nested types are records too (`PlanLine` above), so the whole snapshot is immutable, not only its top level.

### Facade and visibility

Status: **proposed, needs validation**. The developer wants time to think about it before it stands.

Java's access levels and Modulith's boundaries do not line up. Package-private does not reach subpackages, so if code
in `industry/internal/...` calls a method of a class in `industry`, that method must be `public`, and every other
module can call it too. Modulith checks which types a module uses, not which methods, so it does not notice. The other
way round is fine: classes in `internal` may be `public`, with public methods, because Modulith hides the whole
`internal` tree from other modules. Inside `internal`, Modulith is the access control, not the `public` keyword.

What follows:

- **One thin facade per module** in the base package, for example `IndustryService`. It has only the methods other
  modules and the external API may call. Each delegates to a use case in `internal/application`, which checks
  visibility and returns a record (see [Pages, the API and the facade](#pages-the-api-and-the-facade)). It has no
  helpers, and nothing inside its own module calls it.
- **Testable logic lives in public classes under `internal`**, tested directly through their public methods. A private
  method that needs a test of its own is a sign of a missing class, usually in `internal/domain`.
- **The facade is tested through its public methods only**, in `@ApplicationModuleTest` tests that call it as another
  module would.
- **Package-private** members are used to keep small collaborators out of sight, with their tests in the same package
  under `src/test/java`. They are not used to widen the facade for tests.
- **Spring proxies**: `@Transactional`, `@Cacheable`, `@Async` and method security go on public methods of beans that
  are called from outside the bean, meaning the facade or the use cases. They do nothing on `private` methods, and
  nothing on a call from one method of a bean to another of the same bean, so tests and production can differ.
- **Mocking**: Mockito's default inline mock maker mocks concrete classes, so `@MockitoBean IndustryService` works
  without an interface.
- **Guardrail**, part of the ArchUnit rules from day 1 (see [Verification](#verification)): a rule that nothing in `industry.internal` depends on the `industry` base package
  except its records and events catches a facade that is called from inside its own module, which Modulith misses.
  The one exception is an **outbound port**: an interface in the base package marked with jMolecules' `@SecondaryPort`
  that the module calls and another module implements, such as `esi`'s `AccessTokens`, which `characters` implements so
  that `esi` depends on nothing. It has to be in the base package for the other module to see it. The mark is required,
  so a facade cannot pass as a port, and a fixture in the rule's own tests proves both. This was added on 2026-11-09 for
  the asset fetch and is **proposed**, because it loosens a check the developer agreed.

### Pages, the API and the facade

Status: **proposed**. The developer chose this option after comparing it with one where everything calls the facade, in
a spike (`spikes/api-boundary/` on branch `api-boundary-spike`, pushed to `origin`).

Pages and the external API want different things. A page wants what one screen shows, such as a plan list with totals,
or the changed rows and new total after an edit. The API wants stable resources for clients EveAssist does not know.
They therefore meet at the use cases, not at the controllers or the facade:

- **Page controllers** in `internal/web` call use cases in `internal/application` directly.
- **The `api` module and other modules** call the facade, which delegates to the same use cases.
- **Types and queries only pages use** stay under `internal`, so a screen change cannot widen the module's API or change
  the JSON contract (see [Use case results](#use-case-results)).
- **Every public use case method takes the `Viewer` as its first parameter.**
- **Every public repository method takes the `Viewer` as its first parameter, and its SQL filters on it.** A use case
  cannot get or change a record without one, so the build fails when a new repository method leaves it out. The
  developer chose this over a viewer-checking class such as `VisiblePlans`, which the spike used, because the check then
  belongs in the SQL, list queries follow the same rule, and no class only passes calls through. The spike did not try
  this form.
- **The viewer comes only from the signed-in session or API token**, never from request data such as a form field or URL.
- **Writes filter too.** An `UPDATE` or `DELETE` includes the owner in its `WHERE` clause. A change to child rows first
  checks the parent in the same transaction.
- **Zero rows means not found**, whether the record is missing or belongs to someone else, so a response does not reveal
  that another user's record exists.
- **Membership is looked up in the SQL**, from the account, rather than passed as a list of groups, so someone who leaves
  a group loses access at once. The rules for each table are in [Sharing in groups](data.md#sharing-in-groups-phase-2).

ArchUnit enforces the signature rules (see [Verification](#verification)). They prove that the viewer is passed, not that
the SQL uses it correctly, so each repository method has a test that another account gets nothing back.

The costs: the facade only passes calls through for now, a reader has to know that pages and the API enter at different
levels, and a screen spanning several modules needs a small module of its own, which the spike did not try.

### Use case results

Status: **proposed**. The developer chose this so that pages and the API can differ in granularity without use cases
changing for either. The spike returned screen-shaped types here (`EditorRow`, `PlanListRow`, `PlanEditorView`,
`PlanChange`), which only worked because its API is read-only. `App/` does not follow it on this point.

- **A write returns neutral facts**, for example `PlanChangeResult(planId, changedLines, totalCost, version)`. It does
  not return a type shaped for one screen, or only an id and version. A page picks the fragment to render from the
  result, and the API picks its response.
- **Reads return neutral records too**, kept in `internal/application` beside their queries and named for what the data
  means, not for the screen that uses them, for example `PlanLineCosting` and `PlanOverview`. A read and a write that
  draw the same row use the same record, so one fragment serves both.
- **Templates render these records directly.** A view model in `internal/web` is added only when a screen needs grouping
  or formatting that the record should not carry. If a shared record starts gathering fields for one screen, it is
  split.
- **A query may have its own SQL**, such as a list with totals, and its record sits beside it. Each consumer can have a
  different shape, but the rules and calculations stay in the domain.
- Use cases do not return domain objects to `internal/web`, so templates cannot change state.
- **Records carry age and version as their own fields, where they apply**, not in a generic wrapper. For example,
  `PlanDetails` has `version`, and `PlanCosting` has `version`, `pricesAsOf` and `sdeVersion`. Pages need these for
  phase 1, for the "N minutes old" labels and for flagging plans after an SDE update.
- **A write finds the changed lines by comparing.** The use case gathers its inputs from other modules once, costs the
  plan, applies the edit, costs it again with the same inputs, and compares the two results line by line. One edit can
  ripple through the material tree, and a line missed by per-operation tracking would show a wrong number with no error.
  The domain does not track what each operation touched, and redrawing everything fails the large-plans rule in
  [UI](README.md#ui).
- **The cost is two costings per edit, which turned out negligible.** Timed with `RealSdeMeasurementIT` against the real
  SDE on the developer's WSL machine, with the costing still simplified, so skills, facilities and invention will add
  to it:

  | Plan | Lines | Two costings and compare (median) | p95 |
  |---|---|---|---|
  | One Titan-class ship | 79 | 0.07 ms | 0.19 ms |
  | All capitals (48 products) | 196 | 0.16 ms | 0.19 ms |
  | Everything buildable (4,057 products) | 4,669 | 1.91 ms | 3.35 ms |

  [Costing in memory](data.md#costing-in-memory) allowed a few seconds, so the comparison stays. A real edit on a plan of
  four capital ships, through the running app, waited about 30 to 65 ms for the server, including saving the priced items.
- **Choosing to buy or build a part redraws the part and material tables** rather than comparing. It adds and removes whole
  subtrees of rows, which a response of changed rows would have to place in order. A write that changes which rows exist
  returns the whole plan as `PlanDetails`, and one that only changes their values returns `PlanChangeResult`.
- **A quantity edit changes nearly every row of a capital plan**: 71 of 90 rows, in a 29 KB response, in that test. Updating
  only changed rows helps most for plans of unrelated products and for smaller edits such as build-or-buy later.

Template tests catch a renamed record field, because templates depend on those names (see [UI](README.md#ui)).

### What starts an ESI fetch

Status: **proposed**. Pages may queue fetches (see [Background fetches](esi.md#background-fetches)), but an API request
never may (see [The API and ESI](api.md#the-api-and-esi)). Pages and the API share use cases, so the two have to be kept
apart. Deciding what to fetch is orchestration, so it stays out of controllers, which remain thin.

- **A page-entry use case** handles a page action that starts a fetch, or does other work on opening. `PlanEntry` replaces an
  out-of-date stored total when a plan is opened, refreshes a plan's prices when the user asks, and fetches the prices
  of a product added to a plan. Opening a plan never fetches: a plan keeps its prices until the user refreshes them.
  A fetching action loads the plan in a read-only transaction, asks `market` for prices with no transaction open, and
  then calls a write in `PlanEditing` that takes the prices as arguments and saves them in a second transaction. So
  `PlanEditing` never fetches, and no database connection is held while ESI is being asked.
- **Queries and writes do not start fetches.** Most small htmx updates are one or the other, so they need no page-entry
  use case.
- **Use cases are grouped into one class per set of related actions, not one class per action**, for example `PlanEntry`
  for actions that start fetches, `PlanEditing` for writes and `PlanQueries` for reads. A class that grows too big is
  split. A class is named for what it does, never for one screen (see [Use case results](#use-case-results)).
- **ArchUnit keeps fetches away from the API**: query classes do not depend on the fetch-queuing code, and the facade does
  not depend on page-entry use cases.

A refresh is one request: the button shows a spinner, and the tables and total are swapped in when the fetch is done.
Pushing progress over SSE, as [Background fetches](esi.md#background-fetches) describes for character data, is not
used for plan prices.

### Work with no signed-in person

Status: **agreed**. The developer compared a separate repository for background work, a special system viewer, acting as
each owner, and not re-costing in the background, and chose the last.

- **Nothing re-costs saved plans in the background.** The list works out in SQL whether a stored total is out of date and
  says so, and `PlanEntry` stores a fresh total when the plan is opened. An edit stores one too. See
  [Last-costed total](data.md#last-costed-total).
- **So the viewer rules have no exception.** No code reads a plan without a viewer, and no listener or system viewer
  exists.
- The cost is that a list total can be old, with its age and mark showing that, until someone opens the plan. Work is not
  spent on plans nobody opens.
- Deleting an account's data needs no viewer, because its rows cascade from the account row (see
  [Ownership](data.md#ownership)).
- **Still open**: flagging plans after an SDE import only for the blueprints that changed would need to read plans with
  no viewer. Until then a new SDE version marks every costed plan out of date. If background work ever has to read plans,
  a separate, clearly named class and repository, with an ArchUnit rule limiting who uses them, is the option to start
  from. A system viewer was rejected, because a wrong value in page code could expose every user's plans and the build
  could not catch it.

### Background tasks

Status: **agreed**. The developer chose this on 2026-11-09 while reviewing the public contract loader, and it is the way
to write every task that runs outside a request, on a timer or queued.

- **Each run is on its own virtual thread.** A `@Scheduled` method only starts it, with `Thread.ofVirtual()`, and
  returns. Spring Boot's scheduler has a single platform thread, so a long run would otherwise block every other
  scheduled job, such as the alert sweep.
- **One run at a time is kept with an in-memory flag**: `AtomicBoolean.compareAndSet(false, true)` at the start and
  `set(false)` in a `finally`. For work per key, such as per character, the same with a concurrent set of the keys
  running. A start that finds the flag set does nothing. The flag covers one instance, which is all EveAssist runs and
  the developer expects ever to run.
- **No Postgres advisory lock for this.** A session lock keeps a pooled connection for as long as the run lasts, and a
  transaction lock would hold a transaction while ESI is being asked, which the code never does (see
  [What starts an ESI fetch](#what-starts-an-esi-fetch)).
- **Writes are idempotent** (`ON CONFLICT DO NOTHING` or an upsert, deletes that find nothing), because the flag stops
  two runs in one instance and nothing stops them in two. A duplicate run then wastes calls and does no harm.
- **If a second instance is ever run**, each batch claims its work in the database (for example `FOR UPDATE SKIP LOCKED`
  with a short lease) instead of holding a lock across calls.
- **Work is done in batches, and each batch in small chunks that are saved one by one.** A run stops when nothing is
  waiting, when a batch makes no progress (ESI throttling or down, so the next start tries again), or when the app is
  stopping. It checks a `stopping` flag before every chunk, and a stopped loader starts no new run.
- **Shutdown waits for the run.** The `@PreDestroy` method sets the flag and then waits, up to a timeout (10 seconds
  by default), for the run to finish its chunk and log that it stopped. Spring destroys a bean before the beans it
  depends on, so the database pool is still open while it waits, and an integration test checks that the loader
  depends on the pool. If the wait runs out, shutdown goes on and the run is cut off. That is safe: each unit of work
  is one transaction, so nothing is half written, and what was not saved is picked up by the next run. The timeout is
  a property.
- **A timer starts runs at a fixed delay, not at clock times**, and the first start is a short delay after start-up.
  The interval and the batch size are properties, and a property switches the timer off so that tests call the task
  themselves.
- **Sign-in and token refresh are not background tasks.** Signing in, linking a character and refreshing an access token
  run in the request that needs them, and they fail where the person can see it. A successful authentication and
  authorization is not optional, so none of it is deferred, queued or left to a timer, and the rules above do not
  apply to it. In particular, the refresh keeps its Postgres advisory lock on the character id (see
  [Refreshing access tokens](identity-and-tokens.md#refreshing-access-tokens)): the lock is held for one bounded call to
  EVE SSO, which has a timeout, and it works across instances. The developer decided this on 2026-11-09. A background
  fetch that needs a token calls the same refresh and waits for it.
- **A task that reads public, shared data needs no signed-in user.** Anything that reads or writes a user's data keeps
  the viewer rules (see [Work with no signed-in person](#work-with-no-signed-in-person)).

The first example is `ContractLoader` and `ContractLoadSchedule` in the `market` module (see
[Background fetches](esi.md#background-fetches)).

## Naming

Spring Modulith sets no naming convention for these types. The ones below follow DDD and jMolecules practice: names
that cross a module boundary say what the data means, and technical suffixes stay inside `internal`.

| Kind | Convention | Example | Lives in |
|---|---|---|---|
| Query result in a module's API | Domain noun and role (`Summary` for list rows, `Details` for one item in full) | `PlanSummary`, `PlanDetails`, `JobCosting` | Module base package |
| Use case result used only inside the module | Domain noun and role, never a screen's name | `PlanOverview`, `PlanLineCosting`, `PlanChangeResult` | `internal/application` |
| Command input to a use case | Verb phrase and `Command` | `CreatePlanCommand`, `ConfirmJobMatchCommand` | `internal/application`, until another module or an API write needs it; then the module base package |
| Event | Past tense, no `Event` suffix | `JobFinished`, `CharacterUnlinked` | Module base package |
| Id | Noun and `Id`, a record around the UUID | `PlanId`, `CharacterId` | Module base package |
| Form backing object for a Thymeleaf page | Noun and `Form` | `PlanForm` | `internal/web` |
| JSON of the external API (later) | Noun and `Response`; inputs are a verb phrase and `Request` | `PlanResponse`, `CreatePlanRequest` | `api` module |
| Domain model | Plain noun | `Plan` | `internal/domain` |

- `Dto` is not used as a suffix. It names the mechanism, not the meaning.
- `Request` and `Response` are kept for HTTP, in the `api` module. A module's command says what it asks the module to
  change, so it is a `Command`, and the CQRS module of jMolecules can mark it with `@Command`. Queries and commands then
  read alike: `PlanDetails getPlan(PlanId)` and `PlanId createPlan(CreatePlanCommand)`.
- A command stays under `internal` until something outside the module needs it, so the module's API lists only what
  outside callers can do. Moving one means moving the record and adding a facade method. The developer chose this over
  always keeping commands in the base package.
- Ids passed between modules are typed (`PlanId`), not bare `UUID` or `long`, so one kind of id cannot be passed where
  another is expected.
- The external API's `Request` types are its own, in the `api` module, even when they carry the same fields as a
  module's `Command`. The JSON contract is versioned (see [Shape](api.md#shape)) and must not change when a module's API does.

### Service interfaces

Status: **proposed, needs validation**. The developer wants time to think about it before it stands.

A module's service is an interface only when one of these holds:

- **Tests need a fake with behavior of its own**, as with the `esi` module (see below).
- **There are several real implementations.**
- **The dependency points the other way (an SPI).** If module A needs something that module B provides, but B already
  depends on A, A declares an interface and B implements it. A then depends on its own interface, not on B, and the
  cycle that Modulith would reject is gone. The interface lives in a named interface of A, conventionally a package
  called `spi` (`@NamedInterface("spi")`), so that it is clear which types are meant to be implemented elsewhere.
  This is the most common reason for interfaces in a modular monolith.

When a service becomes an interface, the interface keeps the role name, and each implementation is named for how it
works:

| Type | Example | Lives in |
|---|---|---|
| Interface, the module's API | `EsiClient` | Module base package |
| Production implementation | `HttpEsiClient` | `internal` |
| Test implementation | `RecordedEsiClient`, `FakeEsiClient` | Test sources, or shared test fixtures when several modules' tests need it |

- When no word describes how the production class works, it is `Default...` (`DefaultEsiClient`). `Impl` suffixes and
  `I` prefixes are not used.
- The switch from class to interface does not change any caller: the class is renamed to its implementation name and
  moved to `internal`, and the interface is extracted under the old name, which callers keep injecting.
- Before extracting an interface, check whether `@MockitoBean` on the concrete class is enough. A mock is enough when
  a test only needs stubbed answers. An interface pays off when the fake has behavior of its own, such as replaying
  recorded ESI responses with their `ETag` and `Expires` headers, or simulating a 429.

## Inside a module

Two kinds of module, with different amounts of structure:

**Modules with real domain logic** get hexagonal layering: `industry` (costing, plans, invention, job matching) and
`market` (prices from the user's share of volume, shopping lists). Here:

- `domain` holds the model and the calculations, as plain Java with no Spring, JPA or Jackson annotations. jMolecules
  annotations are allowed, because they only describe the code and do nothing at run time. It is unit tested without
  a Spring context.
- `application` holds the use cases. It calls the domain and talks to the outside through ports: interfaces it owns,
  such as a plan repository.
- `persistence` and `web` are adapters that implement or call those ports.

**Thin modules** keep a plain controller, service and repository: `identity`, `alerts`, `sde`, and `groups` in phase 2.
Mapping between a domain model, rows and DTOs costs code in every change, and a module that mostly stores and
shows rows gets nothing back for it. If one of them grows real rules, it moves to the layered form.

The `characters` and `esi` modules sit between the two. They have rules that matter (token refresh, cache, rate and
error limits) but little domain model. They keep their rules in classes that do not depend on Spring's HTTP client, so
those rules can be tested without calls to ESI.

### Persistence

Status: **agreed**. The developer decided that every module uses `JdbcClient`, with hand-written SQL. `App/` does not use
JPA or Spring Data JDBC.

- **Reads** map to records with `.query(PlanOverview.class)`, which matches snake_case columns to record fields and
  converts a `UUID` to a typed id such as `PlanId`. A hand-written mapper is needed only where the record does not line
  up with the columns, such as a parent with its children.
- **Layered modules** keep the SQL and the mapping in `internal/persistence`, behind the application layer's port. Domain
  types stay plain and have no persistence annotations.
- **Saving an aggregate** updates the parent, upserts its children by a stable key, and deletes the children no longer
  present, in one transaction. Rows that other tables reference therefore keep their ids and are not deleted by an edit.
- **Optimistic locking** is `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?` on the parent, on every
  save, including one that only changes children. Zero rows updated means the aggregate has changed since it was loaded.
- **Integration tests against Postgres** cover each repository, because SQL mistakes do not show up at compile time.

Why not the others, from a comparison the developer asked for and a throwaway test on Boot 4.1.2 and Postgres 18:

- **JPA**: the domain must stay free of it, so entities need two-way mapping, and change tracking then only helps if the
  child collections are reconciled by hand. Bulk ESI writes would bypass it anyway.
- **Spring Data JDBC**: it can map a plain domain using only jMolecules' `@Identity`, and it saves the upsert and delete
  code. But with no `@Table` or `@Column` in the domain, renaming a field or class changes the schema names it expects,
  and only a test catches that. Each save deletes and reinserts every child row, so a foreign key to a child with
  `ON DELETE CASCADE` loses its rows silently, and those keys must be deferred instead. Child ids cannot be `GENERATED
  ALWAYS`. When a save inserts new children, the object `save()` returns has them in the wrong order, and saving that
  object again stores the wrong order. Direct SQL would still be needed for bulk writes and list queries, so it would add
  a second tool rather than replace one.

The cost is about 20 lines (an estimate) of upsert and delete per child table, the code that joins parents to children on
load, and several places to edit when a column changes. Plans should have few child tables, because they mostly store the
user's decisions and the material tree comes from the SDE. If this code becomes a burden, jOOQ or Spring Data JDBC can
replace it inside `internal/persistence` without changing the port or the domain.

## ESI as a boundary

The `esi` module is itself the adapter to ESI (see [The ESI client](esi.md#the-esi-client)). Feature modules call its
public API, typed per resource, and do not define their own ports for ESI as well. Two layers of interfaces for the
same call would be ceremony without benefit.

What this gives tests:

- A feature module's tests (`@ApplicationModuleTest`) replace the `esi` module's API with a fake that returns recorded
  responses. Most tests then need no network, and no `esi-live` tag.
- The `esi` module's own tests check its client against recorded responses, and a few tests tagged `esi-live` check it
  against the real ESI, as the prototype does today.

## How modules talk

- **Queries** call another module's public service directly, and get plain objects back.
- **Facts that other modules react to** are published as application events, for example a fetched industry job that
  has finished, which `alerts` turns into an alert, or a character that needs re-authorization. Listeners use
  `@ApplicationModuleListener`, so they run after the publishing transaction commits, in their own transaction.
- An event states something that has happened. The module that publishes it needs no answer. When it needs one, it
  makes a call instead.
- **An event goes through Modulith's event publication registry when losing it would leave stored data wrong, or
  leave something the user should know about without a notice.** The registry stores the event in Postgres in the
  publishing transaction, so it is delivered again after a crash. Events whose loss costs nothing, such as fetch
  progress for the SSE stream, do not use it. This matches the rule that queued fetch tasks themselves are kept in
  memory only (see [Background fetches](esi.md#background-fetches)).
- Events carry ids and the few values listeners need, not entities.

### Events

Examples from the requirements and architecture so far. The names and the list are settled when each feature is
designed.

| Event | Published by | Reacted to by | Registry |
|---|---|---|---|
| `IndustryJobsFetched(characterId)` | `industry`, after storing fetched jobs | `alerts` sets or updates job-finish timers from the stored end times | Yes: without it, a finished job raises no alert |
| `CharacterNeedsReauthorization(characterId, missingScopes)` | `characters`, when a refresh fails or a turned-on feature needs scopes the token lacks | `alerts` shows the banner, and fetches skip the character | Yes |
| `CharacterLinked(characterId)` | `characters` | `industry` and `market` queue the first fetches | No: the next page or refresh queues them anyway |
| `CharacterUnlinked(characterId)` | `characters` | `industry` and `market` remove that character's data | Yes: the data must be removed |
| `SdeVersionImported(version)` | `sde`, after the staging schema is swapped in | `industry` flags saved plans that use a changed blueprint, and the user re-costs them. No open page is refreshed, because imports run during an outage (see [SDE import](data.md#sde-import-and-versioning)) | Yes |
| `ContractExpiring(contractId)` | `market`, from contract timers | `alerts` | Yes |
| `FetchProgressed(accountId, done, total)` | fetch tasks | the SSE progress bar under the header | No: a lost event only skips a progress update |
| `AccountDeleted(accountId)` | `identity` | every module that owns rows deletes them | Yes: a privacy requirement |
| `MemberLeftGroup(groupId, accountId)` (phase 2) | `groups` | modules drop that account's access to shared data | Yes |

`CharacterNeedsReauthorization` and `ContractExpiring` do not follow the past-tense rule in [Naming](#naming) yet.

## Verification

Status: **agreed**. The developer decided to begin as EveAssist means to go on: ArchUnit and jMolecules are in the
build from the first commit of `App/`, not added later. Rules written while there is no code to break them cost
almost nothing, and they never need ArchUnit's frozen-violation baseline (`FreezingArchRule`).

- **Modulith**: one test runs `ApplicationModules.of(EveAssistApplication.class).verify()`, so a broken boundary or a
  cycle fails `./mvnw verify`.
- **jMolecules, where it describes the code**:
  - Architecture: a layered module marks its adapters' packages (`@PrimaryAdapter`, `@SecondaryAdapter`) and its ports'
    classes (`@PrimaryPort` on use cases, `@SecondaryPort` on repositories), checked with
    `JMoleculesArchitectureRules.ensureHexagonal()`. Domain types and plain records stay unmarked. At `STRICT`, ports
    and adapters may reach only ports and unmarked types, so marking `domain` as `@Application` would refuse a
    repository that returns a `Plan`.
  - DDD: domain types are marked `@AggregateRoot`, `@Entity`, `@ValueObject` and `@Identity`, checked with
    `JMoleculesDddRules.all()`, for example that one aggregate refers to another by id only.
  - API types: events are marked `@DomainEvent` and commands `@Command` (from jMolecules' CQRS module), so the names in
    [Naming](#naming) and the stereotypes agree.
  - Not used: jMolecules' ByteBuddy plugin, which generates JPA and Jackson code from the annotations at build time.
    `App/` does not use JPA (see [Persistence](#persistence)), and generated code is
    harder to follow when something goes wrong.
- **EveAssist's own ArchUnit rules**, in the same test run:
  - `domain` packages do not depend on `org.springframework`, `jakarta.persistence`, `com.fasterxml.jackson` or
    `tools.jackson` (Jackson 3, the default in Spring Boot 4).
  - Only the `esi` and `characters` modules use an HTTP client (ESI, and EVE SSO for character linking).
  - Data types and events in a module's base package are records. No class name ends in `Dto` or `Impl`, and no
    interface name starts with `I` followed by a capital letter.
  - The facade rule from [Facade and visibility](#facade-and-visibility), if that section stands.
  - The viewer rules from [Pages, the API and the facade](#pages-the-api-and-the-facade). One covers every class in
    `internal/application` that holds use cases, however it is annotated. The other covers every public repository
    method in `industry` for now, with no exceptions (see [Work with no signed-in person](#work-with-no-signed-in-person)).
    Thin modules such as `identity` are outside it: finding an account by its Keycloak id happens before there is a viewer.
  - The fetch rules from [What starts an ESI fetch](#what-starts-an-esi-fetch).
  - Each rule runs against fixtures that break it on purpose and fixtures that follow it (`archfixture` in the test
    sources, outside the application's base package), because a rule that cannot fail proves nothing.
- Modulith's documenter writes the module diagram (C4 or PlantUML) and a page per module during the build.
- **`verify()` and the jMolecules rules**: checked against Modulith 2.1 and `jmolecules-archunit` 0.33, whose rule and
  annotation names match the ones above. With `jmolecules-archunit` on the class path, plain `verify()` already runs
  `JMoleculesDddRules.all()` and `ensureHexagonal` at `LENIENT` depth. `ensureHexagonal()` called directly means
  `STRICT`. To run each rule once and at `STRICT`, the test passes both rules to
  `verify(VerificationOptions.defaults().withAdditionalVerifications(...))`, which replaces the defaults.

## Open questions

- **Needs validation**: [Facade and visibility](#facade-and-visibility) and [Service interfaces](#service-interfaces).
  The developer wants time to think about both, including how they meet the testing problems the developer has had
  with non-public methods.
- ~~How [Pages, the API and the facade](#pages-the-api-and-the-facade) applies to thin modules.~~ Settled by the first
  slice: a thin module's controllers call an internal service and it has no facade. It gains one only when another
  module needs it, as `market`'s `MarketService` does for `industry`. `alerts` is not built until job tracking or
  contracts need it.
- Whether `market` needs the layered form from the start, or begins thin and moves to it once its price rules are
  built.
- Whether the generated module diagrams are committed under `documentation/architecture/`, or only produced by the
  build.
- Past-tense names for `CharacterNeedsReauthorization` and `ContractExpiring` (see [Events](#events)).
