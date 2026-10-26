---
name: handoff
description: Write a handoff note so work can continue on another computer. Pushes the current branch and records where things stand in the branch's draft pull request. Use when the user says /handoff, "write a handoff", "I'm switching machines", or is stopping for the day.
---

# Handoff

Record where the current work stands so it can be picked up on another computer with `/resume-handoff`.
The note lives in the branch's **draft pull request** on GitHub, not in the repo, so it never clutters the code.

The base branch for work branches and draft PRs is **`development`** (`origin/development`), not `main`.

Running `/handoff` is the user's go-ahead to push the current branch and to create or edit its draft PR.
It is not a go-ahead to commit: always ask before committing.

## 1. Check the branch

```bash
git branch --show-current
git status --short
git log --oneline origin/development..HEAD
```

- **On `development` or `main`:** do not write a PR note on a shared branch. Offer to create a branch for the work
  (`git switch -c <short-topic-name>`; this keeps the current commits and changes). If the user declines, use the
  [fallback](#fallback-no-branch-or-no-gh).
- **Uncommitted changes:** show them in a short summary and ask whether to commit them as a WIP commit
  (`WIP: <what is in progress>`). If the user says no, list them under "Not committed" in the note, because the
  next machine will not have them.

## 2. Push

```bash
git push -u origin HEAD
```

If the push is rejected because the remote branch has moved, stop and tell the user. Do not force-push.

## 3. Find or create the draft PR

```bash
gh pr view --json number,url,isDraft,body
```

- **No PR:** create one as a draft against `development`:
  `gh pr create --draft --base development --title "<short description of the branch's goal>" --body-file <file>`.
  The body is a one-paragraph summary of the branch's goal, then the handoff section.
- **PR exists but is not a draft:** it is in review. Ask before editing its description.
- **PR exists with a base other than `development`:** leave the base as it is and mention it to the user.

## 4. Write the handoff section

The handoff section goes between these markers. Replace everything between them on each handoff and leave the rest of
the PR description as it is. If the markers are missing, add the section at the end of the description.
GitHub keeps the edit history of a PR description, so earlier handoffs are not lost.

```markdown
<!-- handoff:start -->
## Handoff

_<YYYY-MM-DD HH:MM> from `<output of hostname>` · last commit `<short sha>` <subject>_

### Where things stand
<two or three sentences: what works now, what is half done>

### Done this session
- <change, with file paths>

### Decisions
- <decision> — <why; what was rejected>

### Open questions
- <question that needs the user, or "None">

### Next steps
1. <the first concrete thing to do next, with file paths>
2. ...

### Not in git
- <anything the next machine will not get from git: uncommitted changes, edits to `env.properties` or `.env`,
  database or Docker volume state, SDE imports, running containers. "Nothing" if so.>
<!-- handoff:end -->
```

Write it for a reader with no memory of this conversation, which is what the next session will be.
Be specific: name files, classes, commands and error messages. Leave out anything already obvious from the diff.
Never put secrets, passwords, tokens or ESI client credentials in the note. PR descriptions are visible to anyone who
can see the repository.

Save the full new description to a file in the scratchpad directory (or the system temp directory), then:

```bash
gh pr edit --body-file <file>
```

## 5. Report

Tell the user the PR link and the first next step, in one or two lines.

## Fallback (no branch or no `gh`)

If the user wants to stay on `development` or `main`, or `gh` is not installed or not signed in, write the same handoff section
(without the markers) to `documentation/ai/hand-off/HANDOFF.md`, replacing the file's contents.
Ask before committing and pushing it. Delete the file once the work it describes is finished.
