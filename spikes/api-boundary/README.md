# Spike: where htmx pages and the external API meet a module

Throwaway code comparing two options for `App/`. It is not a basis for `App/`. The developer chose option C. The options
come from the discussion of htmx and API granularity, and answer a contradiction in the docs: `api.md` says pages and
the API call the same services, while `code-structure.md` puts page controllers in `internal/web` and forbids them from
calling the facade, which is where visibility is checked.

- **Option A** (`option-a/`, port 7601): everything calls the facade, which checks visibility.
- **Option C** (`option-c/`, port 7602): pages call use cases directly, the API calls a small facade, and use cases
  check visibility.

Both have two screens (plan list, plan editor with a quantity change that returns the changed row and the total) and
three read-only API endpoints (`/api/v1/plans`, `/{id}`, `/{id}/costing`, with ETags). Left out, because they do not
differ between the options: Keycloak, Postgres, ESI, styling, UI text in `messages.properties`, personal access tokens
and rate limits. The viewer is faked with an `X-Viewer` header (default `alice`; `bob` owns the third plan).

## Run it

Run from this directory, with Java 25:

```bash
./mvnw verify
```

```bash
java -jar option-c/target/option-c-0.0.1-SNAPSHOT.jar
```

Then open http://localhost:7602/plans (use `option-a` and 7601 for A).

## Results

`./mvnw verify` passes on both. `BehaviorTest` is the same source in both, and all 9 of its tests pass on each, so the
options look the same from outside. The API module, its response types, the domain, the repository and the templates
are also the same source in both.

| Measure | A | C |
|---|---|---|
| Public methods on the facade | 6 | 3 |
| Public types in `industry` | 11 | 6 |
| Screen types visible to other modules | Yes, tested | No, tested |
| Lines of main Java | 619 | 603 |
| A new screen method that skips visibility and has no test | **Build passes** | **Build fails** (2 rules) |

The last row was tried on a temporary copy, so that code is not here. The two rules in C are tested against fixtures
that break them on purpose.

## What it showed

1. **The two granularities really do differ, even at this size.** The list screen wanted totals on each row, which the
   API keeps in its costing resource, so `PlanSummary` could not serve both. The editor wanted a change result
   (`PlanChange`) the API has no use for. Half of A's facade is methods only screens use.
2. **C's main safety does not come from C.** It comes from `VisiblePlans`, the one class allowed to load plans, so a
   plan cannot be loaded without saying who is asking. A could use the same class, its facade would then pass the
   viewer down rather than check, and it would get the same build failure. A as `code-structure.md` describes it has
   no such protection, but the fix is available to both.
3. **The difference left between the options is surface.** With `VisiblePlans` in either, the choice comes down to
   whether screen methods and types are public to other modules (A) or hidden (C).
4. **C's facade is pure pass-through.** Its three methods only delegate. That is what the facade is for in C, but it
   does mean one more layer on the API path that does nothing today.
5. **C makes the page path shorter than the API path.** A's page path has one more hop, and all callers look alike. In
   C, a reader has to know that pages and the API enter at different levels.
6. **The rules have a limit.** They prove `VisiblePlans` is used, not that it is right, so it still needs its own tests.
   The behavior tests cover it here.

## What it cannot show

- How each option holds up over dozens of screens. Two screens only hint at facade growth.
- Screens that span modules. Neither option has one, and C needs a small extra module for them.
- htmx in a real browser. The fragment was checked by test and by `curl`, not by swapping it in a page: no browser was
  available when the spike was built. Both options serve the same template, so this does not affect the comparison.

## Thymeleaf note

The row fragment first lived in `editor.html`, named `row(planId, row)`, used with `th:each` and `th:replace` on the
same `<tr>`, and with the fragments inside `th:remove="all"`. Its `row` parameter arrived null. Moving the fragments to
their own file, renaming them and putting `th:each` on a wrapping `th:block` fixed it. Which of those changes was the
fix was not isolated. The tests that render the templates caught it, as the UI section of the architecture README
predicts.
