# Users, Accounts and Roles

Status: draft, interview in progress.

## Sign-in

- Users sign in with an EVE Online character (EVE SSO), Facebook, Google or Discord.
- All authentication goes through Keycloak, which brokers the external identity providers.
- EveAssist stores no local accounts or passwords. This applies to admins too.
- Sign-up is open: anyone can create an account by signing in for the first time. There are no invites or
  pre-registered accounts. To bring someone in, an admin or user sends them the site's address. Invites can be added
  if sign-up is ever closed.

## Accounts and sign-in methods

- A sign-in method that EveAssist does not know creates a new account. Accounts are never merged automatically, not
  even by email address.
- A signed-in user can add more sign-in methods to their account from their account page, and remove any of them
  except the last one.
- Signing in with an EVE character only proves who the user is. It does not link that character for ESI data (see
  below).

## EVE characters

- A user can link any number of EVE characters to their account, from one or several EVE accounts. Each one is linked
  through EVE SSO, separately from signing in.
- An account can have no characters. A new user can sign in and look around before linking one.
- Linking requests one fixed set of ESI scopes that covers every feature (skills, implants, blueprints, industry jobs,
  orders, wallet, standings and the rest). Every character has the same scopes.
- A character belongs to one EveAssist account at a time. Linking a character that is already on another account is
  refused. The other account must remove it first, or an admin unlinks it.
- When a user removes a character, its ESI tokens and the ESI data stored for it (blueprints, jobs, wallet
  transactions, skills and the rest) are deleted right away. Plans and projects stay, and the jobs that were assigned
  to the character show as unassigned.
- When a character's ESI token stops working (revoked, expired or missing scopes), the character is marked as needing
  re-authorization. Its stored data stays, but it no longer refreshes, and an alert is raised (see
  [Manufacturing and invention](manufacturing-invention.md#alerts)). Re-authorizing through EVE SSO clears the mark.
- How and when character tokens are used is covered in [ESI access and tokens](esi-access.md).

## Profile

An account profile holds:

- **Display name**, editable. Its default comes from the first sign-in.
- **Main character**, one linked character chosen as the account's face (portrait and name in the header). It is
  optional, since an account can have no characters.
- **Time zone**, used to show job finish times and alerts.
- **Email**, taken from the identity provider when it gives one (EVE SSO does not). It is used for contact.

## Deleting an account

- A user can delete their own account. This deletes the Keycloak user, the linked characters, their ESI tokens and
  data, and all of the user's plans, projects and settings.
- An admin can delete an account in the same way.

## Roles

Roles are app-wide and assigned in Keycloak.

- **Visitor** (not signed in): the home page, marketing pages and tutorials. No tools are public.
- **USER**: every signed-in account gets this role by default. It gives full use of the manufacturing, invention,
  market and logistics features, for the user's own characters and data.
- **ADMIN**: maintains user accounts (see below). It is not on top of USER: an admin account is separate, does not use
  the industry features and has no linked characters.

### Group roles (later phase)

The group and corporation phase adds roles inside each group (owner, manager and member) on top of the app-wide
roles, as described in [Groups and corporations](groups.md#group-roles). They are not built yet, but the account and
role model must leave room for them so that adding groups does not require a rewrite.

## Admin

- An admin signs in with an external identity provider like anyone else. ADMIN is granted and revoked in the Keycloak
  console, not in the app.
- Admins can see account fields (display name, sign-in methods, email, status, dates) and the names and IDs of each
  account's linked characters.
- Admins cannot see any user's EVE data from ESI (wallets, assets, blueprints, jobs, orders and similar), or their
  plans and projects.

Admins can:

- Rename an account (its display name).
- Disable an account, so its user cannot sign in, and re-enable it. Disabling keeps all data.
- Unlink a character from an account, for example to settle an ownership dispute or after a character sale. This
  deletes the character's tokens and ESI data, as when the user removes it.
- Delete an account.

### Audit log

- Every admin action is written to an audit log: which admin, what action, which account and when.
- Entries are kept for one year, then deleted.
- When an account is deleted, its entries stay until they expire, so the deletion itself stays on record. They keep
  the account's id and the display name it had at the time, but no email.
- Admins read the log in the app, read-only. An audit log page in the admin area lists every entry, newest first, and
  can be filtered by admin, account, action and date range. Each account's admin page also shows that account's
  entries.
- Nobody can change or delete an entry in the app.

## Open questions

None at present.
