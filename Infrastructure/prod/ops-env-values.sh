#!/bin/bash
# Looks up the current value of every name in ops.env from AWS and prints them as NAME=value lines, ready to paste into ops.env.
# Read-only: it changes nothing in AWS and does not write ops.env. The lookups are the same commands as the comments in template_ops.env.
# Run it on the development computer, signed in first:   aws sso login --profile EveAssist-admin
#   ./Infrastructure/prod/ops-env-values.sh
# If ops.env exists next to this script, a line whose value differs from it ends with "# DIFFERS from ops.env" (the old value is not
# printed). The output holds identifiers: do not commit it, paste it into an issue or a pull request.
set -u

PROFILE=EveAssist-admin
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

# Every aws call names the profile; nothing depends on the default profile or on AWS_PROFILE.
a() { aws --profile "$PROFILE" "$@"; }

if ! a sts get-caller-identity >/dev/null 2>&1; then
  echo "Not signed in: aws sso login --profile $PROFILE --use-device-code" >&2
  exit 1
fi

# The current values, read in a subshell so that this script's own variables stay untouched.
old() { [ -r "$HERE/ops.env" ] && ( set -a; . "$HERE/ops.env" >/dev/null 2>&1; eval "printf '%s' \"\${$1:-}\"" ); }

show() {  # show NAME VALUE
  local name=$1 value=$2 mark=
  if [ -z "$value" ] || [ "$value" = None ]; then
    echo "$name=   # NOT FOUND: check the lookup in template_ops.env"
    return
  fi
  if [ -r "$HERE/ops.env" ] && [ "$(old "$name")" != "$value" ]; then mark="   # DIFFERS from ops.env"; fi
  echo "$name=$value$mark"
}

ACCOUNT_ID=$(a sts get-caller-identity --query Account --output text)
show EVEASSIST_ACCOUNT_ID "$ACCOUNT_ID"

INSTANCE_ID=$(a ec2 describe-instances --filters Name=tag:Name,Values=eveassist-prod Name=instance-state-name,Values=running \
  --query 'Reservations[].Instances[].InstanceId' --output text)
show EVEASSIST_INSTANCE_ID "$INSTANCE_ID"

show EVEASSIST_RDS_HOST "$(a rds describe-db-instances --db-instance-identifier eveassist-prod \
  --query 'DBInstances[0].Endpoint.Address' --output text)"

show EVEASSIST_RDS_MASTER_SECRET_ARN "$(a rds describe-db-instances --db-instance-identifier eveassist-prod \
  --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)"

show EVEASSIST_EC2_SG "$(a ec2 describe-security-groups --filters Name=group-name,Values=eveassist-ec2 \
  --query 'SecurityGroups[0].GroupId' --output text)"

show EVEASSIST_RDS_SG "$(a ec2 describe-security-groups --filters Name=group-name,Values=eveassist-rds \
  --query 'SecurityGroups[0].GroupId' --output text)"

# Elastic IP and its allocation id come from one call: two fields, tab separated.
read -r EIP EIP_ALLOCATION < <(a ec2 describe-addresses \
  --query 'Addresses[?InstanceId==`'"$INSTANCE_ID"'`].[PublicIp,AllocationId]' --output text)
show EVEASSIST_ELASTIC_IP "${EIP:-}"
show EVEASSIST_EIP_ALLOCATION "${EIP_ALLOCATION:-}"

# The zone id without the /hostedzone/ prefix.
ZONE=$(a route53 list-hosted-zones-by-name --dns-name eveassist.net --query 'HostedZones[0].Id' --output text)
show EVEASSIST_ZONE_ID "${ZONE#/hostedzone/}"

show EVEASSIST_KMS_KEY_ID "$(a kms describe-key --key-id alias/eveassist-token-key --query KeyMetadata.KeyId --output text)"

# The support access role's trust policy: the account's SSO administrator role.
show EVEASSIST_SSO_ADMIN_ROLE_ARN "$(a iam list-roles --path-prefix /aws-reserved/sso.amazonaws.com/ \
  --query 'Roles[?contains(RoleName, `AdministratorAccess`)].Arn' --output text)"

# The database's resource id, for rds-db:connect (not its name).
show EVEASSIST_RDS_RESOURCE_ID "$(a rds describe-db-instances --db-instance-identifier eveassist-prod \
  --query 'DBInstances[0].DbiResourceId' --output text)"
