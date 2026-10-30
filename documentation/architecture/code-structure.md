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
            web/               ← htmx controllers and templates' models
            persistence/
    market/
        ...
    esi/
        ...
```

- The module's base package is its public API: services, the plain objects they take and return, and its events.
- The plain objects and events are Java records: immutable snapshots with no business logic (see [Records](#records)).
  The service maps its domain model or JPA entities to them at the module boundary, so other modules never hold an
  entity or reach a lazy-loaded field. A service is a concrete class, and becomes an interface only for the reasons
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

### Pages, the API and the facade

Status: **proposed**. The developer chose this option after comparing it with one where everything calls the facade, in
a spike ([`spikes/api-boundary`](../../spikes/api-boundary/README.md)).

Pages and the external API want different things. A page wants what one screen shows, such as a plan list with totals,
or the changed rows and new total after an edit. The API wants stable resources for clients EveAssist does not know.
They therefore meet at the use cases, not at the controllers or the facade:

- **Page controllers** in `internal/web` call use cases in `internal/application` directly.
- **The `api` module and other modules** call the facade, which delegates to the same use cases.
- **Screen-only types and queries**, such as `PlanEditorView` and `PlanChange`, stay under `internal`, so a screen change
  cannot widen the module's API or change the JSON contract.
- **Every public use case method takes the `Viewer` as its first parameter.**
- **Records load only through a viewer-checking class**, for example `VisiblePlans`, which is the only class allowed to
  use `PlanRepository`. Having the viewer is not enough, because a use case could ignore it. This rule is what makes
  the build fail when a new use case skips the check.

ArchUnit enforces the last two rules (see [Verification](#verification)). They prove that the class is used, not that it
is right, so it has tests of its own.

The costs: the facade only passes calls through for now, a reader has to know that pages and the API enter at different
levels, and a screen spanning several modules needs a small module of its own, which the spike did not try.

## Naming

Spring Modulith sets no naming convention for these types. The ones below follow DDD and jMolecules practice: names
that cross a module boundary say what the data means, and technical suffixes stay inside `internal`.

| Kind | Convention | Example | Lives in |
|---|---|---|---|
| Query result in a module's API | Domain noun and role (`Summary` for list rows, `Details` for one item in full) | `PlanSummary`, `PlanDetails`, `JobCosting` | Module base package |
| Command input to a module's API | Verb phrase and `Command` | `CreatePlanCommand`, `ConfirmJobMatchCommand` | Module base package |
| Event | Past tense, no `Event` suffix | `JobFinished`, `CharacterUnlinked` | Module base package |
| Id | Noun and `Id`, a record around the UUID | `PlanId`, `CharacterId` | Module base package |
| Form backing object for a Thymeleaf page | Noun and `Form` | `PlanForm` | `internal/web` |
| JSON of the external API (later) | Noun and `Response`; inputs are a verb phrase and `Request` | `PlanResponse`, `CreatePlanRequest` | `api` module |
| Domain model | Plain noun | `Plan` | `internal/domain` |
| JPA entity, where it differs from the domain model | Noun and `Entity` | `PlanEntity` | `internal/persistence` |

- `Dto` is not used as a suffix. It names the mechanism, not the meaning.
- `Request` and `Response` are kept for HTTP, in the `api` module. A module's command says what it asks the module to
  change, so it is a `Command`, and the CQRS module of jMolecules can mark it with `@Command`. Queries and commands then
  read alike: `PlanDetails getPlan(PlanId)` and `PlanId createPlan(CreatePlanCommand)`.
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
Mapping between a domain model, JPA entities and DTOs costs code in every change, and a module that mostly stores and
shows rows gets nothing back for it. If one of them grows real rules, it moves to the layered form.

The `characters` and `esi` modules sit between the two. They have rules that matter (token refresh, cache, rate and
error limits) but little domain model. They keep their rules in classes that do not depend on Spring's HTTP client, so
those rules can be tested without calls to ESI.

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
| `SdeVersionImported(version)` | `sde`, after the staging schema is swapped in | `industry` marks plans costed with an older SDE version for re-costing | Yes |
| `JitaPricesUpdated(typeIds)` | `market` | `industry` re-costs open plans that use those items, and the SSE stream refreshes the page | No: prices are fetched again on demand |
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
  - Architecture: the layered modules (`industry`, `market`) mark their packages with the hexagonal annotations
    (`@Port`, `@Adapter` and their primary and secondary forms), checked with
    `JMoleculesArchitectureRules.ensureHexagonal()`.
  - DDD: domain types are marked `@AggregateRoot`, `@Entity`, `@ValueObject` and `@Identity`, checked with
    `JMoleculesDddRules.all()`, for example that one aggregate refers to another by id only.
  - API types: events are marked `@DomainEvent` and commands `@Command` (from jMolecules' CQRS module), so the names in
    [Naming](#naming) and the stereotypes agree.
  - Not used: jMolecules' ByteBuddy plugin, which generates JPA and Jackson code from the annotations at build time.
    The domain is mapped to JPA entities by hand (see [Inside a module](#inside-a-module)), and generated code is
    harder to follow when something goes wrong.
- **EveAssist's own ArchUnit rules**, in the same test run:
  - `domain` packages do not depend on `org.springframework`, `jakarta.persistence` or `com.fasterxml.jackson`.
  - Only the `esi` and `characters` modules use an HTTP client (ESI, and EVE SSO for character linking).
  - Data types and events in a module's base package are records. No class name ends in `Dto` or `Impl`, and no
    interface name starts with `I` followed by a capital letter.
  - The facade rule from [Facade and visibility](#facade-and-visibility), if that section stands.
  - The viewer and loading rules from [Pages, the API and the facade](#pages-the-api-and-the-facade), each tested against
    a fixture that breaks it on purpose.
- Modulith's documenter writes the module diagram (C4 or PlantUML) and a page per module during the build.
- When the build is set up, check whether Modulith's `verify()` already runs the jMolecules rules (it does when
  `jmolecules-archunit` is on the class path, in recent versions), so that they are not run twice.

## Open questions

- **Needs validation**: [Facade and visibility](#facade-and-visibility) and [Service interfaces](#service-interfaces).
  The developer wants time to think about both, including how they meet the testing problems the developer has had
  with non-public methods.
- Where screen view models live: `internal/application` (the spike's choice, simple, but use cases then know about
  screens) or `internal/web` (cleaner, but needs a mapping step and a richer use case result).
- Whether a command stays under `internal` until another module or an API write needs it, as in the spike, or always
  lives in the base package, as [Naming](#naming) says now.
- How [Pages, the API and the facade](#pages-the-api-and-the-facade) applies to thin modules, whose controllers call a
  plain service. If that service is also the facade, it breaks the rule that nothing inside a module calls its facade.
- Whether `market` needs the layered form from the start, or begins thin and moves to it once its price rules are
  built.
- Whether the generated module diagrams are committed under `documentation/architecture/`, or only produced by the
  build.
- Past-tense names for `CharacterNeedsReauthorization` and `ContractExpiring` (see [Events](#events)).
