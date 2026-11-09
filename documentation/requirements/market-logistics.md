# Market and Logistics

Status: draft, interview in progress.

## Price source

- Prices come only from Jita (The Forge region, Jita 4-4). Other trade hubs and player-owned structure markets are not
  needed for now.
- A plan keeps the prices it was costed with, so that it can be kept as quoted to a customer. They change only when the
  user asks to refresh that plan's prices, or when a product is added whose items the plan has no price for (those are
  fetched at once, and the rest stay as they were). Opening a plan never changes its prices, and there is no
  background refresh. The developer decided this on 2026-11-08, replacing "refreshed when the user opens a plan".
- Each price shows when it was fetched.
- A price is the volume-weighted average of the best orders that make up a set share of the item's order volume (the
  cheapest sell orders, or the highest buy orders). The share is a user setting, 5% by default. The same price is used
  whatever the quantity.

## Price basis

- By default, materials are costed at the Jita sell price (bought right away) and products are valued at the Jita sell
  price (listed as sell orders).
- The user can instead cost materials at the Jita buy price (bought through buy orders) and value products at the Jita
  buy price (sold right away).
- The price basis is a user-wide default, and each plan can override it.
- The user can enter a manual price for an item, for example a contract price, which overrides the market. Manual prices
  stay until the user changes them. Each one shows its age, with a warning once it is older than a set number of days.

## Contract prices

Added 2026-11-09. Blueprint copies (BPCs) are not sold on the market, so their price comes from public contracts.

- Public item exchange contracts in The Forge are the source. Courier and auction contracts are not used. The contracts
  of the characters themselves are a separate feature (see [Contracts](#contracts)).
- The price of a BPC is per run, for one blueprint type with its ME and TE. A copy of 10 runs for 1,000,000 ISK is
  100,000 ISK per run.
- The price uses copies with exactly the ME and TE asked for. When there are none, it uses copies whose ME and TE are
  both at least as good, and says so.
- A price is worked out like a market price: the volume-weighted average of the cheapest offers that make up the same
  share of the runs on offer (5% by default).
- A pack of several copies is split between them in proportion to their runs, so every run in it costs the same. Items
  in the contract that are not BPCs are valued at their Jita sell price first, and the BPCs share the rest. A contract
  that asks for items, holds an item with no Jita price, or costs less than its other items, is not used.
- **Contracts with a blueprint original (BPO) or an Abyssal item are flagged and set aside.** Neither has a market price,
  and a fair split of the contract price will need a rule for each kind (how to value an original, how to value a
  rolled Abyssal module), so they are not priced and not looked at again for now. The page says how many are set aside.
  Abyssal items are recognised from the SDE's mutaplasmid mappings, not from names. How to split such contracts, and
  how to split a pack of copies, are separate decisions still to be made.
- Offers more than ten times above or below the middle offer for the blueprint are left out as joke or mistaken
  prices, once there are at least three offers.
- Only contracts up to 100 m³ are read: a copy takes 0.01 m³, and larger contracts are ships and cargo.
- The contracts are public data shared by all users. They are read in the background, and no one has to be signed in
  for that: a minute after the app starts, and then every 30 minutes. A region has tens of thousands of
  contracts and each one's contents need a call of their own, so the first run takes about half an hour to an hour,
  and later runs read only the new contracts. The developer decided this on 2026-11-09. The page also has a button
  that reads one more batch, and says how many contracts are still to read, because a price may miss some offers
  until all are read. A progress display is not built yet.
- The price is a third source beside Jita sell and buy prices. Using it in plans (as a price basis, and as the fallback
  for an item with no market orders) comes with the plan features that need a BPC, such as invention.

## Fees

Cost and profit include every fee:

- Job install cost (system cost index, structure bonuses and the SCC surcharge)
- Structure tax
- Broker fee and sales tax, worked out from the selling character's skills and standings. The user picks a default
  selling character, and each plan can choose another.

## Market features

- **My orders**: open buy and sell orders for all characters. When the user opens My orders or refreshes it, orders that
  have been outbid or undercut are marked. Orders are not checked in the background.
- **Sales history**: actual sales are read from wallet transactions, so each project shows its real profit as well as
  its expected profit. When several open projects built the same item, a sale goes to the oldest of them first.
- **Demand check**: price and volume history for each product. Profit ranking uses profit per day, capped at a share of
  the product's average daily volume in Jita over a set number of days, so that items that cannot be sold in quantity
  do not rank high. The share and the number of days are user settings, 10% and 30 days by default.
- **Shopping lists**: export the materials still needed in a format that can be pasted into the in-game multibuy window,
  for one project or for several selected projects added together.

## Contracts

- Only item exchange contracts are tracked. Courier and auction contracts are not needed for now, and corporation
  contracts come with the group phase.
- A contract list shows the outstanding and finished contracts of all characters, with status, price and expiry. It is
  fetched like My orders, when the user opens or refreshes it.
- **Sales**: when an item exchange contract the user issued is completed, the tool suggests which project it belongs to,
  as it does for industry jobs. The user confirms or changes the match. Confirmed contract sales count toward the
  project's actual profit and its units sold, like market sales from wallet transactions.
- **Purchases**: when a contract the user bought is completed, the tool offers to add its items to the materials on hand
  at the contract price.
- **Several items**: when a contract holds several items (for example a ship with its fitting), its price is split
  between them in proportion to each item's Jita price at the time. The user can change the split when confirming a
  sale or a purchase.
- **Alerts**: an alert is raised when a contract is accepted or completed, and when an outstanding contract will expire
  within a set time, a user setting that defaults to 24 hours. The alerts appear with the other alerts (see
  [Manufacturing and invention](manufacturing-invention.md#alerts)).

## Logistics

- **Volume planning**: total m³ of the materials still to buy (inbound) and of the products built (outbound), for one
  project or several added together, like the shopping list. Ships use their packaged volume.
- Hauling cost, courier contracts and ship fitting are not needed for now.

## Open questions

- **Items with their own attributes.** Added 2026-11-09 at the developer's request, not for now. Contract lines have fields
  for blueprints (ME, TE, runs, copy or original) and nothing for other items that differ one from another, mainly
  Abyssal (mutated) modules. Their value comes from the rolled attributes, so a contract line is not worth the type's
  price. ESI gives the line an `item_id` (EveAssist does not read it yet), and `GET /dogma/dynamic/items/{type_id}/{item_id}`
  gives the mutaplasmid, the source item and the attributes, one call per item. Things to decide when this is taken up:
  how to value such an item, whether the extra call per item fits in the background loader's budget (see
  [Background fetches](../architecture/esi.md#background-fetches)), and what the stored line needs. Until then a contract
  that holds one is flagged and set aside (see [Contract prices](#contract-prices)).
