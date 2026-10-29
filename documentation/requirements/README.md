# EveAssist Requirements

EveAssist is an out-of-game (OOG) tool for industry in EVE Online. The code that is already in `Api/` and `Client/`
was written to try out ESI and the SDE. It is not the basis for these requirements.

## Audience

The tool is built in phases, and later phases must not need a rewrite of earlier ones:

1. **Single player**: one person with several characters, possibly across several accounts.
2. **Small group / corporation**: several people who share data, structures and roles.
3. **Public multi-tenant** (possible): anyone can sign up, and each tenant's data is kept apart.

## Scope of the first requirements

- [Manufacturing and invention](manufacturing-invention.md)
- [Market and logistics](market-logistics.md)
- [Users, accounts and roles](users.md)
- [ESI access and tokens](esi-access.md)

Reactions, planetary industry, mining and reprocessing are out of scope for now.

These features, common in other OOG tools, are also left for later phases: Upwell structure management (fuel and
timers), appraisal, skill planning, stock levels and restocking, corporation buyback and freight services, fitting,
killboards, mapping and LP store analysis. Contract tracking is in scope (see [Market and logistics](market-logistics.md#contracts)).

## Process

The requirements are gathered by interview, one area at a time, and are drafted into the files above as answers come in.
Open questions are listed at the end of each file.
