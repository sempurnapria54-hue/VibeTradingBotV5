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
# скрыл сломанную сборку модуля после правки общей модели.
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
#      и код maven сводит одна функция `decide`, и проба идёт по ней;
#   5) знаменатель оси 3 — заголовки модулей, а не строки упаковки
#      «Building jar:», которые фаза verify печатает по строке на артефакт;
#   6) демон Docker не отвечает — прогон не начинается вовсе: ящики сервисов
#      поднимают субстрат контейнерами, и без демона они краснели бы ошибками
#      контейнеров, неотличимыми от дефекта кода. Docker — ПРЕДУСЛОВИЕ
#      реактора, а не условие пропуска тестов
#      (.claude/decisions/test-contour-design-pass.md §«9. Docker — предусловие
#      реактора, а не условие пропуска тестов»);
#   7) режим модулей: названный модуль не исполнил ни одного теста — прогон
#      не измерил того, ради чего назван (отбор классов промахнулся), и
#      зелёным не объявляется;
#   8) режим модулей: названное не является модулем реактора — прогон не
#      начинается;
#   9) режим модулей: перечень изъятия несёт тесты прочих модулей и не несёт
#      тестов названных;
#  10) режим модулей: знаменатель оси 3 — без агрегатора, когда его нет в
#      сборке (`-am` от листового модуля родителя-агрегатора не тянет).
#
# РЕЖИМ МОДУЛЕЙ — `--modules <модуль>[,<модуль>…]`: закрывающий прогон сессии,
# изменившей только тестовый код этих модулей
# (.claude/rules/session-work-unit.md §«Объём закрывающего прогона»).
# Модули собираются с `-am` фазой `verify`, как в реакторе, но ТЕСТЫ
# исполняются только у названных: тесты прочих модулей сборки изымаются
# файлом `-Dsurefire.excludesFile`. Без изъятия `-am` прогонял бы тесты всех
# зависимостей — у сквозного набора `tests` это семь сервисов, то есть весь
# реактор под другим именем. Изъятие — файлом, а не `-Dtest`: перечень
# классов сервиса в строке команды не помещается в предел `mvn.cmd`, а
# шаблон пакета в `-Dtest` не отбирает ничего и выглядит зелёным
# (.claude/traps/environment-commands-traps.md ENV-005) — против этого же
# стоит ось 7.
#
# Каталоги `target/classes` и `target/test-classes` всех модулей реактора
# СНОСЯТСЯ перед прогоном: плагин `clean` в офлайне не резолвится, а без
# сноса ось 1 срабатывала бы на каждом втором запуске и мерить было бы
# нечего. Перечень путей идёт по ДВУМ уровням `services/`: общие артефакты
# лежат в `services/common/<артефакт>`, то есть на сегмент глубже сервиса, а
# модели — ещё на сегмент (`services/common/model/<слой>`; каталог `libs/`
# снят 2026-09-12). Пропущенный уровень — не лишняя
# работа, а ровно вакуумный прогон: несносенные классы дают «Nothing to
# compile», и прогон отказывает кодом 2. Сквозной набор лежит вне `services/`
# (каталог `tests/` верхнего уровня) — его `test-classes` сносятся отдельной
# строкой; `classes` у него нет, исходников main он не несёт.
#
# ФАЗА — `verify`, А НЕ `test`. Сторона сквозной тропы поднимается своим
# процессом из исполняемого jar'а модуля (.claude/decisions/test-contour-design-pass.md
# §«12. Сторона сквозной тропы поднимается СВОИМ ПРОЦЕССОМ, а не вторым
# контекстом в той же JVM»), а jar'ы появляются фазой `package`: после `test`
# их нет, и набор отказал бы «не измерялось». Реактор поэтому пакует каждый
# модуль до того, как дойдёт до набора.
#
# Запуск (из корня репозитория):
#   bash tools/reactor-test.sh                                  # полный реактор
#   bash tools/reactor-test.sh --modules services/bff,tests     # режим модулей
# Код возврата: 0 — собранное скомпилировано целиком и тесты зелёные;
# 1 — сборка или тесты упали; 2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (нет JDK, нет
# maven, не отвечает демон Docker, прогон оказался вакуумным, названное — не
# модуль реактора, названный модуль не исполнил ни одного теста).
#
# Переопределяется окружением: REACTOR_JDK (JAVA_HOME сборки), REACTOR_MVN
# (путь к mvn), REACTOR_MVN_ARGS (аргументы прогона), REACTOR_DOCKER
# (клиент docker, чей `info` спрашивает демон).
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

