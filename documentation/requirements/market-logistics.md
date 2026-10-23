# Market and Logistics

Status: draft, interview in progress.

## Price source

- Prices come only from Jita (The Forge region, Jita 4-4). Other trade hubs and player-owned structure markets are not
  needed for now.
- Prices are refreshed on demand, when the user opens a plan or asks for a refresh. There is no background refresh.
- Each price shows when it was fetched.

## Price basis

- By default, materials are costed at the Jita sell price (bought right away) and products are valued at the Jita sell
  price (listed as sell orders).

## Fees

Cost and profit include every fee:

- Job install cost (system cost index, structure bonuses and the SCC surcharge)
- Structure tax
- Broker fee and sales tax, worked out from the selling character's skills and standings

## Market features

- **My orders**: open buy and sell orders for all characters, with an alert when an order is outbid or undercut.
- **Sales history**: actual sales are read from wallet transactions, so each project shows its real profit as well as
  its expected profit.
- **Demand check**: price and volume history for each product. Profit ranking uses profit per day, capped at 10% of the
  product's average daily volume in Jita over the last 30 days, so that items that cannot be sold in quantity do not
  rank high.
- **Shopping lists**: export the materials still needed in a format that can be pasted into the in-game multibuy window.

## Logistics

- **Volume planning**: total m³ for the materials to move and the products to ship, so the user can plan trips. Ships
  use their packaged volume.
- Hauling cost, courier contracts and ship fitting are not needed for now.

## Open questions

None at present.
