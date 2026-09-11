#!/usr/bin/env bash
# Вывод членов популяций резолва причины остановки: перечень радиусов и
# полное произведение «стои́т / не стои́т» по каждому из них.
#
# ПРЕДМЕТ ВЫВОДА. Пространство задаёт объявление enum HoldScope — дом
# соответствия «радиус → причина», — а не список случаев, который кто-то
# написал. Радиусы читаются из объявления, и произведение строится ПО НИМ:
# третий радиус даёт восемь членов из трёх полей вместо четырёх из двух, то
# есть перечень разойдётся с объявленным и `population-derive-check` покажет
# РАСХОЖДЕНИЕ, а не отказ замера.
#
# ПОРЯДОК ПОЛЕЙ — порядок ключей популяции: счётный радиус первым
# (underAccountRung), прочие следом в порядке объявления enum.
#
# Без ключа: по строке на член произведения, поля разделены табуляцией.
# С ключом --scopes: по строке на радиус (популяция «радиус собственной
# реакции у первого хода»).
set -euo pipefail
# LC_ALL=C.UTF-8 обязателен у grep -P на объявленной среде (дом ловушки —
# .claude/processes/roadmap-step-execution.md §«`grep -P` в этой среде
# требует `LC_ALL=C.UTF-8`»).
scopes=$(LC_ALL=C.UTF-8 grep -oP '^\s{4}\K[A-Z_]+(?=\()' \
         services/trading-core/src/main/java/com/example/tradingcore/domain/safety/HoldScope.java)

# Счётный радиус первым: его ключ (underAccountRung) стои́т первым в популяции.
ordered=$( { printf '%s\n' "$scopes" | grep -x 'EXCHANGE_ACCOUNT' || true
             printf '%s\n' "$scopes" | grep -vx 'EXCHANGE_ACCOUNT' || true; } | grep . )

if [ "${1:-}" = "--scopes" ]; then
  printf '%s\n' "$ordered"
  exit 0
fi

members=("")
while IFS= read -r scope; do
  [ -n "$scope" ] || continue
  next=()
  for row in "${members[@]}"; do
    for standing in false true; do
      if [ -z "$row" ]; then
        next+=("$standing")
      else
        next+=("$row"$'\t'"$standing")
      fi
    done
  done
  members=("${next[@]}")
done <<< "$ordered"
printf '%s\n' "${members[@]}"
