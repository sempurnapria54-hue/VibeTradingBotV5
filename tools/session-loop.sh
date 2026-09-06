#!/usr/bin/env bash
# Цепочка неинтерактивных сессий: штатное продолжение роадмапа без
# держателя.
#
# ПРЕДМЕТ. Каждая итерация — НОВАЯ сессия `claude -p` со стоячим промптом
# (tools/session-prompt.md), а не продолжение прежней: состояние живёт в
# репозитории (реестр компонентов, роадмап, хроника, снапшот), и свежий
# контекст — цель цепочки, а не её потеря.
#
# ЧТО ОНА НЕ ДЕЛАЕТ. Не поднимает стенд (tools/stand/up.sh) и не
# выкладывает сборку (tools/stand/deploy-services.sh) — это делает сама
# сессия, когда ей нужно.
#
# КОММИТ — НА ГРАНИЦЕ ШАГА, И ДЕЛАЕТ ЕГО СЦЕНАРИЙ. У каждого закрытого шага
# роадмапа своя точка отката: на `step_done` и на `phase_done` при зелёных
# гейтах цикл делает `git add -A && git commit`, а сообщение собирает из
# КОНВЕРТА (фаза, шаг, название шага) — не из журнала, который живёт вне
# репозитория. На всех прочих остановках коммита нет: дельта остаётся
# staged держателю на ревью. Правило «CC не коммитит» (CLAUDE.md) этим не
# меняется — коммитит сценарий держателя, а не сессия.
#
# ЦИКЛ ИДЁТ ТОЛЬКО ПРИ `continue`/`step_done` + `gates_green`. Всякий иной
# исход — остановка с названной причиной и без повторов: повтор сессии,
# которая уже уперлась, стои́т денег и приводит туда же.
#
# Запуск (из корня репозитория):
#   bash tools/session-loop.sh 5             # не больше пяти сессий за запуск
#   bash tools/session-loop.sh --max 5
#   bash tools/session-loop.sh 5 --dry-run   # показать команду, не тратя
#
# Код возврата: 0 — лимит исчерпан штатно либо фаза закрыта; 2 — отказ
# предполётной проверки; 3 — нужен держатель (`holder_decision`);
# 4 — `blocked`; 5 — гейты красные; 6 — отказ CLI или негодный ответ;
# 7 — предохранитель по диску; 8 — отказ коммита на границе шага.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PROMPT_FILE="${SESSION_PROMPT:-$ROOT/tools/session-prompt.md}"
LOOP_DIR="${SESSION_LOOP_DIR:-${LOCALAPPDATA:-$HOME}/vibetrading-stand/sessions}"
MIN_FREE_GIB="${SESSION_MIN_FREE_GIB:-10}"
# Режим прав тот же, в котором держатель ведёт этот проект; allow-правила
# проекта приезжают из .claude/settings.local.json сами. `--permission-prompts
# none` держит обещание «ничто не ждёт ответа»: то, что запросило бы
# подтверждение, отклоняется, а не висит.
PERMISSION_MODE="${SESSION_PERMISSION_MODE:-bypassPermissions}"

# ФОНОВЫЕ ЗАДАЧИ ЖДУТСЯ ДО КОНЦА. У Claude Code есть потолок ожидания фоновых
# задач, по которому сессия завершается принудительно («Background tasks still
# running after 600s; terminating»). Обрывается при этом не пауза, а идущая
# работа — так оборвалась фоновая критика, и конверт о ней уже не отчитался.
# Ноль снимает потолок; время сессии ограничивает SESSION_TIMEOUT, для того он
# и есть. Значение из среды не перебивается: держатель вправе вернуть потолок.
export CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS="${CLAUDE_CODE_PRINT_BG_WAIT_CEILING_MS:-0}"

MAX=1
DRY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --max) MAX="${2:?--max требует число}"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    -h|--help) awk 'NR > 1 { if (/^#/) print; else exit }' "${BASH_SOURCE[0]}"; exit 0 ;;
    ""|*[!0-9]*) echo "ОТКАЗ: неизвестный аргумент «$1»" >&2; exit 2 ;;
    *) MAX="$1"; shift ;;
  esac
