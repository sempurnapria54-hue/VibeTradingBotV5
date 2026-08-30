#!/usr/bin/env bash
# Батарея падающих проб для tools/spec-mutation-check.sh.
#
# Инструмент, чьи оси не доказаны падением, ничего не удостоверяет: перечень
# недоказательных величин на действительном корпусе показывает, что скрипт
# ИСПОЛНЯЕТСЯ, а не что он МЕРИТ свой предмет. Каждая проба кладёт во
# временную копию корпуса фикстуру-спеку с величинами известной
# доказательности и засчитывается только тогда, когда замер разложил их
# ровно так, как объявлено: недоказательная — в перечне, доказанная — вне.
#
# Стандарт — .claude/processes/roadmap-step-execution.md §«Оси проверочной
# команды доказываются поимённо». Фикстура самодостаточна намеренно: проба,
# заякоренная на величину живого корпуса, зеленеет от любой правки этого
# корпуса, и тогда переякоривание проб становится постоянной работой.
#
# Запуск (из корня репозитория):  bash tools/spec-mutation-probe.sh
# Код возврата: 0 — все оси доказаны; 1 — какая-то ось не доказана;
# 3 — не найден интерпретатор Python.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1
DIR=target/probe-mutation
WORK=target/probe-mutation-work
FAILED=0

rm -rf "$DIR" "$WORK" && mkdir -p "$DIR" && cp docs/spec/*.json "$DIR/"

# Фикстура: пять величин, доказательность каждой известна по построению.
cat > "$DIR/probe-mutation.json" <<'JSON'
{
 "subject": "probe-mutation",
 "question": "Ловит ли мутационный замер недоказательную величину",
 "home": "tools/spec-mutation-probe.sh",
 "operands": {
  "amount": "число примера — единственный операнд фикстуры"
 },
 "values": [
  {
   "name": "probeProven",
   "note": "ДОКАЗАНА: примеры ожидают и true, и false — ни одна константа их не удовлетворяет.",
   "expr": "amount > 0"
  },
  {
   "name": "probeUnreferenced",
   "note": "НЕДОКАЗАТЕЛЬНА: величину не зовёт ни один пример.",
   "expr": "amount < 0"
  },
  {
   "name": "probeOneSided",
   "note": "НЕДОКАЗАТЕЛЬНА: предикат ожидается true во всех примерах — фальсифицирующего примера нет.",
   "expr": "amount >= 0"
  },
  {
   "name": "probeNumberSingle",
   "note": "НЕДОКАЗАТЕЛЬНА: число ожидается одним и тем же номиналом — его удовлетворяет константа-литерал корпуса.",
   "expr": "amount * 0 + 7"
  },
  {
   "name": "probeNumberVaried",
   "note": "ДОКАЗАНА: число ожидается двумя разными номиналами.",
   "expr": "amount * 2"
  },
  {
   "name": "probeTheorem",
   "note": "ТЕОРЕМА: ложна по построению (тождество), но читает содержание основания — косвенная проба обязана пройти.",
   "provenBy": "probeNumberVaried",
   "expr": "probeNumberVaried != amount * 2"
  },
  {
   "name": "probeFalseTheorem",
   "note": "ДЕФЕКТ: объявляет основанием величину, от содержания которой не зависит — косвенная проба обязана НЕ пройти, и указатель от дефекта не спасает.",
   "provenBy": "probeProven",
   "expr": "amount < 0"
  }
 ],
 "examples": [
  {
   "case": "положительное значение",
   "state": { "amount": 5 },
   "expect": {
    "probeProven": true,
    "probeOneSided": true,
    "probeNumberSingle": 7,
    "probeNumberVaried": 10,
    "probeTheorem": false,
    "probeFalseTheorem": false
   }
  },
  {
   "case": "нулевое значение",
   "state": { "amount": 0 },
   "expect": {
    "probeProven": false,
    "probeOneSided": true,
    "probeNumberSingle": 7,
    "probeNumberVaried": 0,
    "probeTheorem": false,
    "probeFalseTheorem": false
   }
  }
 ]
}
JSON

REPORT="$(bash tools/spec-mutation-check.sh "$DIR" "$WORK" 2>&1)"

# $1 — имя оси, $2 — величина, $3 — must_appear|must_not_appear|must_be_theorem
axis () {
  local name="$1" value="$2" mode="$3" line
  line="$(printf '%s\n' "$REPORT" | grep " / $value —" || true)"
  if [ "$mode" = "must_be_theorem" ]; then
    echo "### ось: $name"
    if printf '%s' "$line" | grep -q "теорема"; then
      printf '%s\n' "$line" | sed 's/^/  /'
    else
      echo "  ${line:-величина $value в перечне отсутствует}"
      echo "  ОСЬ НЕ ДОКАЗАНА: величина-теорема косвенной пробой не подтверждена"
      FAILED=1
    fi
    echo
    return
  fi
  echo "### ось: $name"
  if [ -n "$line" ]; then
    printf '%s\n' "$line" | sed 's/^/  /'
    if [ "$mode" = "must_not_appear" ]; then
      echo "  ОСЬ НЕ ДОКАЗАНА: доказанная величина попала в перечень (ложное срабатывание)"
      FAILED=1
    fi
  else
    echo "  величина $value в перечне отсутствует"
    if [ "$mode" = "must_appear" ]; then
      echo "  ОСЬ НЕ ДОКАЗАНА: недоказательная величина не найдена"
      FAILED=1
    fi
  fi
  echo
}

axis "1. величину не зовёт ни один пример"                         probeUnreferenced must_appear
axis "2. предикат без фальсифицирующего примера (везде true)"      probeOneSided     must_appear
axis "3. число с единственным ожидаемым номиналом"                 probeNumberSingle must_appear
axis "4. контроль: предикат с обоими исходами не объявляется недоказательным" \
                                                                   probeProven       must_not_appear
axis "5. контроль: число с двумя номиналами не объявляется недоказательным" \
                                                                   probeNumberVaried must_not_appear
axis "6. величина-теорема подтверждается косвенной пробой основания"  probeTheorem      must_be_theorem
axis "7. указатель на постороннее основание от дефекта не спасает"    probeFalseTheorem must_appear

echo "### действительное состояние корпуса"
bash tools/spec-mutation-check.sh 2>&1 | tail -1

rm -rf "$DIR" "$WORK"
if [ "$FAILED" -eq 0 ]; then
  echo "ВСЕ ОСИ ДОКАЗАНЫ"
else
  echo "ЕСТЬ НЕДОКАЗАННАЯ ОСЬ"
fi
exit "$FAILED"
