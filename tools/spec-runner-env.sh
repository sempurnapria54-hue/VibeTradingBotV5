#!/usr/bin/env bash
# Общая подготовка раннера исполнимых спецификаций: JDK, classpath, компиляция.
#
# ПРЕДМЕТ. Не проверка, а её оснастка: скрипты-проверки подключают этот файл
# (`source`) и получают переменные JAVA и CP, а также маппинг исхода JVM в
# три кода и его батарею (spec_run_jvm, spec_verdict_battery — ниже; батарею
# исполняет команда-потребитель, трёх исходов у самой оснастки нет). Заведён затем, чтобы каталог
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
# Каталог классов конвертируется тем же ходом, что SPEC_ROOT и jar'ы выше:
# POSIX-форма mktemp внутри составного аргумента -cp Windows-Java не
# читается, и оба спек-гейта падали кодом 1 на здоровом корпусе
# (E1/G-7 `DOCS_CHECK_33`).
command -v cygpath >/dev/null && SPEC_CLASSES="$(cygpath -m "$SPEC_CLASSES")"
trap 'rm -rf "$SPEC_CLASSES"' EXIT

"$SPEC_JDK_HOME/bin/javac" -encoding UTF-8 -nowarn -cp "$CP" -d "$SPEC_CLASSES" \
    "$SPEC_ROOT/services/common/test-support/src/test/java/com/example/tradingbot/spec/Spec.java" \
    "$SPEC_ROOT/services/common/test-support/src/test/java/com/example/tradingbot/spec/SpecExpression.java" \
    "$SPEC_ROOT/services/common/test-support/src/test/java/com/example/tradingbot/spec/SpecException.java" \
    "$SPEC_ROOT/services/common/test-support/src/test/java/com/example/tradingbot/spec/SpecScope.java" \
    "$SPEC_ROOT/services/common/test-support/src/test/java/com/example/tradingbot/spec/SpecMutation.java" \
  || { echo "ПРОВЕРКА НЕ ПРОВОДИТСЯ: раннер не собрался" >&2; exit 2; }

JAVA=("$SPEC_JDK_HOME/bin/java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8
      -cp "$SPEC_CLASSES$SPEC_SEP$CP")

# --- исход прогона JVM: по ВЕРДИКТУ раннера, а не по коду процесса ------------
#
# Код 1 у JVM не принадлежит раннеру: его же дают необработанное исключение
# (NoClassDefFoundError при несобранном classpath, OutOfMemoryError), отказ
# загрузки главного класса (не найден, UnsupportedClassVersionError) и всякий
# отказ до main. Прежний маппинг узнавал ОДНУ строку отказа («Could not find or
# load main class»), и прочие системные отказы приезжали кодом 1 «есть
# расхождения»; нет исполняемого java — кодом 127, убитая JVM — 137, а молча
# вышедший процесс — кодом 0, то есть ложно зелёным. Перечень строк отказа
# закрытым не бывает, поэтому признак обращён: исход раннера засчитывается,
# только когда раннер его ОБЪЯВИЛ — последней строкой stdout напечатан вердикт
# своего кода. Код 0 — только с вердиктом «сошлось», код 1 — только с вердиктом
# расхождения, код 2 — отказ самого раннера (он печатает причину сам); всё
# прочее — код 2, системный отказ, а не расхождение.
#
# Цена названа: вердикты раннера (Spec.main, SpecMutation.main) записаны здесь
# литералами. Их переформулировка у раннера роняет прогон кодом 2 на здоровом
# корпусе — громко, в сторону «не измерялось», а не молча зелёным.

# spec_verdict_code <код> <файл stdout> <вердикт кода 0> <префикс вердикта кода 1>
spec_verdict_code() {
  local last
  last="$(tail -n 1 "$2" 2>/dev/null | tr -d '\r')"
  if [ "$1" -eq 0 ] && [ "$last" = "$3" ]; then
    echo 0
    return
  fi
  if [ "$1" -eq 1 ]; then
    case "$last" in
      "$4"*) echo 1; return ;;
    esac
  fi
  echo 2
}

