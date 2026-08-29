#!/usr/bin/env bash
# Проверка реестра гейтящих предусловий CODE против тест-плана контура.
#
# Что меряется:
#   1) слот-сверка — множество пар «пункт × кейс» реестра == множеству пар,
#      объявленных гейт-заголовками плана ("**ГЕЙТИТ `CODE`** (предусловия п. N …)");
#   2) разведённость слота — в одной строке реестра ровно один кейс
#      (иначе статус наблюдения нельзя назначить покейсно);
#   3) носители прогона — по каждому кейсу реестра: есть ли код-тест и запрос коллекции.
#
# Колонки берутся ПО ИМЕНАМ шапки таблицы («Пункт»/«№» и «Кейс»), не по позиции:
# смена формы таблицы обязана ронять проверку громко, а не менять смысл вывода молча.
#
# Использование:
#   bash tools/preconditions-check.sh [<plan.md> <code-preconditions.md> <src-test-dir> <collection.json>]
# Без аргументов — умолчания для контура OKX (шаг 7 фазы 1).
# Код возврата: 0 — сошлось, 1 — расхождение/дефект, 3 — форма таблицы не разобрана.

set -u
PLAN="${1:-.claude/tests/source-api/okx/plan.md}"
REG="${2:-.claude/tests/source-api/okx/code-preconditions.md}"
TESTS="${3:-src/test/java/com/example/tradingbot/integration/sourceapi/okx/}"
COLL="${4:-.claude/tests/source-api/okx/collection.postman_collection.json}"

for f in "$PLAN" "$REG"; do
  [ -f "$f" ] || { echo "ОШИБКА: нет файла $f" >&2; exit 3; }
done

rc=0
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# --- пары «кейс пункт», объявленные планом
grep '^### .*ГЕЙТИТ' "$PLAN" \
| sed -E 's/^### ([A-Za-z0-9.]+).*ГЕЙТИТ[^(]*\((.*)\) *$/\1 :: \2/' \
| sed -E 's/пп\. *([0-9]+) и ([0-9]+)/п. \1 п. \2/g' \
| awk -F' :: ' '{n=split($2,a,/п\. */); for(i=2;i<=n;i++) if (match(a[i],/^[0-9]+/)) print $1" "substr(a[i],RSTART,RLENGTH)}' \
| sort -u > "$tmp/plan-pairs"

[ -s "$tmp/plan-pairs" ] || { echo "ОШИБКА: в плане не разобран ни один гейт-заголовок — форма заголовков изменилась" >&2; exit 3; }

# --- пары «кейс пункт» и перечень кейсов из таблицы реестра
awk -F'|' '
  function trim(s){gsub(/^[ \t]+|[ \t]+$/,"",s); return s}
  /^\|/ && (p==0 || c==0) {
    for (i=2;i<=NF;i++){ h=trim($i); if (h=="Пункт"||h=="№") p=i; if (h=="Кейс") c=i }
    if (p&&c) next
  }
  /^\| *[0-9]+ *\|/ {
    if (!p||!c) { print "ОШИБКА: шапка таблицы реестра не разобрана (нет колонок «Пункт»/«№» и «Кейс»)" > "/dev/stderr"; exit 3 }
    n=trim($p); k=$c; gsub(/`/,"",k); m=0; split("",ids)
    while (match(k,/[A-Za-z]+[0-9]+\.[0-9]+/)) { m++; ids[m]=substr(k,RSTART,RLENGTH); k=substr(k,RSTART+RLENGTH) }
    if (m==0) { print "ДЕФЕКТ: пункт "n" — кейс не назван" > "/dev/stderr"; bad=1; next }
    if (m>1)  { print "ДЕФЕКТ: пункт "n" — в одной строке "m" кейса ("ids[1]", "ids[2]"…): слот не разведён, статус наблюдения нельзя назначить покейсно" > "/dev/stderr"; bad=1 }
    for (j=1;j<=m;j++) print ids[j]" "n
  }
  END { if (bad) exit 4 }
' "$REG" | sort -u > "$tmp/reg-pairs" || rc=1

st=${PIPESTATUS:-0}
[ -s "$tmp/reg-pairs" ] || { echo "ОШИБКА: из таблицы реестра не извлечено ни одной пары" >&2; exit 3; }

if ! diff "$tmp/plan-pairs" "$tmp/reg-pairs" > "$tmp/d"; then
  echo "ДЕФЕКТ: множества слотов «пункт × кейс» расходятся (< только в плане, > только в реестре):"
  cat "$tmp/d"
  rc=1
fi

# --- носители прогона; перечень кейсов берётся ИЗ РЕЕСТРА, не хардкодом
awk '{print $1}' "$tmp/reg-pairs" | sort -u > "$tmp/cases"
echo "--- носители прогона (кейс: код-тест / коллекция)"
while read -r c; do
  t=0; p=0
  [ -d "$TESTS" ] && t=$(grep -rl -- "$c" "$TESTS" 2>/dev/null | wc -l | tr -d ' ')
  [ -f "$COLL" ] && p=$(grep -c -- "$c" "$COLL" 2>/dev/null || true)
  printf '%-8s код-тест=%s коллекция=%s\n' "$c" "$t" "$p"
done < "$tmp/cases"

if [ $rc -eq 0 ]; then echo "СОШЛОСЬ: слоты реестра == гейт-пометки плана"; fi
exit $rc
