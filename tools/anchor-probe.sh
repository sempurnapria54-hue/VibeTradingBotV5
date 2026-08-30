#!/usr/bin/env bash
# Предмет: оси tools/anchor-check.py доказываются падающей пробой — по одной
# на ось. Проба засчитана, только если падает; зелёная означает, что мутация
# перестала попадать в носитель, и проба переякоривается.
set -u
t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
ok=0; fail=0
probe() { # имя; ожидание: rc != 0
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "  ЗЕЛЕНО (проба не доказывает): $name"; fail=$((fail+1));
  else echo "  падает: $name"; ok=$((ok+1)); fi
}
mk() { mkdir -p "$t/$1"; }

# ось 1: адрес, которому не соответствует ни заголовок, ни лид-жирный пассаж
mk a; printf '# Ф\n\nСм. §«Пассажа такого нет вовсе».\n' > "$t/a/f.md"
probe "ось 1 — пассаж не существует" python3 tools/anchor-check.py "$t/a"

# ось 2: адрес в чужой файл, названный в том же абзаце
mk b; printf '# Ц\n\n## Настоящий пассаж\n' > "$t/b/target.md"
printf '# И\n\nДом — `%s/b/target.md` §«Выдуманный пассаж».\n' "$t" > "$t/b/src.md"
probe "ось 2 — чужой файл, пассажа нет" python3 tools/anchor-check.py "$t/b"

# ось 3: сокращение НЕ по границе хвоста дефектом остаётся
mk c; printf '# Ц\n\n## Длинное имя пассажа продолжается дальше\n' > "$t/c/target.md"
printf '# И\n\n`%s/c/target.md` §«Длинное имя пассажа».\n' "$t" > "$t/c/src.md"
probe "ось 3 — сокращение не по границе" python3 tools/anchor-check.py "$t/c"

# ось 4: вход не разобран — каталога нет
probe "ось 4 — каталог не найден (rc=3)" python3 tools/anchor-check.py "$t/нет-такого"

# контроль ложного срабатывания 1: заголовок
mk d; printf '# Ф\n\n## Есть такой пассаж\n\nСм. §«Есть такой пассаж».\n' > "$t/d/f.md"
if python3 tools/anchor-check.py "$t/d" >/dev/null 2>&1; then echo "  контроль: заголовок разрешается"; ok=$((ok+1));
else echo "  ЛОЖНОЕ СРАБАТЫВАНИЕ: заголовок"; fail=$((fail+1)); fi

# контроль 2: лид-жирный пассаж
mk e; printf '# Ф\n\n**Жирный лид.** Текст.\n\nСм. §«Жирный лид».\n' > "$t/e/f.md"
if python3 tools/anchor-check.py "$t/e" >/dev/null 2>&1; then echo "  контроль: лид-жирный разрешается"; ok=$((ok+1));
else echo "  ЛОЖНОЕ СРАБАТЫВАНИЕ: лид-жирный"; fail=$((fail+1)); fi

# контроль 3: законный хвост (скобка / тире) опускается
mk g; printf '# Ф\n\n## Имя пассажа (решение держателя, 2026-08-26)\n\nСм. §«Имя пассажа».\n' > "$t/g/f.md"
if python3 tools/anchor-check.py "$t/g" >/dev/null 2>&1; then echo "  контроль: скобочный хвост опускается"; ok=$((ok+1));
else echo "  ЛОЖНОЕ СРАБАТЫВАНИЕ: скобочный хвост"; fail=$((fail+1)); fi

echo
if [ "$fail" -eq 0 ]; then echo "ВСЕ ОСИ ДОКАЗАНЫ ПАДЕНИЕМ (проб: $ok)"; exit 0
else echo "НЕ ДОКАЗАНО: $fail"; exit 1; fi
