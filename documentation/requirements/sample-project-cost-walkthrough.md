# Sample project cost walkthrough: a Golem

Status: worked example, 2026-11-18. It follows one project through `ProjectCosting` from the plan to the quote, so the flow and
the logic can be read step by step. The rules are in [Projects](projects.md) and [Costing components](costing-detail.md).

**What is real and what is made up.** The numbers in this file were produced by running the real `ProjectCosting`,
`BlueprintSplitter` and `JobCosting` classes over real data, so they are what the code gives, not hand-made figures.

| Part | Source |
|---|---|
| Recipes, build times, volumes, required skills | The SDE in the dev database (loaded 2026-11-08) |
| Prices | ESI `GET /markets/prices` on 2026-11-18: `adjusted_price` for each job's estimated item value, and `average_price` as a **stand-in** for the Jita price. The app does not read prices this way. It prices from Jita orders, which is not built for projects yet, so every ISK figure below is illustrative |
| The structure, skills, rates | An Azbel in high-sec with no rigs and a builder with Industry and Advanced Industry at V, as in the readings of [costing-detail](costing-detail.md#5-readings-from-the-game). The cost index 2.01% is ESI's value for Dodenvale, rounded to four decimals as ESI gives it |
| **Made up** | The blueprints the developer "owns", the price of the Golem copy (20,000,000 ISK a run), the hauling rate (300 ISK/m³), the 15% margin, the 1,000 ISK quote step |

**Not read in the game.** The cost formulas were read in the game for Cap Booster 800, 10000MN Afterburner I, Multispectrum
Coating II and an Atron ([costing-detail](costing-detail.md#12-what-the-readings-in-the-game-settled)). Nothing here was compared with a Golem
job in the game. The project-level lines (hauling, blueprint cost, contract fee, margin) are the project's own rules, not the game's.

## The order

One **Golem** (a Caldari Marauder), delivered to a buyer by contract.

The Golem is built from a **Raven** hull and **seven component parts**, and it needs minerals and a few other materials as well.
The Raven is built from minerals and three intermediate items. Those three (Auto-Integrity Preservation Seal, Core
Temperature Regulator, Life Support Backup Unit) are **bought**, because their own materials come from reactions and
planetary industry that the first version does not model. Everything else with a recipe is **built**.

| What | Choice in this project |
|---|---|
| Product | 1 Golem |
| Built | Golem, Raven, Magpulse Thruster, Gravimetric Sensor Cluster, Quantum Microprocessor, Titanium Diborite Armor Plate, Graviton Reactor Unit, Scalar Capacitor Unit, Sustained Shield Emitter |
| Bought (has a recipe, marked buy) | Auto-Integrity Preservation Seal, Core Temperature Regulator, Life Support Backup Unit |
| Bought (no recipe) | minerals, Titanium Carbide, Sylramic Fibers, Ferrogel and the like |
| Golem blueprint | a copy, ME 2 / TE 4, 1 run, 20,000,000 ISK for its run |
| Raven and component blueprints | originals, ME 10 / TE 20, no depreciation |
| Facility | Azbel, high-sec, no rigs: materials −1%, time −20%, job fee −4%, cost index 2.01%, facility tax 0%, SCC surcharge 4% |
| Builder | Industry V and Advanced Industry V, no implant. The Golem also gets three time skills at V (Advanced Large Ship Construction, Caldari Starship Engineering, Mechanical Engineering, 1% a level each, see below), and each component gets its own one |
| Settings | time value 0, hauling 300 ISK/m³, contract fee 10,000 ISK, margin 15% on the price, quote rounded up to 1,000 ISK |

## The flow

```
products ──► walk the tree, parents first ──► for each built item:
                                                demand ──► runs ──► jobs (splitter) ──► JobCosting per job
                                                                      │                  materials, time, EIV, install cost
                                                                      └── materials are added to the demand of the items below
             items that are bought ──► price × demand, and the m³ to haul
             lines: materials + install + blueprint + time value + hauling + contract fee − surplus
             ──► delivered cost ──► quote (margin on the price, rounded up) and the market comparison
```

Two properties drive everything:

1. **Parents come first.** An item's demand is complete when it is reached, because everything that uses it was costed
   earlier. That is why a part's runs are known only after the jobs above it are costed.
2. **Each job rounds its own materials up.** An item's demand is the sum of what the jobs above it **used**, after their
   rounding, not the sum of the exact amounts.

## Step 1: the Golem job

**Which required skills count.** A skill that a blueprint requires shortens the time only if the SDE gives it
`manufactureTimePerLevel` (attribute 1982). Checked in the dev SDE on 2026-11-18: **Advanced Large Ship Construction (3398),
Caldari Starship Engineering (11454) and Mechanical Engineering (11452) each have attribute 1982 = −1**, so each takes 1% off
per level and all three count for the Golem. **Capital Ship Construction (22242) has no such attribute**, so a blueprint that
only requires it gets nothing from it (the game agreed in the 10000MN Afterburner reading). The Golem's
fourth required skill, Industry, is the job's base skill, which `BuilderTerms` already applies to every job. That the game really
applies these three to a Golem is not read, since only Nanite Engineering and Hydromagnetic Physics were.

The Golem's demand is 1, its recipe makes 1 a run, so it needs **1 run**. The splitter looks at the owned blueprints: the
one copy (ME 2 / TE 4, 1 run left). It gives one job of 1 run. A job is never longer than the 30-day limit, and one
run of this takes 1.9 days, so there is nothing to cut.

**Materials** (`JobCosting.materialQuantity`): base × runs × (1 − ME) × (1 − structure), rounded to two decimals half up and
then up, never below one per run. Here the factor is 0.98 × 0.99 = 0.9702.

| Material | Base for 1 run | × 0.9702 | Used |
|---|---|---|---|
| Titanium Diborite Armor Plate | 37,500 | 36,382.50 | **36,383** |
| Quantum Microprocessor | 6,000 | 5,821.20 | **5,822** |
| Sustained Shield Emitter | 3,795 | 3,681.91 | **3,682** |
| Scalar Capacitor Unit | 3,000 | 2,910.60 | **2,911** |
| Gravimetric Sensor Cluster | 863 | 837.28 | **838** |
| Graviton Reactor Unit | 675 | 654.89 | **655** |
| Magpulse Thruster | 375 | 363.83 | **364** |
| Morphite | 975 | 945.95 | **946** |
| Construction Blocks | 450 | 436.59 | **437** |
| R.A.M.- Starship Tech | 30 | 29.11 | **30** |
| Raven | 1 | 0.97 | **1** (the floor of one per run) |

**Time** (`JobCosting.seconds`): 360,000 s × (1 − TE 4%) × skills × (1 − structure 20%), where the skills multiply:
Industry 0.80 × Advanced Industry 0.85 × three time skills at 0.95 each. That is
`360,000 × 0.96 × 0.5830 × 0.80 = 161,191.9`, rounded to the nearest second: **161,192 s** (1 d 20 h 46 m 32 s).

**Estimated item value** is the base materials (ME 0) at ESI's adjusted prices: **800,744,904 ISK**. It does not change with ME.

**Install cost** = EIV × (index × (1 − 4%) + tax + SCC) = EIV × (2.01% × 0.96 + 0% + 4%) = 800,744,904 × 0.059296
= **47,480,970 ISK**.

**Blueprint cost:** 1 run × 20,000,000 = **20,000,000 ISK**.

## Step 2: the parts get their runs from the Golem's demand

Using the Golem's materials, the demand for each built part is now known, and each part is a job with as many runs as units
needed (each recipe makes one unit a run). Every part is made from the original at ME 10 / TE 20, so its factor is
0.9 × 0.99 = 0.891, and each has one time skill at V.

| Job | Runs | Time | EIV | Install cost | Uses (main materials) |
|---|---|---|---|---|---|
| Golem | 1 | 161,192 s | 800,744,904 | 47,480,970 | the parts above and Morphite, Construction Blocks, R.A.M. |
| Titanium Diborite Armor Plate | 36,383 | 376,055 s | 183,839,550 | 10,900,950 | Titanium Carbide 1,426,360; Sylramic Fibers 356,590 |
| Quantum Microprocessor | 5,822 | 180,529 s | 215,391,842 | 12,771,875 | Titanium Carbide 88,186; Phenolic Composites 31,125; Nanotransistors 10,375; Nonlinear Metamaterials 10,375 |
| Sustained Shield Emitter | 3,682 | 228,343 s | 32,723,507 | 1,940,373 | Titanium Carbide 72,175; Sylramic Fibers 29,526; Ferrogel 3,682 |
| Scalar Capacitor Unit | 2,911 | 180,529 s | 103,046,278 | 6,110,232 | Titanium Carbide 70,030; Fullerides 28,531; Nanotransistors 2,911; Nonlinear Metamaterials 5,188 |
| Gravimetric Sensor Cluster | 838 | 51,969 s | 7,368,570 | 436,927 | Titanium Carbide 16,427; Nanotransistors 838; Hypersynaptic Fibers 1,494 |
| Graviton Reactor Unit | 655 | 81,241 s | 22,960,631 | 1,361,474 | Titanium Carbide 5,253; Fermionic Condensates 1,168 |
| Magpulse Thruster | 364 | 22,574 s | 3,611,390 | 214,141 | Titanium Carbide 4,217; Phenolic Composites 973; Ferrogel 364 |
| Raven | 1 | 7,834 s | 109,095,739 | 6,468,941 | Tritanium 4,633,200; Pyerite 2,316,600; Mexallon 347,490; Isogen 115,830; Nocxium 13,900; Zydrine 3,475; Megacyte 1,738; and the three bought intermediates |

Nine jobs, one builder, so the times add up: **1,290,266 s, 14 d 22 h 24 m 26 s**. The **single longest job** is the armor
plates, **376,055 s (4 d 8 h 27 m 35 s)**, which is what the time would be at best with enough slots for every job to run at
once. It is a lower bound, since the Golem job cannot start before the parts it uses are made. None of these is near the 30-day limit
(the longest is the armor plates, 4.4 days), so the splitter makes one job each.

## Step 3: the bought items and where they add up

The leaves of the tree have no job. Their demand is the sum of what **every** job above them used, and they are priced at
the price basis (here `average_price`, a stand-in).

Titanium Carbide shows it: the seven component jobs used 1,426,360 + 88,186 + 72,175 + 70,030 + 16,427 + 5,253 + 4,217 =
**1,682,648**, which is the project's one purchase of it. Each of those amounts was rounded up inside its own job, so the
total is the sum of rounded numbers.

| Bought | Quantity | Unit price | Cost |
|---|---|---|---|
| Nonlinear Metamaterials | 15,563 | 11,751 | 182,882,214 |
| Titanium Carbide | 1,682,648 | 83 | 140,147,752 |
| Ferrogel | 4,046 | 26,251 | 106,210,373 |
| Sylramic Fibers | 386,116 | 216 | 83,242,748 |
| Fermionic Condensates | 1,168 | 46,421 | 54,219,459 |
| Nanotransistors | 14,124 | 3,455 | 48,799,832 |
| Pyerite | 2,316,600 | 17 | 38,525,058 |
| Phenolic Composites | 32,098 | 967 | 31,041,334 |
| Isogen | 115,830 | 191 | 22,143,221 |
| Mexallon | 347,490 | 52 | 18,215,426 |
| Fullerides | 28,531 | 643 | 18,357,416 |
| Tritanium | 4,633,200 | 4 | 18,023,148 |
| Morphite | 946 | 18,030 | 17,056,560 |
| Nocxium | 13,900 | 705 | 9,803,114 |
| Hypersynaptic Fibers | 1,494 | 5,997 | 8,958,920 |
| Auto-Integrity Preservation Seal | 134 | 50,565 | 6,775,675 |
| Life Support Backup Unit | 67 | 93,355 | 6,254,767 |
| Construction Blocks | 437 | 10,035 | 4,385,146 |
| Zydrine | 3,475 | 1,041 | 3,616,259 |
| Megacyte | 1,738 | 2,000 | 3,475,705 |
| Core Temperature Regulator | 1 | 3,251,103 | 3,251,103 |
| R.A.M.- Starship Tech | 30 | 585 | 17,543 |
| **Materials** | | | **825,402,773** |

## Step 4: the lines

| Line | Amount (ISK) | How |
|---|---|---|
| Materials | 825,402,773 | the table above |
| Install cost | 87,685,882 | the nine jobs' install costs added up |
| Blueprint | 20,000,000 | the Golem copy's 1 run; the originals add nothing |
| Time value | 0 | 1,290,266 s ÷ 3,600 × 0 ISK per hour (undecided, see [Projects](projects.md#open-questions)) |
| Hauling | 60,715,821 | (152,386 m³ bought + 50,000 m³ for the Golem, packaged) × 300 ISK/m³ |
| Contract fee | 10,000 | flat |
| Surplus credit | 0 | every recipe makes exactly the units needed, so nothing is left over |
| **Delivered cost** | **993,814,476** | the sum; per unit the same, since there is one product |

The Golem is hauled out at its **packaged** volume of 50,000 m³, not the assembled 470,000 m³. The surplus, if there were
any, would be credited and not hauled.

## Step 5: the quote

- **Cost plus margin** (the mode in use): 993,814,476 ÷ (1 − 0.15) = 1,169,193,501, rounded **up** to the 1,000 ISK step =
  **1,169,194,000 ISK**. The profit is 175,379,524, which is 15.00004% of the price, a hair above the 15% asked for because of
  the rounding up.
- **Market comparison:** the Golem's stand-in market price is 938,600,729 ISK, rounded up to **938,601,000**. Against
  the delivered cost that is a profit of **−55,213,476 ISK (−5.9%)**. At these stand-in prices, and with the ME 2 copy and
  the 20 M blueprint charge, building this Golem costs more than buying one. The quote card would show this beside the
  cost-plus price.

## Warnings and notices

`ProjectCost.quotable()` is true: every bought item has a price, every job's materials have an adjusted price, the jobs make the
units needed, and every volume is known. The only entries are eight **notices** (`USES_ORIGINAL`), one for each item built from a
blueprint original (the Raven and the seven parts). They tell the developer an original is in use. They do not block
a quote.

If one price were missing, for example no price for Ferrogel, that item would show a warning, its cost would be left out of the
materials, and **Issue quote** would be refused until the price is there.

## A second order: a Golem and a Raven

Add one Raven to the order. The Raven's demand is now **2**: 1 for the Golem and 1 for the buyer. It is one job of **2 runs**, so its
materials are rounded once for both. Here the amounts happen to divide evenly (Tritanium 9,266,400 is exactly 2 × 4,633,200), so
the rounding does not change the total, but a part whose base is not whole after ME would show it.

| | Golem | Golem + Raven |
|---|---|---|
| Raven job | 1 run, 7,834 s | 2 runs, 15,667 s |
| Materials | 825,402,773 | 955,484,248 |
| Install cost | 87,685,882 | 94,154,823 |
| Hauling | 60,715,821 | 99,614,217 |
| **Delivered cost** | **993,814,476** | **1,169,263,288** |
| Quote (15%, rounded up) | 1,169,194,000 | 1,375,604,000 |
| Market comparison | 938,601,000 | 1,088,174,000 |

The Raven for the buyer is not costed apart from the Raven inside the Golem: both are demand for the same item, so they join and
the splitter sees 2 runs. The per-unit price in the quote is the total divided by 2 units (687,802,000), which is only a mean of two
different ships and is not a price for either.

## Why the job size matters

Rounding is per job. The 364 Magpulse Thruster runs of this project are one job, which needs
Titanium Carbide 13 × 364 × 0.891 = 4,216.2, rounded up to **4,217**. Made as 364 jobs of one run each, every job would need
ceil(13 × 0.891 = 11.58) = 12, a total of **4,368**, which is 151 more. For the armor plates, with 36,383 runs, the
difference would be far larger. The splitter therefore makes as few jobs as the 30-day limit allows (one job here), and cuts
a long job only at that limit.

## A time bound: finishing sooner for a little more

The strategy above is **least cost**: one job per item. A **time bound** (the developer's second scenario) says no job may take longer
than a limit, and splits each item into the fewest jobs that fit, evenly. `ProjectCosting.compareToLeastCost` costs the project both ways.
This is the real output for the same Golem with every job bounded:

| | Least cost | Bound 2 days (172,800 s) | Bound 1 day (86,400 s) |
|---|---|---|---|
| Jobs | 9 | 14 | 19 |
| What split | | armor plates in 3 (12,128 / 12,128 / 12,127), Sustained Shield Emitter in 2, Scalar Capacitor Unit in 2, Quantum Microprocessor in 2 | the same, in more jobs (plates in 5, the others in 3) |
| Longest job | 376,055 s (4 d 8 h 27 m) | **161,192 s** (1 d 20 h 46 m), the Golem job | 161,192 s |
| Extra materials | | +17,199 ISK | +29,370 ISK |
| Extra hauling | | +501 ISK | +816 ISK |
| Install cost, blueprint, fee | | unchanged | unchanged |
| **Delivered cost** | 993,814,476 | **+17,700** (993,832,176) | +30,186 |
| Longest job shortened by | | **214,863 s (2 d 11 h 41 m)** | 214,863 s |
| Cost of each hour saved | | **297 ISK** | 506 ISK |
| Notice | | | `TIME_BOUND_NOT_MET` for the Golem |

- **The price of the speed is small.** The extra 17,700 ISK is 0.0018% of the cost, and it is all material rounded up once more for
  each extra job, plus the m³ that material adds to hauling. The install cost does not move, because it comes from the base materials.
- **The Golem itself is now the longest job.** A one-run job cannot be split, so after the parts are made faster the single Golem job
  (1.9 days) is what remains. A 1-day bound cannot be met for it, and the project says so instead of failing.
- **The longest job is only a lower bound** on the real lead time, since the Golem job still waits for the parts. The comparison reports the
  longest job and the total, and the developer decides what the saving is worth.
- **The total time barely moves** (−3 s and +3 s of rounding), because splitting changes how the same runs are shared out, not how many there are.

## Things this example shows

- **The Golem's cost is mostly materials (83%), then hauling (6%) and install cost (9%).** The blueprint charge (2%) and
  everything else is small. Hauling comes from the mass of the cheap bulky inputs: 1.68 million Titanium Carbide and
  4.6 million Tritanium are about 63,000 m³ between them (0.01 m³ each), and Ferrogel and Nonlinear Metamaterials add 19,609 m³.
- **Eight notices for one project is noisy.** Originals are flagged per item, so a project built from originals gets one for each
  built item. The page may want to collapse them into one line ("built from 8 originals").
- **The sequential time is 14.9 days, the longest single job 4.4 days.** The times add up because one builder runs the jobs
  one after another. With several slots the real lead time is between the two, and more than the longest job plus the Golem's own
  1.9 days, since the Golem waits for its parts. The costing does not use slots (see [Projects](projects.md#later)).
- **The seven components are built here, but need not be.** Their jobs add 33.7 M ISK of install cost and 13 days
  of the builder's time. Buying them instead is a choice the project already supports: put them in the bought list.
  Whether that is cheaper depends on component prices, which this example does not have.
- **The numbers come from `average_price`.** With Jita sell prices they will differ, mostly in the rare materials
  (Nonlinear Metamaterials, Ferrogel, Fermionic Condensates, Core Temperature Regulator).

## What to try next

- Mark the seven components as bought: the project should lose their nine jobs and gain their purchase.
- Give the Golem copy 0 runs left: the project should warn that there are too few runs and refuse the quote.
- Set the time value to a number and read the line.
- A second Golem: the copy has 1 run, so the splitter has to find a second blueprint or report the shortfall.

## How the numbers were made

A throwaway program (not in the repository) loaded the recipes and volumes from the SDE and the prices from ESI, built the
`ProjectDefinition` described above, and printed the `ProjectCost`. The classes are `ProjectCosting`, `BlueprintSplitter` and
`JobCosting` in `com.eveassist.industry.internal.domain`, and the hand-worked cases that check them are in `ProjectCostingTest`,
`BlueprintSplitterTest` and `JobCostingTest`. The figures here are rounded to whole ISK from the exact decimals, so adding the table
rows may differ from a total by 1 ISK.
