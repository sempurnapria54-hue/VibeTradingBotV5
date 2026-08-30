#!/usr/bin/env bash
# Батарея падающих проб для tools/spec-scope-check.py.
#
# Инструмент, чьи оси не доказаны падением, ничего не удостоверяет: зелёный
# прогон показывает, что скрипт ИСПОЛНЯЕТСЯ, а не что он МЕРИТ свой предмет.
# Каждая проба вносит во временную копию корпуса дефект ровно одной
# объявленной оси и засчитывается только при коде возврата 1.
#
# Запуск (из корня репозитория):  bash tools/spec-scope-probe.sh
# Код возврата: 0 — все оси доказаны; 1 — какая-то проба зелена (мутация
# перестала попадать в носитель — пробу переякорить, не удалять).
#
# ВНИМАНИЕ (Windows): там детектор поднимается только лаунчером `py`;
# `python` и `python3` в PATH — заглушки Windows Store и дают ложный
# зелёный. Интерпретатор выбирается ниже по платформе, а не зашит.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
DIR=target/probe-scope
FAILED=0

# Лаунчер `py` есть только под Windows; под Linux настоящий интерпретатор —
# `python3`. Молчаливого умолчания нет: если ни один не найден, скрипт
# падает, а не «проходит» без проверки.
if command -v py >/dev/null 2>&1; then
  PY=(py -3)
elif command -v python3 >/dev/null 2>&1; then
  PY=(python3)
else
  echo "ОШИБКА: не найден интерпретатор Python (ни py, ни python3)" >&2
  exit 3
fi

probe () {
  local axis="$1" mutation="$2"
  rm -rf "$DIR" && mkdir -p "$DIR" && cp docs/spec/*.json "$DIR/"
  eval "$mutation"
  echo "### ось: $axis"
  "${PY[@]}" tools/spec-scope-check.py "$DIR"
  local code=$?
  if [ "$code" -eq 0 ]; then
    echo "ПРОБА ЗЕЛЕНА — ось не доказана, мутация не попадает в носитель"
    FAILED=1
  fi
  echo
}

probe "1. разрыв у заимствованной величины (нетранзитивность includes)" \
  'perl -0pi -e "s/\"includes\": \[\"protection-coverage\", \"order-lifecycle\", \"risk-at-stop\"\]/\"includes\": [\"protection-coverage\", \"order-lifecycle\"]/" '"$DIR"'/deal-risk-numbers.json'

probe "2. разрыв у собственной величины (дом не подключён)" \
  'perl -0pi -e "s/\"includes\": \[\"order-lifecycle\"\],//" '"$DIR"'/protection-coverage.json'

probe "3. операнд-указатель признаётся тропой резолва" \
  'perl -0ni -e "s/^ *\"hasLiveEpisode\": \"[^\"]*\",\n//m; print" '"$DIR"'/stop-distance.json'

probe "4. класс B: подмена дома гостевым состоянием"   'perl -0pi -e "s/\"includes\": \[\"risk-at-stop\", \"stop-distance\", \"protection-coverage\", \"order-lifecycle\"\]/\"includes\": [\"risk-at-stop\", \"stop-distance\"]/" '"$DIR"'/strategy-reference.json'

probe "C1. объявленное исключение снято — совпадение выражений снова находка" \
  'perl -0pi -e "s/\n *\"independentFrom\": \"[^\"]*\",\n *\"independentReason\": \"[^\"]*\",//g" '"$DIR"'/order-lifecycle.json'

probe "C2. копия формы дома объявлена под собственным именем у соседа" \
  '"${PY[@]}" -c "
import json, io
p = \"'"$DIR"'/risk-limits.json\"
spec = json.load(io.open(p, encoding=\"utf-8\"))
spec[\"values\"].append({\"name\": \"copyOfStopDistanceFloor\", \"expr\": \"feeRate * (entryAnchor + stopPrice)\"})
json.dump(spec, io.open(p, \"w\", encoding=\"utf-8\"), ensure_ascii=False, indent=1)
"'

echo "### действительное состояние корпуса"
"${PY[@]}" tools/spec-scope-check.py docs/spec || FAILED=1

rm -rf "$DIR"
[ "$FAILED" -eq 0 ] && echo "ВСЕ ОСИ ДОКАЗАНЫ ПАДЕНИЕМ" || echo "ЕСТЬ НЕДОКАЗАННАЯ ОСЬ"
exit "$FAILED"
