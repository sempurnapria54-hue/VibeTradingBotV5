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
# LC_ALL=C.UTF-8 обязателен у grep -P на объявленной среде (дом ловушки —
# .claude/processes/roadmap-step-execution.md §«`grep -P` в этой среде
# требует `LC_ALL=C.UTF-8`»; B2/E2/G-6 `DOCS_CHECK_33`).
# Перечень фаз извлекается ПРИЗНАКОМ — строкой-перечнем секции (строка
# целиком состоит из кодов в бэктиках), а не позиционным `head -4`:
# позиционный срез молча ломался бы на росте перечня и на новом коде в
# прозе секции (E6 `DOCS_CHECK_33`).
phases=$(sed -n '/^## Енум `Type`/,/^## Вычисление/p' docs/models/domain/other/MarketPhase.md \
         | LC_ALL=C.UTF-8 grep -m1 -E '^`[A-Z_]+`(, `[A-Z_]+`)*\.?$' \
         | LC_ALL=C.UTF-8 grep -oP '`\K[A-Z_]+(?=`)')
policies=$(LC_ALL=C.UTF-8 grep -oP '^`PhaseEntryPolicy` — \K.*' docs/models/domain/aggregate/Strategy.md \
           | LC_ALL=C.UTF-8 grep -oP '`\K[A-Z_]+(?=`)')
for phase in $phases; do
  for policy in $policies; do
    printf '%s\t%s\n' "$phase" "$policy"
  done
done