done
[ "$MAX" -ge 1 ] 2>/dev/null || { echo "ОТКАЗ: максимум сессий — целое от 1" >&2; exit 2; }

# КОНТРАКТ СТАТУСА. `phase`/`step`/`step_title` названы в КАЖДОМ конверте, а не
# только на границе шага: условно обязательное поле модель заполняет по своему
# прочтению условия, и на `step_done` — там, где оно и нужно, — его могло бы не
# оказаться. Из них собирается сообщение коммита (tools/session-prompt.md).
SCHEMA='{"type":"object","properties":{"status":{"type":"string","enum":["continue","step_done","holder_decision","phase_done","blocked"]},"gates_green":{"type":"boolean"},"phase":{"type":"integer"},"step":{"type":"integer"},"step_title":{"type":"string"},"summary":{"type":"string"}},"required":["status","gates_green","phase","step","step_title","summary"],"additionalProperties":false}'

JOURNAL="$LOOP_DIR/journal.md"
RAW_DIR="$LOOP_DIR/raw"
LAUNCH="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RAW_DIR"
export PYTHONIOENCODING=utf-8

say() { printf "\n=== %s\n" "$*"; }
jrn() { printf '%s\n' "$*" >>"$JOURNAL"; }

# ------------------------------------------------------------------ диск
# Хранилище Docker на этой машине — образ WSL под %LOCALAPPDATA%\Docker\wsl;
# DockerRootDir у Docker Desktop указывает ВНУТРЬ виртуальной машины
# (/var/lib/docker) и о свободном месте хоста ничего не говорит. Поэтому
# сначала проверяется путь хоста, и только если его нет — то, что отдал
# демон, и лишь когда это существующий локальный каталог.
docker_data_dir() {
  local host_path="${LOCALAPPDATA:-}/Docker/wsl" root
  if [ -n "${LOCALAPPDATA:-}" ] && [ -d "$host_path" ]; then printf '%s' "$host_path"; return; fi
  root="$(docker info --format '{{.DockerRootDir}}' 2>/dev/null || true)"
  if [ -n "$root" ] && [ -d "$root" ]; then printf '%s' "$root"; return; fi
  printf '%s' "${LOCALAPPDATA:-$HOME}"
}

free_gib() { df -P -k "$1" 2>/dev/null | awk 'NR==2 {printf "%d", int($4/1048576)}'; }

# ------------------------------------------------- предполётные проверки
say "Предполётная проверка"

command -v claude >/dev/null || { echo "ОТКАЗ: claude не найден на PATH" >&2; exit 2; }
[ -f "$PROMPT_FILE" ] || { echo "ОТКАЗ: нет стоячего промпта $PROMPT_FILE" >&2; exit 2; }
git -C "$ROOT" rev-parse --git-dir >/dev/null 2>&1 || { echo "ОТКАЗ: $ROOT не репозиторий" >&2; exit 2; }

# BARE НЕ ВКЛЮЧЁН — проверка явная, а не подразумеваемая. `--bare` снимает
# CLAUDE.md, скиллы, агентов и хуки, то есть весь пайплайн, а документация
# обещает сделать его умолчанием `-p` в будущем. Флага мы не передаём
# никогда; здесь ловится включение ИЗВНЕ — переменной среды и умолчанием
# сборки. Второе из флагов не выводится вовсе, поэтому проверяется живой
# пробой: сессия без инструментов чтения отвечает, видит ли она CLAUDE.md.
[ -z "${CLAUDE_CODE_SIMPLE:-}" ] || { echo "ОТКАЗ: CLAUDE_CODE_SIMPLE=${CLAUDE_CODE_SIMPLE} — среда включила bare-режим" >&2; exit 2; }
[ -z "${CLAUDE_CODE_SAFE_MODE:-}" ] || { echo "ОТКАЗ: CLAUDE_CODE_SAFE_MODE=${CLAUDE_CODE_SAFE_MODE} — среда отключила настройки проекта" >&2; exit 2; }

