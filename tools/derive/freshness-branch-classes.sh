#!/usr/bin/env bash
# Вывод членов популяции «состояния транша, различимые дискриминатором ветви
# реакции на устаревание данных».
#
# ПРЕДМЕТ ВЫВОДА. Классы различимости задаёт САМА ФОРМУЛА дискриминатора:
# величина protectionBranch в docs/spec/market-data-freshness.json. Её
# булевы операнды перебираются полным произведением — так перечень выводится
# из предмета, а не переписывается с множества кортежей, которые случайно
# оказались в примерах.
#
# Печатает по строке на член, значения операндов через TAB, в порядке
# объявления операндов в формуле (он же — порядок ключей популяции).
set -euo pipefail
# Лаунчер Python — переносимая форма: на объявленной среде python3 — заглушка
# WindowsApps, исполняет только py -3 (дом фактов среды —
# .claude/tests/source-api/okx/code-preconditions.md §«Среда контура»,
# строка «Лаунчер Python»; B2/E2/G-6 `DOCS_CHECK_33`).
if command -v py >/dev/null 2>&1; then PY_LAUNCHER=(py -3); else PY_LAUNCHER=(python3); fi
"${PY_LAUNCHER[@]}" - "$@" <<'PY'
import itertools
import json
import re

SPEC = 'docs/spec/market-data-freshness.json'
spec = json.load(open(SPEC, encoding='utf-8'))
expr = next(v for v in spec['values'] if v['name'] == 'protectionBranch')['expr']
# Операнды формулы — в порядке их появления в ней; берутся только те имена,
# которые объявлены операндами спеки (величины и литералы отсеиваются).
declared = {name.rstrip('?') for name in spec['operands']}
seen = []
for token in re.findall(r'[A-Za-z_][A-Za-z0-9_]*', expr):
    if token in declared and token not in seen:
        seen.append(token)
for combination in itertools.product(['true', 'false'], repeat=len(seen)):
    print('\t'.join(combination))
PY
