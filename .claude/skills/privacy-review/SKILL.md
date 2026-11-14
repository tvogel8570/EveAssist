---
name: privacy-review
description: Review a pull request (or the current branch) for changes the privacy statement should get, and recommend the wording. Read-only; run ad hoc. Use when the developer says /privacy-review, "does this PR need a privacy policy change", or "check this against the privacy policy".
---

# Privacy review

Decide whether a change makes the privacy policy untrue, incomplete or out of date, and recommend the edits.
The policy is `App/src/main/resources/templates/privacy.html`, served at `/privacy`. The commitments behind it are in
`documentation/requirements/non-functional.md#privacy`, `users.md` (deleting an account, export) and `gain-trust.md`.

This skill **recommends**. It does not edit the policy, commit, push or comment on the PR unless the developer asks
afterwards. It is not a legal review: say so if a recommendation touches a legal duty (GDPR, CCP's license terms).

## Input

`/privacy-review [PR number | branch | nothing]`

- **A number:** `gh pr view <n> --json title,body,baseRefName,headRefName,files` and `gh pr diff <n>`.
- **A branch name:** diff it against `origin/development`: `git diff origin/development...<branch>`.
- **Nothing:** the current branch against `origin/development`, plus uncommitted changes (`git diff HEAD`).
  If the branch is `development`, `main` or `local-dev` and has nothing ahead, say there is nothing to review and stop.

Run `git fetch origin` first so the comparison is current. Strip any handoff section from the PR description before
relying on it (it sits between `<!-- handoff:start -->` and `<!-- handoff:end -->`). The diff is the evidence, the
description only says what the author meant.

## Steps

1. **Read the current policy** in full, and its "Last updated" date. Check `git log -3 --format='%h %ad %s' --date=short -- App/src/main/resources/templates/privacy.html`
   to see whether the PR already touches it. If it does, review that edit too: is it accurate and does it match the code?
2. **Read the diff.** For a large one, list the files first (`git diff --stat`) and read the ones in the signal table
   below before the rest. Skip generated and SDE data.
3. **Match changes to signals** (table below). For each hit, find the policy sentence it affects, or note that none exists.
4. **Check the other direction too.** A change can make an existing sentence false (a retention period, a "never",
   "only", "no other service", the list of cookies). Re-read each policy sentence that contains an absolute and check it
   against the diff.
5. **Check the places that repeat the policy** so they do not drift from it: `messages.properties` and `help.properties`
   (`home.faq.safety`, `account.data.*`), the deletion and export behaviour in `users.md`, `security.txt`, and the
   backup numbers in `documentation/runbooks/update-deployment.md`. Report any that need the same change.
6. **Report** in the format below.

## Signals

| In the diff | Ask | Policy section |
|---|---|---|
| New or changed table, column or entity (Flyway `V*.sql`, JPA entities) | Is it personal data or data about a user's characters, plans or activity? Does export (`Download my data`) and account deletion cover it? | stored, control |
| New ESI scope, endpoint or cached ESI data | Is the data listed? Is the scope still read-only? Does removing a character delete it? | stored, support |
| Token, key, KMS or encryption changes | Does "encrypted", "key held by AWS KMS", "access tokens in memory only" still hold? | stored, security |
| Keycloak realm export, client, mapper, session or event settings | What does Keycloak now keep (names, sessions, addresses, failed-login counts, tokens)? Session lengths (30 min / 10 h)? | stored |
| Logging config, log statements, Caddyfile, CloudWatch settings | What identifiers can reach a log? Retention (30 days)? Do access logs exist now? | stored (logs), visitors |
| New dependency, script, font, image, CDN, analytics, error reporter, email or push service | Does any data or request go to a third party? Does "no font, script or image from another site" and "no other service" still hold? | cookies, where |
| New cookie, `localStorage`, `sessionStorage`, session or CSRF change | Is it essential for sign-in? Is it listed? | cookies |
| New AWS service, region, or data location, backup or snapshot change | Region statement, transfer wording, backup retention (7 days daily, 28 weekly). | where, control |
| Admin or support features, new database roles, operator access, support runbooks | Who can read or change what, and is it audited? | support |
| Rate limiting, IP address use, forwarded headers, `/try` and other pre-sign-in flows | Is an address stored, logged or only held in memory? | visitors |
| Account, character or plan deletion and export code | Do "deleted at once", "everything it holds" and the export contents still hold? | control |
| Groups, corporations, sharing, invitations, public pages (later phases) | Who else can now see a user's data? This usually needs a new section. | stored, new |
| Email, notifications, Discord or other contact channels | Contact data is new personal data. | stored, new |
| Billing, donations, ads, tracking | CCP's terms forbid ads and donation links. Flag as a licensing problem, not only a privacy one. | who, cookies |
| Changes to breach handling, `security.txt`, incident process | "Within one week", the contact address. | incidents, security |

A change with none of these signals needs no policy change. Say that and name the paths you looked at.

## What not to recommend

- Do not add detail the policy does not need. It says what is stored and why, in plain words. Keep that voice.
- Do not promise what the code does not do. If the code and the policy disagree and the code is the thing to change, say
  so, and name the code change rather than softening the policy.
- Do not copy secrets, deployment identifiers or the developer's IP addresses into the report (see CLAUDE.md, Secrets).
- Do not call a policy gap "required by law" unless the requirements docs already say it is required. Say it is a
  question for the developer instead.

## Report format

```
## Privacy review: <PR #n title | branch>

**Verdict:** policy change needed | policy change recommended | no change needed

### Recommended changes
1. <section id> (`#stored`) — <what changes and why, tied to file:line in the diff>
   Current: "<quoted policy sentence, or "nothing">"
   Suggested: "<replacement wording, in the policy's own style>"
   Also update: <other files, if any>

### Questions for the developer
- <anything the diff cannot answer, such as whether a new field counts as personal data>

### Checked, no change needed
- <signal> — <path or reason>

### Also
- Bump "Last updated" in `privacy.html` to <today's date> when any wording changes.
- If the PR changes the policy's meaning for existing users, say whether it deserves a visible note.
```

Keep it short. Quote only the sentences that change. Order recommendations by how wrong the current policy would be
(false statements first, then missing data, then wording).

If the developer says to go ahead, make the edits in `privacy.html` on the PR's branch (ask which branch if it is not
checked out), update the date, and run `./mvnw verify` in `App/` (`PublicPagesIT` checks the page). Ask before committing.