PROBE_RAW="$RAW_DIR/$LAUNCH-probe.json"
PROBE_SCHEMA='{"type":"object","properties":{"claude_md_loaded":{"type":"boolean"},"project":{"type":"string"}},"required":["claude_md_loaded","project"],"additionalProperties":false}'
PROBE_PROMPT="Ответь только по своему контексту, ничего не читая и не догадываясь. Есть ли в твоём контексте инструкции проекта из файла CLAUDE.md? Если есть — назови имя проекта из его первого заголовка."
if [ "$DRY" -eq 1 ]; then
  echo "проба контекста пропущена (--dry-run)"
elif [ -n "${SESSION_SKIP_PROBE:-}" ]; then
  echo "проба контекста пропущена (SESSION_SKIP_PROBE)"
else
  claude -p "$PROBE_PROMPT" \
    --output-format json --json-schema "$PROBE_SCHEMA" \
    --permission-mode "$PERMISSION_MODE" --permission-prompts none \
    --disallowed-tools "Read,Bash,PowerShell,Glob,Grep,Agent,WebFetch,WebSearch" \
    --model claude-haiku-4-5-20251001 </dev/null >"$PROBE_RAW" 2>/dev/null \
    || { echo "ОТКАЗ: проба не прошла — claude вернул ненулевой код (аутентификация? сеть?). Ответ: $PROBE_RAW" >&2; exit 2; }
  py -3 "$ROOT/tools/session_envelope.py" probe "$PROBE_RAW" || exit 2
fi

DOCKER_DIR="$(docker_data_dir)"
echo "хранилище Docker: $DOCKER_DIR"
echo "журнал: $JOURNAL"
echo "режим прав: $PERMISSION_MODE, максимум сессий: $MAX"

if [ "$DRY" -eq 1 ]; then
  say "Команда сессии (--dry-run, ничего не запущено)"
  printf 'claude -p "$(cat %s)" \\\n  --output-format json --json-schema <контракт статуса> \\\n  --permission-mode %s --permission-prompts none\n' \
    "$PROMPT_FILE" "$PERMISSION_MODE"
  exit 0
fi

PROMPT="$(cat "$PROMPT_FILE")"

jrn ""
jrn "# Запуск $LAUNCH"
jrn ""
jrn "Максимум сессий: $MAX. Режим прав: \`$PERMISSION_MODE\`. Порог диска: ${MIN_FREE_GIB} ГиБ."

stop_with() { # $1 — код возврата, $2 — причина
  jrn ""
  jrn "**ОСТАНОВКА ($LAUNCH):** $2"
  say "ОСТАНОВКА: $2"
  echo "журнал: $JOURNAL"
  exit "$1"
}

