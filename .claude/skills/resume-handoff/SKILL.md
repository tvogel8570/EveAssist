---
name: resume-handoff
description: Pick up work that was handed off from another computer with /handoff. Fetches the branch, reads the handoff note in its draft pull request, and summarizes where things stand. Use when the user says /resume-handoff, "pick up where I left off", "continue from my other machine", or "read the handoff".
---

# Resume a handoff

Continue work that `/handoff` recorded on another computer. The note is in the branch's draft pull request, between
`<!-- handoff:start -->` and `<!-- handoff:end -->`. Draft PRs target **`development`** (`origin/development`).

## 1. Find the branch

```bash
git fetch --prune origin
git branch --show-current
gh pr list --author "@me" --draft --json number,title,headRefName,updatedAt
```

- If the user named a branch or PR, use that one.
- If the current branch has a draft PR, use it.
- Otherwise, if there is one draft PR, use it. If there are several, list them (title, branch, last updated) and ask
  which one.

## 2. Check out and update

Check `git status --short` first. If the working tree has uncommitted changes, stop and ask: they may be work this
machine has that the other one doesn't.

```bash
git switch <branch>
git pull --rebase
```

If the branch is new on this machine, `git switch <branch>` creates it from `origin/<branch>`.

## 3. Read the handoff

```bash
gh pr view <number> --json url,title,body,comments
git log --oneline origin/development..HEAD
git diff --stat origin/development...HEAD
```

Read the handoff section and any PR comments newer than it. Read the files the "Next steps" section names.

If there is no handoff section, check `documentation/ai/hand-off/HANDOFF.md`. If neither exists, say so and summarize
the branch from its commits and diff instead.

## 4. Check this machine

Go through the "Not in git" section and check what you can on this machine. For example, whether
`env.properties` exists, whether the Docker containers from `Infrastructure/compose.yaml` are running, or whether the
database the note mentions is reachable. Report anything missing. Do not create secrets or change the database
without asking.

## 5. Summarize and confirm

Tell the user, briefly:

- the branch, the PR link, and when and where the handoff was written
- where things stand
- open questions from the note that still need an answer
- anything missing on this machine
- the first next step you propose

Then wait for the user to confirm before starting, unless they asked you to continue straight away.

## 6. Offer to remove the handoff section

Once the note has been read, ask the user whether to remove the handoff section from the draft PR's description.
Don't edit the description without their answer.

- **Yes:** save the description to a file in the scratchpad directory (or the system temp directory), with
  everything from `<!-- handoff:start -->` through `<!-- handoff:end -->` removed, markers included. Keep the rest of
  the description as it is. Then run `gh pr edit <number> --body-file <file>`, and check with
  `gh pr view <number> --json body` that no marker is left. The next `/handoff` adds a new section at the end.
- **No:** leave the section as it is. The next `/handoff` replaces it.

If the note came from `documentation/ai/hand-off/HANDOFF.md`, ask instead whether to delete the file. Deleting it
needs a commit, so ask before committing.
