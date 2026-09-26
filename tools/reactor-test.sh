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
#      сборке (`-am` от листового модуля родителя-агрегатора не тянет);
#  11) режим классов: названный класс не исполнил ни одного теста — отбор
#      промахнулся по нему, и зелёным прогон не объявляется (ось 7 этого не
#      видит: модуль, исполнивший ОДИН класс из двух названных, для неё зелен).
#      Счёт — по отчётам surefire названных модулей, снесённым перед прогоном,
#      а не по логу: строку класса surefire печатает по `@DisplayName`;
#  12) режим классов: названное — не простое имя класса (шаблон пакета в
#      `-Dtest` не отбирает ничего — ENV-005), класса нет в тестовых
#      исходниках названных модулей либо у названного модуля нет ни одного
#      названного класса — прогон не начинается;
#  13) аргументы: `--classes` без `--modules`, аргумент без значения,
#      неизвестный аргумент — прогон не начинается.
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
# РЕЖИМ КЛАССОВ — `--modules <модуль>[,…] --classes <Класс>[,…]`: закрывающий
# прогон сессии тестера, изменившей только тестовый код, — тронутые классы, а
# не модуль целиком (.claude/rules/session-work-unit.md §«Объём закрывающего
# прогона»). Сборка та же, что в режиме модулей, и `test-compile` собирает
# ВЕСЬ `src/test` названных модулей — компиляцию соседей прогон мерит, их
# поведения не мерит. Отбор — `-Dtest=<Класс>,…` с
# `-Dsurefire.failIfNoSpecifiedTests=false` поверх файла изъятия: модули
# зависимостей без совпадений не падают. Классы называются простыми именами
# и проверяются по файлам ДО сборки (ось 12), исполнение каждого — по отчёту
# surefire ПОСЛЕ (ось 11). Класс, все клетки которого несут исключённую метку
# (`debt`, `smoke`), исполняет ноль тестов и отказывается осью 11.
#
# ЛОГ — постоянный путь `target/reactor-test.log` от корня репозитория
# (`target/` вне git): опустошается первым ходом прогона и остаётся после
# него. Постоянный — ради ленты цикла: она показывает давность записи лога у
# долгой команды, а временный файл в тексте команды не назван
# (.claude/skills/session-chain.md §«Лента хода сессии в консоли»). Прогоны
# обёртки поэтому не идут параллельно — второй опустошил бы лог первого.
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
#   bash tools/reactor-test.sh --modules tests --classes ExitOrderPathTest,ExitJournalPathTest
#                                                               # режим классов
# Код возврата: 0 — собранное скомпилировано целиком и тесты зелёные;
# 1 — сборка или тесты упали; 2 — ПРОВЕРКА НЕ ПРОВОДИЛАСЬ (нет JDK, нет
# maven, не отвечает демон Docker, прогон оказался вакуумным, названное — не
# модуль реактора либо не класс названных модулей, названный модуль или
# класс не исполнил ни одного теста, аргументы не разобраны).
#
# Переопределяется окружением: REACTOR_JDK (JAVA_HOME сборки), REACTOR_MVN
# (путь к mvn), REACTOR_MVN_ARGS (аргументы прогона), REACTOR_DOCKER
# (клиент docker, чей `info` спрашивает демон).
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# --- разбор аргументов: чистая функция, на ней стои́т ось 13 --------------
# Печатает `OK<TAB><модули><TAB><классы>` либо `ERR:<причина>`.
parse_args() {
  local modules="" classes=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --modules|--classes)
        if [ -z "${2:-}" ]; then
          echo "ERR:$1 требует значение — через запятую"
          return
        fi
        if [ "$1" = "--modules" ]; then modules="$2"; else classes="$2"; fi
        shift 2
        ;;
      *)
        echo "ERR:неизвестный аргумент «$1» (допустимы --modules <модуль>[,…] и --classes <Класс>[,…])"
        return
        ;;
    esac
  done
  if [ -n "$classes" ] && [ -z "$modules" ]; then
    echo 'ERR:--classes требует --modules — классы ищутся в тестовых исходниках названных модулей'
    return
  fi
  printf 'OK\t%s\t%s\n' "$modules" "$classes"
}

