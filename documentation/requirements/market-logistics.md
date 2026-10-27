# Market and Logistics

Status: draft, interview in progress.

## Price source

- Prices come only from Jita (The Forge region, Jita 4-4). Other trade hubs and player-owned structure markets are not
  needed for now.
- Prices are refreshed on demand, when the user opens a plan or asks for a refresh. There is no background refresh.
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

## Logistics

- **Volume planning**: total m³ of the materials still to buy (inbound) and of the products built (outbound), for one
  project or several added together, like the shopping list. Ships use their packaged volume.
- Hauling cost, courier contracts and ship fitting are not needed for now.

## Open questions

None at present.
