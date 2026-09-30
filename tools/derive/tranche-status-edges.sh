#!/usr/bin/env bash
# Вывод членов популяции «рёбра статусной машины транша».
#
# ПРЕДМЕТ ВЫВОДА. Пространство рёбер задаёт ПЕРЕЧЕНЬ СТАТУСОВ транша —
# таблица §Статусы в docs/lifecycles/DealTranche.md, — а не матрица
# переходов, которую кто-то написал: матрица молчит о ребре, которого в ней
# нет. Печатается полное произведение статусов без петель; какие из рёбер
# объявлены, а какие исключены, решает сама популяция (объявленный
# недостижимым член предъявляет контрпример с названной причиной
# исключения). Форма та же, что у рёбер сделки (tools/derive/deal-status-edges.sh).
#
# Печатает по строке на ребро: «из» TAB «в».
set -euo pipefail
# LC_ALL=C.UTF-8 обязателен у grep -P на объявленной среде (дом ловушки —
# .claude/rules/measurement-commands.md §«Ловушки среды»).
statuses=$(sed -n '/^## Статусы/,/^## Группы/p' docs/lifecycles/DealTranche.md \
           | LC_ALL=C.UTF-8 grep -oP '^\| `\K[A-Z_]+(?=`)' || true)
if [ -z "$statuses" ]; then
  echo "ВЫВОД НЕ СОСТОЯЛСЯ: таблица статусов docs/lifecycles/DealTranche.md не разобрана" >&2
  exit 2
fi
for from in $statuses; do
  for to in $statuses; do
    [ "$from" = "$to" ] || printf '%s\t%s\n' "$from" "$to"
  done
done