PARSED="$(parse_args "$@")"
if [ "${PARSED%%:*}" = "ERR" ]; then
  echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: ${PARSED#ERR:}"
  exit 2
fi
MODULES="$(printf '%s' "$PARSED" | cut -f2)"
CLASSES="$(printf '%s' "$PARSED" | cut -f3)"

# Постоянный лог: опустошается первым ходом, чтобы давность его записи у ленты
# цикла мерила этот прогон, а не прошлый.
LOG="$REPO_ROOT/target/reactor-test.log"
mkdir -p "$REPO_ROOT/target"
: > "$LOG"

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

# --- режим классов: чистые функции, на них стоят оси 11-12 ---------------
# Названные классы — простые имена тестовых классов названных модулей, и у
# каждого модуля есть хоть один из них? Второй аргумент — модули через
# запятую. Печатает OK | NOT_SIMPLE:<имя> | MISSING:<имя> | IDLE:<модуль>.
classes_verdict() {
  local root="$1" modules_csv="$2" class module found
  local -a modules
  shift 2
  IFS=',' read -r -a modules <<< "$modules_csv"
  for class in "$@"; do
    if ! [[ "$class" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "NOT_SIMPLE:$class"
      return
    fi
    found=0
    for module in "${modules[@]}"; do
      [ -d "$root/${module%/}/src/test/java" ] || continue
      if [ -n "$(find "$root/${module%/}/src/test/java" -name "$class.java" -print -quit)" ]; then
        found=1
      fi
    done
    if [ "$found" -eq 0 ]; then
      echo "MISSING:$class"
      return
    fi
  done
  for module in "${modules[@]}"; do
    found=0
    for class in "$@"; do
      if [ -d "$root/${module%/}/src/test/java" ] \
          && [ -n "$(find "$root/${module%/}/src/test/java" -name "$class.java" -print -quit)" ]; then
        found=1
      fi
    done
    if [ "$found" -eq 0 ]; then
      echo "IDLE:${module%/}"
      return
    fi
  done
  echo "OK"
}

# Исполнил ли каждый названный класс хоть один тест. Счёт — по отчёту
# surefire `TEST-<пакет>.<Класс>.xml` названных модулей (атрибут `tests`
# элемента `<testsuite>`), а НЕ по строке класса в логе: строку surefire
# печатает по `@DisplayName`, а не по имени класса, и в кодировке консоли.
# Отчёты названных модулей сносятся перед прогоном — прежние отчитали бы
# класс, который этот прогон не исполнил. Печатает OK | UNTESTED_CLASS:<Класс>.
class_tests_verdict() {
  local root="$1" modules_csv="$2" class module report counted ran
  local -a modules
  shift 2
  IFS=',' read -r -a modules <<< "$modules_csv"
  for class in "$@"; do
    counted=0
    for module in "${modules[@]}"; do
      for report in "$root/${module%/}/target/surefire-reports/TEST-"*".$class.xml"                     "$root/${module%/}/target/surefire-reports/TEST-$class.xml"; do
        [ -f "$report" ] || continue
        ran="$(grep -m1 '<testsuite' "$report" | grep -oE ' tests="[0-9]+"' | grep -oE '[0-9]+' | head -1)"
        counted=$((counted + ${ran:-0}))
      done
    done
    if [ "$counted" -eq 0 ]; then
      echo "UNTESTED_CLASS:$class"
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

  # Режим классов. Отчёт класса — `TEST-<пакет>.<Класс>.xml`; имя из
  # хвоста чужого класса (`XATest` против `ATest`) не засчитывается.
  mkdir -p "$work/rep/m/target/surefire-reports"
  printf '<?xml version="1.0"?>
<testsuite name="p.ATest" tests="3" skipped="0">
' > "$work/rep/m/target/surefire-reports/TEST-p.ATest.xml"
  printf '<?xml version="1.0"?>
<testsuite name="q.BTest" tests="2" failures="1">
' > "$work/rep/m/target/surefire-reports/TEST-q.BTest.xml"
  printf '<?xml version="1.0"?>
<testsuite name="q.CTest" tests="0">
' > "$work/rep/m/target/surefire-reports/TEST-q.CTest.xml"
  printf '<?xml version="1.0"?>
<testsuite name="q.XETest" tests="4">
' > "$work/rep/m/target/surefire-reports/TEST-q.XETest.xml"
  report_axis '19. названный класс без единого теста — отказ' "$(class_tests_verdict "$work/rep" m ATest CTest)" UNTESTED_CLASS:CTest || failed=1
  report_axis '20. отчёта названного класса нет вовсе — отказ' "$(class_tests_verdict "$work/rep" m ATest DTest)" UNTESTED_CLASS:DTest || failed=1
  report_axis '21. отчёт чужого класса с тем же хвостом не засчитан — отказ' "$(class_tests_verdict "$work/rep" m ETest)" UNTESTED_CLASS:ETest || failed=1
  report_axis '22. контроль: классы исполнили тесты, красный тоже считан' "$(class_tests_verdict "$work/rep" m ATest BTest)" OK || failed=1

  report_axis '23. имя с пакетом — отказ' "$(classes_verdict "$work/root" x p.XTest)" NOT_SIMPLE:p.XTest || failed=1
  report_axis '24. класса нет в названных модулях — отказ' "$(classes_verdict "$work/root" x YTest)" MISSING:YTest || failed=1
  report_axis '25. названный модуль без названного класса — отказ' "$(classes_verdict "$work/root" x,y XTest)" IDLE:y || failed=1
  report_axis '26. контроль: классы названных модулей приняты' "$(classes_verdict "$work/root" x,y XTest YTest)" OK || failed=1

  report_axis '27. --classes без --modules — отказ' "$(parse_args --classes XTest | cut -c1-4)" ERR: || failed=1
  report_axis '28. аргумент без значения — отказ' "$(parse_args --modules | cut -c1-4)" ERR: || failed=1
  report_axis '29. неизвестный аргумент — отказ' "$(parse_args --module x | cut -c1-4)" ERR: || failed=1
  report_axis '30. контроль: модули и классы разобраны' "$(parse_args --modules x,y --classes XTest | tr '\t' '|')" 'OK|x,y|XTest' || failed=1
  report_axis '31. контроль: без аргументов — полный реактор' "$(parse_args | tr '\t' '|')" 'OK||' || failed=1

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

CLASS_LIST=()
CLASS_ARGS=()
if [ -n "$CLASSES" ]; then
  IFS=',' read -r -a CLASS_LIST <<< "$CLASSES"
  NAMED="$(classes_verdict "$REPO_ROOT" "$MODULES" "${CLASS_LIST[@]}")"
  case "$NAMED" in
    OK) ;;
    NOT_SIMPLE:*)
      echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: «${NAMED#NOT_SIMPLE:}» — не простое имя класса (шаблон пакета в -Dtest не отбирает ничего)"
      exit 2
      ;;
    MISSING:*)
      echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: класса «${NAMED#MISSING:}» нет в тестовых исходниках модулей ${MODULES}"
      exit 2
      ;;
    IDLE:*)
      echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: у модуля «${NAMED#IDLE:}» нет ни одного названного класса — он назван зря"
      exit 2
      ;;
  esac
  CLASS_ARGS=("-Dtest=$CLASSES" "-Dsurefire.failIfNoSpecifiedTests=false")
  # Прежние отчёты отчитали бы класс, который этот прогон не исполнил (ось 11).
  for module in "${TARGETS[@]}"; do
    rm -rf "$REPO_ROOT/$module/target/surefire-reports"
  done
fi

if [ -n "$MODULES" ]; then
  EXCLUDES="$(mktemp)"
  exclusion_list "$REPO_ROOT" "${TARGETS[@]}" > "$EXCLUDES"
  # Путь файла — в форме, которую прочтёт JVM, а не только Git Bash.
  EXCLUDES_ARG="$(cygpath -m "$EXCLUDES" 2>/dev/null || printf '%s' "$EXCLUDES")"
  JAVA_HOME="$JDK_HOME" "$MVN" -o ${REACTOR_MVN_ARGS:-verify} \
      -am -pl "$(IFS=,; printf '%s' "${TARGETS[*]}")" "-Dsurefire.excludesFile=$EXCLUDES_ARG" \
      "${CLASS_ARGS[@]}" -f "$REPO_ROOT/pom.xml" > "$LOG" 2>&1
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
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: в логе есть «Nothing to compile» — часть дерева не компилировалась; лог: $LOG"
    exit 2
    ;;
  EMPTY)
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: не скомпилировано ни одного файла — измерять нечего; лог: $LOG"
    exit 2
    ;;
  SHORT:*)
    echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: компилировавших модулей меньше объявленных — ${VERDICT#SHORT:}; лог: $LOG"
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
  if [ -n "$CLASSES" ]; then
    RAN="$(class_tests_verdict "$REPO_ROOT" "$MODULES" "${CLASS_LIST[@]}")"
    if [ "$RAN" != "OK" ]; then
      echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: класс ${RAN#UNTESTED_CLASS:} не исполнил ни одного теста — отбор промахнулся либо все его клетки под исключённой меткой; лог: $LOG"
      exit 2
    fi
    echo "режим классов: ${MODULES} — ${CLASSES}; скомпилировано файлов: $SOURCES; тестов пройдено: $TESTS; ДЕФЕКТОВ: 0"
    exit 0
  fi
  echo "режим модулей: ${MODULES}; скомпилировано файлов: $SOURCES; тестов пройдено: $TESTS; ДЕФЕКТОВ: 0"
  exit 0
fi

echo "скомпилировано файлов: $SOURCES; тестов пройдено: $TESTS; ДЕФЕКТОВ: 0"
exit 0
