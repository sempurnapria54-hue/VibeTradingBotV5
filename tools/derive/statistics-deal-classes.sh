#!/usr/bin/env bash
# Члены популяции «класс закрытой сделки для агрегата» — полным произведением.
#
# ПРЕДМЕТ. Перечень выводится из ДВУХ осей дока-дома
# (docs/rules/statistics-aggregates.md §«Классы закрытой сделки для
# агрегата»), а не из списка классов, встреченных в примерах: список
# встреченного молчит ровно о том классе, которого в нём нет, — а
# недостижимые классы и есть то, о чём правило обязано высказаться.
#
# LC_ALL=C.UTF-8 обязателен у grep -P на объявленной среде (дом ловушки —
# .claude/processes/roadmap-step-execution.md §«`grep -P` в этой среде
# требует `LC_ALL=C.UTF-8`»). Классы символов здесь ASCII-only намеренно:
# `\w` в PCRE кириллицу не берёт, и шаблон по русскому слову молча дал бы
# ноль (та же §, клауза о кириллице).
#
# Разделитель кортежа — ТАБУЛЯЦИЯ: сверщик режет строку по ней
# (tools/population-derive-check.py, функция derive).
set -euo pipefail

DOC="${1:-docs/rules/statistics-aggregates.md}"

# Ось «приняла риск» — из своей строки таблицы классов.
risk_row=$(LC_ALL=C.UTF-8 grep -m1 -F '| приняла риск |' "$DOC" || true)
risk=$(printf '%s' "$risk_row" | LC_ALL=C.UTF-8 grep -oP '`\K[a-z]+(?=`)' || true)

# Ось «результат» — из своей строки той же таблицы.
result_row=$(LC_ALL=C.UTF-8 grep -m1 -F '| результат |' "$DOC" || true)
result=$(printf '%s' "$result_row" | LC_ALL=C.UTF-8 grep -oP '`\K[A-Z]+(?=`)' || true)

if [ -z "$risk" ] || [ -z "$result" ]; then
  echo "ВЫВОД НЕ СОСТОЯЛСЯ: в $DOC не найдена таблица классов закрытой сделки" >&2
  exit 2
fi

for r in $risk; do
  for c in $result; do
    printf '%s\t%s\n' "$r" "$c"
  done
done
