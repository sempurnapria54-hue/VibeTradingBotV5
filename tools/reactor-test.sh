#!/usr/bin/env bash
# Прогон реактора монорепозитория с ДОКАЗАННОЙ компиляцией.
#
# ПРЕДМЕТ. Не «запустить maven», а отличить прогон, который измерил, от
# прогона, который не измерил ничего. `mvn test` считает модуль собранным,
# когда его `target/classes` новее исходников, и печатает «Nothing to
# compile — all classes are up to date». Классы при этом могли быть собраны
# против ПРЕЖНЕЙ версии общей библиотеки: реактор отчитается BUILD SUCCESS
# и зелёными тестами, ни разу не проверив, компилируется ли дерево кода
# вообще. Такой зелёный неотличим от подлинного, и именно он один раз уже
# скрыл сломанную сборку донора после правки общей модели.
#
# Оси проверки — каждая поднимает дефект до кода возврата; каждая доказана
# падающей пробой, и батарея проб исполняется ЭТОЙ ЖЕ командой (стандарт —
# .claude/processes/roadmap-step-execution.md §«Оси проверочной команды
# доказываются поимённо»):
#   1) вакуумный прогон — в логе есть «Nothing to compile»: часть дерева не
#      компилировалась, и её исправность не измерена;
#   2) пустой прогон — в логе нет ни одной строки «Compiling N source
#      files»: не компилировалось НИЧЕГО;
#   3) недосчитанные модули — компилировавших модулей меньше, чем строк
#      «Building <модуль>» с исходниками: молча пропущенное дерево;
#   4) отказ сборки либо теста — ненулевой код возврата maven; вердикт лога
#      и код maven сводит одна функция `decide`, и проба идёт по ней.
#
# Каталоги `target/classes` и `target/test-classes` всех модулей реактора
# СНОСЯТСЯ перед прогоном: плагин `clean` в офлайне не резолвится, а без
# сноса ось 1 срабатывала бы на каждом втором запуске и мерить было бы
# нечего.
#
# Запуск (из корня репозитория):  bash tools/reactor-test.sh
# Код возврата: 0 — дерево скомпилировано целиком и тесты зелёные;
# 1 — сборка или тесты упали; 2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (нет JDK, нет
# maven, либо прогон оказался вакуумным).
#
# Переопределяется окружением: REACTOR_JDK (JAVA_HOME сборки), REACTOR_MVN
# (путь к mvn), REACTOR_MVN_ARGS (аргументы прогона).
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# --- разбор лога: чистая функция, на ней же стои́т батарея ----------------
# Печатает вердикт одной строкой: OK | VACUUM | EMPTY | SHORT:<есть>/<надо>.
analyze_log() {
  local log="$1"
  local nothing compiled building
  nothing="$(grep -c 'Nothing to compile' "$log")"
  compiled="$(grep -cE '^\[INFO\] Compiling [0-9]+ source files' "$log")"
  building="$(grep -cE '^\[INFO\] Building ' "$log")"
  if [ "$nothing" -gt 0 ]; then
    echo "VACUUM"
    return
  fi
  if [ "$compiled" -eq 0 ]; then
    echo "EMPTY"
    return
  fi
  # Агрегатор реактора («Building vibetradingbot») исходников не несёт и
  # компилировать ему нечего — он из знаменателя выведен.
  if [ "$compiled" -lt $((building - 1)) ]; then
    echo "SHORT:${compiled}/$((building - 1))"
    return
  fi
  echo "OK"
}

# Сводит вердикт лога и код возврата maven в исход прогона: чистая функция,
# на ней стои́т ось 4. Печатает GREEN | RED | вердикт отказа.
decide() {
  local verdict="$1" mvn_code="$2"
  if [ "$verdict" != "OK" ]; then
    echo "$verdict"
    return
  fi
  if [ "$mvn_code" -ne 0 ]; then
    echo "RED"
    return
  fi
  echo "GREEN"
}

# --- батарея осей: исполняется тем же прогоном ---------------------------
battery() {
  local work verdict failed=0
  work="$(mktemp -d)"

  printf '[INFO] Building a\n[INFO] Compiling 3 source files\n[INFO] Building b\n[INFO] Nothing to compile - all classes are up to date.\n' > "$work/vacuum.log"
  verdict="$(analyze_log "$work/vacuum.log")"
  report_axis '1. вакуумный прогон («Nothing to compile») — отказ' "$verdict" VACUUM || failed=1

  printf '[INFO] Building a\n[INFO] Building b\n[INFO] Tests run: 1, Failures: 0\n' > "$work/empty.log"
  verdict="$(analyze_log "$work/empty.log")"
  report_axis '2. пустой прогон (не компилировалось ничего) — отказ' "$verdict" EMPTY || failed=1

  printf '[INFO] Building a\n[INFO] Compiling 3 source files\n[INFO] Building b\n[INFO] Building c\n[INFO] Building aggregator\n' > "$work/short.log"
  verdict="$(analyze_log "$work/short.log")"
  report_axis '3. недосчитанные модули — отказ' "$verdict" SHORT:1/3 || failed=1

  printf '[INFO] Building a\n[INFO] Compiling 3 source files\n[INFO] Building b\n[INFO] Compiling 7 source files\n[INFO] Building aggregator\n' > "$work/ok.log"
  verdict="$(analyze_log "$work/ok.log")"
  report_axis '4. красная сборка при полном логе — не зелёный' "$(decide OK 1)" RED || failed=1
  report_axis '5. контроль: полный прогон — отказа нет' "$verdict" OK || failed=1
  report_axis '6. контроль: полный лог и нулевой код — зелёный' "$(decide OK 0)" GREEN || failed=1
  report_axis '7. отказ лога старше кода maven — отказ' "$(decide VACUUM 0)" VACUUM || failed=1

  rm -rf "$work"
  return $failed
}

