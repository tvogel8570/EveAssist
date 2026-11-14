# Automating the deployment

Status: **potential**. These are options the developer has seen and has not decided on. They are not proposed decisions, they are not in
the [open questions](README.md#open-questions), and nothing in the code or the runbooks depends on them. The runbook
[Deploying an update](../runbooks/update-deployment.md) is the procedure today and is done by hand.

What is **agreed** already is in [Deployment and operations](deployment.md): GitHub Actions builds and tests each push, builds the image and
pushes it to Amazon ECR. The runbook still says "**[to build]**" for that step. This page is about how far beyond it a pipeline could go.

## What each section of the runbook needs

| Runbook section | Automatable? | Needs |
|---|---|---|
| 1. Release readiness | Mostly: `./mvnw verify`, a clean `development`. The privacy review is a judgment call and stays a human gate. | CI runner |
| 2. Build and publish | Fully: both images, the inspection, the push, the digests, and the check whether the Keycloak files changed. | arm64 build, ECR push |
| 3. Snapshot | Fully | AWS API |
| 4. Rehearse | Fully: restore, run the image against it, check for health `UP` and "Successfully validated N migrations", delete. | AWS API and a command on the EC2 instance |
| 5. Deploy | Fully, behind an approval | a command on the EC2 instance |
| 6. Verify | Partly: health, the footer, `/try`. Signing in and trying the changed feature stay with a person. | HTTP checks |
| 7. Record | Partly: the tag and the deploy-log text. The pull request to `main` and telling the testers stay manual. | git |

## Options, from least to most

1. **Scripts run by the developer, no CI.** The runbook's commands become one `release.sh` with stages (`build`, `snapshot`, `rehearse`,
   `deploy`, `verify`), each ending with the next stage to run. `Infrastructure/prod/release-status.sh` already reports where a release is.
   The build still runs on the development computer, under emulation for arm64.
2. **GitHub Actions for build and publish only.** A `workflow_dispatch` workflow (a button and a version). It runs verify, builds both images
   natively on GitHub's arm64 runners (free for public repositories), pushes them to ECR and prints the digests. The rest stays the runbook.
   This is the "[to build]" step, and the built commit is provably one that passed verify.
3. **Add AWS Systems Manager Run Command for the EC2 instance.** The pipeline sends `rehearse-app.sh` and `start.sh app` through SSM, which the
   project already uses for shells and the database tunnel, so no new port or SSH key is needed. A GitHub environment `production` with the
   developer as required reviewer makes the deploy wait for approval. A full pipeline that is not continuous.
4. **Automated verify and rollback.** Option 3 plus smoke checks, and a rollback when the health check fails. The most work, and the rollback
   logic is the risky part: leave it until options 1 to 3 have carried several releases.

## If any of it is built

- **No long-lived AWS keys in GitHub.** GitHub proves the workflow's identity by OIDC, and AWS issues short-lived credentials to an IAM role
  that trusts only this repository and only the `production` environment.
- **Narrow roles.** One role that can push to ECR; another that can snapshot, restore and send commands to the instance. Neither is the
  administrator permission set the developer uses today. Whether a role that can restore a snapshot of prod data and run commands on the
  instance may be assumed from GitHub Actions at all is the first thing to decide.
- **Triggers.** `workflow_dispatch` or a protected tag only. Never `pull_request` with secrets, because a fork can open one.
- The rulesets on `development`, `main` and the `v*` tags (see `CLAUDE.md`, "Releasing to main") already protect what a pipeline would touch.
- The runbook's rehearsal against a restored copy of prod stays the safety net: it tests the exact image that would be deployed.

## Where this stands

The developer is running the manual runbook end to end first (release 0.3.0), to prove it. Options 1 and 2 share most of their commands, so
they would be done together, and option 3 only after a few releases have gone through by hand.