MODULES=""
case "${1:-}" in
  "") ;;
  --modules)
    MODULES="${2:-}"
    if [ -z "$MODULES" ] || [ $# -ne 2 ]; then
      echo 'ПРОВЕРКА НЕ ПРОВОДИТСЯ: --modules требует один аргумент — модули через запятую'
      exit 2
    fi
    ;;
  *)
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: неизвестный аргумент «$1» (допустим только --modules <модуль>[,<модуль>…])"
    exit 2
    ;;
esac

# --- разбор лога: чистая функция, на ней же стои́т батарея ----------------
# Печатает вердикт одной строкой: OK | VACUUM | EMPTY | SHORT:<есть>/<надо>.
# Второй аргумент — сколько в сборке агрегаторов без исходников (умолчание 1:
# корень реактора); режим модулей передаёт счёт строк упаковки `[ pom ]`.
analyze_log() {
  local log="$1" aggregators="${2:-1}"
  local nothing compiled building
  nothing="$(grep -c 'Nothing to compile' "$log")"
  compiled="$(grep -cE '^\[INFO\] Compiling [0-9]+ source files' "$log")"
  # Знаменатель — заголовки модулей, а не всякая строка «Building»: фаза
  # verify пакует модули, и плагин упаковки печатает «Building jar: <путь>»
  # по строке на артефакт — без изъятия знаменатель вырос бы вдвое, и
  # здоровый прогон читался бы недосчитанным.
  building="$(grep -E '^\[INFO\] Building ' "$log" | grep -cvE '^\[INFO\] Building (jar|war|ear|zip|tar): ')"
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
  if [ "$compiled" -lt $((building - aggregators)) ]; then
    echo "SHORT:${compiled}/$((building - aggregators))"
    return
  fi
  echo "OK"
}

# --- режим модулей: чистые функции, на них стоят оси 7-9 -----------------
# Модули реактора — по объявлению корневого pom.xml.
reactor_modules() {
  grep -oE '<module>[^<]+</module>' "$1/pom.xml" | sed -E 's#</?module>##g'
}

# Названное — модуль реактора? Печатает OK | UNKNOWN:<названное>.
modules_verdict() {
  local root="$1" wanted known
  shift
  for wanted in "$@"; do
    known=0
    while IFS= read -r module; do
      [ "$module" = "$wanted" ] && known=1
    done < <(reactor_modules "$root")
    if [ "$known" -eq 0 ]; then
      echo "UNKNOWN:$wanted"
      return
    fi
  done
  echo "OK"
}

# artifactId модуля — тот, что maven печатает заголовком `< группа:artifactId >`.
module_artifact() {
  sed '/<parent>/,/<\/parent>/d' "$1/pom.xml" | grep -m1 -oE '<artifactId>[^<]+' | sed 's/<artifactId>//'
}

# Перечень изъятия: тестовые исходники всех модулей реактора, кроме
# названных, — путями от корня тестовых исходников, как их читает surefire.
exclusion_list() {
  local root="$1" module wanted skip
  shift
  while IFS= read -r module; do
    skip=0
    for wanted in "$@"; do
      [ "$module" = "$wanted" ] && skip=1
    done
    [ "$skip" -eq 1 ] && continue
    [ -d "$root/$module/src/test/java" ] || continue
    (cd "$root/$module/src/test/java" && find . -name '*.java' | sed 's#^\./##')
  done < <(reactor_modules "$root")
}

