# UI Spikes

Throwaway code for choosing EveAssist's UI stack (see [the architecture's open questions](../../documentation/architecture/README.md#open-questions)).
The same plan-editor screen is built three ways, so the stacks can be compared by using them and reading their code.
None of this is product code, and it is deleted once the choice is made.

| Spike | Stack | Port |
|---|---|---|
| A | Thymeleaf + htmx, with Tailwind and daisyUI | http://localhost:8091 |
| B | React single-page app (Vite, TypeScript, Tailwind, daisyUI) over a JSON API | http://localhost:8092 |
| C | Vaadin Flow (UI written in Java), Lumo theme | http://localhost:8093 |

All three use `plan-core`, plain Java holding fake data (an Ishtar with a Vexor hull, T2 components, reaction products
and minerals), four facilities, and the costing. Only the UI layer differs. The numbers are invented.

## The screen

- Summary: total cost, value at Jita sell, profit and margin.
- Quantity of Ishtars to build, and a button that resets every choice.
- A material tree. Each intermediate has a Build or Buy toggle, and built items have a facility picker. Buy and build
  costs are shown side by side, with the cheaper one in green.
- Intermediates follow "build if cheaper" until you override them. An override shows a badge and how much it costs
  compared with the rule.
- Every change is recalculated on the server by `PlanCosting`, in all three spikes.

## The job board (A and B only)

Added after Vaadin was rejected, to test the kind of screen where React should be strongest. Open **Jobs** in the top
bar.

- Four columns: unassigned jobs, then three characters with their manufacturing slots and their skill time bonus.
- Drag a job onto a character. Its duration changes with that character's skills, and the slot count goes up.
- A character with no free slot refuses the job with a message, and the job stays where it was.
- The server owns the board (`JobBoard` in `plan-core`) and checks every move. Both spikes call it the same way.

What differs is what you see *while* dragging:

| | A. htmx + SortableJS | B. React + dnd-kit |
|---|---|---|
| Drop targets light up, a full character in red | Yes | Yes |
| The dragged card shows its duration for the character under the pointer | No: it shows the old duration until you drop | Yes |
| The slot badge previews the new count | No | Yes (and turns red past the limit) |
| After the drop | The server re-renders the board (one round trip before it settles) | The move shows at once; the server confirms it or the job jumps back |
| Code | 45 Java, 98 template, of which 37 are JavaScript | 46 Java, 164 TypeScript |

Mid-drag, the Magnetometric Sensor Cluster over Main Builder: [htmx](screenshots/jobs-htmx-mid-drag.jpg) and
[React](screenshots/jobs-react-mid-drag.jpg).

Notes from building and testing it:

- **A**: SortableJS's native drag mode does not react to automated tests, so it runs with `forceFallback`. SortableJS
  also keeps a card in the last column the pointer crossed, so dropping on a full column first landed the job in its
  neighbour with no message. The drop now uses the column under the pointer and lets the server decide. Both problems
  would have reached users without testing in a browser.
- **B**: the preview needs the duration rule (`base × (1 − bonus)`) and its formatting in TypeScript as well as in
  Java. The slot rule is also checked twice, in the browser for instant feedback and on the server for real.
- In both, a drop zone has to cover the whole column, not just its list of cards, or drops on the empty part are
  ignored.

## Running

Java 25 is needed. Maven, Node and npm are not: the Maven wrapper downloads Maven, and spike B downloads its own Node
into `react/node`.

```bash
./mvnw -DskipTests clean install
```

Then start each spike in its own terminal:

```bash
java -jar htmx/target/htmx-0.0.1-SNAPSHOT.jar
```

```bash
java -jar react/target/react-0.0.1-SNAPSHOT.jar
```

```bash
java -jar vaadin/target/vaadin-0.0.1-SNAPSHOT.jar
```

For live reloading while editing spike B, run `npm run dev` in `react/frontend` (with `react/node` on the `PATH`) and
open http://localhost:5173.

## What to try in each

1. Switch the Vexor to Buy, then back to Build. Watch the totals and the override badge.
2. Switch a component such as Fusion Thruster to Buy. Is the change instant or is there a delay?
3. Change a facility, and the quantity.
4. Narrow the browser window to phone width (dev tools, device toolbar). Is the tree still usable?
5. Open the code for the same feature in each spike, for example the Build/Buy toggle: `htmx/.../fragments/row.html`,
   `react/frontend/src/App.tsx` (`Rows`), `vaadin/.../PlanView.java` (`decisionCell`). Which could you change yourself?

## What was measured

| | A. Thymeleaf + htmx | B. React | C. Vaadin |
|---|---|---|---|
| UI code (non-blank lines) | 205: 80 Java, 125 HTML | 347: 67 Java, 280 TypeScript and config | 233 Java |
| Languages to read | Java, HTML with `th:` and `hx-` attributes | Java, TypeScript/JSX, npm and Vite config | Java only |
| Extra toolchain | None in the spike (CDN); Tailwind's npm build in a real app | Node (140 MB) and `node_modules` (98 MB) per checkout | None: Vaadin used its pre-built bundle, no Node was downloaded |
| API types written twice | No | Yes: `api.ts` repeats the Java records, and `isk()` repeats `Isk.java` | No |
| Runnable jar | 21 MB | 20 MB | 33 MB |
| Each change | One POST, returns the HTML of the editor | One POST, returns the plan as JSON, React re-renders | One round trip of events; the server updates the components |
| State held on the server | The plan, in the HTTP session | The plan, in the HTTP session | The plan and the whole component tree, per browser tab |

Notes from building them:

- **A**: two Thymeleaf traps came up. A recursive fragment must live in its own template, and `th:replace` runs before
  `th:each` on the same element, so the loop needs its own `th:block`. Both are fixed and commented in `row.html`.
- **B**: every change still goes to the server, because costing is in Java. Making the editor feel instant would mean
  writing the costing a second time in TypeScript, or optimistic updates. Node 26 needs `libatomic`, which this WSL
  machine lacks, so the spike uses Node 24 LTS.
- **C**: Vaadin 25.3.4 is built against Spring Boot 4.1.1, the same as the prototype's Api. Grid, TreeGrid, Select and
  IntegerField are in the free core. Collapsed rows have to be remembered by hand, because each recalculation creates
  new row objects.
