#!/bin/bash
# Prod only. Starts the stack, or applies a changed image or setting. Run it from anywhere on the instance:
#   /opt/eveassist/Infrastructure/prod/start.sh [docker compose up arguments, e.g. app]
# The three Secrets Manager secrets become environment variables of this process only, so no .env file with secrets
# exists on disk. The images to run come from images.env (see images.env.template).
set -euo pipefail
cd "$(dirname "$0")"

# prod.env and images.env are plain NAME=value files; exporting them lets compose.yaml substitute them
set -a
. ./prod.env
. ./images.env
set +a

for secret in eveassist/db eveassist/keycloak eveassist/app; do
    # @sh quotes each value, so a secret may hold any character
    eval "$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text \
        | jq -r 'to_entries[] | "export \(.key)=\(.value | @sh)"')"
done

aws ecr get-login-password | docker login --username AWS --password-stdin "${APP_IMAGE%%/*}"
docker compose up -d --remove-orphans "$@"
docker compose ps
