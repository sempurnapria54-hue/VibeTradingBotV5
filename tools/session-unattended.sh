#!/usr/bin/env bash
# Режим цикла без присмотра: tools/session-loop.sh раз за разом, пока не
# понадобится держатель.
#
# ПРЕДМЕТ. Держателя нет несколько суток, и цикл должен идти сам, а
# останавливаться только тогда, когда без держателя дальше нельзя. Обёртка
# крутит обычный цикл, НЕ МЕНЯЯ его поведения: всё, что цикл делает на
# сессии (предполётная проверка, лента, конверт, коммит границы шага), он
# делает так же. Обёртка читает только его код возврата и его журнал.
#
# ПОРЦИЯ — ОДНА СЕССИЯ. Цикл запускается с пределом 1, и между его запусками
# обёртка решает, брать ли следующий. Иначе ни файл-стоп, ни потолок
# стоимости не действовали бы между сессиями одной порции: следующую сессию
# порции запускал бы цикл, а не обёртка. Проба контекста (≈$0.1) делается на
# первом запуске цикла; дальше обёртка передаёт SESSION_SKIP_PROBE=1 — среда
# процесса за время запуска не меняется.
#
# ЧЕМ ИСХОДЫ ЦИКЛА ОБРАБАТЫВАЮТСЯ (коды — шапка tools/session-loop.sh):
#   0 + «ЛИМИТ» в журнале — сессия закрыла единицу штатно: берётся следующая;
#   0 + «ОСТАНОВКА»       — фаза закрыта: остановка (код обёртки 10);
#   3 — holder_decision: апрув в REVIEW, вопрос держателю, нет концепции —
#       вердикт держателя, остановка навсегда (3);
#   9 — шаг закрыт, вход в следующий решает держатель: остановка (9);
#   5 — гейты красные, 6 — отказ CLI или негодный ответ: ПЕРЕЗАПУСК.
#       Следующая сессия по правилу «сначала незакрытое»
#       (tools/session-prompt.md, шаг 0) закроет хвост предшественницы;
#   2, 4, 7, 8 — отказ проверки, blocked, диск, коммит: остановка с тем же
#       кодом — повтор упрётся туда же;
#   6 с классом «аутентификация» — остановка (17): просроченный вход паузой
#       не лечится, нужен вход держателя;
#   иное — остановка (16): неизвестный исход не повторяется.
#
# ПРЕДЕЛЫ ПЕРЕЗАПУСКА. Не больше пяти за запуск (14); одна и та же причина
# три раза подряд — остановка без перезапуска (15). Причина — код цикла и
# класс его строки остановки («гейты красные», «отказ CLI, код N», «ответ
# не разобран», «ошибка сессии <subtype>», «статус вне контракта»); чистая
# сессия между двумя остановками серию обнуляет. Перед перезапуском —
# пауза SESSION_UNATTENDED_PAUSE секунд (значение — в конфиге: отказ API и
# перегрузка за минуты проходят, а повтор сразу упёрся бы туда же).
#
# СТЕНД ПРОВЕРЯЕТСЯ НА СТАРТЕ И ПЕРЕД КАЖДЫМ ПЕРЕЗАПУСКОМ: Docker отвечает
# (`docker info`), Vault распечатан (`vault status` в vault-0). Нет —
# остановка (13) с названием, что именно и чем чинится. На старте — затем,
# чтобы запечатанный Vault нашёлся, пока держатель ещё у машины.
#
# СЧЁТ И ПОТОЛОК. Сессии и стоимость считаются по журналу цикла — по тому,
# что он дописал за каждый свой запуск. Каждой сессии передаётся
# SESSION_MAX_USD, равный остатку потолка (или заданному держателем, если
# он меньше), поэтому одна сессия потолка не перепрыгивает. ОСТАТОК НАЗВАН:
# сессия, оборванная без итоговой строки, стоимости в журнал не пишет, и в
# счёт она не входит; число таких сессий сводка называет.
#
# МЯГКАЯ ОСТАНОВКА. Файл %LOCALAPPDATA%\vibetrading-stand\STOP: текущая
# сессия доходит до конца, следующая не запускается (12). Файл остаётся на
# месте и убирается на следующем старте обёртки.
#
# ИТОГ — одной сводкой в журнал цикла (`# Итог запуска без присмотра`):
# сессий, запусков цикла, перезапусков с причинами, стоимость, чем
# остановились. Правило и таблица кодов — .claude/skills/session-chain.md
# §«Режим без присмотра».
#
# РУЧКИ — tools/session-loop.conf, общий с циклом; переменная окружения его
# перекрывает (tools/session-config.sh). Обёртка значений файла НЕ
# экспортирует: цикл читает тот же файл сам и печатает источник честно.
# Сама она передаёт циклу окружением только своё: SESSION_MAX_USD (остаток
# потолка) и SESSION_SKIP_PROBE после первой пробы.
#
# Запуск (из корня репозитория, Git Bash):
#   bash tools/session-unattended.sh 40 600     # до 40 сессий, не дороже $600
#
# Код возврата: 0 — исчерпан счёт сессий; 3 — вердикт держателя
# (holder_decision); 9 — шаг закрыт; 2, 4, 7, 8 — остановка цикла с тем же
# кодом; 10 — фаза закрыта; 11 — потолок стоимости; 12 — файл-стоп;
# 13 — стенд не готов; 14 — исчерпаны перезапуски; 15 — одна причина трижды
# подряд; 16 — неизвестный исход цикла; 17 — claude не аутентифицирован;
# 130 — прерван сигналом.
#
# Подмена цикла для сухого прогона — SESSION_UNATTENDED_LOOP=<сценарий>
# окружением (или SESSION_CONFIG=<иной конфиг>).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MAX_RESTARTS=5
SAME_REASON_LIMIT=3

