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
- The plain objects and events are Java records: immutable snapshots with no behavior. The service maps its domain
  model or JPA entities to them at the module boundary, so other modules never hold an entity or reach a lazy-loaded
  field. A service is a concrete class, and becomes an interface only when tests need a fake for it, as with the
  `esi` module's API (see [ESI as a boundary](#esi-as-a-boundary)).
- Everything under `internal` is hidden from other modules. Modulith's test fails if another module reaches into it.
- Where a module needs to expose more than its base package, it declares a named interface (`@NamedInterface`) rather
  than making classes public by accident.
- Modules depend only on modules they declare (`@ApplicationModule(allowedDependencies = ...)`), so a new dependency
  is a visible change in review.

### Facade and visibility

Status: **proposed, needs validation**. The developer wants time to think about it before it stands.

Java's access levels and Modulith's boundaries do not line up. Package-private does not reach subpackages, so if code
in `industry/internal/...` calls a method of a class in `industry`, that method must be `public`, and every other
module can call it too. Modulith checks which types a module uses, not which methods, so it does not notice. The other
way round is fine: classes in `internal` may be `public`, with public methods, because Modulith hides the whole
`internal` tree from other modules. Inside `internal`, Modulith is the access control, not the `public` keyword.

What follows:

- **One thin facade per module** in the base package, for example `IndustryService`. It has only the methods other
  modules may call. Each checks visibility, delegates to a use case in `internal/application`, and maps the result to
  a record. It has no helpers, and nothing inside its own module calls it.
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

## Naming

Spring Modulith sets no naming convention for these types. The ones below follow DDD and jMolecules practice: names
that cross a module boundary say what the data means, and technical suffixes stay inside `internal`.

| Kind | Convention | Example | Lives in |
|---|---|---|---|
| Query result in a module's API | Domain noun and role (`Summary` for list rows, `Details` for one item in full) | `PlanSummary`, `PlanDetails`, `JobCosting` | Module base package |
| Command input to a module's API | Verb phrase and `Command` | `CreatePlanCommand`, `ConfirmJobMatchCommand` | Module base package |
| Event | Past tense, no `Event` suffix | `JobFinished`, `CharacterNeedsReauthorization` | Module base package |
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

When a service becomes an interface because tests need a fake, the interface keeps the role name, and each
implementation is named for how it works:

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
- Events that must not be lost (those that create alerts) go through Modulith's event publication registry, stored in
  Postgres, so they are delivered again after a crash. Events whose loss costs nothing, such as fetch progress for the
  SSE stream, do not need it. This matches the rule that queued fetch tasks themselves are kept in memory only (see
  [Background fetches](esi.md#background-fetches)).
- Events carry ids and the few values listeners need, not entities.

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
- Modulith's documenter writes the module diagram (C4 or PlantUML) and a page per module during the build.
- When the build is set up, check whether Modulith's `verify()` already runs the jMolecules rules (it does when
  `jmolecules-archunit` is on the class path, in recent versions), so that they are not run twice.

## Open questions

- **Needs validation**: [Facade and visibility](#facade-and-visibility) and [Service interfaces](#service-interfaces).
  The developer wants time to think about both, including how they meet the testing problems the developer has had
  with non-public methods.

- Whether `market` needs the layered form from the start, or begins thin and moves to it once its price rules are
  built.
- Whether the generated module diagrams are committed under `documentation/architecture/`, or only produced by the
  build.
- Which events need the event publication registry, beyond those that create alerts.