# Исполнил ли каждый названный модуль хоть один тест. Счёт — по ИТОГУ
# модуля (строка без хвоста «, Time elapsed …»), отнесённому к заголовку
# `< группа:artifactId >`, под которым он напечатан. Печатает OK | UNTESTED:<artifactId>.
module_tests_verdict() {
  local log="$1" artifact counted
  shift
  for artifact in "$@"; do
    counted="$(awk -v want="$artifact" '
      /^\[INFO\] -+< [^:]+:[^ ]+ >-+$/ {
        current = $0
        sub(/^\[INFO\] -+< [^:]+:/, "", current)
        sub(/ >-+$/, "", current)
        next
      }
      /^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$/ {
        if (current == want) {
          line = $0
          sub(/^.*Tests run: /, "", line)
          sub(/,.*$/, "", line)
          sum += line
        }
      }
      END { print sum + 0 }' "$log")"
    if [ "$counted" -eq 0 ]; then
      echo "UNTESTED:$artifact"
      return
    fi
  done
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

# Предусловие субстрата ящиков: клиент спрашивает демон. Чистая функция по
# переданному клиенту, на ней стои́т ось 6. Печатает OK | NO_DOCKER.
docker_verdict() {
  local docker_cmd="$1"
  if "$docker_cmd" info >/dev/null 2>&1; then
    echo "OK"
    return
  fi
  echo "NO_DOCKER"
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

  printf '[INFO] Building a\n[INFO] Compiling 3 source files\n[INFO] Building jar: a.jar\n[INFO] Building b\n[INFO] Compiling 7 source files\n[INFO] Building jar: b.jar\n[INFO] Building aggregator\n' > "$work/package.log"
  verdict="$(analyze_log "$work/package.log")"
  report_axis '8. строки упаковки «Building jar:» модулями не считаются' "$verdict" OK || failed=1

  report_axis '9. демон Docker не отвечает — отказ' "$(docker_verdict false)" NO_DOCKER || failed=1
  report_axis '10. контроль: демон отвечает — отказа нет' "$(docker_verdict true)" OK || failed=1
  report_axis '11. клиента docker нет вовсе — отказ' "$(docker_verdict "$work/нет-такого-клиента")" NO_DOCKER || failed=1

  # Режим модулей. Итог модуля — строка без хвоста; строка класса несёт
  # «, Time elapsed …» и в счёт не идёт.
  printf '[INFO] ---< com.example:a >---\n[INFO] Building a\n[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.2 s\n[INFO] ---< com.example:b >---\n[INFO] Building b\n[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0\n' > "$work/modules.log"
  report_axis '12. названный модуль без единого теста — отказ' "$(module_tests_verdict "$work/modules.log" b a)" UNTESTED:a || failed=1
  report_axis '13. контроль: названный модуль исполнил тесты — отказа нет' "$(module_tests_verdict "$work/modules.log" b)" OK || failed=1

  mkdir -p "$work/root/x/src/test/java/p" "$work/root/y/src/test/java/q"
  printf '<project>\n  <modules>\n    <module>x</module>\n    <module>y</module>\n  </modules>\n</project>\n' > "$work/root/pom.xml"
  : > "$work/root/x/src/test/java/p/XTest.java"
  : > "$work/root/y/src/test/java/q/YTest.java"
  report_axis '14. названное вне реактора — отказ' "$(modules_verdict "$work/root" x z)" UNKNOWN:z || failed=1
  report_axis '15. контроль: модуль реактора принят' "$(modules_verdict "$work/root" y)" OK || failed=1
  report_axis '16. изъятие несёт тесты прочих модулей, а не названного' "$(exclusion_list "$work/root" x | paste -sd, -)" q/YTest.java || failed=1

  printf '[INFO] Building a\n[INFO] Compiling 3 source files\n[INFO] Building b\n' > "$work/short-noagg.log"
  report_axis '17. без агрегатора недосчитанный модуль — отказ' "$(analyze_log "$work/short-noagg.log" 0)" SHORT:1/2 || failed=1
  printf '[INFO] Building a\n[INFO] Compiling 3 source files\n[INFO] Building b\n[INFO] Compiling 2 source files\n' > "$work/ok-noagg.log"
  report_axis '18. контроль: без агрегатора полный прогон — отказа нет' "$(analyze_log "$work/ok-noagg.log" 0)" OK || failed=1

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

DOCKER="${REACTOR_DOCKER:-docker}"
if [ "$(docker_verdict "$DOCKER")" != "OK" ]; then
  echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: демон Docker не отвечает — $DOCKER info (переопределяется REACTOR_DOCKER); ящики без него не поднимут субстрат"
  exit 2
fi

# Снос классов: плагин clean в офлайне не резолвится, а без сноса ось 1
# срабатывала бы на здоровом дереве.
rm -rf "$REPO_ROOT"/services/*/target/classes "$REPO_ROOT"/services/*/target/test-classes \
       "$REPO_ROOT"/services/common/*/target/classes \
       "$REPO_ROOT"/services/common/*/target/test-classes \
       "$REPO_ROOT"/services/common/model/*/target/classes \
       "$REPO_ROOT"/services/common/model/*/target/test-classes \
       "$REPO_ROOT"/tests/target/test-classes

TARGETS=()
TARGET_ARTIFACTS=()
if [ -n "$MODULES" ]; then
  IFS=',' read -r -a TARGETS <<< "$MODULES"
  for i in "${!TARGETS[@]}"; do
    TARGETS[$i]="${TARGETS[$i]%/}"
  done
  KNOWN="$(modules_verdict "$REPO_ROOT" "${TARGETS[@]}")"
  if [ "$KNOWN" != "OK" ]; then
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: «${KNOWN#UNKNOWN:}» — не модуль реактора (перечень — <module> корневого pom.xml)"
    exit 2
  fi
  for module in "${TARGETS[@]}"; do
    TARGET_ARTIFACTS+=("$(module_artifact "$REPO_ROOT/$module")")
  done
fi

LOG="$(mktemp)"
if [ -n "$MODULES" ]; then
  EXCLUDES="$(mktemp)"
  exclusion_list "$REPO_ROOT" "${TARGETS[@]}" > "$EXCLUDES"
  # Путь файла — в форме, которую прочтёт JVM, а не только Git Bash.
  EXCLUDES_ARG="$(cygpath -m "$EXCLUDES" 2>/dev/null || printf '%s' "$EXCLUDES")"
  JAVA_HOME="$JDK_HOME" "$MVN" -o ${REACTOR_MVN_ARGS:-verify} \
      -am -pl "$(IFS=,; printf '%s' "${TARGETS[*]}")" "-Dsurefire.excludesFile=$EXCLUDES_ARG" \
      -f "$REPO_ROOT/pom.xml" > "$LOG" 2>&1
  MVN_CODE=$?
  rm -f "$EXCLUDES"
  VERDICT="$(analyze_log "$LOG" "$(grep -cE '^\[INFO\] -+\[ pom \]-+$' "$LOG")")"
else
  JAVA_HOME="$JDK_HOME" "$MVN" -o ${REACTOR_MVN_ARGS:-verify} \
      -f "$REPO_ROOT/pom.xml" > "$LOG" 2>&1
  MVN_CODE=$?
  VERDICT="$(analyze_log "$LOG")"
fi

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

if [ -n "$MODULES" ]; then
  TESTED="$(module_tests_verdict "$LOG" "${TARGET_ARTIFACTS[@]}")"
  if [ "$TESTED" != "OK" ]; then
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: модуль ${TESTED#UNTESTED:} не исполнил ни одного теста — отбор промахнулся; лог: $LOG"
    exit 2
  fi
  echo "режим модулей: ${MODULES}; скомпилировано файлов: $SOURCES; тестов пройдено: $TESTS; ДЕФЕКТОВ: 0"
  rm -f "$LOG"
  exit 0
fi

echo "скомпилировано файлов: $SOURCES; тестов пройдено: $TESTS; ДЕФЕКТОВ: 0"
rm -f "$LOG"
exit 0
