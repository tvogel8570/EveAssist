# Market analysis

Status: draft, interview in progress (first three rounds done 2026-11-26).

**Market analysis** ranks items by how profitable they would be to build, so that the user can decide what to make. It
evaluates many items at once, where a [plan](manufacturing-invention.md#plans) or a [project](projects.md) costs work
the user has already chosen.

This replaces the sketch under [Profit ranking](manufacturing-invention.md#profit-ranking). The developer chose it as
the next feature on 2026-11-26, before project tracking.

## Scope

Decided with the developer on 2026-11-26.

- **Which items.** The goal is three ways of choosing, switchable: **chosen groups**, **everything buildable**, and
  **the user's own blueprints**. **The first version has chosen groups only.**
- **Choosing groups.** Branches of **the game's market tree** (as in the game's market window, for example Ships ›
  Frigates). A branch includes everything below it. An item with no market group is not offered.
- **Kinds of item.** What is built directly from a blueprint, and **Tech II roughly** (see [Tech II](#tech-ii-roughly)).
- **Saved.** The user keeps **named analyses**, each with its groups and settings, for example *Frigates* and
  *Ammunition*. The **last result** of each is kept with its date.

## How an item is costed

- **Settings start from the user's [project defaults](projects.md#project-defaults)** (facility, builder and skills,
  hauling), and **each named analysis can change them**.
- **ME and TE are defaults by tech level**, not the user's own blueprints.
- **Parts: a switch** per analysis between **buy**, **build** and **cheapest** (for each part, whichever costs less).
- Job install costs follow the same rules as projects ([Costing components](costing-detail.md)).

### Tech II, roughly

The user types a **blueprint cost per run for each group**. There is no invention calculation and no contract price in
this. A result that rests on a typed cost is **marked as rough**.

## What it would sell for

- The user **chooses the selling basis**, and **the fees follow it**:

  | Basis | Revenue at | Fees |
  |---|---|---|
  | Sell order | Jita sell | broker fee and sales tax |
  | Sell to buy orders | Jita buy | sales tax only |
  | Contract | to decide | none |

- The fee percentages are **typed**. Working them out from a character's skills and standings
  ([Fees](market-logistics.md#fees)) is later.

## The ranking

- Ranked **by margin %** first, with **daily sales volume shown** as a column and a **minimum-volume filter**.
- **This changes** the earlier requirement, which ranked by profit per day capped at a share of the daily volume
  ([Demand check](market-logistics.md#market-features)).
- **Calculated on demand.** Market data is collected in the background, a ranking is worked out when the user asks, and
  it shows how old its data is.

## From a row of the result

- **Open the breakdown**: materials, job costs, fees, the prices used and the warnings, so the user can check why the
  item ranks where it does.
- **Start a project** for the item, with the analysis's settings copied. Today a project starts from a plan, so this is a
  second way to start one.
- **Export CSV** of the whole ranking.

Adding an item to a plan from the result was not asked for.

## Later

- Everything buildable, and the user's own blueprints with their real ME and TE (needs `industry` to read the blueprints
  that `inventory` holds, the same gap as [projects](projects.md) step 5).
- Full invention for Tech II, and Tech III.
- Fees from a character.
- Past results of an analysis, to see how margins change.

## Facts that shape the design

Measured on 2026-11-26. They will change.

- The SDE loaded in development has **4,054** published products with a published manufacturing blueprint.
- ESI's `market-order` limit is **12,000 tokens per 15 minutes** for each IP address, and a successful request costs 2.
  The Forge's **whole order book** was **420 pages**, so 840 tokens. Asking item by item for thousands of items would
  use the whole allowance or more.
- Sales history is one request per item. No rate limit was found for it in ESI's OpenAPI spec or in the developer
  documentation. **To be measured.**

## Privacy

Named analyses are new data held for an account. They belong in the account export and the privacy statement, which do
not yet cover projects either ([#70](https://github.com/tvogel8570/EveAssist/issues/70),
[#63](https://github.com/tvogel8570/EveAssist/issues/63)).

## Open questions

1. **ME and TE per tech level:** which levels get their own values, and what are they? Suggested: Tech I ME 10 / TE 20,
   Tech II ME 2 / TE 4 (an invented copy's base). Do capitals differ from other Tech I?
2. **Tech III:** is it left out of the first version? Only Tech II was asked to be included roughly.
3. **Typed Tech II cost:** which groups does a cost apply to (the chosen market branches, or their sub-groups), and
   what happens to a Tech II item in a group with no cost typed?
4. **Tech II materials:** are the datacores and other invention inputs wholly inside the typed cost per run?
5. **Contract basis:** at which price is the revenue (Jita sell, buy or split)?
6. **Material price basis:** does it follow the project default (sell, buy or split), separately from the selling basis?
7. **Parts switch:** which is the default? Does *cheapest* compare the market price with materials and job fees only, or
   include hauling and time value?
8. **Volume:** over how many days is daily volume averaged (30 before)? Is the minimum volume off by default? Is the
   share cap (10%) still used anywhere?
9. **Misleading prices:** how are thin markets and joke orders handled beyond the volume filter?
10. **Missing prices:** is an item with an unpriced material left out, or shown without a margin and with a warning?
11. **Margin:** is it on the selling price, as projects define it? Which other columns (profit per unit, cost, build
    time, volume in ISK)?
12. **Size:** how many items may one analysis hold?
