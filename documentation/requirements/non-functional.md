# Non-functional Requirements

Status: draft, interview in progress.

## Environments and hosting

- **Dev** runs on the local machine in use, with Docker Compose (see `Infrastructure/`).
- **Prod** runs in AWS.
- There is no staging environment. Changes are checked locally before they are deployed to prod.

## Scale

The design must handle the largest number for each phase without a rewrite:

| Phase | Users |
|---|---|
| Single player | 1 |
| Small group / corporation | about 50 |
| Public multi-tenant | a few thousand |

## Availability and backups

- Availability is best effort. Downtime is acceptable.
- Postgres, which holds both the app's data and Keycloak's, is backed up daily. Seven daily and four weekly backups are
  kept.
- Restoring from a backup is tested.

## Performance

- Pages load in about 1 second from stored data.
- A large plan, for example a capital ship with every intermediate decided build or buy, is costed in a few seconds.
- Long ESI fetches, such as a sign-in with many characters, run in the background and show their progress.

## Monitoring

- Prod logs are collected in one place. Logs never contain secrets or tokens.
- Health checks run on each service.
- The operator is alerted when errors or ESI failures spike.

## User interface

- Built for desktop browsers first. On a phone, at least the dashboard, alerts and job status must be usable.
- The UI is in English. Its text is kept out of the code so that other languages can be added later. Item names are
  shown in English from the SDE.

## Privacy

From the first prod deployment that anyone other than the operator can sign in to:

- A privacy policy says what is stored and why.
- Only the data a feature needs is collected.
- Users can export all their data (account, characters, plans, projects and settings) as JSON or CSV.
- Users can delete their account and all its data (see [Users, accounts and roles](users.md#deleting-an-account)).
- Only essential cookies are used.

## CCP license

EveAssist follows CCP's [Developer License Agreement](https://developers.eveonline.com/license-agreement):

- EveAssist is free. It takes no payment, donations or ads. The license allows only ISK payments, donations to cover
  running costs and non-intrusive ads, so any change to this must be checked against it first.
- CCP's copyright notice is shown, and CCP's logos are not combined with other marks.
- Character data is read only for characters the user linked through EVE SSO, which is their consent.
- CCP can disable an application's ESI and SSO access without notice, so the operator's contact email with CCP is kept
  current. CCP may suspend access when three of its emails in a row go unanswered.

## Security

- Secrets are never committed (see `CLAUDE.md`). In prod they come from a secrets store, not from files in the image.
- All traffic uses TLS.
- ESI refresh tokens are encrypted at rest (see [ESI access and tokens](esi-access.md)).
- Admin actions are written to an audit log (see [Users, accounts and roles](users.md#admin)).

## Open questions

None at present.