# ------------------------------------------------------- граница шага
# ТОЧКА ОТКАТА У КАЖДОГО ЗАКРЫТОГО ШАГА. Коммит ставится только на границе
# `step_done`/`phase_done` и только при зелёных гейтах — проверку делает
# вызывающий, сюда управление доходит уже зелёным. Сообщение собирается из
# конверта: журнал живёт вне репозитория, и вывести из него подпись коммита
# значило бы поставить историю репозитория в зависимость от машинного
# вывода, который никто не хранит.
#
# ОТКАЗ КОММИТА — ОСТАНОВКА, А НЕ ПРОДОЛЖЕНИЕ БЕЗ ТОЧКИ ОТКАТА. Всякая
# причина отказа (нет идентичности git, пустой индекс, конфликт, хук)
# означает, что граница не зафиксирована; следующая сессия наложила бы
# свою дельту на незафиксированную, и разделить их было бы уже нечем.
COMMIT_ERR=""
COMMIT_INFO=""
commit_boundary() { # $1 — статус конверта; печатает причину в COMMIT_ERR
  local status="$1" subject body failure
  COMMIT_ERR=""
  COMMIT_INFO=""

  case "${ST_PHASE:-}" in ""|*[!0-9]*) COMMIT_ERR="конверт не назвал фазу числом (получено «${ST_PHASE:-}»)"; return 1 ;; esac
  case "${ST_STEP:-}" in ""|*[!0-9]*) COMMIT_ERR="конверт не назвал шаг числом (получено «${ST_STEP:-}»)"; return 1 ;; esac
  [ -n "${ST_TITLE:-}" ] || { COMMIT_ERR="конверт не назвал название шага"; return 1; }

  [ -n "$(git -C "$ROOT" config user.name 2>/dev/null || true)" ] || { COMMIT_ERR="нет идентичности git: не задан user.name"; return 1; }
  [ -n "$(git -C "$ROOT" config user.email 2>/dev/null || true)" ] || { COMMIT_ERR="нет идентичности git: не задан user.email"; return 1; }

  git -C "$ROOT" add -A || { COMMIT_ERR="git add -A отказал"; return 1; }
  if git -C "$ROOT" diff --cached --quiet; then
    COMMIT_ERR="индекс пуст, а сессия объявила «$status» — фиксировать нечего"
    return 1
  fi

  subject="ROADMAP ${ST_PHASE}-${ST_STEP} DONE — ${ST_TITLE}"
  body="${ST_SUMMARY:-(итог не назван)}"
  if [ "$status" = "phase_done" ]; then
    body="$body

Фаза ${ST_PHASE} закрыта."
  fi
  body="$body

Сессия: ${SESSION_ID:-?} (цикл $LAUNCH)"

  # Отказ пересказывается СЛОВАМИ git, а не догадкой о причине: конфликт,
  # хук и состояние репозитория пишут разное, и держателю нужно именно оно.
  if ! failure="$(git -C "$ROOT" commit -m "$subject" -m "$body" 2>&1)"; then
    COMMIT_ERR="git commit отказал: $(printf '%s' "$failure" | tail -n 3 | paste -sd ' ' -)"
    return 1
  fi
  COMMIT_INFO="$(git -C "$ROOT" log -1 --format='%h %s')"
  return 0
}