# spec_run_jvm <метка отказа> <вердикт кода 0> <префикс вердикта кода 1> <команда...>
# Исполняет команду, stdout — на экран и в журнал, stderr — после неё.
# Исход — в переменной SPEC_RC (функция зовётся без подоболочки).
spec_run_jvm() {
  local label="$1" ok="$2" fail="$3" rc
  shift 3
  local out="$SPEC_CLASSES/stdout.log" err="$SPEC_CLASSES/stderr.log"
  set +e
  "$@" 2>"$err" | tee "$out"
  rc=${PIPESTATUS[0]}
  set -e
  cat "$err" >&2
  SPEC_RC="$(spec_verdict_code "$rc" "$out" "$ok" "$fail")"
  if [ "$SPEC_RC" -eq 2 ] && [ "$rc" -ne 2 ]; then
    echo "$label: процесс вернул код $rc без вердикта раннера — системный отказ" \
         "(нет java, классы не собраны, версия класса, нехватка памяти, падение до вердикта)," \
         "а не расхождение" >&2
  fi
}

# Батарея осей маппинга — исполняется командой-потребителем ДО прогона: каждый
# класс системного отказа подаётся фиктивным процессом через ту же spec_run_jvm.
# Недоказанная ось => прогон не проводится (код 2).
# spec_verdict_battery <вердикт кода 0> <префикс вердикта кода 1>
spec_verdict_battery() {
  local ok="$1" fail="$2" passed=0 total=0 broken=0
  local missing="$SPEC_CLASSES/нет-jdk/bin/java"
  axis() {
    local title="$1" expected="$2"
    shift 2
    total=$((total + 1))
    spec_run_jvm "проба" "$ok" "$fail" "$@" >/dev/null 2>&1
    if [ "$SPEC_RC" = "$expected" ]; then
      passed=$((passed + 1))
    else
      broken=$((broken + 1))
      echo "  НЕ ДОКАЗАНА: $title — ожидался код $expected, получен $SPEC_RC" >&2
    fi
  }
  axis "1. вердикт «сошлось» при коде 0 — код 0" 0 \
       bash -c 'printf "Спецификаций: 1\n%s\n" "$1"; exit 0' _ "$ok"
  axis "2. вердикт с переводом строки Windows — код 0" 0 \
       bash -c 'printf "%s\r\n" "$1"; exit 0' _ "$ok"
  axis "3. вердикт расхождения при коде 1 — код 1" 1 \
       bash -c 'printf "%s 3\n" "$1"; exit 1' _ "$fail"
  axis "4. отказ самого раннера (код 2) — код 2" 2 \
       bash -c 'printf "ПРОГОН НЕ СОСТОЯЛСЯ: каталог не найден\n"; exit 2'
  axis "5. главный класс не найден (код 1) — код 2" 2 \
       bash -c 'echo "Error: Could not find or load main class X" >&2; exit 1'
  axis "6. NoClassDefFoundError, classpath не собран (код 1) — код 2" 2 \
       bash -c 'echo "Exception in thread \"main\" java.lang.NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper" >&2; exit 1'
  axis "7. UnsupportedClassVersionError (код 1) — код 2" 2 \
       bash -c 'echo "Error: LinkageError occurred while loading main class X: java.lang.UnsupportedClassVersionError" >&2; exit 1'
  axis "8. OutOfMemoryError посреди вывода (код 1) — код 2" 2 \
       bash -c 'printf "Спецификаций: 30\n"; echo "Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space" >&2; exit 1'
  axis "9. исполняемого java нет (код 127) — код 2" 2 "$missing"
  axis "10. JVM убита сигналом (код 137) — код 2" 2 bash -c 'exit 137'
  axis "11. код 0 без вердикта — код 2, а не зелёный" 2 bash -c 'exit 0'
  axis "12. код 1 при вердикте «сошлось» — код 2" 2 \
       bash -c 'printf "%s\n" "$1"; exit 1' _ "$ok"
  unset -f axis
  echo "--- батарея маппинга исхода JVM (той же командой): доказано осей $passed из $total"
  [ "$broken" -eq 0 ]
}