# shellcheck source=tools/session-config.sh
. "$ROOT/tools/session-config.sh"
load_session_config || exit 2

LOOP="$SESSION_UNATTENDED_LOOP"
PAUSE="$SESSION_UNATTENDED_PAUSE"
STAND_BASE="${LOCALAPPDATA:-$HOME}/vibetrading-stand"
STOP_FILE="$STAND_BASE/STOP"
# Тот же адрес, что у цикла: сводка ложится в его журнал, рядом с его записями.
LOOP_DIR="$SESSION_LOOP_DIR"
JOURNAL="$LOOP_DIR/journal.md"
VAULT_NS="vault-system"
VAULT_POD="vault-0"

case "${1:-}" in -h|--help) awk 'NR > 1 { if (/^#/) print; else exit }' "${BASH_SOURCE[0]}"; exit 0 ;; esac
[ $# -eq 2 ] || { echo "ОТКАЗ: нужно два аргумента — <всего сессий> <потолок USD>" >&2; exit 2; }
TOTAL="$1"
CEILING="$2"
case "$TOTAL" in ""|*[!0-9]*) echo "ОТКАЗ: всего сессий — целое от 1 (получено «$TOTAL»)" >&2; exit 2 ;; esac
[ "$TOTAL" -ge 1 ] || { echo "ОТКАЗ: всего сессий — целое от 1" >&2; exit 2; }
[[ "$CEILING" =~ ^[0-9]+([.][0-9]+)?$ ]] || { echo "ОТКАЗ: потолок USD — положительное число (получено «$CEILING»)" >&2; exit 2; }
case "$PAUSE" in ""|*[!0-9]*) echo "ОТКАЗ: SESSION_UNATTENDED_PAUSE=«$PAUSE» — не число секунд" >&2; exit 2 ;; esac
[ -f "$LOOP" ] || { echo "ОТКАЗ: нет сценария цикла $LOOP" >&2; exit 2; }
if [ -n "${SESSION_MAX_USD:-}" ] && ! [[ "$SESSION_MAX_USD" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "ОТКАЗ: SESSION_MAX_USD=«$SESSION_MAX_USD» — не число" >&2; exit 2
fi

# Арифметика долларов — дробная, у bash её нет.
fadd() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.4f", a + b }'; }
fge()  { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 >= b + 0) }'; }
fcap() { # остаток потолка, урезанный заданным держателем пределом сессии
  awk -v c="$CEILING" -v s="$1" -v m="${SESSION_MAX_USD:-}" \
    'BEGIN { r = c - s; if (m != "" && m + 0 < r) r = m + 0; printf "%.2f", r }'
}

say()  { printf '%s ◆ %s\n' "$(date '+%H:%M')" "$*"; }
jrn()  { printf '%s\n' "$*" >>"$JOURNAL"; }
jsize() { if [ -f "$JOURNAL" ]; then wc -c <"$JOURNAL" | tr -d ' '; else echo 0; fi; }

mkdir -p "$LOOP_DIR"
LAUNCH="$(date +%Y%m%d-%H%M%S)"
STARTED="$(date '+%Y-%m-%d %H:%M:%S')"

SESSIONS=0
COST=0
UNMEASURED=0
PORTIONS=0
RESTARTS=0
STREAK_KEY=""
STREAK=0
RESTART_LOG=()
SKIP_PROBE="$SESSION_SKIP_PROBE"
FINISHED=0

finish() { # $1 — код возврата, $2 — причина
  [ "$FINISHED" -eq 0 ] || exit "$1"
  FINISHED=1
  local code="$1" reason="$2" entry
  jrn ""
  jrn "# Итог запуска без присмотра $LAUNCH"
  jrn ""
  jrn "- остановка: **$reason** (код обёртки $code)"
  jrn "- сессий: $SESSIONS из $TOTAL; запусков цикла: $PORTIONS"
  if [ "$UNMEASURED" -gt 0 ]; then
    jrn "- стоимость: \$$COST из потолка \$$CEILING; у $UNMEASURED сесс. стоимости в журнале нет (оборваны без итоговой строки) — в счёт не вошли"
  else
    jrn "- стоимость: \$$COST из потолка \$$CEILING"
  fi
  jrn "- перезапусков: $RESTARTS из $MAX_RESTARTS"
  for entry in ${RESTART_LOG[@]+"${RESTART_LOG[@]}"}; do jrn "  - $entry"; done
  jrn "- время: $STARTED → $(date '+%Y-%m-%d %H:%M:%S')"
  say "■ без присмотра: остановка (код $code) · $reason"
  say "  сессий $SESSIONS/$TOTAL · \$$COST/\$$CEILING · перезапусков $RESTARTS/$MAX_RESTARTS"
  echo "журнал: $JOURNAL"
  exit "$code"
}
trap 'finish 130 "прерван сигналом"' INT TERM

# ------------------------------------------------------------------ стенд
STAND_ERR=""
stand_check() {
  STAND_ERR=""
  if ! timeout 60 docker info >/dev/null 2>&1; then
    STAND_ERR="Docker не отвечает (docker info за 60 с) — запустить Docker Desktop"
    return 1
  fi
  # `vault status`: 0 — распечатан, 2 — запечатан, прочее — не ответил.
  local out code=0 sealed
  out="$(timeout 60 kubectl -n "$VAULT_NS" exec "$VAULT_POD" -- vault status -format=json 2>/dev/null)" || code=$?
  sealed="$(printf '%s' "$out" | py -3 -c 'import json, sys
try:
    print(str(json.load(sys.stdin)["sealed"]).lower())
except Exception:
    print("?")' 2>/dev/null || echo "?")"
  case "$sealed" in
    false) return 0 ;;
    true)  STAND_ERR="Vault запечатан — распечатать: bash tools/stand/vault-setup.sh"; return 1 ;;
    *)     STAND_ERR="Vault не ответил (kubectl exec $VAULT_NS/$VAULT_POD, код $code) — кластер стенда поднят? bash tools/stand/up.sh"; return 1 ;;
  esac
}

