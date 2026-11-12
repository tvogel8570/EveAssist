# Common Commands

A permanent list of commands used often when working with EveAssist. No secrets here. Add a command when you find yourself
looking it up twice, and say where to run it (dev machine or instance).

The prod instance is `$EVEASSIST_INSTANCE_ID` in `us-west-2`, account `$EVEASSIST_ACCOUNT_ID` (see [deploy-log.md](deploy-log.md)).

## AWS

Run on the dev machine.

Sign in again when the SSO session has expired (`Token has expired and refresh failed`):

```bash
aws sso login --profile EveAssist-admin
```

Open a shell on the prod instance (Session Manager), started in bash. The prompt changes to `ssm-user@ip-...`; if it still shows
the dev machine's user, the session did not start:

```bash
aws ssm start-session --target $EVEASSIST_INSTANCE_ID --profile EveAssist-admin --region us-west-2 \
  --document-name AWS-StartInteractiveCommand --parameters 'command=["bash -l"]'
```

To shorten it, put a function in `~/.bashrc` on the dev machine (the profile, region and target are client-side, so nothing is set on
the instance). A function is better than exporting `AWS_PROFILE`, which would redirect all your other AWS work too:

```bash
prod() {
  local profile=EveAssist-admin region=us-west-2 target=$EVEASSIST_INSTANCE_ID start
  case "${1:-home}" in
    home)  start='cd ~ && exec bash -l' ;;
    stack) start='cd /opt/eveassist/Infrastructure/prod && exec bash -l' ;;
    *)     echo "usage: prod [home|stack]" >&2; return 2 ;;
  esac
  # An expired or missing SSO session makes this call fail; sign in again, then carry on.
  if ! aws sts get-caller-identity --profile "$profile" >/dev/null 2>&1; then
    echo "The AWS session for $profile is missing or expired. Signing in..."
    aws sso login --profile "$profile" --use-device-code || return
  fi
  aws ssm start-session --profile "$profile" --region "$region" --target "$target" \
    --document-name AWS-StartInteractiveCommand --parameters "command=[\"$start\"]"
}
```

`prod` opens the shell in the home directory, `prod stack` in the stack's directory (`/opt/eveassist/Infrastructure/prod`). The `cd` runs
first and `exec` then replaces the starting `sh` with bash, so `exit` ends the session at once.  `sudo` is still needed for Docker commands.

The sign-in is AWS IAM Identity Center's, so there is no password prompt in the terminal. `--use-device-code` prints a web address and
a short code. Open the address in any browser (it works from WSL, where opening a browser automatically often does not), sign in there,
enter the code if asked, and the command carries on by itself.

Without the `--document-name` part, the session starts in plain `sh`, where a multi-line paste goes wrong. Run `bash` first then.
(An account-wide alternative is a Session Manager preferences document, `SSM-SessionManagerRunShell`, with `shellProfile.linux` set to
`exec /bin/bash`. It affects every session in the account and region, and is also where session logging is configured.)

## On the instance

Run inside the SSM shell. In bash a block of several commands pastes correctly (tested 2026-11-12). Plain `sh` does not handle a
multi-line paste, so if the session did not start in bash, type `bash` first. Commands whose output you must check before the next
step are still run as separate blocks.

The stack's directory (read from the running Caddy container; it is `/opt/eveassist/Infrastructure/prod`):

```bash
D=$(sudo docker inspect eveassist-caddy-1 --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'); echo "$D"; cd "$D"
```

Remove the stack's containers and network, keeping the volumes (certificates). Run it from a directory with no Compose file: inside
the stack's directory Compose reads `compose.yaml` and refuses to run without every secret, and `sudo` does not pass your exported
variables. Never add `-v` (it deletes the certificates, and Let's Encrypt limits how many it issues):

```bash
cd ~ && sudo docker compose -p eveassist down
```

Start the stack again (loads the secrets from Secrets Manager, logs in to ECR, then `docker compose up -d`):

```bash
cd /opt/eveassist/Infrastructure/prod && sudo ./start.sh
```

Editor on the instance: `nano` (preinstalled; `Ctrl+O` saves, `Ctrl+X` quits). Prefer writing a file from the repository and checking it
with `diff` over editing by hand on the host; use an editor there only to read a file or for a one-line emergency fix.
