# Чтение конфига цепочки сессий — подключается (source) сценариями
# tools/session-loop.sh и tools/session-unattended.sh; сам не запускается.
#
# ПРЕДМЕТ. Ручки цикла живут в tools/session-loop.conf, а не в `export`
# перед каждым стартом (решение держателя 2026-09-26). Переменная
# окружения, если задана (в том числе пустой), перекрывает строку файла.
# Источник каждого значения запоминается и печатается предполётной
# проверкой: «файл» либо «окружение».
#
# ФАЙЛ ЧИТАЕТСЯ, А НЕ ИСПОЛНЯЕТСЯ. `source` конфига выполнил бы в нём всё,
# что там окажется, и значение с пробелом (путь хранилища Docker) пришлось
# бы брать в кавычки. Поэтому разбор построчный: КЛЮЧ=значение, `#` —
# комментарий, `\r` в конце строки срезается (файл правят и из Windows).
#
# ПЕРЕЧЕНЬ КЛЮЧЕЙ — ЗДЕСЬ, ЗНАЧЕНИЯ — В ФАЙЛЕ. Ключ вне перечня (опечатка
# молча не действовала бы) и ключ, которого в файле нет, — отказ (код 2).
#
# Иной файл — SESSION_CONFIG=<путь> (сухие прогоны).

SESSION_CONFIG_FILE="${SESSION_CONFIG:-$ROOT/tools/session-loop.conf}"
SESSION_CONFIG_KEYS=(
  SESSION_TIMEOUT SESSION_MAX_USD SESSION_MODEL SESSION_EFFORT
  SESSION_PERMISSION_MODE SESSION_PROMPT SESSION_SKIP_PROBE
  SESSION_LOOP_DIR SESSION_MIN_FREE_GIB SESSION_DOCKER_DATA_DIR
  SESSION_UNATTENDED_PAUSE SESSION_UNATTENDED_LOOP
)
declare -A SESSION_CONFIG_ORIGIN=()

load_session_config() { # отказ — сообщение в stderr и код 2
  local file="$SESSION_CONFIG_FILE" line key val n=0 k known
  declare -A seen=()
  [ -f "$file" ] || { echo "ОТКАЗ: нет конфига цикла $file" >&2; return 2; }
  while IFS= read -r line || [ -n "$line" ]; do
    n=$(( n + 1 ))
    line="${line%$'\r'}"
    case "$line" in ""|"#"*) continue ;; esac
    if ! [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]]; then
      echo "ОТКАЗ: $file:$n — не КЛЮЧ=значение: «$line»" >&2; return 2
    fi
    key="${BASH_REMATCH[1]}"
    val="${BASH_REMATCH[2]}"
    known=0
    for k in "${SESSION_CONFIG_KEYS[@]}"; do [ "$k" = "$key" ] && known=1; done
    [ "$known" -eq 1 ] || { echo "ОТКАЗ: $file:$n — неизвестный ключ $key" >&2; return 2; }
    seen[$key]=1
    # Замена в кавычках: у bash 5.2 `&` и `\` в незакавыченной замене особые,
    # а путь Windows несёт обратные косые.
    val="${val//'${LOCALAPPDATA}'/"${LOCALAPPDATA:-$HOME}"}"
    if [ -n "${!key+x}" ]; then
      SESSION_CONFIG_ORIGIN[$key]="окружение"
    else
      printf -v "$key" '%s' "$val"
      SESSION_CONFIG_ORIGIN[$key]="файл"
    fi
  done <"$file"
  for k in "${SESSION_CONFIG_KEYS[@]}"; do
    [ -n "${seen[$k]+x}" ] || { echo "ОТКАЗ: в $file нет ключа $k" >&2; return 2; }
  done
}

# Экспорт разрешённых значений — ТОЛЬКО у цикла: их читают его дети
# (сессия видит остаток SESSION_TIMEOUT, tools/session-prompt.md). Обёртка
# не экспортирует, иначе цикл напечатал бы значения файла как «окружение».
export_session_config() {
  local k
  for k in "${SESSION_CONFIG_KEYS[@]}"; do export "$k"; done
}

print_session_config() { # $1… — ключи; без аргументов — все
  local k v keys=("$@")
  [ ${#keys[@]} -gt 0 ] || keys=("${SESSION_CONFIG_KEYS[@]}")
  echo "конфиг: $SESSION_CONFIG_FILE"
  for k in "${keys[@]}"; do
    v="${!k-}"
    # Ширина у printf байтовая: выравнивается только латинское имя ключа.
    printf '  %-25s %s  ← %s\n' "$k" "${v:-<не задан>}" "${SESSION_CONFIG_ORIGIN[$k]:-?}"
  done
}
