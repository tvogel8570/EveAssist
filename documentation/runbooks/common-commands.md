# Common Commands

A permanent list of commands used often when working with EveAssist. No secrets here. Add a command when you find yourself
looking it up twice, and say where to run it (dev machine or instance).

## Values

The repository is public, so the identifiers of EveAssist's AWS setup (account id, instance id, RDS endpoint, security groups and so on) are
not in it. The commands in the runbooks name them as variables, for example `$EVEASSIST_INSTANCE_ID`, and the values are in
`Infrastructure/prod/ops.env`, which git ignores. Create it once on each computer from `Infrastructure/prod/template_ops.env`: the template
gives the AWS command that looks up each value (the commands need a signed-in admin profile), or copy the file from another computer.
No secret belongs in it. Load it before running a command that uses a variable:

```bash
set -a; . ~/git/EveAssist/Infrastructure/prod/ops.env; set +a
```

The instance is in `us-west-2`. Where a value is needed on the instance itself (for example `RDS_HOST` in its `prod.env`), the instance has its
own copy of the file that needs it; the instance role is deliberately not allowed to look things up.

## AWS

Run on the dev machine.

Sign in again when the SSO session has expired (`Token has expired and refresh failed`):

```bash
aws sso login --profile EveAssist-admin
```

Open a shell on the prod instance (Session Manager), started in bash. The prompt changes to `ssm-user@ip-...`; if it still shows
the dev machine's user, the session did not start:

```bash
aws ssm start-session --target "$EVEASSIST_INSTANCE_ID" --profile EveAssist-admin --region us-west-2 \
  --document-name AWS-StartInteractiveCommand --parameters 'command=["bash -l"]'
```

To shorten it, put a function in `~/.bashrc` on the dev machine (the profile, region and target are client-side, so nothing is set on
the instance). A function is better than exporting `AWS_PROFILE`, which would redirect all your other AWS work too:

```bash
prod() {
  # the instance id comes from ops.env (see "Values"); loaded here so that the function works in any shell
  local ops=~/git/EveAssist/Infrastructure/prod/ops.env
  [ -r "$ops" ] || { echo "Missing $ops: create it from template_ops.env" >&2; return 1; }
  local profile=EveAssist-admin region=us-west-2 target start
  target=$(. "$ops" >/dev/null 2>&1; echo "$EVEASSIST_INSTANCE_ID")
  [ -n "$target" ] || { echo "EVEASSIST_INSTANCE_ID is empty in $ops" >&2; return 1; }
  case "${1:-home}" in
    home)  start='cd ~ && exec bash -l' ;;
    stack) start='cd /opt/eveassist/Infrastructure/prod && exec bash -l' ;;
    *)     echo "usage: prod [home|stack]" >&2; return 2 ;;
  esac
  # A dropped new connection (this WSL machine drops about 1 in 25) would otherwise hang for the CLI's default 60 s: give up after 5 s and retry.
  local retry=(--cli-connect-timeout 5)
  export AWS_MAX_ATTEMPTS=5
  # An expired or missing SSO session makes this call fail; sign in again, then carry on.
  if ! aws "${retry[@]}" sts get-caller-identity --profile "$profile" >/dev/null 2>&1; then
    echo "The AWS session for $profile is missing or expired. Signing in..."
    aws sso login --profile "$profile" --use-device-code || return
  fi
  aws "${retry[@]}" ssm start-session --profile "$profile" --region "$region" --target "$target" \
    --document-name AWS-StartInteractiveCommand --parameters "command=[\"$start\"]"
}
```

The retry settings only cover the API calls (the sign-in check and starting the session), not the websocket that the Session Manager plugin
opens afterwards: if a session hangs at "Starting session", press Ctrl+C and run `prod` again. (If the sign-in check fails because the network
dropped a connection and not because the session expired, it signs you in again, which is harmless.)

`prod` opens the shell in the home directory, `prod stack` in the stack's directory (`/opt/eveassist/Infrastructure/prod`). The `cd` runs
first and `exec` then replaces the starting `sh` with bash, so `exit` ends the session at once.  `sudo` is still needed for Docker commands.

The sign-in is AWS IAM Identity Center's, so there is no password prompt in the terminal. `--use-device-code` prints a web address and
a short code. Open the address in any browser (it works from WSL, where opening a browser automatically often does not), sign in there,
enter the code if asked, and the command carries on by itself.

Without the `--document-name` part, the session starts in plain `sh`, where a multi-line paste goes wrong. Run `bash` first then.
(An account-wide alternative is a Session Manager preferences document, `SSM-SessionManagerRunShell`, with `shellProfile.linux` set to
`exec /bin/bash`. It affects every session in the account and region, and is also where session logging is configured.)

## Alarms

Run on the dev machine. The alarms are in `us-west-2`, except the site-down alarm, which is in `us-east-1` (Route 53 health metrics exist
only there). Stopping an alarm's actions silences its emails and its OK emails; the alarm still changes state, so its history keeps the
episode. Always switch them back on.

Planned restarts of the stack (`docker compose down` and `start.sh`, a switch to another database) make Caddy log a few "could not reach the
app" errors while the app and Keycloak start, which raises `eveassist-prod-upstream-unreachable` (5 or more in 5 minutes). Silence it for
the restart:

```bash
aws cloudwatch disable-alarm-actions --profile EveAssist-admin --region us-west-2 --alarm-names eveassist-prod-upstream-unreachable
```

If the restart can take a couple of minutes or more, also silence the site-down alarm (a Route 53 health check of `/privacy`, which fails
while the app is down):

```bash
aws cloudwatch disable-alarm-actions --profile EveAssist-admin --region us-east-1 --alarm-names eveassist-prod-site-down
```

Switch them back on when the stack is healthy again:

```bash
aws cloudwatch enable-alarm-actions --profile EveAssist-admin --region us-west-2 --alarm-names eveassist-prod-upstream-unreachable
aws cloudwatch enable-alarm-actions --profile EveAssist-admin --region us-east-1 --alarm-names eveassist-prod-site-down
```

Check that nothing was left silenced (prints the names of alarms whose actions are off; no output means all are on):

```bash
for r in us-west-2 us-east-1; do aws cloudwatch describe-alarms --profile EveAssist-admin --region $r --query 'MetricAlarms[?ActionsEnabled==`false`].AlarmName' --output text; done
```

The other alarms (`eveassist-prod-errors`, `-esi-failures`, `-instance-status`, `-cpu-credits`, and the billing alarm in `us-east-1`) are left
on: a real ERROR during a restart is worth an email.

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
