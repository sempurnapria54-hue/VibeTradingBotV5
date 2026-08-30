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
# ВНИМАНИЕ: сам детектор поднимается только лаунчером `py`; `python` и
# `python3` в PATH — заглушки Windows Store и дают ложный зелёный.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
DIR=target/probe-scope
FAILED=0

probe () {
  local axis="$1" mutation="$2"
  rm -rf "$DIR" && mkdir -p "$DIR" && cp docs/spec/*.json "$DIR/"
  eval "$mutation"
  echo "### ось: $axis"
  py -3 tools/spec-scope-check.py "$DIR"
  local code=$?
  if [ "$code" -eq 0 ]; then
    echo "ПРОБА ЗЕЛЕНА — ось не доказана, мутация не попадает в носитель"
    FAILED=1
  fi
  echo
}

probe "1. разрыв у заимствованной величины (нетранзитивность includes)" \
  'perl -0pi -e "s/\"includes\": \[\"protection-coverage\", \"order-lifecycle\"\]/\"includes\": [\"protection-coverage\"]/" '"$DIR"'/deal-risk-numbers.json'

probe "2. разрыв у собственной величины (дом не подключён)" \
  'perl -0pi -e "s/\"includes\": \[\"order-lifecycle\"\],//" '"$DIR"'/protection-coverage.json'

probe "3. операнд-указатель признаётся тропой резолва" \
  'perl -0ni -e "s/^ *\"hasLiveEpisode\": \"[^\"]*\",\n//m; print" '"$DIR"'/stop-distance.json'

probe "4. класс B: подмена дома гостевым состоянием"   'perl -0pi -e "s/\"includes\": \[\"stop-distance\", \"protection-coverage\", \"order-lifecycle\"\]/\"includes\": [\"stop-distance\"]/" '"$DIR"'/strategy-reference.json'

echo "### действительное состояние корпуса"
py -3 tools/spec-scope-check.py docs/spec || FAILED=1

rm -rf "$DIR"
[ "$FAILED" -eq 0 ] && echo "ВСЕ ОСИ ДОКАЗАНЫ ПАДЕНИЕМ" || echo "ЕСТЬ НЕДОКАЗАННАЯ ОСЬ"
exit "$FAILED"