# ------------------------------------------------ разбор записи цикла
# Всё, что цикл дописал в журнал за один свой запуск (хвост от метки
# размера): сессии, их стоимость, строка остановки, признак лимита.
P_SESSIONS=0; P_COST=0; P_UNMEASURED=0; P_STOP=""; P_LIMIT=0
read_portion() { # $1 — размер журнала до запуска цикла
  local tail_text costs measured
  tail_text="$(tail -c +"$(( $1 + 1 ))" "$JOURNAL" 2>/dev/null || true)"
  P_SESSIONS="$(printf '%s\n' "$tail_text" | grep -c '^## Сессия ' || true)"
  costs="$(printf '%s\n' "$tail_text" | sed -n 's/^- стоимость: \$\([0-9.]*\).*/\1/p')"
  measured="$(printf '%s\n' "$costs" | grep -c . || true)"
  P_COST="$(printf '%s\n' "$costs" | awk '{ s += $1 } END { printf "%.4f", s + 0 }')"
  P_UNMEASURED=$(( P_SESSIONS > measured ? P_SESSIONS - measured : 0 ))
  P_STOP="$(printf '%s\n' "$tail_text" | sed -n 's/^\*\*ОСТАНОВКА ([^)]*):\*\* //p' | tail -n 1)"
  P_LIMIT=0
  if printf '%s\n' "$tail_text" | grep -q '^\*\*ЛИМИТ ('; then P_LIMIT=1; fi
}

