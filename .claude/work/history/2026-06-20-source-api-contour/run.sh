#!/usr/bin/env bash
# Runner for source-api live contour against demo-OKX.
# Usage:
#   run.sh <logfile> [extra mvn args...]
# Sources JDK25 + Maven 3.9.11 + Vault test token, runs source-api-live tag.
set -uo pipefail

export JAVA_HOME=/home/romankrdr/.jdks/corretto-25
export PATH="$JAVA_HOME/bin:/home/romankrdr/.m2/wrapper/dists/apache-maven-3.9.11-bin/6mqf5t809d9geo83kj4ttckcbc/apache-maven-3.9.11/bin:$PATH"

PROJ=/home/romankrdr/IdeaProjects/VibeTradingBotV5
cd "$PROJ" || exit 2

# Vault token for the `test` profile (datasource + okx-test creds).
# shellcheck disable=SC1091
source "$PROJ/.env.vault.test.local"

LOG="${1:?logfile required}"
shift || true

echo "=== RUN START $(date -u +%FT%TZ) ===" | tee "$LOG"
echo "JAVA_HOME=$JAVA_HOME  VAULT_TOKEN_LEN=${#VAULT_TOKEN}" | tee -a "$LOG"

mvn -o test \
    -Dgroups=source-api-live \
    -Dmaven.test.failure.ignore=true \
    "$@" >>"$LOG" 2>&1
RC=${PIPESTATUS[0]}
echo "=== RUN END rc=$RC $(date -u +%FT%TZ) ===" | tee -a "$LOG"
exit "$RC"
