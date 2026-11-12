#!/bin/bash
# Merges NAME=value lines from a file into one Secrets Manager secret, then shreds the file. Prints key names only.
#   AWS_PROFILE=<admin profile> ./load-secrets.sh eveassist/keycloak ./keycloak.env
# For adding or rotating a secret value without the value reaching the terminal, a command line or shell history: the
# developer fills in the file in an editor, this loads it. Existing keys not in the file are kept. Run it where the
# admin profile is signed in (the instance role may read these secrets but not write them).
set -euo pipefail

secret=${1:?usage: load-secrets.sh <secret-name> <file>}
file=${2:?usage: load-secrets.sh <secret-name> <file>}
[ -f "$file" ] || { echo "no such file: $file" >&2; exit 1; }

umask 077
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

current=$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text)
merged=$current
keys=()
while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    name=${line%%=*}
    value=${line#*=}
    [[ "$name" =~ ^[A-Z][A-Z0-9_]*$ ]] || { echo "not a NAME=value line: ${name:0:20}" >&2; exit 1; }
    [ -n "$value" ] || { echo "$name has no value; fill it in or delete the line" >&2; exit 1; }
    merged=$(jq --arg k "$name" --arg v "$value" '. + {($k): $v}' <<<"$merged")
    keys+=("$name")
done < "$file"

[ "${#keys[@]}" -gt 0 ] || { echo "the file has no values" >&2; exit 1; }
printf '%s' "$merged" > "$work/secret.json"
aws secretsmanager put-secret-value --secret-id "$secret" --secret-string "file://$work/secret.json" --query VersionId --output text > /dev/null
shred -u "$file"
echo "Set in $secret: ${keys[*]} (file shredded)"
