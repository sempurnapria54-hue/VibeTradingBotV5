#!/usr/bin/env bash
# Прогон исполнимых спецификаций против их примеров.
#
# Штатно: mvn -Dtest=SpecRunnerTest test
# Здесь — автономный прогон без Maven: компилирует раннер и гоняет docs/spec.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v cygpath >/dev/null && ROOT="$(cygpath -m "$ROOT")"
JDK="${SPEC_JDK:-C:/Users/RomanKrd/.jdks/corretto-25.0.3}"
M2="${SPEC_M2:-$HOME/.m2/repository}"
OUT="$ROOT/target/spec-runner-classes"

jar() {
  local found
  found="$(find "$M2/$1" -name "$2" ! -name '*sources*' | sort | tail -1)"
  command -v cygpath >/dev/null && found="$(cygpath -m "$found")"
  printf '%s' "$found"
}

CP="$(jar com/fasterxml/jackson/core/jackson-databind 'jackson-databind-*.jar')"
CP="$CP;$(jar com/fasterxml/jackson/core/jackson-core 'jackson-core-*.jar')"
CP="$CP;$(jar com/fasterxml/jackson/core/jackson-annotations 'jackson-annotations-*.jar')"

mkdir -p "$OUT"
"$JDK/bin/javac" -encoding UTF-8 -cp "$CP" -d "$OUT" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/Spec.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecExpression.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecException.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecScope.java"

exec "$JDK/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
    -cp "$OUT;$CP" com.example.tradingbot.spec.Spec "$ROOT/docs/spec"
