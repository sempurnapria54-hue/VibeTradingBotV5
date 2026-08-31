#!/usr/bin/env bash
# Вывод членов популяции «классы пары фаза × политика входа».
#
# ПРЕДМЕТ ВЫВОДА. Матрица допустимости — полное произведение двух закрытых
# перечней: значений MarketPhase.Type (docs/models/domain/other/MarketPhase.md
# §«Енум `Type`») и значений PhaseEntryPolicy
# (docs/models/domain/aggregate/Strategy.md). Перечень пар, «которые
# встретились в примерах», предметом не является: клейм популяции — матрица
# ЦЕЛИКОМ.
#
# Печатает по строке на пару: фаза TAB политика.
set -euo pipefail
phases=$(sed -n '/^## Енум `Type`/,/^## Вычисление/p' docs/models/domain/other/MarketPhase.md \
         | grep -oP '`\K[A-Z_]+(?=`)' | head -4)
policies=$(grep -oP '^`PhaseEntryPolicy` — \K.*' docs/models/domain/aggregate/Strategy.md \
           | grep -oP '`\K[A-Z_]+(?=`)')
for phase in $phases; do
  for policy in $policies; do
    printf '%s\t%s\n' "$phase" "$policy"
  done
done
