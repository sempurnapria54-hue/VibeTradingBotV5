#!/usr/bin/env bash
# Члены популяции «окружение × контур площадки» — полным произведением.
#
# ПРЕДМЕТ. Перечень выводится из ДВУХ перечней дока-дома, а не из списка
# объявленных пар: список объявленных — это то, что кто-то написал, и он
# молчит о паре, которой в нём нет. Окружения берутся из шапки таблицы
# §«Чем различаются окружения», контуры — из её строки «допустимые контуры
# площадки» (union по всем окружениям).
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

DOC="${1:-docs/architecture/platform.md}"

# Окружения — из шапки таблицы: | Ось | `dev` | `stage` | `prod` |
header=$(LC_ALL=C.UTF-8 grep -m1 -F '| Ось |' "$DOC")
environments=$(printf '%s' "$header" | LC_ALL=C.UTF-8 grep -oP '`\K[a-z]+(?=`)')

# Контуры — из строки допустимых контуров, union по всем окружениям.
row=$(LC_ALL=C.UTF-8 grep -m1 -F 'допустимые контуры площадки' "$DOC")
contours=$(printf '%s' "$row" | LC_ALL=C.UTF-8 grep -oP '`\K[A-Z]+(?=`)' | sort -u)

if [ -z "$environments" ] || [ -z "$contours" ]; then
  echo "ВЫВОД НЕ СОСТОЯЛСЯ: в $DOC не найдена таблица окружений либо строка контуров" >&2
  exit 2
fi

for e in $environments; do
  for c in $contours; do
    printf '%s\t%s\n' "$e" "$c"
  done
done
