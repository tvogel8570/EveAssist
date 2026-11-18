# Costing components

Status: researched and checked against the game on 2026-11-18. The job-level formulas (components 1 to 5) are confirmed
by readings in the game's Industry window, with two parts assumed (see [1.4](#14-assumed-open-and-out-of-scope)).
Components 6 to 12 are the project's own rules. The steps of a project's cost are in
[Projects](projects.md#how-the-cost-is-worked-out); this file holds the formulas behind them, where each comes from, and how
far it has been checked.

How the file is laid out: [1. Conclusions](#1-conclusions) is the summary, [2. Evidence tags](#2-evidence-tags) says what each
tag means, [3. Component details](#3-component-details) has each formula and its evidence, [4. Reference data](#4-reference-data-and-how-to-read-it-again)
has the SDE and ESI values and the queries that produced them, [5. Readings](#5-readings-from-the-game) is the log of every game
reading, [6. Code and tests](#6-code-and-tests) maps it to the code, and [7. Next](#7-next-steps-and-open-questions) lists what is left.

## 1. Conclusions

### 1.1 Status of each component

`R` is the runs of a job, `base` a material's quantity at ME 0 for one run, and "percent" is the whole number (2.4 is 2.4%).

| # | Component | Formula | Status | In code |
|---|---|---|---|---|
| 1 | Material quantity (per job) | `max(R, ceil(round2(base × R × (1−ME) × (1−structure) × (1−rig))))` | **Confirmed in game**; rig part assumed | `JobCosting.materialQuantity` |
| 2 | Material price | `quantity to buy × unit price` at the price basis | Own | plans; project costing ([7.2](#72-project-level-costing)) |
| 3 | Estimated item value (EIV) | `Σ(base × R × adjusted price)` | **Confirmed in game** (to the ISK) | `JobCosting.cost` |
| 4 | Job install cost | `EIV × (index × (1−structure fee bonus) + tax + SCC [+ alpha])` | **Confirmed in game** (Azbel, tax 0); tax assumed | `JobCosting.installCost`, no alpha tax |
| 5 | Job time | `time × R × (1−TE) × skills × (1−implant) × (1−structure) × (1−rig)`, nearest second | **Confirmed in game**; rig part assumed | `JobCosting.seconds` |
| 6 | Blueprint cost per run | BPC: `price per run × runs used`; BPO: depreciation per run | Own | project costing ([7.2](#72-project-level-costing)) |
| 7 | Time value | `total seconds ÷ 3600 × ISK per hour` | Own, default 0 | project costing ([7.2](#72-project-level-costing)) |
| 8 | Hauling | `(inbound m³ + outbound m³) × ISK per m³` | Own; volumes from the SDE | project costing ([7.2](#72-project-level-costing)) |
| 9 | Surplus credit | `− surplus units × unit price` | Own | plans; project costing ([7.2](#72-project-level-costing)) |
| 10 | Contract fee | flat, 10,000 ISK default | Own (the developer's decision) | project costing ([7.2](#72-project-level-costing)) |
| 11 | Delivered cost | `2 + 4 + 6 + 7 + 8 + 10 − 9` | Own | project costing ([7.2](#72-project-level-costing)) |
| 12 | Quote | margin: `cost ÷ (1 − margin)`; market: `Jita price × units × (1 + premium)` | Own | project costing ([7.2](#72-project-level-costing)) |
| 13 | Maximum runs of a job (a limit, not a cost) | blueprint original: `ceil(30 days ÷ seconds per run)` | **Confirmed in game** (4 readings); the 30 days is inferred | not built |
| 14 | Manufacturing job slots (a limit) | `1 + Mass Production + Advanced Mass Production` | One reading (11 at V and V) and the SDE; the base 1 is inferred | not built |

`round2` is "round half up to two decimals". `skills` is the product of Industry, Advanced Industry and the blueprint's
other time-saving skills, and the implant is a further factor (see [3.5](#35-job-time)).

### 1.2 What the readings in the game settled

Twelve readings, in two Azbels (high-sec and a wormhole) with no rigs, one builder with all industry skills at V ([5](#5-readings-from-the-game)).

- **Materials.** ME and the structure's bonus **multiply**. The product is rounded to **two decimals, half up, and then up to a
  whole unit**, **once per job** (not per run), and a job uses at least **one per run**.
- **Time.** The blueprint's TE, the builder's skills, the implant and the structure all **multiply**. Skills include the
  blueprint's required skills that carry `manufactureTimePerLevel` (the Tech II skills). The result is rounded to the
  **nearest second**, not up.
- **The structure's bonuses are the same in every space.** High-sec and wormhole gave identical materials and time. Only
  rigs are scaled by security (SDE).
- **EIV** is `Σ base × runs × adjusted price` from `GET /markets/prices`, to the ISK, and ME, TE and the structure do not change it.
  The game's window labels it "Estimated Items value (EIV)".
- **Install cost** is `fee × (1 − 4%) + 4% × EIV` for an Azbel: the structure's bonus applies to the index part only, the SCC
  surcharge is 4% of EIV, and there was no facility tax line in either Azbel.
- **An implant multiplies with the skills** (BX-804: `0.80 × 0.85 × 0.96`), and the game shows them as one "Skills and Implants" line.
- **The window's "Blueprint Time Efficiency" line is the blueprint's own TE** (TE 14 shows −14.0%).
- **ESI's cost index is a fraction, rounded to four decimals, has no wormhole systems, and moves over time.**
- **A blueprint original and a copy cost the same** to build, at the same ME and TE. **A ship costs like a module** (G12, an Atron: materials, time, EIV and install cost all by the same formulas).
- **A job's runs are limited by its length:** the window's "Maximum runs" on a blueprint original is `ceil(2,592,000 s ÷ seconds per run)`, 30 days (see [3.14](#314-maximum-runs-of-a-job)).

### 1.3 What was wrong before the readings

The first version of `JobCosting` and of this file got these wrong, and the readings found them. Each was fixed, with a test that
failed on the old code where the code was wrong.

| Was assumed | The game does | Found by |
|---|---|---|
| The product is rounded up exactly (252 × 47 × 0.891 = 10,553.004 gives 10,554) | rounds to two decimals first: **10,553** | reading G5 |
| The third decimal might be dropped, or a half might go to even | it **rounds half up**: 21,106.008 gives 21,107, and 2,722.005 gives 2,723 | readings G6, G11 |
| Time is rounded up (9,926.03 s gives 9,927) | **nearest**: 9,926 s (02:45:26) | reading G8 |
| The cost index is a percent | ESI gives a **fraction** (0.0613), **rounded to four decimals** | ESI |
| ESI has the cost index of every system | **no wormhole systems** (ids from 31,000,000) | ESI |
| A system's index stays the same | it **moves** (2.00912% to 2.00737% within hours) | G2, G8 |
| Every required skill saves time | only skills with attribute **1982** do (Capital Ship Construction does not) | G10 |
| An implant might add to the skills | it **multiplies** | G9 |
| The structure's bonus might scale with security | it does not; **only rigs** do | SDE, G1, G2 |

Also: both wiki pages were first said to agree on per-job rounding, but only the EVE University page says it. A mockup
value (cost index 3.21%) was fake; Perimeter's real index is 6.13%.

### 1.4 Assumed, open and out of scope

- **Assumed** (the developer decided on 2026-11-18 to treat them as correct until a game reading is possible, since testing costs a
  rig or a taxed structure): the **rig scaling** (1.0 high-sec, 1.9 low-sec, 2.1 null-sec, and wormholes at 2.1, the last one the
  developer's report), and the **facility tax** as `EIV × tax`.
- **Open:** what the game charges for an **item exchange contract** (so the 10,000 ISK fee is a setting); the cost and time of
  **invention and copying**; **which product groups each rig applies to** (not read from the SDE); the **alpha clone tax**
  (0.25%, wiki, not in the code).
- **Out of scope:** **Pochven** (the developer has no access, so its security multiplier is unknown), reactions (bought in
  a project), NPC stations.

### 1.5 Worked example

One run of a 10000MN Afterburner I copy at ME 8 / TE 14, in the high-sec Azbel (Dodenvale), builder with Industry V,
Advanced Industry V and a BX-804. The game showed 1,653,860 ISK and 04:29:29 (reading G10).

1. **Materials.** `base × 1 × 0.92 (ME 8) × 0.99 (Azbel)` = base × 0.9108, rounded to two decimals, then up:
   Tritanium 1,313,168 → 1,196,033.41 → **1,196,034**; Pyerite 430,794 → **392,368**; Mexallon 135,255 → **123,191**;
   Isogen 31,628 → **28,807**; Nocxium 5,480 → 4,991.18 → **4,992**; Zydrine 1,748 → 1,592.08 → **1,593**; Megacyte 1,146 → 1,043.78 → **1,044**.
2. **Time.** `36,000 s × 1 × 0.86 (TE 14) × (0.80 × 0.85 × 0.96 = 0.6528) × 0.80 (Azbel)` = 16,168.55, nearest → **16,169 s = 04:29:29**.
3. **EIV.** `Σ base × 1 × adjusted price` (ESI, 2026-11-18) = **27,903,159.24**. Not reduced by ME.
4. **Install cost.** Index 2.00744%: fee `27,903,159 × 0.0200744` = 560,139; structure bonus 4% = −22,406; gross 537,734;
   SCC `4% × EIV` = 1,116,126; no facility tax. Total **1,653,860**. Matches the window to the ISK.

## 2. Evidence tags

The game's rules are not published in one place. ESI's developer documentation (the authority named in CLAUDE.md) describes
endpoints and fields, not the industry formulas. So each claim carries the source it rests on:

| Tag | Meaning |
|---|---|
| **Game** | Read by the developer in the game's Industry window on 2026-11-18, recorded in [5](#5-readings-from-the-game) and in `JobCostingTest`. The strongest tag. |
| **SDE** | Read from the SDE in the dev database (`sde.sde_version`: `latest-postgresschema.pgdump`, dumped 2026-10-26, loaded 2026-11-08), by the attribute ids named in the text. Exact for that SDE. |
| **ESI** | Read from ESI's OpenAPI spec (`https://esi.evetech.net/meta/openapi.json`) and from real calls on 2026-11-18. |
| **Wiki** | The [EVE University wiki, Manufacturing](https://wiki.eveuniversity.org/Manufacturing) (shows "last modified 9 October 2026") or the community page [How the Math Works](https://github.com/beulea/eve-industry-releases/wiki/How-the-Math-Works) (shows "last edited Jul 26, 2026"). Written by players, not by CCP. |
| **CCP 2014** | The CCP devblog [Industry & 3rd Party Developers](https://www.eveonline.com/article/industry-3rd-party-developers/), 2014-07-10. The only CCP text found; from the Crius release, so used only for what the base cost is made of. |
| **Assumed** | From the SDE and the wiki where no source disagrees, and not read in the game. Treated as correct by the developer's decision until a reading is possible. |
| **Developer** | Reported by the developer from the game, not in the SDE or a written source. The rule until a reading disagrees. |
| **Own** | The project's own definition (a business rule decided with the developer). Nothing to verify against the game. |

## 3. Component details

### 3.1 Material quantity

```
q = max( R , ceil( round2( base × R × (1 − ME/100) × (1 − structure/100) × (1 − rig/100) ) ) )
```

- **ME** is 0 to 10 (whole percent), from the blueprint (the Blueprints page, or typed).
- **Structure material bonus:** Raitaru, Azbel and Sotiyo 1% (SDE, attribute 2600 `strEngMatBonus` 0.99). Fortizars have none.
- **Rig material bonus:** Tech I −2%, Tech II −2.4% (SDE, 2594), scaled by security (see [3.13](#313-security-space)).
  The effective bonus is the rig's value times the security multiplier. **Assumed.**
- **Per job:** the product is rounded once for the whole job, so three 1-run jobs can use more than one 3-run job. **Game** (G4):
  4 runs at ME 10 gave 5,354 / 1,569 / 899 / 47 / 22 / 22, where rounding each run and multiplying by 4 would give 5,356 / 1,572 /
  900 / 48 / 24 / 24. Wiki (EVE University) says so too.
- **Two decimals, half up, then up:** **Game** (G5, G6). 252 × 47 × 0.891 = 10,553.004 showed **10,553**; 252 × 94 × 0.891 =
  21,106.008 showed **21,107**. Rounding the exact product up would give 10,554, and dropping the third decimal would give 21,106.
  **Exactly on the half: Game** (G11). 13 × 235 × 0.891 = **2,722.005** showed **2,723**: half up, where half even would give 2,722. The game may work in floating point, so this is what
  it does and not a rule it states; the code follows it (`RoundingMode.HALF_UP`).
- **Minimum one per run:** **Game** (G7): a job whose seven materials each have a base of 1 and 10 runs at ME 2 and the Azbel's 1% (9.70) used **10** of each.
- **The reductions multiply:** **Game** (G3). At ME 10 in an Azbel, Nocxium 1,159, Zydrine 535 and Megacyte 535 (adding the 10% and 1% would give
  1,157 / 534 / 534); the same at ME 8 (G10) and ME 2 (G7).
- **Which rigs apply to which product** is not read from the SDE. The rigs are named by what they build (Equipment, Ammunition,
  Drone and Fighter, Basic or Advanced Small, Medium or Large Ship, Capital Ship, Basic or Advanced Component, Structure) and give their bonus only to that kind.

### 3.2 Material price

```
cost of a material = quantity to buy × unit price        unit price = Jita sell (default), Jita buy, or a manual price
```

**Own.** Defined in [Market and logistics](market-logistics.md#price-source) and [Plans](manufacturing-invention.md#plans).
The quantity to buy is the demand for the material added up over all jobs, less what a built part provides. Material on hand
is valued at the market price, so it is not free. An item with no price is counted and listed, not treated as free.

### 3.3 Estimated item value (EIV)

```
EIV = Σ over the blueprint's materials ( base quantity × R × adjusted price )
```

- **Base quantity, no bonuses:** ME, TE, the structure and the skills do not change it. **Game** (G1 to G3, G10): 4,374,740 ISK at ME 0 and ME 10 alike.
  CCP 2014 and Wiki say the same. CCP 2014 calls it "base cost"; the wiki and the game's window call it "Estimated Items value (EIV)" (**Game**).
- **Adjusted price** is CCP's index, not the market price: `GET /markets/prices`, field `adjusted_price` (ESI). **Game:** ESI's prices
  gave **4,374,740.21** where the window showed 4,374,740 (G1); 537,646.30 against 537,646 (G8); 27,903,159.24 for G10.
- A material with no adjusted price is counted and left out, so the EIV and the install cost are then too low (the code reports it).
- For a built part used as a material, the game takes the part's adjusted price as an item (*unverified*).
- Copying and invention use another base (2% of EIV, Wiki and CCP 2014). Not built.

### 3.4 Job install cost

```
job fee      = EIV × system cost index × (1 − structure fee bonus)
facility tax = EIV × tax
SCC          = EIV × 4%
alpha tax    = EIV × 0.25%      (only for an Alpha clone; not in the code)
install cost = job fee + facility tax + SCC + alpha tax
```

- **Structure fee bonus** reduces the job fee only, **not the taxes**: Raitaru 3%, Azbel 4%, Sotiyo 5% (SDE 2601; the Fortizars 3%, 3%, 2%).
  **Game** (G1, G2): the window's −4.0% was exactly 4% of the index line.
- **SCC surcharge 4%** of EIV. **Game** (G1: 174,990 = 4% of 4,374,740). CCP's rate, changed over time (older threads give 1.5%), so it is a setting.
- **Facility tax** is set by the structure's owner, capped at 10% (Wiki). **No facility tax line appeared in either Azbel.** The formula for a taxed
  structure is **Assumed** (`EIV × tax`).
- **System cost index:** `GET /industry/systems`, `cost_indices[]` for `manufacturing`. A **fraction** (Perimeter 0.0613 = 6.13%), **rounded to four
  decimals in all 32,910 values**, so ESI's index differs a little from the game's: Dodenvale **0.0201** in ESI against about **2.00912%** in
  the window (G2). With ESI's value the cost is 259,404.6 against the window's 259,368, **36 ISK (0.014%)** more, and at most about 0.005% of EIV in any case.
- **The index moves.** The same system gave 2.00913% (G2), then 2.00745%, 2.00744% and 2.00744% (G8, G9, G10), within the session. ESI showed
  0.0201 throughout. A project's index is the value when it is costed, and a quote keeps the one it was issued with.
- **Both systems' indices moved by the same factor** (observed, two systems). The wormhole went from **0.56883%** (G1) to **0.56835%** (G11), a factor of **0.99915**; Dodenvale from 2.00913% (G2)
  to 2.00744% (G9, G10), **0.99916**. The high-sec value was then steady over three readings (2.00745%, 2.00744%, 2.00744%). That fits a change of the whole universe's total, in a step, which scales every
  system the same (the wiki gives the index as the system's work hours over the universe's). Two systems do not prove it, so it is a lead, not a rule. If it holds, one fresh index and the ratio to the old
  one are enough to refresh every wormhole facility's typed index, but nothing relies on it.
- **No wormhole system is in ESI** (5,485 systems, ids 30,000,001 to 30,100,084). J100640 (id 31000460) showed an index of about **0.56883%** (G1) and **0.56835%** (G11).
  **Developer, 2026-11-18: ESI does not give wormhole indices because industry in wormholes would otherwise be trackable by hostile players** (the absence is **ESI**-checked, the
  reason is the developer's). The index of a wormhole facility is therefore **typed by hand**, with several decimals; this is a requirement
  ([Projects, system cost index of a wormhole system](projects.md#system-cost-index-of-a-wormhole-system)). The exact value comes from a job's cost hover:
  the index line in ISK divided by the estimated item value, since the window shows the percent rounded to two decimals.
- **The index can be worked back from a job's cost** (the inverse of the formula above; the same one the developer's EVE University source gives, pasted 2026-11-18, and the one used here
  to get the indices of the readings): `index = ( total ÷ EIV − tax − 0.04 ) ÷ ( 1 − structure fee bonus )`, with `0.04` the SCC surcharge (plus 0.0025 for an Alpha clone's alpha tax). Two ways to get its inputs:
  - **From the game's job window (exact):** the index line in ISK divided by the EIV, both in the cost hover at that moment. This is how every index in [5](#5-readings-from-the-game) was found.
  - **From ESI (proposed, not verified):** `GET /characters/{id}/industry/jobs` (scope `esi-industry.read_character_jobs.v1`) and `GET /corporations/{id}/industry/jobs` (`esi-industry.read_corporation_jobs.v1`) return each job's `cost`,
    `blueprint_type_id`, `runs`, `activity_id`, `facility_id` and `start_date`; the EIV would come from the blueprint's base materials and `adjusted_price`. **Two problems.** (1) ESI describes `cost` as "the sum of
    job installation fee and industry facility tax" and does **not** mention the SCC surcharge, while the window's total job cost includes it, so whether the field includes the 4% is **unverified**: if it does not, the formula
    above must leave out the `0.04`. One of the developer's jobs, ESI's `cost` against the window's total at install time, settles it. (2) **A wormhole amplifies any error in the EIV about 8.3 times**, because the index is only
    about 12% of the total (the SCC is most of it): an EIV off by 0.1% gives an index off by 0.8%, and by 1% gives 8%. ESI has only today's adjusted prices, not those at the job's start, so the result is good for a recent job and
    rough for an old one, unless the app keeps the adjusted prices it reads. In high-sec (a job's index share is larger) the amplification is about 3.1 times.
  - **Copying, invention and research** use a 2% base instead of the EIV (EVE University, CCP 2014, as noted above); only manufacturing has been read in the game.
  - Industry jobs are **not read by the app yet**.
- **The window rounds each amount to a whole ISK and adds the rounded gross cost and taxes** (G4: gross 956 where the index line 995 less 40 is 955; total
  956 + 7,000 = 7,956, exact 7,955.16). The code keeps exact decimals, which differ by under 1 ISK; a quote is not worked out to the ISK.

### 3.5 Job time

```
seconds = base seconds × R × (1 − TE/100) × skills × (1 − implant/100) × (1 − structure/100) × (1 − rig/100)
skills  = (1 − 0.04 × Industry) × (1 − 0.03 × Advanced Industry) × Π over time-saving required skills (1 − percentPerLevel × level / 100)
```

rounded to the **nearest** whole second. All factors multiply.

- **Blueprint TE** is any whole value 0 to 20 as research gives it (even in practice). The window's "Blueprint Time Efficiency" line shows the blueprint's own TE: **Game** (TE 14 gave −14.0%, TE 20 −20.0%, TE 4 −4.0%; at TE 0 the line is absent).
- **Industry** −4% per level (SDE 440), **Advanced Industry** −3% per level (SDE 1961). Both at V give 0.80 × 0.85 = 0.68, the window's **−32.0%** (G1).
- **Other required skills** (the Tech II production skills): a skill with attribute 1982 `manufactureTimePerLevel` saves that percent per level the **builder** has. 25 skills
  have −1 and Mutagenic Stabilization −2. A blueprint's manufacturing activity lists its required skills (`industryActivitySkills`, activity 1); 1,505 blueprints require
  such a skill. **Game** (G7, G8): Multispectrum Coating II requires Nanite Engineering and Hydromagnetic Physics, both at V: `0.68 × 0.95 × 0.95 = 0.6137`, **−38.6%**, and
  03:03:49 for 10 runs (adding the two would give 03:03:19). **Only skills with 1982 count:** the Afterburner requires Capital Ship Construction, which has no 1982, and the hover listed only
  Industry and Advanced Industry (G10).
- **Implants:** the only production implants in the SDE are the Zainou 'Beancounter' Industry BX-801, BX-802 and BX-804 (−1%, −2%, −4% manufacturing time, slot 8). **Game** (G9, G10): a BX-804 with
  Industry V and Advanced Industry V gave **−34.7%** = `1 − 0.80 × 0.85 × 0.96`, where adding would show −36.0%. The implant changes time only, never materials.
- **Structure time bonus:** Raitaru 15%, Azbel 20%, Sotiyo 30% (SDE 2602; Fortizars 15%, 15%, 10%). **Game:** the Azbel's −20.0% in both spaces.
- **Rig time bonus:** Tech I −20%, Tech II −24% (SDE 2593), scaled by security. **Assumed.**
- **Rounding to the nearest second: Game** (G8). 2,340 × 9 × 0.96 × 0.6137 × 0.80 = 9,926.03 showed **02:45:26**; rounding up would give 9,927. The other readings (11,028.9 to 11,029,
  18,408.96 to 18,409, 36,817.92 to 36,818, 1,566.72 to 1,567, 16,168.55 to 16,169, 37,601.28 to 37,601) agree.

### 3.6 Blueprint cost per run

```
BPC bought:    price per run × runs used
BPC invented:  (invention cost per attempt ÷ expected successes) ÷ runs per BPC × runs used
BPO:           depreciation per run, a setting (default 0)
```

**Own.** The price of a BPC is per run, from public contracts ([Contract prices](market-logistics.md#contract-prices), `BpcQuote`). The invention cost belongs with
the invention work, and the leftover runs of an invented BPC keep their share of it.

### 3.7 Time value

```
time value = total job seconds ÷ 3600 × ISK per hour
```

**Own**, default 0. The developer needs more information before deciding on it. With one builder and independent jobs the total is the sum of the jobs' times.

### 3.8 Hauling

```
inbound m³  = Σ over bought materials ( quantity × volume )
outbound m³ = Σ over products ( units × packaged volume )
hauling     = (inbound m³ + outbound m³) × ISK per m³
```

**Own** (a flat rate by the developer's decision), volumes from the **SDE**: `invTypes.packagedVolume` where set (a Vexor has `volume` 115,000 and `packagedVolume` 10,000; a Hurricane 15,000
packaged; Tritanium 0.01), else `volume`. `invVolumes` is empty. Hauling is its own line with a source (flat rate or typed) so another tool's quote (for example Red Frog) can replace it later.

### 3.9 Surplus credit

```
surplus units = units made by the jobs − units the project needs        credit = surplus units × unit price of that item
```

**Own**, as plans do it. Units made are `R × units per run` (10 charges per run for a Cap Booster 800). The credit uses the price basis.

### 3.10 Contract fee

```
contract fee = a flat amount, 10,000 ISK by default
```

**Own.** The developer decided on 2026-11-17 that every project is delivered by contract: sales tax is 0 and there is no broker fee. What the game charges for a contract is not known, which is why the amount is a setting.

### 3.11 Delivered cost

```
delivered cost = materials (2) + install cost (4) + blueprint cost (6) + time value (7) + hauling (8) + contract fee (10) − surplus credit (9)
per unit       = delivered cost ÷ units delivered
```

**Own.** Job time (5) is not ISK and enters only through the time value (7).

### 3.12 Quote

```
cost plus margin:   quote = delivered cost ÷ (1 − margin/100)     margin is a percent of the price, below 100
market-referenced:  quote = Jita price × units × (1 + premium/100)
profit              = quote − delivered cost
```

**Own**, decided on 2026-11-17: a margin of 20 gives a profit that is 20% of the quote. The other mode is shown as a comparison. The rounding of the quote is open.

### 3.13 Security space

Where the system's security enters. The developer asked whether it was missing from the structure bonuses; the SDE says it is not there.

- **Only rigs are scaled; the structure's own bonuses are flat.** SDE: Raitaru, Azbel and Sotiyo carry attributes 2600 to 2602 and none of 2355 to 2358. The effect
  `structureEngineeringRigSecurityModification` (id 6842) multiplies the rig's own 2593 (time), 2594 (material), 2595 (cost) and 2653 (Thukker material) by `securityModifier` (2358), operation 4 (multiply).
  **Game:** the Azbels' 1% and 20% were identical in high-sec and in the wormhole (G1, G2).
- **Multipliers** on every rig checked: `hiSecModifier` **1.0**, `lowSecModifier` **1.9**, `nullSecModifier` **2.1** (attributes 2355 to 2357).
- **A wormhole takes the null-sec value (2.1).** The developer, from the game. The SDE does not say how attribute 2358 is set for a system.
- **Pochven is out of scope** (no access).

| Rig | High-sec | Low-sec | Null-sec and wormhole |
|---|---|---|---|
| Tech I material (2%) | 2.0% | 3.8% | 4.2% |
| Tech II material (2.4%) | 2.4% | 4.56% | 5.04% |
| Tech I time (20%) | 20% | 38% | 42% |
| Tech II time (24%) | 24% | 45.6% | 50.4% |

The structure's bonus and the rig's multiply: a Raitaru in null-sec with a Tech II material rig saves `1 − 0.99 × 0.9496 = 5.99%` of the materials before the blueprint's ME. Rig cost bonuses (2595, 10% and 12%) exist only on copying, invention and research rigs.
**All of the rig rows above are Assumed** (no rig was available to read).

### 3.14 Maximum runs of a job

A limit that decides how a job is split, so it belongs with the formulas even though it is not a cost. For a **blueprint original** the window shows "Maximum runs" instead of "Runs remaining".

```
maximum runs = ceil( 2,592,000 s ÷ seconds of one run )        2,592,000 s = 30 days
```

where "seconds of one run" is the unrounded time of a single run (TE, skills, implant and structure all applied). The result is the smallest number of runs whose duration reaches 30 days, so
a job at the maximum lasts a little **more** than 30 days (by up to one run).

| Reading | Seconds of one run | 2,592,000 ÷ that | Window showed |
|---|---|---|---|
| G2: Cap Booster 800, ME 0 / TE 0 | 489.6000 | 5,294.1 | **5,295** |
| G3: ME 10 / TE 20 | 391.6800 | 6,617.6 | **6,618** |
| G9: ME 10 / TE 20 with a BX-804 | 376.0128 | 6,893.4 | **6,894** |
| G10: 10000MN Afterburner I, ME 8 / TE 14, BX-804 | 16,168.5504 | 160.3 | **161** |

**Game, 2026-11-18** (read from the "Maximum runs" figure in four screenshots). Together the four readings allow a limit between **2,591,942 s and 2,592,134 s**, and 30 days is 2,592,000 s. The 30 days
is therefore **inferred** from the readings and not stated by a source; the limit itself is not in the SDE or in ESI.

- **Not tested:** whether a **copy** (BPC) is capped the same way (it shows "Runs remaining", and the readings had at most 1,500 runs over 6.8 days), and the cap when the duration is **below** it.
- **Use:** a job size for an item is at most this many runs, so a long build is split into several jobs, each rounding its materials once (see [3.1](#31-material-quantity)).

### 3.15 Manufacturing job slots

```
slots = 1 + Mass Production level + Advanced Mass Production level
```

The two skills each carry `manufacturingSlotBonus` = 1 per level (SDE, attribute 450). **Game:** "Manufacturing jobs 0 / 11" with both at V (1 + 5 + 5). The base of 1 is inferred, since the SDE does not state it;
with the skills at V the reading is consistent with it. Slots are not used by the cost.

## 4. Reference data and how to read it again

### 4.1 Structures (SDE attributes 2600, 2601, 2602)

| Structure | Materials | Job fee | Time |
|---|---|---|---|
| Raitaru | 1% | 3% | 15% |
| Azbel | 1% | 4% | 20% |
| Sotiyo (not allowed in high-sec, attribute 1970) | 1% | 5% | 30% |
| 'Draccous' and 'Horizon' Fortizar | none | 3% | 15% |
| 'Moreau' Fortizar | none | 2% | 10% |

### 4.2 Skills, implants, rigs

- Industry: attribute 440 = −4 per level. Advanced Industry: attribute 1961 = −3 per level (its effects act on the character with a percent operation, so they multiply).
- Product skills: attribute 1982 `manufactureTimePerLevel`: −1 on 25 skills (Nanite Engineering, Hydromagnetic Physics, Mechanical Engineering, the Starship Engineering skills, the Advanced ship construction skills and others), −2 on Mutagenic Stabilization.
  Skills without it (such as Capital Ship Construction) save no time.
- Implants: group Cyber Production has three types, the Zainou 'Beancounter' Industry BX-801, BX-802, BX-804 (attribute 440 = −1, −2, −4).
- Attribute 1954 `industryJobCostMultiplier` ("multiplies the job cost for this blueprint type by the specified value") is on 86 types, **all with the value 0**, and all of them **special-edition
  ship blueprints** (73 named "… Edition Blueprint", such as the Merlin Wiyrkomi Edition, and 13 commemorative ones such as the Police Pursuit Comet and the YC117 editions). It is **not** on ordinary
  ship blueprints: the Atron Blueprint has none. A literal 0 would make the job free, so the game must ignore it, but **no reading has been of an edition blueprint**, so that is unchecked (see 7.1).
- Rigs: 214 types carry 2593 to 2595. Manufacturing rigs: Tech I material −2, time −20; Tech II −2.4, −24 (the M-set rigs are separate time and material rigs, the L-set rigs carry both). Copying, invention and research rigs: cost −10 / −12, time −20 / −24.

### 4.3 ESI

- `GET /markets/prices` (16,234 rows on 2026-11-18): `type_id`, `adjusted_price`, `average_price`. Tritanium 2.914, Morphite 17,724.10. Public. Cache about one hour (`last-modified` 12:40:32 UTC, `expires` 13:40:32).
- `GET /industry/systems` (5,485 systems, 32,910 index values): `solar_system_id` and `cost_indices[]` of `activity` and `cost_index`. Public, rate group `industry` 150 tokens per 15 minutes. No wormhole systems.
- Neither endpoint is read by the app yet.

### 4.4 Re-deriving them

The SDE is in the dev database (Docker container `eve-assist-postgres`, schema `sde`, `dgmTypeAttributes` has `valueInt` and `valueFloat`). Read only:

```
docker exec -i eve-assist-postgres psql -U postgres -d eveassist -At -F ' | ' -c "<query>"
-- structure bonuses: attributes 2600, 2601, 2602 of types Raitaru, Azbel, Sotiyo
select t."typeName", a."attributeID", coalesce(a."valueFloat", a."valueInt") from sde."invTypes" t join sde."dgmTypeAttributes" a using ("typeID") where a."attributeID" in (2600,2601,2602) and t.published;
-- a blueprint's recipe, time and required skills (activityID 1 = manufacturing)
select * from sde."industryActivityMaterials" where "typeID" = <bp> and "activityID" = 1;   select time from sde."industryActivity" where "typeID" = <bp> and "activityID" = 1;
select s."skillID", s.level from sde."industryActivitySkills" s where s."typeID" = <bp> and s."activityID" = 1;
-- skills that save time: attribute 1982 (and 440 for Industry)
```

ESI: `curl -s https://esi.evetech.net/markets/prices -H 'X-Compatibility-Date: 2020-01-01'` and the same for `/industry/systems`. Read both right after a game reading, because the game's numbers move with them.

## 5. Readings from the game

All on 2026-11-18, by the developer, in the Industry window (Azbel in high-sec at Dodenvale, and an Azbel in the wormhole J100640), **no rigs**, one builder with **Industry, Advanced Industry and the other
industry skills at V**, no implant unless stated. Neither Azbel showed a facility tax line. The blueprint is a Cap Booster 800 copy (10 charges per run, 900 s, Tritanium 1,502, Pyerite 440, Mexallon 252, Nocxium 13,
Zydrine 6, Megacyte 6) unless stated. Money is ISK.

| # | Space | Blueprint, runs | Window showed | What it settled |
|---|---|---|---|---|
| G1 | wormhole | ME 0 / TE 0, 100 | 148,698 / 43,560 / 24,948 / 1,287 / 594 / 594; 13:36:00; EIV 4,374,740; index line 24,885, bonus −995 (−4.0%), gross 23,889; SCC 174,990; total **198,879**; hover: Skills and Implants −32.0%, Structure −20.0% | materials, time, EIV, install cost, structure flat; index about 0.56883% |
| G2 | high-sec | the same | the same materials and time; index line 87,894, bonus −3,516, gross 84,378; SCC 174,990; total **259,368** | same in both spaces; ESI index 0.0201 against about 2.00912% |
| G3 | both | ME 10 / TE 20, 100 | 0.13M / 39.20K / 22.45K / 1,159 / 535 / 535; **10:52:48**; cost unchanged (198,879 and 259,368) | ME and TE multiply; ME and TE do not change EIV or cost |
| G4 | both | ME 10 / TE 20, 4 (the box said 47) | 5,354 / 1,569 / 899 / 47 / 22 / 22; 00:26:07; EIV 174,990; totals **7,956** (wormhole), **10,375** (high-sec) | rounding once per job; the window adds rounded gross and taxes |
| G5 | high-sec | ME 10 / TE 20, 47 | 62.90K / 18.43K / 545 / 252 / 252; Mexallon **10,553**; 05:06:49; total 121,903 | two-decimal rounding before rounding up |
| G6 | high-sec | ME 10 / TE 20, 94 | 0.13M / 36.85K / 21.11K / 1,089 / 503 / 503; Mexallon **21,107**; 10:13:38; total 243,806 | the third decimal rounds half up |
| G7 | high-sec | Multispectrum Coating II copy ME 2 / TE 4, 10 | 10 of each of seven materials; **03:03:49**; total 35,407; hover: TE −4.0%, Skills and Implants **−38.6%**, Structure −20.0% (Industry, Advanced Industry, Nanite Engineering, Hydromagnetic Physics at V) | product skills multiply; the minimum of one per run |
| G8 | high-sec | the same, 9 | 9 of each; **02:45:26**; EIV 537,646; index line 10,793, bonus −432, gross 10,361; SCC 21,506; total **31,867** | time rounds to the nearest second; EIV matches ESI; the index moved (about 2.00737%) |
| G9 | high-sec | Cap Booster 800 ME 10 / TE 20, 100, **BX-804** | as G3 materials; **10:26:41**; hover TE −20.0%, Skills and Implants **−34.7%**, Structure −20.0%; total **259,297** | the implant multiplies (index about 2.00744%) |
| G10 | high-sec | 10000MN Afterburner I copy ME 8 / TE 14, 1, BX-804 | 1.20M / 0.39M / 0.12M / 28.81K / 4,992 / 1,593 / 1,044; **04:29:29**; hover TE −14.0%, −34.7%, −20.0%; total **1,653,860** | the TE line is the blueprint's TE; Capital Ship Construction not listed; ME 8; cost to the ISK |
| G11 | wormhole | ME 10 / TE 20, 235 | output ×2,350; Nocxium **2,723**, Zydrine 1,257, Megacyte 1,257; **1D 01:34:05** (92,045 s); EIV 10,280,639; index line 58,430, bonus −2,337, gross 56,093; SCC 411,226; total **467,319** | an exact half (2,722.005) rounds up; the wormhole index is 0.56835% (index line); the total is again the sum of the rounded gross and taxes (exact 467,318.3) |
| G12 | high-sec | Atron Blueprint copy (a standard T1 frigate) ME 10 / TE 20, 1, BX-804 | 28.51K / 5,346 / 2,228 / 446; **00:41:47** (2,507 s); EIV 367,708; index line 7,382, bonus −295 (−4.0%), gross 7,086; SCC 14,708; total **21,794** | a ship is costed like a module; the BX-804 is still fitted; products on a half unit (2,227.5, 445.5) round up; index about 2.0074% |

Notes on reading the window:

- **Set the job runs box, then press Enter or Tab.** The window calculates for the number it last applied: one reading was meant as 47 runs and showed the numbers of 4 (the output said ×40).
  Check the output count (charges per run × runs), the EIV and the duration before trusting a reading.
- **Hover the materials for the exact quantity** (`0 / 10,553`). The window shortens large numbers to a few digits (`0.13M`, `39.20K`).
- **Hover the job cost** for the breakdown (EIV, index line, structure bonus, gross cost, SCC, total taxes, total) and **the duration** for the modifiers (blueprint TE, Skills and Implants, Structure Role Bonus, and the skills at five pips).
- A blueprint original shows "Maximum runs" and a copy "Runs remaining". Both cost the same at the same ME and TE. The maximum is a formula ([3.14](#314-maximum-runs-of-a-job)).

## 6. Code and tests

In the `industry` module, `com.eveassist.industry.internal.domain`:

| Class | What it holds |
|---|---|
| `JobCosting` | `cost`, `materialQuantity` (two decimals half up, then up, minimum one per run), `seconds` (nearest), `installCost` (exact decimals) |
| `JobTerms` | blueprint ME (0 to 10) and TE (0 to 20), a facility and a builder. `JobTerms.PLAN` is the plan's ME 10 / TE 20, no facility and no builder |
| `FacilityTerms` | structure and rig material, time and cost percents (rigs already scaled by security), the index in percent, tax, SCC. Every value is an input |
| `BuilderTerms` | Industry and Advanced Industry levels, a list of `ProductSkill`, implant percent. `skillFactor()` is the skills alone, `timeFactor()` includes the implant (the window's "Skills and Implants" line) |
| `ProductSkill` | one time-saving required skill: the builder's level and the percent per level |
| `JobCost` | materials, seconds, EIV, install cost, units made, the count of materials without an adjusted price |
| `Costing` | the plan costing, whose rounding and time now come from `JobCosting` |

Tests in `JobCostingTest` that come from a reading (each was written with the reading's inputs; those marked * failed on the old code):

| Test | Reading |
|---|---|
| `matchesAnAzbelJobReadInTheGame` | G1 |
| `anIndexRoundedToFourDecimalsMovesTheInstallCostByAFewTensOfIsk` | G2 |
| `theBlueprintsLevelsChangeTheMaterialsAndTheTimeButNotTheInstallCost` | G3 |
| `aJobIsRoundedOnceForAllItsRuns` | G4 |
| `theGameRoundsToTwoDecimalsBeforeRoundingUp` * | G5 |
| `theGameRoundsTheThirdDecimalHalfUp` (fails if the code truncates) | G6 |
| `anExactHalfInTheThirdDecimalRoundsUp` (fails if the code rounds half even) | G11 |
| `theTechIiSkillsATechIiBlueprintRequiresShortenTheTimeAndMultiply` | G7 |
| `theGameRoundsTheTimeToTheNearestSecondAndTheCostIndexMoves` * | G7, G8 |
| `anImplantMultipliesWithTheSkills` (fails if the implant is added) | G9 |
| `aBlueprintWithItsOwnMeAndTeIsRead` | G10 |
| `aStandardShipBlueprintIsCostedLikeAnyOther` | G12 |

The other tests check hand-worked arithmetic, the limits (`impossibleLevelsAndPercentagesAreRefused`) and the missing-price count. `CostingTest` (the plan) still passes unchanged.

Project-level costing (components 2 and 6 to 12) is [7.2](#72-project-level-costing). Known gaps in the job costing: **no alpha clone tax**; **no rig table** (a facility is given its resolved percents); **no reading of `GET /markets/prices` and `GET /industry/systems`**;
**no reading of which skills a blueprint requires** from the SDE (the caller gives the `ProductSkill` list); **no scaling of rigs by security** (the caller gives scaled values).

## 7. Next steps and open questions

### 7.1 Checks still to make

1. **A rig** ([9](#9-test-for-rigs-when-one-is-available)): whether the security scaling is 1.0 / 1.9 / 2.1 and a wormhole 2.1. Assumed until then.
2. **A taxed structure:** whether a facility tax is `EIV × tax`. Assumed until then.
3. **Contract cost:** what the game charges for an item exchange contract.
4. **Invention and copying** (see [8](#8-invention-and-copy-readings)).
5. **Where each rig applies:** the dogma effects that tie a rig to product groups.
6. ~~Half up against half even.~~ **Done (G11): half up.** Still open: whether time rounds each factor or the whole product (no run count from 1 to 1,500 gives a time ending exactly in .5 s at the
   developer's blueprint, skill and structure levels, so exact halves of time cannot be tested with them).
7. **Alpha clone tax** if a builder may be an Alpha.
8. ~~A ship blueprint.~~ **Done (G12): an ordinary ship costs like any other blueprint.** What stays unchecked is **attribute 1954** (`industryJobCostMultiplier`, 0), which is only on the 86
   special-edition ship blueprints (such as the Merlin Wiyrkomi Edition), not on ordinary ones. One cost hover on such a blueprint would show whether the usual formula holds there; they are rare and
   not a likely build, so it is low priority.
9. **Whether a copy is capped by length** like an original ([3.14](#314-maximum-runs-of-a-job)): a copy with more runs than fit in 30 days, for example a slow blueprint with 200 runs.
10. **Facility tax by standing.** Upwell structure owners can set different taxes by the builder's standing to them (not read; the two Azbels had no tax). The facility record holds the one rate that applies to the builder.
11. **ESI's job `cost` against the window's total** (see [3.4](#34-job-install-cost)): one of the developer's jobs, to see whether ESI's field includes the SCC surcharge. Needed only if the app derives an index from jobs.

### 7.2 Project-level costing

Components 2 and 6 to 12 belong to the project, not to a single job, and are built apart from `JobCosting` as `ProjectCosting`: a pure domain class that walks the project's tree (parents first, as `Costing` does),
builds each item's jobs with `JobCosting`, and adds the project's lines (materials, blueprint cost, time value, hauling, contract fee, surplus), warnings for what is missing, and the quote in both modes.

The developer decided its first slice on 2026-11-18; the decisions are in [Projects, first costing slice](projects.md#first-costing-slice-decisions):

- job sizes are an input, one job per item by default, and the splitter for BPC run limits comes later (the limit itself is [3.14](#314-maximum-runs-of-a-job));
- a surplus unit is credited at the project's price basis;
- an incomplete cost (an unpriced material, a missing adjusted price, a wormhole facility with no typed index) is shown with a warning, and *Issue quote* is refused until every line is priced;
- the cost and the profit are exact, and the quote is rounded up to a step (a setting, 1,000 ISK by default).

The formulas for those components (3.2 and 3.6 to 3.12) are the project's own rules, not the game's, so there is nothing to read from the game for them.

## 8. Invention and copy readings

Not needed for the first project quote (which builds items from blueprints that exist), and wanted once invention and copying are costed. A reading of each takes these, written down with the UTC time and the
system, and ESI's `cost_index` and `adjusted_price` read right after:

- **Copy a blueprint original.** The BPO and its ME and TE; **runs per copy** and **number of copies** (1 run × 1 copy, then 10 runs × 1 copy, then 1 run × 5 copies, which separates the three); the facility; the window's
  cost hover (EIV, index line, structure bonus, SCC, total) and the duration hover. The wiki says copy and invention are charged on **2% of EIV** (CCP 2014: `base cost × 0.02 × runs per copy × number of copies`),
  where EIV is the product's manufacturing materials, which the readings test.
- **Invention.** The T1 blueprint copy used (its ME, TE and runs), the **datacores** and the **decryptor** (none first, then one), the facility, the same two hovers, and the shown **chance of success**, runs and ME/TE of
  the result. Datacores and decryptors are priced from the market, so only the install cost and the time are read here.

## 9. Test for rigs, when one is available

Not possible with the two Azbels (no rigs). With one rig fitted, the same Cap Booster 800 copy at ME 0 / TE 0, 100 runs, in a high-sec or a wormhole structure of the same type (Azbel), should show,
if the rig applies to the charge and the scaling is as assumed:

| Rig | Materials (Tritanium / Pyerite / Mexallon / Nocxium / Zydrine / Megacyte) | Time |
|---|---|---|
| none (the structure only, G1) | 148,698 / 43,560 / 24,948 / 1,287 / 594 / 594 | 48,960 s |
| Tech I material, high-sec | 145,725 / 42,689 / 24,450 / 1,262 / 583 / 583 | |
| Tech I material, wormhole | 142,453 / 41,731 / 23,901 / 1,233 / 570 / 570 | |
| Tech II material, high-sec | 145,130 / 42,515 / 24,350 / 1,257 / 580 / 580 | |
| Tech II material, wormhole | 141,204 / 41,365 / 23,691 / 1,223 / 565 / 565 | |
| Tech I time, high-sec / wormhole | | 39,168 s / 28,397 s |
| Tech II time, high-sec / wormhole | | 37,210 s / 24,284 s |

A rig that does not apply to charges gives the structure-only numbers, so the first reading also shows which rig applies to which product.
