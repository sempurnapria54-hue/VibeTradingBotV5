#!/usr/bin/env bash
# Boot the app in the `test` profile with a test-scoped Vault token.
#
# Sources VAULT_TOKEN from the local (gitignored) .env.vault.test.local, then
# starts the app via Maven with the `test` profile active. Sourcing here is what
# makes VAULT_TOKEN reach the app process — application.yaml reads it from env
# (token: ${VAULT_TOKEN:}).
#
# Preconditions NOT handled here (boot them separately): Vault unsealed,
# postgres-test up on :5441, the test-scoped token pasted into the env file.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env.vault.test.local"
PLACEHOLDER="<paste-test-scoped-vault-token-here>"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: ${ENV_FILE} not found." >&2
  echo "Run: cp .env.vault.test.local.example .env.vault.test.local" >&2
  echo "then paste a TEST-scoped Vault token into it." >&2
  exit 1
fi

# shellcheck source=/dev/null
source "${ENV_FILE}"

if [[ -z "${VAULT_TOKEN:-}" || "${VAULT_TOKEN}" == "${PLACEHOLDER}" ]]; then
  echo "ERROR: VAULT_TOKEN is unset or still the placeholder in ${ENV_FILE}." >&2
  echo "Paste a real TEST-scoped Vault token before booting." >&2
  exit 1
fi

cd "${REPO_ROOT}/donor"
exec mvn spring-boot:run -Dspring-boot.run.profiles=test
