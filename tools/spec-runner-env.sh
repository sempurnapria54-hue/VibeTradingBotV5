#!/usr/bin/env bash
# Общая подготовка раннера исполнимых спецификаций: JDK, classpath, компиляция.
#
# ПРЕДМЕТ. Не проверка, а её оснастка: скрипты-проверки подключают этот файл
# (`source`) и получают переменные JAVA и CP. Заведён затем, чтобы каталог
# классов был СВОЙ у каждого запуска: общий target/spec-runner-classes делал
# одновременные прогоны небезопасными — javac одного затирал классы другого,
# и проба объявляла оси недоказанными на здоровом корпусе (отказ не
# ложно-зелёный, но неотличимый от подлинно сломанной оси).
#
# Каталог удаляется по выходу вызвавшего скрипта (trap EXIT ставится здесь).
set -euo pipefail

SPEC_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v cygpath >/dev/null && SPEC_ROOT="$(cygpath -m "$SPEC_ROOT")"

# Разделитель classpath и дефолтный JDK — по платформе.
if command -v cygpath >/dev/null; then
  SPEC_SEP=';'
  SPEC_DEFAULT_JDK="C:/Users/RomanKrd/.jdks/corretto-25.0.3"
else
  SPEC_SEP=':'
  SPEC_DEFAULT_JDK="$HOME/.jdks/corretto-25"
fi
SPEC_JDK_HOME="${SPEC_JDK:-$SPEC_DEFAULT_JDK}"
SPEC_M2_REPO="${SPEC_M2:-$HOME/.m2/repository}"

if [ ! -x "$SPEC_JDK_HOME/bin/javac" ]; then
  echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: JDK не найден — $SPEC_JDK_HOME (переопределяется SPEC_JDK)" >&2
  exit 2
fi

spec_jar() {
  local found
  found="$(find "$SPEC_M2_REPO/$1" -name "$2" ! -name '*sources*' 2>/dev/null | sort | tail -1)"
  if [ -z "$found" ]; then
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: не найден $2 в $SPEC_M2_REPO/$1 (переопределяется SPEC_M2)" >&2
    exit 2
  fi
  command -v cygpath >/dev/null && found="$(cygpath -m "$found")"
  printf '%s' "$found"
}

CP="$(spec_jar com/fasterxml/jackson/core/jackson-databind 'jackson-databind-*.jar')"
CP="$CP$SPEC_SEP$(spec_jar com/fasterxml/jackson/core/jackson-core 'jackson-core-*.jar')"
CP="$CP$SPEC_SEP$(spec_jar com/fasterxml/jackson/core/jackson-annotations 'jackson-annotations-*.jar')"

SPEC_CLASSES="$(mktemp -d)"
trap 'rm -rf "$SPEC_CLASSES"' EXIT

"$SPEC_JDK_HOME/bin/javac" -encoding UTF-8 -nowarn -cp "$CP" -d "$SPEC_CLASSES" \
    "$SPEC_ROOT/src/test/java/com/example/tradingbot/spec/Spec.java" \
    "$SPEC_ROOT/src/test/java/com/example/tradingbot/spec/SpecExpression.java" \
    "$SPEC_ROOT/src/test/java/com/example/tradingbot/spec/SpecException.java" \
    "$SPEC_ROOT/src/test/java/com/example/tradingbot/spec/SpecScope.java" \
    "$SPEC_ROOT/src/test/java/com/example/tradingbot/spec/SpecMutation.java" \
  || { echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: раннер не собрался" >&2; exit 2; }

JAVA=("$SPEC_JDK_HOME/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8
      -cp "$SPEC_CLASSES$SPEC_SEP$CP")
