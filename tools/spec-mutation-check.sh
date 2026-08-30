#!/usr/bin/env bash
# Мутационный замер исполнимой спецификации: доказательна ли каждая величина.
#
# ПРЕДМЕТ. Зелёный tools/spec-run.sh показывает, что примеры ИСПОЛНЯЮТСЯ и
# сходятся; что они ЧТО-ТО ПРОВЕРЯЮТ — не показывает. Величина, заменённая
# константой и оставившая прогон зелёным, не различает ничего: любая ошибка
# её формы пройдёт незамеченной. Замер подменяет каждую объявленную величину
# константой (true / false / 0 / 1 / -1) и гоняет ВЕСЬ каталог спецификаций;
# величина доказательна, если падает на каждой константе.
#
# Замер идёт по ВСЕМУ телу спек, а не по дельте правки: стандарт приёмки —
# .claude/processes/roadmap-step-execution.md §«Мутационная проба — условие
# приёмки спеки, а не только правки».
#
# Запуск (из корня репозитория):  bash tools/spec-mutation-check.sh
# Код возврата: 0 — все величины доказательны; 1 — есть недоказательные
# (перечень в stdout); 2 — не собрался раннер.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
command -v cygpath >/dev/null && ROOT="$(cygpath -m "$ROOT")"

# Разделитель classpath и дефолтный JDK — по платформе (как в spec-run.sh).
if command -v cygpath >/dev/null; then
  SEP=';'
  DEFAULT_JDK="C:/Users/RomanKrd/.jdks/corretto-25.0.3"
else
  SEP=':'
  DEFAULT_JDK="$HOME/.jdks/corretto-25"
fi
JDK="${SPEC_JDK:-$DEFAULT_JDK}"
M2="${SPEC_M2:-$HOME/.m2/repository}"
OUT="$ROOT/target/spec-runner-classes"

jar() {
  local found
  found="$(find "$M2/$1" -name "$2" ! -name '*sources*' | sort | tail -1)"
  command -v cygpath >/dev/null && found="$(cygpath -m "$found")"
  printf '%s' "$found"
}

CP="$(jar com/fasterxml/jackson/core/jackson-databind 'jackson-databind-*.jar')"
CP="$CP$SEP$(jar com/fasterxml/jackson/core/jackson-core 'jackson-core-*.jar')"
CP="$CP$SEP$(jar com/fasterxml/jackson/core/jackson-annotations 'jackson-annotations-*.jar')"

mkdir -p "$OUT"
"$JDK/bin/javac" -encoding UTF-8 -cp "$CP" -d "$OUT" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/Spec.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecExpression.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecException.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecScope.java" \
    "$ROOT/src/test/java/com/example/tradingbot/spec/SpecMutation.java" || exit 2

cd "$ROOT"
exec "$JDK/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
    -cp "$OUT$SEP$CP" com.example.tradingbot.spec.SpecMutation \
    "${1:-docs/spec}" "${2:-target/spec-mutation}"
