# Market analysis

Status: draft, interview in progress (first seven rounds done 2026-11-26).

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
- **ME and TE are defaults by kind of item**, not the user's own blueprints:

  | Kind | ME | TE |
  |---|---|---|
  | Tech I | 10 | 20 |
  | Tech II (an invented copy's base) | 2 | 4 |
  | Capital ships, **including freighters, jump freighters and industrial capitals** | 8 | 8 |
  | Capital parts | 10 | 10 |
- **Parts: a switch** per analysis between **buy**, **build** and **cheapest** (for each part, whichever costs less).
  **Cheapest is the default.** It compares **materials and job fees** only, **not hauling or time value**.
- **An item's cost includes hauling, not time value.** The breakdown shows hauling, so the user can see where leaving it
  out of the *cheapest* comparison matters.
- **Material price basis** (sell, buy or split) **starts from the project defaults** and is set **separately from the
  selling basis**.
- **A missing material price:** the item is **shown without a margin**, **names the unpriced material** and **sorts to the
  bottom**.
- Job install costs follow the same rules as projects ([Costing components](costing-detail.md)).

### Tech II, roughly

The user types a **blueprint cost per run** for **each chosen market branch**, and **a sub-group can override it**. It
**covers the datacores and the other invention inputs**, so nothing is added for them. There is no invention calculation
and no contract price in this. A result that rests on a typed cost is **marked as rough**. A Tech II item **with no cost
typed is shown without a margin**.

## What it would sell for

- The user **chooses the selling basis**, and **the fees follow it**:

  | Basis | Revenue at | Fees |
  |---|---|---|
  | Sell order | Jita sell | broker fee and sales tax |
  | Sell to buy orders | Jita buy | sales tax only |
  | Contract | Jita split | none |

- The fee percentages are **typed**. Working them out from a character's skills and standings
  ([Fees](market-logistics.md#fees)) is later.

## The ranking

- Ranked **by margin %** first, with **daily sales volume shown** as a column and a **minimum-volume filter**. Daily volume is
  **averaged over 15 days**.
- **Minimum volume, two filters, both adjustable** (the defaults come from [measuring real data](#minimum-volume)): an
  item must have **traded on at least 10 of the 15 days** and **at least 10M ISK a day**.
- **A warning** on an item whose sell price is **1.3 times its traded average or more**.
- **Tech I items whose blueprint cannot be bought are hidden**, and **a switch shows them, marked**. A blueprint counts
  as one that cannot be bought only if it **has no market group and no public contract offers it**
  ([checked](#checking-the-blueprint-rule)). The rule does not apply to Tech II.
- **A Tech I blueprint with no market group that contracts do offer** is charged at its
  [contract price per run](market-logistics.md#contract-prices), and the result is **marked as rough**.
- **No cap by share of volume.** The earlier 10% share is dropped for now.

### Columns

Margin % (on the selling price, as [projects](projects.md) define it), profit per unit, cost per unit, units traded a
day, ISK traded a day, days traded, build time and warnings.
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
- Full invention for Tech II.
- Tech III. It is left out of the first version.
- Fees from a character.
- Past results of an analysis, to see how margins change.

## Facts that shape the design

Measured on 2026-11-26. They will change.

- The SDE loaded in development has **4,054** published products with a published manufacturing blueprint.
- ESI's `market-order` limit is **12,000 tokens per 15 minutes** for each IP address, and a successful request costs 2.
  The Forge's **whole order book** was **420 pages**, so 840 tokens. Asking item by item for thousands of items would
  use the whole allowance or more.
- Sales history is one request per item. No rate limit was found for it in ESI's OpenAPI spec or in the developer
  documentation. About 730 requests at roughly 7 a second drew no refusal, and the answers carried only the **error
  limit** headers. A higher rate was not tried.

## Minimum volume

Asked by the developer on 2026-11-26. Measured that day on real data: Tech I items with a market group, priced from a
snapshot of The Forge's sell orders, with sales over the 15 days to 2026-11-25. A day without a trade counts as zero.

**How it was measured, and its limits.** Margin was *(lowest Jita 4-4 sell price − materials at the lowest Jita 4-4 sell
price, ME 10, every part bought) / the sell price*, with **no job fees, sales fees, hauling or blueprint cost**. The app
prices differently (a volume-weighted share), so its figures will differ. One day's snapshot.

**1. A margin ranking needs a blueprint filter first.** Some blueprints are not sold on the market. Gnosis and Praxis
list **1 Tritanium** as materials, so they show a 100% margin. Of the 25 highest margins among all 1,630 priced items,
**23** had a blueprint with no market group (38 of the top 50). Some of these items trade heavily, so a volume minimum
does not remove them.

A blueprint's market group is only a rough sign of whether it can be bought:

- 219 of 1,718 Tech I products have a blueprint without one. How each of those blueprints is obtained was **not
  checked**, and some may be ordinary to get as copies.
- **It must not be applied to Tech II:** 872 of 940 Tech II products have a blueprint without a market group, because
  those blueprints are copies.

**2. Without a minimum, the top of the list is mostly unsellable.** Among those 1,441, in the **top 50** by margin:

| | Count of 50 |
|---|---|
| Traded on fewer than 10 of the 15 days | 38 |
| Sell price at least 1.3 times the traded average, or no sale at all | 32 |
| Under 1M ISK profit a day, even if every unit traded were theirs | 25 |

The median item of that top 50 sold **0.1 units a day**.

**3. What each minimum does** (a random 300 of the 1,441 for the share kept):

| Minimum | Share of all items kept | Of the top 100 by margin kept | Of the first 20 kept, under 1M ISK a day |
|---|---|---|---|
| None | 100% | 100 | 12 |
| Traded on ≥ 10 of 15 days | 72% | 32 | 1 |
| ≥ 10 units a day | 67% | 36 | 1 |
| ≥ 10M ISK traded a day | 67% | 40 | 0 |
| ≥ 50M ISK traded a day | 48% | 18 | 0 |
| ≥ 10 of 15 days **and** ≥ 10M ISK a day | 55% | 17 | 0 |

In that random sample the median item traded **54 units** and **48M ISK** a day; a quarter traded under 4 units or 4.7M ISK.

**Decided 2026-11-26:** the defaults in [The ranking](#the-ranking). Why they take this form:

- **ISK, not units.** A units minimum treats ammunition and ships alike: 10 units a day would drop a ship that sells 7.5 a
  day, about 4 billion ISK of trade a day.
- **Days traded** catches an item with one large sale in an otherwise dead market.
- **The warning** catches a margin that rests on a sell order nobody is paying.

## Checking the blueprint rule

Checked 2026-11-26 against the development database: public contracts of **The Forge only**, whose list ESI caps (see
[ESI](../architecture/esi.md)). Another day or region will differ.

Of the **219** Tech I products whose blueprint has no market group, contracts offered **113** blueprints (all as copies,
none as originals) and **106** not at all.

| Highest margins, from [the first measurement](#minimum-volume) | No market group | Hidden by the rule | Still shown |
|---|---|---|---|
| Top 25 | 23 | 20 | 3 |
| Top 50 | 38 | 27 | 11 |
| Top 100 | 56 | 35 | 21 |

**The rule alone does not do its job.** After the rule and both volume filters, **12 of the first 20** results by margin
have a blueprint found only on contracts, and **Gnosis is still first at 100%**. Its copies were offered at 700M ISK for
5 runs (140M a run), against a sell price of about 45M: the margin shown is wrong because **the blueprint's cost is not
counted**.

**Decided 2026-11-26:** such a blueprint is charged at its contract price per run.

**The copies on offer are poor ones.** Of the 113 blueprints, **109** were offered only at **ME 0**, 112 had a copy at
ME 0 / TE 0, and **1** had a copy at ME 10 / TE 20 or better. A contract price uses copies at least as good as the ME and
TE asked for, so asking at the [Tech I default](#how-an-item-is-costed) would find almost no price.

## Privacy

Named analyses are new data held for an account. They belong in the account export and the privacy statement, which do
not yet cover projects either ([#70](https://github.com/tvogel8570/EveAssist/issues/70),
[#63](https://github.com/tvogel8570/EveAssist/issues/63)).

## Open questions

1. **ME and TE of a contract blueprint:** the Tech I default of 10 / 20 finds almost no price
   ([measured](#checking-the-blueprint-rule)). Suggested: cost the item at the ME and TE of the copies that were priced.
2. **Kinds of item for ME and TE:** which groups are *capital parts* (capital construction components, and also the
   Tech II capital components)? Do faction, storyline and structure items use the Tech I values?
3. **Misleading prices:** is anything needed beyond the volume filters and the warning, for example for joke buy orders
   when selling to buy orders, or for material prices?
4. **Size:** how many items may one analysis hold? To be decided after measuring how long costing takes.
5. **Blueprints sold on the market** also cost something, which is not counted. Does it stay ignored?