TOTAL_COST=0
for (( n = 1; n <= MAX; n++ )); do
  # ПРЕДОХРАНИТЕЛЬ ПО ДИСКУ — до запуска, а не после: сессия, начатая на
  # исходе места, роняет не себя, а стенд и демон Docker.
  FREE="$(free_gib "$DOCKER_DIR")"
  if [ -z "$FREE" ]; then
    stop_with 7 "свободное место на $DOCKER_DIR не измеряется — сессия $n не запущена"
  fi
  if [ "$FREE" -lt "$MIN_FREE_GIB" ]; then
    stop_with 7 "свободно ${FREE} ГиБ на $DOCKER_DIR при пороге ${MIN_FREE_GIB} — сессия $n не запущена"
  fi

  say "Сессия $n из $MAX (свободно ${FREE} ГиБ)"
  STARTED="$(date '+%Y-%m-%d %H:%M:%S')"
  RAW="$RAW_DIR/$LAUNCH-$n.json"

  set +e
  if [ -n "${SESSION_TIMEOUT:-}" ]; then
    timeout "$SESSION_TIMEOUT" claude -p "$PROMPT" \
      --output-format json --json-schema "$SCHEMA" \
      --permission-mode "$PERMISSION_MODE" --permission-prompts none \
      ${SESSION_MODEL:+--model "$SESSION_MODEL"} \
      ${SESSION_MAX_USD:+--max-budget-usd "$SESSION_MAX_USD"} \
      </dev/null >"$RAW"
  else
    claude -p "$PROMPT" \
      --output-format json --json-schema "$SCHEMA" \
      --permission-mode "$PERMISSION_MODE" --permission-prompts none \
      ${SESSION_MODEL:+--model "$SESSION_MODEL"} \
      ${SESSION_MAX_USD:+--max-budget-usd "$SESSION_MAX_USD"} \
      </dev/null >"$RAW"
  fi
  CLI_CODE=$?
  set -e
  FINISHED="$(date '+%Y-%m-%d %H:%M:%S')"

  jrn ""
  jrn "## Сессия $n/$MAX — $STARTED → $FINISHED"
  jrn ""
  jrn "- ответ CLI: \`$RAW\`"

  if [ "$CLI_CODE" -ne 0 ]; then
    jrn "- код выхода claude: **$CLI_CODE**"
    stop_with 6 "claude вернул код $CLI_CODE на сессии $n (аутентификация, лимит, обрыв) — повторов нет"
  fi

  PARSE_OK=0
  PARSE_ERR=""
  eval "$(py -3 "$ROOT/tools/session_envelope.py" fields "$RAW")"

  if [ "$PARSE_OK" -ne 1 ]; then
    jrn "- ответ не разобран: ${PARSE_ERR:-неизвестно}"
    stop_with 6 "ответ сессии $n не разобран как JSON — смотреть $RAW"
  fi

  TOTAL_COST="$(py -3 -c "import sys; print(round(float(sys.argv[1])+float(sys.argv[2]), 4))" "$TOTAL_COST" "$COST")"

  jrn "- сессия: \`$SESSION_ID\`, ходов: ${NUM_TURNS:-?}, отказов прав: ${DENIALS:-?}"
  jrn "- стоимость: \$${COST} (за запуск: \$${TOTAL_COST})"
  jrn "- статус: **${ST_STATUS:-нет}**, гейты: **${ST_GATES:-нет}**, шаг: ${ST_PHASE:-?}-${ST_STEP:-?} «${ST_TITLE:-?}»"
  jrn "- \`result\`: \`${RESULT}\`"
  jrn ""
  jrn "> ${ST_SUMMARY:-(итог не назван)}"

  echo "статус: ${ST_STATUS:-нет}, гейты: ${ST_GATES:-нет}, \$${COST}"
  echo "итог: ${ST_SUMMARY:-(не назван)}"

  if [ "$IS_ERROR" = "true" ]; then
    stop_with 6 "сессия $n завершилась ошибкой (subtype=$SUBTYPE)"
  fi

  case "$ST_STATUS" in
    continue|step_done|phase_done) : ;;
    holder_decision) stop_with 3 "сессии $n нужен держатель: $ST_SUMMARY" ;;
    blocked)         stop_with 4 "сессия $n заблокирована: $ST_SUMMARY" ;;
    *)               stop_with 6 "сессия $n не назвала статус из контракта (получено «${ST_STATUS}»)" ;;
  esac

  # ГЕЙТЫ РАНЬШЕ КОММИТА: точка отката ставится на состоянии, которое сессия
  # объявила зелёным, — иначе она фиксировала бы красное как рубеж.
  if [ "$ST_GATES" != "true" ]; then
    stop_with 5 "гейты красные после сессии $n: $ST_SUMMARY"
  fi

  if [ "$ST_STATUS" = "step_done" ] || [ "$ST_STATUS" = "phase_done" ]; then
    if commit_boundary "$ST_STATUS"; then
      jrn ""
      jrn "- коммит границы: \`$COMMIT_INFO\`"
      echo "коммит: $COMMIT_INFO"
    else
      jrn "- **коммит не сделан:** $COMMIT_ERR"
      stop_with 8 "коммит границы шага после сессии $n не сделан: $COMMIT_ERR"
    fi
  fi

  if [ "$ST_STATUS" = "phase_done" ]; then
    stop_with 0 "фаза закрыта на сессии $n: $ST_SUMMARY"
  fi
done

jrn ""
jrn "**ЛИМИТ ($LAUNCH):** $MAX сессий отработано, все — \`continue\`/\`step_done\` с зелёными гейтами. Стоимость запуска: \$${TOTAL_COST}."
say "Лимит $MAX сессий исчерпан штатно. Стоимость запуска: \$${TOTAL_COST}"
echo "журнал: $JOURNAL"