# Класс причины перезапуска: одна и та же причина — один класс.
reason_key() { # $1 — код цикла, $2 — строка остановки
  local n
  if [ "$1" -eq 5 ]; then echo "гейты красные"; return; fi
  case "$2" in
    "таймаут сессии:"*)     echo "таймаут сессии" ;;
    "аутентификация:"*)     echo "аутентификация" ;;
    "лимит подписки:"*)     echo "лимит подписки" ;;
    *"claude вернул код "*) n="$(printf '%s' "$2" | sed -n 's/.*claude вернул код \([0-9]*\).*/\1/p')"; echo "отказ CLI, код $n" ;;
    *"не разобран"*)        echo "ответ не разобран" ;;
    *"завершилась ошибкой"*) n="$(printf '%s' "$2" | sed -n 's/.*subtype=\([^)]*\).*/\1/p')"; echo "ошибка сессии $n" ;;
    *"не назвала статус"*)  echo "статус вне контракта" ;;
    *)                      echo "код 6" ;;
  esac
}

# Пределы запуска — перед каждой следующей сессией, в том числе перед
# перезапуском: ни один из них не зависит от того, чем кончилась прошлая.
check_limits() {
  if [ "$SESSIONS" -ge "$TOTAL" ]; then finish 0 "исчерпан счёт сессий: $SESSIONS из $TOTAL"; fi
  if fge "$COST" "$CEILING"; then finish 11 "потолок стоимости: \$$COST при потолке \$$CEILING"; fi
  if [ "$(fcap "$COST")" = "0.00" ]; then finish 11 "остаток потолка меньше цента: \$$COST при потолке \$$CEILING"; fi
  if [ -f "$STOP_FILE" ]; then finish 12 "файл-стоп $STOP_FILE — следующая сессия не запущена"; fi
}

# ------------------------------------------------------------------ старт
jrn ""
jrn "# Запуск без присмотра $LAUNCH"
jrn ""
jrn "Сессий всего: $TOTAL. Потолок: \$$CEILING. Перезапусков не больше $MAX_RESTARTS, одна причина $SAME_REASON_LIMIT раза подряд — остановка. Файл-стоп: \`$STOP_FILE\`."
say "▶ без присмотра $LAUNCH · сессий $TOTAL · потолок \$$CEILING · файл-стоп $STOP_FILE"
print_session_config

if [ -f "$STOP_FILE" ]; then
  rm -f "$STOP_FILE"
  jrn "Файл-стоп прошлого запуска убран."
  say "  файл-стоп прошлого запуска убран"
fi

stand_check || finish 13 "стенд не готов на старте: $STAND_ERR"