report_axis() {
  local title="$1" observed="$2" expected="$3"
  if [ "$observed" = "$expected" ]; then
    echo "  доказана: ${title} — вердикт ${observed}"
    return 0
  fi
  echo "  НЕ ДОКАЗАНА: ${title} — ожидался ${expected}, получен ${observed}"
  return 1
}

echo '--- батарея осей детектора (исполняется той же командой)'
if ! battery; then
  echo 'ПРОВЕРКА НЕ ПРОВОДИТСЯ: есть недоказанная ось — чистый прогон ничего не удостоверял бы'
  exit 2
fi

# --- собственно прогон ---------------------------------------------------
JDK_HOME="${REACTOR_JDK:-C:/Users/RomanKrd/.jdks/corretto-25.0.3}"
MVN="${REACTOR_MVN:-C:/Program Files/JetBrains/IntelliJ IDEA 2026.1/plugins/maven/lib/maven3/bin/mvn.cmd}"

if [ ! -x "$JDK_HOME/bin/javac" ] && [ ! -f "$JDK_HOME/bin/javac.exe" ]; then
  echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: JDK не найден — $JDK_HOME (переопределяется REACTOR_JDK)"
  exit 2
fi
if [ ! -f "$MVN" ] && ! command -v "$MVN" >/dev/null; then
  echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: maven не найден — $MVN (переопределяется REACTOR_MVN)"
  exit 2
fi

# Снос классов: плагин clean в офлайне не резолвится, а без сноса ось 1
# срабатывала бы на здоровом дереве.
rm -rf "$REPO_ROOT"/libs/*/target/classes "$REPO_ROOT"/libs/*/target/test-classes \
       "$REPO_ROOT"/services/*/target/classes "$REPO_ROOT"/services/*/target/test-classes \
       "$REPO_ROOT"/donor/target/classes "$REPO_ROOT"/donor/target/test-classes

LOG="$(mktemp)"
JAVA_HOME="$JDK_HOME" "$MVN" -o ${REACTOR_MVN_ARGS:-test -DexcludedGroups=source-api-live} \
    -f "$REPO_ROOT/pom.xml" > "$LOG" 2>&1
MVN_CODE=$?

VERDICT="$(analyze_log "$LOG")"
OUTCOME="$(decide "$VERDICT" "$MVN_CODE")"
case "$OUTCOME" in
  VACUUM)
    echo 'ПРОВЕРКА НЕ ПРОВОДИТСЯ: в логе есть «Nothing to compile» — часть дерева не компилировалась'
    rm -f "$LOG"
    exit 2
    ;;
  EMPTY)
    echo 'ПРОВЕРКА НЕ ПРОВОДИТСЯ: не скомпилировано ни одного файла — измерять нечего'
    rm -f "$LOG"
    exit 2
    ;;
  SHORT:*)
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: компилировавших модулей меньше объявленных — ${VERDICT#SHORT:}"
    rm -f "$LOG"
    exit 2
    ;;
esac

SOURCES="$(grep -oE '^\[INFO\] Compiling [0-9]+ source files' "$LOG" | grep -oE '[0-9]+' | awk '{s+=$1} END {print s+0}')"
# Только ИТОГ модуля: строка класса несёт хвост «, Time elapsed …», и без
# фильтра каждый тест считался бы дважды.
TESTS="$(grep -oE '^\[INFO\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' "$LOG"     | grep -oE '^\[INFO\] Tests run: [0-9]+' | grep -oE '[0-9]+' | awk '{s+=$1} END {print s+0}')"

if [ "$OUTCOME" = "RED" ]; then
  echo "СБОРКА КРАСНАЯ: код возврата maven $MVN_CODE; скомпилировано файлов: $SOURCES"
  grep -E '^\[ERROR\]' "$LOG" | head -30
  echo "полный лог: $LOG"
  exit 1
fi

echo "скомпилировано файлов: $SOURCES; тестов пройдено: $TESTS; ДЕФЕКТОВ: 0"
rm -f "$LOG"
exit 0
