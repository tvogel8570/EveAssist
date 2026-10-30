# Groups and Corporations

Status: draft, interview in progress.

This is the second phase (see the [README](README.md#audience)). Nothing here is built in the single-player phase,
but the data model of that phase must leave room for it.

## Kinds of group

- A **corporation** is always an in-game EVE corporation. Corporation groups are built first, in phase 2.
- A **coalition** is a group made in EveAssist that can include characters from any corporation. Coalitions come
  later, but the model must allow for them.
- "Group" means either kind.

## Membership

- An EveAssist account joins a group, and the user chooses which of their linked characters take part. Other members
  see only those characters.
- An account can belong to several groups.
- A corporation group is created by a user whose linked character is the corporation's CEO or a Director, checked
  through ESI. There is at most one group per corporation.
- Any user with a character in the corporation can see its group and choose to join. Managers can remove members.
- Corporation membership is read from ESI. A shared character must be in the corporation in game, checked at each
  fetch. A character that has left the corporation is dropped from the group.

## Group roles

These are roles inside each group, on top of the app-wide roles in [Users, accounts and roles](users.md#roles).

- **Owner**: everything a manager can do, plus deleting the group and transferring ownership. The user who creates
  the group is its first owner.
  When the owner's character leaves the corporation, ownership passes to the manager who has been one longest. If
  there is none, it passes to a member whose character is the CEO or a Director. If there is none of those either, the
  group has no owner until a CEO or Director claims it.
- **Manager**: manages shared facilities, links corporation data and removes members.
- **Member**: shares their own blueprints, and sees and plans with blueprints others share.

## What a group shares

Plans, projects and materials on hand stay personal. A group shares:

- **Blueprints**: all blueprints of a member's shared characters are shared by default. The user can withdraw them one
  at a time.
- **Facilities**: the group's structures, with their rigs and tax.
- **Corporation blueprints** read from ESI are shared with every member. Jobs that use them are marked for a
  corporation member with access to the hangar they are in.

Other members see a shared character's name and portrait and its shared blueprints, nothing more (no skills, slots or
other ESI data).

## Planning with shared blueprints

- A user's plans can use blueprints shared in their groups.
- A job that needs another member's blueprint is assigned to the owner's character, or marked "ask the owner for a
  copy or the build".
- When the owner fetches their character's industry jobs, the status of jobs that belong to other members' plans
  (started, finished) is passed on to those plans. The planner sees only that status, not the owner's other ESI data.
- When a member leaves or withdraws a blueprint, the plans and projects that use it are flagged, as after an SDE change.
  The user picks another source: their own blueprint, buying, or another member's blueprint.

## Corporation data from ESI

- Corporation data (corporation blueprints, industry jobs and structures) needs a character with the in-game roles that
  ESI requires for each endpoint. A member whose character has those roles grants the corporation scopes.
- Several role-holders can link their characters. Following [ESI access and tokens](esi-access.md), a role-holder's
  token is used only while that role-holder is signed in. Other members see the stored data with its age.

## Shared facilities

- Corporation structures are read from ESI with a role-holder's token. `GET /corporations/{corporation_id}/structures`
  gives the type and system and, when available, the name.
- When ESI does not give the name or system, a manager can enter them. Both are optional.
- ESI gives neither rigs nor tax rate, so managers enter them by hand.
- To check during the build: the user has seen the name and system missing in practice, while the ESI spec lists
  `system_id` as always present and `name` as optional. Test with a Station_Manager token when facilities are built.

## Open questions

None at present.
