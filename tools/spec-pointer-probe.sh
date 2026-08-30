#!/usr/bin/env bash
# Предмет: оси tools/spec-pointer-check.py доказываются падающей пробой.
# Мутации — во временной копии; рабочие файлы не трогаются.
set -u
ok=0; fail=0

w=$(mktemp -d); trap 'rm -rf "$w"' EXIT
mkdir -p "$w/docs" "$w/.claude"
cp -r docs/spec "$w/docs/spec"
mkdir -p "$w/docs/rules"
printf '# П\n\nФорма — `docs/spec/stop-distance.json` (`lossAtStopPerUnit`).\n' > "$w/docs/rules/p.md"
if ( cd "$w" && python3 "$OLDPWD/tools/spec-pointer-check.py" ) >/dev/null 2>&1; then
  echo "  ЗЕЛЕНО (проба не доказывает): ось 1 — указатель на чужую спеку"; fail=$((fail+1))
else echo "  падает: ось 1 — указатель на чужую спеку"; ok=$((ok+1)); fi

# контроль ложного срабатывания: тот же указатель, но на дом
printf '# П\n\nФорма — `docs/spec/risk-at-stop.json` (`lossAtStopPerUnit`).\n' > "$w/docs/rules/p.md"
if ( cd "$w" && python3 "$OLDPWD/tools/spec-pointer-check.py" ) >/dev/null 2>&1; then
  echo "  контроль: указатель на дом проходит"; ok=$((ok+1))
else echo "  ЛОЖНОЕ СРАБАТЫВАНИЕ: указатель на дом"; fail=$((fail+1)); fi

# ось 2: каталог спек не найден — громкий отказ, не тихий ноль
if ( cd "$w/docs" && python3 "$OLDPWD/tools/spec-pointer-check.py" ) >/dev/null 2>&1; then
  echo "  ЗЕЛЕНО (проба не доказывает): ось 2 — каталог спек не найден"; fail=$((fail+1))
else echo "  падает: ось 2 — каталог спек не найден (rc=3)"; ok=$((ok+1)); fi

echo
if [ "$fail" -eq 0 ]; then echo "ВСЕ ОСИ ДОКАЗАНЫ ПАДЕНИЕМ (проб: $ok)"; exit 0
else echo "НЕ ДОКАЗАНО: $fail"; exit 1; fi
