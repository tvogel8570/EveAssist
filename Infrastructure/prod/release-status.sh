#!/bin/bash
# Where am I in a release? Looks at what exists (git, Docker, ECR, RDS) and says which section of
# documentation/runbooks/update-deployment.md is done and which is next. Read-only: it changes nothing.
# Run it on the development computer, signed in first:   aws sso login --profile EveAssist-admin
#   ./Infrastructure/prod/release-status.sh 0.3.0        # or set VERSION and give no argument
# What it cannot see: the EC2 computer (sections 5 and 6: images.env, the running container) and anything you did in a browser.
set -u

# The version to report on comes from the command line; the VERSION variable of your shell is what the runbook commands use, so the two
# must agree. The variable is only visible to this script if it is exported (export VERSION=0.3.0).
ARG_VERSION=${1:-}
ENV_VERSION=${VERSION:-}
VERSION=${ARG_VERSION:-$ENV_VERSION}
[ -n "$VERSION" ] || { echo "ERROR no version: give it on the command line ($0 0.3.0) or export VERSION" >&2; exit 2; }
PROFILE=EveAssist-admin
SNAPSHOT=pre-v${VERSION//./-}
REHEARSAL=eveassist-rehearsal
a() { timeout 60 aws --profile "$PROFILE" --no-cli-pager "$@"; }

if ! a sts get-caller-identity >/dev/null 2>&1; then
  echo "Not signed in: aws sso login --profile $PROFILE --use-device-code" >&2
  exit 1
fi

mark() { printf '  %-4s %s\n' "$1" "$2"; }   # mark DONE|TODO|INFO text

echo "Release $VERSION   (git: $(git branch --show-current) at $(git rev-parse --short HEAD))"
echo

echo "VERSION variable"
VERSION_OK=yes
if [ -z "$ENV_VERSION" ]; then
  VERSION_OK=no
  mark ERROR "VERSION is empty in your environment: the runbook commands would use an empty version. Run: export VERSION=$VERSION"
  mark INFO  "(a VERSION set without 'export' is not visible to this script: export it, or run this script with the variable on its own line: VERSION=$VERSION $0 $VERSION)"
elif [ -n "$ARG_VERSION" ] && [ "$ENV_VERSION" != "$ARG_VERSION" ]; then
  VERSION_OK=no
  mark ERROR "VERSION in your environment is '$ENV_VERSION' but the version you asked about is '$ARG_VERSION'. Run: export VERSION=$ARG_VERSION"
else
  mark DONE "VERSION=$ENV_VERSION in your environment"
fi
echo

echo "Section 2: build and publish"
for pair in "app:eveassist-app" "keycloak:eveassist-keycloak"; do
  name=${pair%%:*}; repo=${pair#*:}
  local_tags=$(docker images --format '{{.Tag}}' "*/$repo" 2>/dev/null | grep "^$VERSION-" | tr '\n' ' ')
  ecr_tags=$(a ecr describe-images --repository-name "$repo" --query 'imageDetails[].imageTags[]' --output text 2>/dev/null \
    | tr '\t' '\n' | grep "^$VERSION-" | tr '\n' ' ')
  [ -n "$local_tags" ] && mark INFO "$name image built locally: $local_tags" || mark INFO "$name image: no local build of $VERSION"
  if [ -n "$ecr_tags" ]; then mark DONE "$name image in ECR: $ecr_tags"
  elif [ "$name" = keycloak ]; then mark INFO "keycloak: nothing pushed (right if section 2 step 4 said the Keycloak files are unchanged)"
  else mark TODO "$name image is not in ECR"; fi
  [ "$name" = app ] && APP_PUSHED=$ecr_tags
done
echo

echo "Section 3: snapshot"
snap=$(a rds describe-db-snapshots --db-snapshot-identifier "$SNAPSHOT" --query 'DBSnapshots[0].[Status,SnapshotCreateTime]' --output text 2>/dev/null)
SNAP_TIME=$(echo "$snap" | awk '{print $2}')
if [ -n "$snap" ] && [ "$snap" != None ]; then mark DONE "snapshot $SNAPSHOT is $(echo "$snap" | awk '{print $1}')"; SNAP=yes; else mark TODO "no snapshot $SNAPSHOT"; SNAP=no; SNAP_TIME=; fi
echo

echo "Section 4: rehearsal"
inst=$(a rds describe-db-instances --db-instance-identifier "$REHEARSAL" --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null)
# Only events after this release's snapshot count: an earlier release used the same instance name.
events=
[ -n "$SNAP_TIME" ] && events=$(a rds describe-events --source-type db-instance --source-identifier "$REHEARSAL" \
  --start-time "$SNAP_TIME" --query 'Events[].Message' --output text 2>/dev/null)
if [ -n "$inst" ] && [ "$inst" != None ]; then
  mark INFO "$REHEARSAL exists, status $inst: run the new image against it (step 2), then delete it (step 5)"; REH=exists
elif echo "$events" | grep -qi "deleted"; then
  mark DONE "$REHEARSAL was created and has been deleted (RDS events since the snapshot)"; REH=deleted
else
  mark TODO "$REHEARSAL does not exist and has no events since the snapshot: the restore (step 1) has not run"; REH=none
fi
echo

echo "Sections 5 and 6: deploy and verify   (not visible from here)"
mark INFO "check on the EC2 computer: images.env and 'sudo docker ps'; and the footer of the site shows the commit id"
echo

echo "Section 7: record"
tagged=$(git tag --list "v$VERSION")
[ -n "$tagged" ] && mark DONE "git tag v$VERSION exists" || mark TODO "no git tag v$VERSION yet (tag only after the deploy was checked)"
grep -q "$VERSION" documentation/runbooks/deploy-log.md 2>/dev/null && mark INFO "deploy-log.md mentions $VERSION" || mark TODO "deploy-log.md does not mention $VERSION"
echo

echo -n "Next: "
if [ -z "${APP_PUSHED:-}" ]; then echo "section 2, build and push the image."
elif [ "$SNAP" = no ]; then echo "section 3, take the snapshot."
elif [ "$REH" = none ]; then echo "section 4, step 1, restore the snapshot to $REHEARSAL."
elif [ "$REH" = exists ]; then echo "section 4, finish the rehearsal on $REHEARSAL, then delete it."
elif [ -z "$tagged" ]; then echo "section 5, deploy, if the rehearsal passed; if you already deployed, section 6, verify."
else echo "section 7, finish the record."; fi
[ "$VERSION_OK" = yes ] || { echo; echo "Fix the VERSION variable first (see above): the runbook commands read it."; exit 1; }
