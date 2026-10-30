# Gaining Players' Trust

Status: draft. Proposed, not yet agreed with the developer, except for sections marked **agreed** and the
requirements that the first section points to.

EVE Online is known for in-game scams and theft, and many players will not give a hosted tool their characters' ESI
tokens. With a hosted EveAssist, players must trust both the code and whoever runs it, and they can check only the
code. So trust is earned by limiting what any token can do, by being open about what the server holds and does, and by
making it cheap to leave.

The standalone alternative, where tokens never leave the player's computer, is discussed in
[Standalone deployment](../architecture/alt-deploy/standalone.md).

## Already agreed elsewhere

- Refresh tokens are encrypted at rest and never logged or shown ([ESI access and tokens](esi-access.md)).
- Character data is fetched only while the user is signed in ([ESI access and tokens](esi-access.md#when-character-data-is-fetched)).
- Removing a character deletes its tokens and ESI data at once ([Users, accounts and roles](users.md)).
- Admins cannot see any user's EVE data, plans or projects, and every admin action is written to an audit log
  ([Users, accounts and roles](users.md#admin)).
- Users can export all their data and delete their account. There is a privacy policy, only essential cookies are used,
  and EveAssist is free with no ads ([Non-functional requirements](non-functional.md#privacy)).

## What a token can and cannot do

- EveAssist says plainly, on the character-linking page and in the privacy policy, that ESI has no endpoint that moves
  ISK, items or ships. A stolen token can leak information, such as intel for a scam or a war target, but cannot take
  assets.
- ESI does have endpoints that act as a character: sending and deleting mail, changing contacts, saving and deleting
  fittings, managing fleets, answering calendar events, and opening windows or setting waypoints in the game client
  (checked against ESI's OpenAPI spec). EveAssist commits publicly to **read-only scopes**, so that even a breach cannot
  act as a character. Any later feature that needs a write scope must be checked against this commitment first.

## Scopes

Status: **agreed**. The developer chose scopes per feature over the earlier fixed set.

- Each scope is asked for only when a feature that needs it is turned on, and the page says which feature needs it.
- Features are turned on for the whole account, not per character. Turning one on asks the user to re-authorize each
  linked character that lacks its scopes, and a character linked later is asked for the scopes of every feature that
  is on.
- A character's page lists the scopes it granted and what each is used for, with a way to drop scopes the user no
  longer wants. Features that need a dropped scope stop for that character, as for a missing scope today
  ([ESI access and tokens](esi-access.md#failures)).

## Revoking access

- Removing a character in EveAssist deletes its token at once (already agreed).
- The same page links to CCP's third-party applications page, where the user can revoke EveAssist's access without
  relying on EveAssist.

## Showing what the server does

- **Fetch log**: each character has a log the user can see, of which ESI endpoints were called for it and when. It
  shows that nothing is read beyond what the user set up. Entries are kept for a limited time.
- **Admin actions on the user's own account**: the audit log entries for an account are also shown to that account's
  user, read-only.
- **Data kept to what features use**: only the ESI fields a feature uses are stored, and data that no feature uses any
  more is deleted on a schedule.

## Open source and traceable builds

- The source is public.
- Images are built only by GitHub Actions from the public repository. The image digest and a GitHub artifact
  attestation are published for each release.
- The page footer shows the commit that is running.
- EveAssist says honestly that a user can see what is supposed to be running but cannot prove the server runs exactly
  that.

## No third parties in the page

- All scripts, styles and fonts are served by EveAssist itself, with no CDN links.
- A strict Content Security Policy is sent with every page.
- No analytics or tracking scripts are used.

## Operator transparency

- The operator is named on the site. CCP's license already requires a watched contact email
  ([Non-functional requirements](non-functional.md#ccp-license)).
- The site publishes `security.txt` with a way to report vulnerabilities.
- Security incidents that affect users' data are disclosed to them within a stated time.

## Groups (phase 2)

Corporation spies are a real threat in EVE, so EveAssist must not become a spying channel.

- Sharing with a group is opt-in for each item.
- Every shared item shows who can see it.
- Group roles, including directors, never give access to a member's characters or data unless that member shared
  them.

## Leaving

- The user can move from hosted EveAssist to a standalone or self-hosted one: hosted export produces a file that a
  standalone or self-hosted install can import. "Don't trust us? Run the same code yourself" is the strongest trust
  signal.
- This depends on the standalone decision, which is still open.

## Open questions

- **Fetch log retention**: how long fetch log entries are kept.
- **Incident disclosure time**: what time to commit to, for example 72 hours.
- **Export and import between hosted and standalone**: depends on whether a standalone build is made.
