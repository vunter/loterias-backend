#!/bin/bash
# Fetches secrets from HashiCorp Vault and exports as environment variables.
# Usage: source vault-env.sh (before starting the application)
# Requires: VAULT_ADDR, VAULT_ROLE_ID, VAULT_SECRET_ID

set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:?VAULT_ADDR not set}"
VAULT_ROLE_ID="${VAULT_ROLE_ID:?VAULT_ROLE_ID not set}"
VAULT_SECRET_ID="${VAULT_SECRET_ID:?VAULT_SECRET_ID not set}"

# Authenticate with AppRole
VAULT_TOKEN=$(curl -sf "${VAULT_ADDR}/v1/auth/approle/login" \
  -d "{\"role_id\":\"${VAULT_ROLE_ID}\",\"secret_id\":\"${VAULT_SECRET_ID}\"}" \
  | jq -r '.auth.client_token')

if [ -z "$VAULT_TOKEN" ] || [ "$VAULT_TOKEN" = "null" ]; then
  echo "ERROR: Failed to authenticate with Vault" >&2
  exit 1
fi

# Read secrets from KV v2
SECRETS=$(curl -sf -H "X-Vault-Token: ${VAULT_TOKEN}" \
  "${VAULT_ADDR}/v1/secret/data/loterias" \
  | jq -r '.data.data')

if [ -z "$SECRETS" ] || [ "$SECRETS" = "null" ]; then
  echo "ERROR: Failed to read secrets from Vault" >&2
  exit 1
fi

# Export as environment variables (dot-notation keys become uppercase with underscores)
export DATABASE_URL=$(echo "$SECRETS" | jq -r '."database.url"')
export DATABASE_USERNAME=$(echo "$SECRETS" | jq -r '."database.username"')
export DATABASE_PASSWORD=$(echo "$SECRETS" | jq -r '."database.password"')
export DATADOG_API_KEY=$(echo "$SECRETS" | jq -r '."datadog.api-key"')
export DATADOG_SITE=$(echo "$SECRETS" | jq -r '."datadog.site"')
export LOKI_URL=$(echo "$SECRETS" | jq -r '."loki.url" // empty')
export ADMIN_API_KEY=$(echo "$SECRETS" | jq -r '."admin.api-key" // empty')

# Clear sensitive variables from environment
unset VAULT_TOKEN
unset SECRETS

echo "Vault secrets loaded successfully"
