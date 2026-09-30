#!/usr/bin/env bash
# Члены популяции «значение RiskCheckCode × реакция при живом риске» — из
# объявления перечня кодов и из таблицы карв-аута, а не из примеров.
#
# ПРЕДМЕТ. Правило «у каждого кода преконтроля названа реакция карты при
# живом риске» обязано иметь исход на КАЖДОМ значении перечня: перечень,
# собранный из кодов, встреченных в примерах, молчит ровно о том коде,
# которого в нём нет. Реакция выводится из дома членства — таблицы
# карв-аута (docs/processes/risk-evaluation.md, первая колонка строк
# таблицы «Код | Почему не признак рассогласования»): код из таблицы —
# SKIP_ACTION, всякий иной — MOVE_DEAL_TO_ERROR. Формула спеки
# (величина liveRiskBlockReaction) обязана совпасть с этим выводом; так
# расхождение формы с таблицей становится расхождением перечней.
#
# КОМАНДА ЧИТАЕТ ОБЕ СТОРОНЫ И СВЕРЯЕТ ИХ. Код таблицы, которого нет в
# перечне RiskCheckCode, законен только как код расчётного слоя
# (CalculationErrorCodes общего артефакта расчёта): его карв-аут исполняет
# другое звено, и в популяцию он не входит. Всякий иной код таблицы вне
# перечня — отказ вывода (код 2): таблица называет членство, которого
# некому исполнить. Отказ по неполноте графа (DEAL_GRAPH_INCOMPLETE) уводит
# в ERROR на любой стадии, и его строка в таблице — тоже отказ вывода.
#
# LC_ALL=C.UTF-8 обязателен у grep -P на объявленной среде (дом ловушки —
# .claude/rules/measurement-commands.md §«Ловушки среды»). Классы символов
# ASCII-only намеренно: коды латиницей, а `\w` в PCRE кириллицу не берёт.
#
# Разделитель кортежа — ТАБУЛЯЦИЯ: сверщик режет строку по ней
# (tools/population-derive-check.py, функция derive).
set -euo pipefail

ENUM="services/trading-core/src/main/java/com/example/tradingcore/domain/command/risk/RiskCheckResult.java"
CALC="services/common/strategy-engine/src/main/java/com/example/strategy/engine/calc/util/CalculationErrorCodes.java"
DOC="docs/processes/risk-evaluation.md"

for file in "$ENUM" "$CALC" "$DOC"; do
  if [ ! -f "$file" ]; then
    echo "ВЫВОД НЕ СОСТОЯЛСЯ: нет файла $file" >&2
    exit 2
  fi
done

# Значения перечня — константы вложенного enum с признаком бессрочности.
codes=$(LC_ALL=C.UTF-8 grep -oP '^\s{8}\K[A-Z_]+(?=\((true|false)\))' "$ENUM" || true)
# Члены карв-аута — первая колонка строк таблицы до первой пустой строки.
carve=$(sed -n '/^| Код | Почему не признак рассогласования |/,/^$/p' "$DOC" \
        | LC_ALL=C.UTF-8 grep -oP '^\| `\K[A-Z_]+(?=`)' || true)
# Коды расчётного слоя — строковые константы общего артефакта расчёта.
calc=$(LC_ALL=C.UTF-8 grep -oP 'public static final String \K[A-Z_]+(?= =)' "$CALC" || true)

if [ -z "$codes" ] || [ -z "$carve" ]; then
  echo "ВЫВОД НЕ СОСТОЯЛСЯ: не разобран перечень кодов ($ENUM) либо таблица карв-аута ($DOC)" >&2
  exit 2
fi

for member in $carve; do
  if printf '%s\n' "$codes" | grep -xF -- "$member" >/dev/null; then
    if [ "$member" = "DEAL_GRAPH_INCOMPLETE" ]; then
      echo "ВЫВОД НЕ СОСТОЯЛСЯ: DEAL_GRAPH_INCOMPLETE в таблице карв-аута — его реакция не делится стадией" >&2
      exit 2
    fi
    continue
  fi
  if printf '%s\n' "$calc" | grep -xF -- "$member" >/dev/null; then
    continue
  fi
  echo "ВЫВОД НЕ СОСТОЯЛСЯ: член карв-аута $member не значение RiskCheckCode и не код расчёта" >&2
  exit 2
done

for code in $codes; do
  if printf '%s\n' "$carve" | grep -xF -- "$code" >/dev/null; then
    printf '%s\t%s\n' "$code" "SKIP_ACTION"
  else
    printf '%s\t%s\n' "$code" "MOVE_DEAL_TO_ERROR"
  fi
done