while :; do
  check_limits
  CAP="$(fcap "$COST")"
  BEFORE="$(jsize)"
  PORTIONS=$(( PORTIONS + 1 ))
  say "▶ запуск цикла $PORTIONS · сессия $(( SESSIONS + 1 ))/$TOTAL · предел сессии \$$CAP"

  LOOP_ENV=(SESSION_MAX_USD="$CAP")
  if [ -n "$SKIP_PROBE" ]; then LOOP_ENV+=(SESSION_SKIP_PROBE="$SKIP_PROBE"); fi
  set +e
  env "${LOOP_ENV[@]}" bash "$LOOP" 1
  CODE=$?
  set -e

  read_portion "$BEFORE"
  SESSIONS=$(( SESSIONS + P_SESSIONS ))
  COST="$(fadd "$COST" "$P_COST")"
  UNMEASURED=$(( UNMEASURED + P_UNMEASURED ))
  # Проба контекста прошла, если цикл дошёл до сессии.
  if [ "$P_SESSIONS" -gt 0 ]; then SKIP_PROBE=1; fi
  say "■ цикл вернул $CODE · сессий $SESSIONS/$TOTAL · \$$COST/\$$CEILING"

  case "$CODE" in
    0)
      if [ "$P_SESSIONS" -eq 0 ]; then finish 16 "цикл вернул 0, не запустив ни одной сессии"; fi
      if [ "$P_LIMIT" -eq 1 ]; then STREAK_KEY=""; STREAK=0; continue; fi
      finish 10 "фаза закрыта: ${P_STOP:-строка остановки не найдена}"
      ;;
    3) finish 3 "вердикт держателя: ${P_STOP:-holder_decision}" ;;
    9) finish 9 "вердикт держателя — вход в следующий шаг: ${P_STOP:-шаг закрыт}" ;;
    2|4|7|8) finish "$CODE" "цикл остановился кодом $CODE: ${P_STOP:-см. вывод цикла выше}" ;;
    5|6) : ;;
    *) finish 16 "цикл вернул неизвестный код $CODE: ${P_STOP:-см. вывод цикла выше}" ;;
  esac

  # ------------------------------------------------------- перезапуск
  KEY="$(reason_key "$CODE" "$P_STOP")"
  # ПРОСРОЧЕННЫЙ ВХОД ПАУЗОЙ НЕ ЛЕЧИТСЯ. Токен, который CLI не смог обновить,
  # не обновится и через полчаса, а проба контекста на перезапуске пропущена
  # (SESSION_SKIP_PROBE) — следующая сессия упала бы на первом же обращении,
  # и серия дошла бы до остановки через полтора часа пауз.
  if [ "$KEY" = "аутентификация" ]; then
    finish 17 "claude не аутентифицирован — вход заново (\`claude\`, /login), затем перезапуск: ${P_STOP:-код $CODE}"
  fi
  if [ "$KEY" = "$STREAK_KEY" ]; then STREAK=$(( STREAK + 1 )); else STREAK_KEY="$KEY"; STREAK=1; fi
  if [ "$STREAK" -ge "$SAME_REASON_LIMIT" ]; then
    finish 15 "одна причина $STREAK раза подряд — «$KEY»: ${P_STOP:-код $CODE}"
  fi
  check_limits
  if [ "$RESTARTS" -ge "$MAX_RESTARTS" ]; then
    finish 14 "исчерпаны перезапуски ($RESTARTS из $MAX_RESTARTS); последняя остановка — «$KEY»: ${P_STOP:-код $CODE}"
  fi
  stand_check || finish 13 "стенд не готов к перезапуску: $STAND_ERR (цикл остановился «$KEY»)"

  RESTARTS=$(( RESTARTS + 1 ))
  RESTART_LOG+=("$(date '+%Y-%m-%d %H:%M') · код $CODE · $KEY · $(printf '%s' "${P_STOP:-строка остановки не найдена}" | cut -c1-300)")
  say "↻ перезапуск $RESTARTS/$MAX_RESTARTS через $PAUSE с · $KEY"
  jrn ""
  jrn "**ПЕРЕЗАПУСК без присмотра ($LAUNCH) $RESTARTS/$MAX_RESTARTS:** $KEY — пауза $PAUSE с."
  sleep "$PAUSE"
done
