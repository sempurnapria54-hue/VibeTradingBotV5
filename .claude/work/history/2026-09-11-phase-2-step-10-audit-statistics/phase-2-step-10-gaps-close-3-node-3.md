# Узел 3 закрытия `GAPS_CLOSE_3` — состав и семантика счётчиков агрегата (B1, B2, B5, B6, C5)

## На какой вопрос отвечает этот файл

Как закрыт узел 3 прогона `DOCS_CHECK_3` шага 10 фазы 2.

Рамка — `.claude/decisions/per-node-closure-frame.md` в редакции поправки
держателя по `PROC-Q5`: чистота меряется **по носителю**, рационал —
проверяемый минимум. Метод узлов — четыре конвенции в
`.claude/work/progress/phase-2-step-10-chronicle.md` §«Четыре конвенции,
введённые узлом 1 и обязательные для узлов 2-6».

## Правило узла

**Число агрегата не говорит о себе неправды: его имя и его нота называют ту
популяцию, которую оно считает, а перечислимый операнд отбора покрыт
целиком — счётчиком, выводимым остатком либо объявленной недостижимостью, и
отнесение проверяется прогоном.**

Правило одно на пять находок:

| Находка | Чем число говорит о себе неправду |
|---|---|
| B1 | из четырёх исходов закрытия счётчик есть у одного; три прочих читаются как штатный выход |
| B2 | `plannedRiskExcludedSum` объявлена «величиной непокрытого риска», а на строке нерезолвленной валюты это сумма без единицы измерения |
| B5 | `anomalyReports` назван «отчётов о происшествиях» и считает в том числе **снятие** ручного холда — состояние, происшествию противоположное |
| B6 | `orderDecisions` назван «решений о заявке» и считает подмножество: решения об отдельной условной заявке классом события не выражены |
| C5 | операнд `grossResult` питает `netResultSum`, чья собственная нота этот способ именования запрещает |

## Уровень пробела и род закрывающего действия

Уровень — **носитель** (`.claude/skills/classify-gap-level.md`) по всем
пяти: истина в каждом случае известна и объявлена своим домом, а расходится
с ней **клейм, написанный рядом**. `CloseOutcome` объявлен четырьмя
значениями и старшинством у своего владельца
(`docs/models/domain/aggregate/Deal.md`,
`docs/spec/position-close-outcome.json`); запрет складывать числа без
единицы измерения объявлен §«Зерно строки» того же правила, которое его
нарушает; обязательность двух разрезов объявлена абзацем выше того
счётчика, у которого их нет. Ни одно из пяти не требует решения,
связывающего носители впервые, — поэтому уровень не «концепция-стык», как у
узла 2.

Род действия, который уровню предписан, — свести клейм к его дому **и
предъявить механическую охрану того, что сведение состоялось**. Второе
несущее: у четырёх перечислимых операндов отбора отнесение значений к
величинам не мерил прогоном никто, и потому расхождение росло молча.

## Популяция правила

Собрана **до первой правки**, четырьмя командами — по одной на форму, в
которой число говорит о себе неправду. Рабочий питон назван в §Среда
снапшота; запуск из корня репозитория.

### Ось A — перечислимый операнд отбора и доля его значений, отнесённых величиной

```bash
python - <<'PY'
import json, io, re
d = json.load(io.open('docs/spec/statistics-aggregates.json', encoding='utf-8'))
blob = ' | '.join(v.get(k, '') for v in d['values'] for k in ('expr', 'where', 'of'))
for name, text in d['operands'].items():
    members = list(dict.fromkeys(re.findall(r'\b[A-Z][A-Z_]{2,}\b', text)))
    if len(members) < 2:
        continue
    marks = [("+" if ("'" + m + "'") in blob else "-") + m for m in members]
    print('%-26s %d/%d  %s' % (name, sum(m[0] == '+' for m in marks), len(members), ' '.join(marks)))
PY
```

```
closeOutcome               1/4  -NORMAL_EXIT +LIQUIDATION -FORCED_REDUCTION -UNDETERMINED
reconciliationStatus       1/3  -NOT_RUN -MATCHED +MISMATCHED
breakdownIncomplete        1/3  -COMPLETE +INCOMPLETE_BY_WINDOW -NOT_ASSESSED
riskBenchmarkAvailability  1/3  -AVAILABLE -NOT_APPLICABLE +MISSING
```

**Что ось даёт узлу.** Находка B1 предъявила первые два операнда; сюда
попали все четыре, и у каждого отнесено ровно одно значение из трёх-четырёх.
Один и тот же дефект четырежды; правка, сделанная только там, где он
предъявлен, оставила бы три (конвенция 4 узла 1).

### Ось B — денежная сумма и её область на строке нерезолвленной валюты

```bash
python - <<'PY'
import json, io, re
d = json.load(io.open('docs/spec/statistics-aggregates.json', encoding='utf-8'))
for v in d['values']:
    if v.get('op') != 'sum':
        continue
    w = v.get('where', '')
    neg = re.search(r'!\s*(entersRowSums|currencyResolved)', w)
    pos = re.search(r'(?<![!\w])(entersRowSums|currencyResolved)', w)
    print('%-26s %-50s %s' % (v['name'], w,
          'СЧИТАЕТСЯ (область — дополнение)' if neg else ('нулевая' if pos else 'НЕ ОХРАНЕНА')))
PY
```

```
resultBeforeFundingSum     entersRowSums                                      нулевая
netResultSum               entersRowSums                                      нулевая
feeSum                     entersRowSums                                      нулевая
fundingSum                 entersRowSums                                      нулевая
liquidationPenaltySum      entersRowSums                                      нулевая
winResultSum               countsAsWinning && currencyResolved                нулевая
lossResultSum              countsAsLosing && currencyResolved                 нулевая
plannedRiskSum             entersRowSums && plannedRisk > 0                   нулевая
plannedRiskExcludedSum     countsInShares && plannedRisk > 0 && !entersRowSums СЧИТАЕТСЯ (область — дополнение)
rSum                       entersRowSums && plannedRisk > 0                   нулевая
```

**Что ось даёт узлу.** Одна сумма из десяти считается ровно там, где
остальные девять нулевые, — то есть клейм §«Зерно строки» («денежные суммы
нулевые по построению») ложен ровно на ней. Мера двухполюсная намеренно:
поиск подстроки `entersRowSums` объявил бы охранённой и её, потому что
отрицание меняет смысл на противоположный.

### Ось C — имя операнда против имени его дома и против величины, которую он питает

```bash
python - <<'PY'
import json, io, re
d = json.load(io.open('docs/spec/statistics-aggregates.json', encoding='utf-8'))
HOME = [r'величин[аы]\s+([A-Za-z][A-Za-z0-9]*)\s+дома', r'\.json,\s+величина\s+([A-Za-z][A-Za-z0-9]*)',
        r'\b[A-Z][A-Za-z]+\.([a-z][A-Za-z0-9]*)']
q = lambda n: {x for x in ('gross', 'net', 'price', 'before') if x in n.lower()}
for v in d['values']:
    of = v.get('of')
    if v.get('op') != 'sum' or of not in d['operands']:
        continue
    home = next((m.group(1) for m in (re.search(r, d['operands'][of]) for r in HOME) if m), None)
    qo, qv = q(of), q(v['name'])
    clash = qo and qv and not (qo & qv)
    print('%-26s %-14s %-24s %-9s %s' % (v['name'], of, home or '—', 'да' if home == of else 'НЕТ',
          ('РАСХОДЯТСЯ %s / %s' % (sorted(qo), sorted(qv))) if clash else 'сходятся'))
PY
```

```
resultBeforeFundingSum     priceResult    priceResult              да        РАСХОДЯТСЯ ['price'] / ['before']
netResultSum               grossResult    resultProfit             НЕТ       РАСХОДЯТСЯ ['gross'] / ['net']
feeSum                     fee            rightTradeFee            НЕТ       сходятся
fundingSum                 funding        fundingCost              НЕТ       сходятся
liquidationPenaltySum      liqPenalty     rightLiquidationPenalty  НЕТ       сходятся
winResultSum               priceResult    priceResult              да        сходятся
lossResultSum              priceResult    priceResult              да        сходятся
plannedRiskSum             plannedRisk    dealPlannedRisk          НЕТ       сходятся
plannedRiskExcludedSum     plannedRisk    dealPlannedRisk          НЕТ       сходятся
```

**Что ось даёт узлу.** Строк «РАСХОДЯТСЯ» две, а дефект из них один, и
разводит их колонка «имя=дом»: `priceResult` носит имя **своей
величины-дома** (`docs/spec/loss-streak-halt.json`), переписывать его нельзя,
и расхождение квалификатора там — следствие домового имени, а не местного
выбора. `grossResult` домового имени не носит (дом зовёт это число
`Deal.resultProfit`) **и** расходится с величиной, которую питает.
Одноколоночная мера дала бы ложное срабатывание на первой строке.

### Ось D — счётчики зерна происшествий против полей содержимого их классов

```bash
python - <<'PY'
import json, io, re, os
d = json.load(io.open('docs/spec/statistics-aggregates.json', encoding='utf-8'))
CONTENT = {'DEAL_OPENED': 'DealOpenedContent', 'ORDER_DECIDED': 'OrderDecidedContent',
           'HOLD_RAISED': 'HoldRaisedContent', 'ANOMALY_REPORTED': 'AnomalyReportedContent'}
root = 'libs/domain-model/src/main/java/com/example/tradingbot/domain/event'
cuts = {}
for v in d['values']:
    if v.get('over') != 'records':
        continue
    w = v.get('where', '')
    cls = re.search(r"eventType == '([A-Z_]+)'", w).group(1)
    cuts.setdefault(cls, []).append((v['name'], w.split('&&', 1)[1].strip() if '&&' in w else '-'))
for cls, items in sorted(cuts.items()):
    body = io.open(os.path.join(root, CONTENT[cls] + '.java'), encoding='utf-8').read()
    fields = [p.split()[-1] for p in re.search(r'record\s+\w+\(([^)]*)\)', body, re.S).group(1).split(',')]
    print('%-18s %s' % (cls, '; '.join('%s [%s]' % i for i in items)))
    print('%-18s поля содержимого: %s' % ('', ', '.join(fields)))
PY
```

```
ANOMALY_REPORTED   anomalyReports [-]
                   поля содержимого: anomalyReportInternalId, exchangeAccountInternalId, instrumentInternalId, scope, severity, code
DEAL_OPENED        openedDeals [-]
                   поля содержимого: dealInternalId, exchangeAccountInternalId, instrumentInternalId, entryReason, direction
HOLD_RAISED        raisedHolds [-]; hardRaisedHolds [holdRung == 'HARD']; manuallyRaisedHolds [actorIsPrincipal]
                   поля содержимого: exchangeAccountInternalId, instrumentInternalId, scope, rung, code
ORDER_DECIDED      orderDecisions [-]
                   поля содержимого: orderInternalId, dealInternalId, exchangeAccountInternalId, instrumentInternalId, orderType, direction, plannedSizeContracts, plannedEntryPrice
```

**Что ось даёт узлу.** У подъёма ступени разрезов два, и оба объявлены
обязательными абзацем правила; у отчёта о происшествии в содержимом лежат
`severity` и `code`, а разрезов ноль. У решения о заявке разрезов тоже ноль,
но дефицит там другой природы — см. диспозицию B6.

## Достижимость непокрытых значений — проверена, а не предположена

Счётчик получает всякое **нездоровое** значение (критерий — дом правила);
достижимость же нужна отдельно: она отвечает, в каком состоянии значение
попадёт читателю и совместимо ли оно с принятием риска вовсе. Проверено по
домам:

| Значение | Достижимо у сделки в денежных суммах | Чем предъявлено |
|---|---|---|
| `FORCED_REDUCTION` | да | значение требует **добытой** записи закрытия эпизода (`docs/spec/position-close-outcome.json`), и на одноэпизодной сделке этого довольно: свёртка «записи добыты у всех эпизодов» истинна, доступность итога не нарушена. На многоэпизодной сделке исход не следует из значения — там своя ветвь старшинства |
| `UNDETERMINED` | да | ветвь `anyUndetermined` либо `episodeCount == 0` того же дома; на второй тропе свёртка `allCloseRecordsFetched` по пустой коллекции истинна, и результат доступен |
| `NOT_RUN` | да | `notRun` есть отрицание обязанности, а обязанность требует `coverageProven` (`docs/spec/pnl-reconciliation.json`); на той же тропе «эпизодов нет» он ложен при доступном результате |
| `NOT_ASSESSED` | **нет** | единственный триггер — «добыча движений не выполнялась» (`docs/models/domain/aggregate/Deal.md`); тогда ложен `flowsFetched`, с ним `flowsComplete` (`docs/spec/deal-context-load.json`) и доступность итога (`docs/spec/deal-result.json`). **Счётчик всё равно свой** — критерий «достижимо в суммах» отвергнут верификацией захода 5 (см. заход 5 ниже) |
| `NOT_APPLICABLE` | **нет** | значение означает «входа не было», то есть `tookRisk` ложен, а вся популяция счёта — `countsInShares` |

Отсюда исходы: счётчик у каждого нездорового значения (четыре новых);
связь `NOT_ASSESSED` с недоступностью итога переводится из прозы в **охранный
инвариант**; `NOT_APPLICABLE` — объявленная недостижимость с контрпримером.
Здоровые значения (`NORMAL_EXIT`, `MATCHED`, `COMPLETE`, `AVAILABLE`)
счётчика не получают: их число есть остаток, а выводимое вторым носителем не
заводится (`docs/concept.md` П4).



## Проект правок

Правки публикуются **исполнимым текстом**, а не пересказом: ниже три скрипта
приземления, и они же есть пара «Было / Стало». Скрипт прозы держит блоки
«Было» дословно и отказывает, если блок находится в цели не ровно один раз;
скрипт спеки правит структуру и выводит её тем же
`json.dumps(indent=2, ensure_ascii=False)`, каким файл записан сегодня
(байт-в-байт, проверено round-trip'ом), поэтому нетронутые места не
переформатируются. Все три файла временные и удаляются до `git add`
(ловушка 3 §Среда снапшота v155).

Сводка правок:

| Носитель | Что |
|---|---|
| `docs/spec/statistics-aggregates.json` | операнд итога переименован в домовое имя; операнд актора снят, заведены операнды критичности и кода ручной тропы; заведены предикаты и счётчики нездоровых значений четырёх признаков отбора, охранный инвариант и два разреза отчёта о происшествии; заведены четыре популяции с выводом перечня; примеры дополнены и заведены под каждый новый член |
| `docs/rules/statistics-aggregates.md` | зерно строки, обе таблицы величин, диспозиция значений признака отбора, разрезы ручной тропы, популяция счётчиков, обе таблицы колонок |
| `docs/spec/event-actor-presence.json` | 3: у отчёта о происшествии ручная тропа есть; поверхность снятия ступени построена (2 места) |
| `docs/models/domain/other/Auditable.md` | 2: перечень ручных троп снят в пользу прогона; следствие непостроенного класса переписано |
| `docs/models/domain/other/AuditRecord.md` | 2: причина и условие возврата у `HoldReleased` |
| `docs/architecture/contracts.md` | 1: перечень ручных троп снят в пользу прогона |
| `docs/models/domain/aggregate/Deal.md` | 1: у объявленной корзины появился носитель |
| `tools/derive/deal-selection-feature-values.sh` | заведён: происхождение перечней четырёх популяций |
| `tools/retired-check.py` | 4 записи реестра снятых редакций |
| `.claude/work/progress/phase-2-step-10-chronicle.md` | 4 позиции дельты `CODE` (7а, 7б, 16, 19) |
| `.claude/work/backlog.md` | 2: парковка обеих cross-cutting половин B6; правка §«Класс события `HoldReleased`…» |
| `.claude/work/decision-digest.md` | Д1053-Д1060 |

### Скрипт спеки

```python
# -*- coding: utf-8 -*-
"""Приземление узла 3 GAPS_CLOSE_3 шага 10 фазы 2 в docs/spec/statistics-aggregates.json.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
Правки структурные, вывод — json.dumps(indent=2, ensure_ascii=False) + \n,
что байт-в-байт совпадает с форматом файла (проверено round-trip'ом).
"""
import io
import json
import sys

PATH = sys.argv[1] if len(sys.argv) > 1 else 'docs/spec/statistics-aggregates.json'

with io.open(PATH, encoding='utf-8') as handle:
    spec = json.load(handle)

ops = spec['operands']
vals = spec['values']
examples = spec['examples']


def index_of(name):
    for position, value in enumerate(vals):
        if value['name'] == name:
            return position
    raise SystemExit('НЕТ ВЕЛИЧИНЫ: ' + name)


def value_named(name):
    return vals[index_of(name)]


def insert_after(name, blocks):
    position = index_of(name) + 1
    vals[position:position] = blocks


# ---------------------------------------------------------------- C5
# Операнд, питающий netResultSum, носит имя, которое нота этой величины
# запрещает; домового имени он при этом не носит (дом зовёт число
# Deal.resultProfit). Переименование — в домовое имя, как у priceResult.
if 'grossResult' not in ops:
    raise SystemExit('ПРАВКА C5 УЖЕ ПРИМЕНЕНА ЛИБО ОПЕРАНД ПЕРЕИМЕНОВАН')
renamed = {}
for key, text in ops.items():
    renamed['resultProfit' if key == 'grossResult' else key] = text
spec['operands'] = ops = renamed
value_named('netResultSum')['of'] = 'resultProfit'
for example in examples:
    state = example.get('state', {})
    for row in state.get('deals', []):
        if 'grossResult' in row:
            row['resultProfit'] = row.pop('grossResult')
    if 'grossResult' in state:
        state['resultProfit'] = state.pop('grossResult')

# ---------------------------------------------------------------- B1
# Проза ликвидации была шире её предиката: «закрыла биржа принудительно»
# накрывает и принудительное сокращение, у которого теперь свой счётчик.
liquidated = value_named('countsAsLiquidated')
if 'ПО МАРЖЕ' in liquidated['note']:
    raise SystemExit('ПРАВКА B1 УЖЕ ПРИМЕНЕНА')
liquidated['note'] = liquidated['note'].replace(
    'Сделку закрыла биржа принудительно.',
    'Сделку закрыла биржа принудительно ПО МАРЖЕ — принудительное сокращение это значение не '
    'накрывает, у него свой счётчик.')
value_named('liquidatedDeals')['note'] = 'Сколько сделок суток закрыла биржа по марже.'
# Штраф у принудительного сокращения не исключён, а наблюдения нет ни у одной
# из двух развязок — обе держат фикстуры дома, и здесь ни одна не предпочтена.
ops['liqPenalty'] = ops['liqPenalty'].replace(
    'Ноль у сделки, закрытой не ликвидацией',
    'Ноль у сделки, которую биржа не закрывала принудительно. У принудительного сокращения '
    'штраф НЕ ИСКЛЮЧЁН: наблюдения нет, и обе развязки держат фикстуры дома '
    '(docs/spec/pnl-reconciliation.json)')
# Популяция четырёх счётчиков отбора — ПРИНЯВШИЕ РИСК, а не вошедшие в суммы:
# каждый признак достижим и у сделки с недоступным результатом, а такая в
# денежные суммы не входит.
POPULATION_CLAUSE = (' Популяция — принявшие риск, а не вошедшие в денежные суммы: признак '
                     'достижим и у сделки с недоступным результатом, а такая в суммы не входит.')
value_named('reconciliationMismatchedDeals')['note'] = (
    'Сколько принявших риск сделок суток закрылось с несошедшейся сверкой.' + POPULATION_CLAUSE)
value_named('breakdownIncompleteDeals')['note'] = (
    'Сколько принявших риск сделок суток закрылось с неполной разбивкой движений.'
    + POPULATION_CLAUSE)
insert_after('countsAsLiquidated', [
    {
        'name': 'countsAsForcedReduction',
        'note': 'Позицию принудительно сократила биржа — делеверидж, а не наш выход. '
                'Признак едет тем же признаком ОТБОРА, что и ликвидация, и отбирать по нему '
                'обязательно по тому же доводу: и то и другое говорит о марже и меняет '
                'риск-аппетит. Счётчик свой, а не общий с ликвидацией: маржинальный вынос и '
                'делеверидж соседа — разные поводы, и одно число на оба давало бы одинаковый '
                'ответ на разных состояниях.',
        'expr': "countsInShares && closeOutcome == 'FORCED_REDUCTION'",
    },
    {
        'name': 'countsAsOutcomeUndetermined',
        'note': 'Кто закрыл позицию, не установлено — значение, а не пустота '
                '(docs/models/domain/aggregate/Deal.md). Состояние достижимо у сделки с '
                'ДОСТУПНЫМ результатом: вошедшая сделка без единого эпизода даёт его, а свёртка '
                '«записи закрытия добыты у всех эпизодов» на пустой коллекции истинна '
                '(docs/spec/position-close-outcome.json, docs/spec/deal-result.json). Без '
                'счётчика такая сделка входит в суммы и читается как штатный выход — '
                'благоприятное умолчание, запрещённое docs/concept.md, П1.',
        'expr': "countsInShares && closeOutcome == 'UNDETERMINED'",
    },
])
insert_after('countsAsMismatched', [
    {
        'name': 'countsAsReconciliationNotRun',
        'note': 'Сверка не была обязана: хотя бы один конъюнкт обязанности не выполнен '
                '(docs/spec/pnl-reconciliation.json). Счётчик доводит до конца тот же довод, '
                'каким обоснован countsAsMismatched: без него «не проверяли» складывается с '
                '«проверили, всё в порядке» — ровно то различение, которого требует '
                'docs/concept.md, П1. Состояние достижимо у сделки с доступным результатом: '
                'порог доказанного покрытия пуст, когда эпизода не было ни одного.',
        'expr': "countsInShares && reconciliationStatus == 'NOT_RUN'",
    },
])
insert_after('liquidatedDeals', [
    {
        'name': 'forcedReductionDeals',
        'over': 'deals',
        'op': 'count',
        'where': 'countsAsForcedReduction',
        'note': 'Сколько принявших риск сделок суток биржа сократила принудительно.'
                + POPULATION_CLAUSE,
    },
    {
        'name': 'outcomeUndeterminedDeals',
        'over': 'deals',
        'op': 'count',
        'where': 'countsAsOutcomeUndetermined',
        'note': 'Сколько принявших риск сделок суток закрылось с неустановленным торговым '
                'исходом — та самая отдельная корзина, которую объявляет владелец перечня.'
                + POPULATION_CLAUSE,
    },
])
insert_after('reconciliationMismatchedDeals', [
    {
        'name': 'reconciliationNotRunDeals',
        'over': 'deals',
        'op': 'count',
        'where': 'countsAsReconciliationNotRun',
        'note': 'Сколько принявших риск сделок суток закрылось непроверенными.'
                + POPULATION_CLAUSE,
    },
])
insert_after('rowCurrencyUniform', [
    {
        'name': 'unassessedBreakdownHasNoResult',
        'note': 'ОХРАННЫЙ ИНВАРИАНТ признака полноты разбивки: у сделки, чья полнота не '
                'оценивалась, итога нет. Единственный триггер значения NOT_ASSESSED — добыча '
                'движений не выполнялась (docs/models/domain/aggregate/Deal.md); тогда ложен '
                'первый конъюнкт полноты разбивки (docs/spec/deal-context-load.json), а с ним '
                'и доступность итога (docs/spec/deal-result.json). Инвариант и есть исход '
                'этого значения: своего счётчика оно не получает, потому что его популяция уже '
                'названа resultUnavailableDeals, — а держится это на импликации, и импликация '
                'обязана быть проверяемой, а не прочитанной.',
        'expr': "!(countsInShares && breakdownIncomplete == 'NOT_ASSESSED') || countsAsUnavailable",
    },
])

# ---------------------------------------------------------------- B2
excluded = value_named('plannedRiskExcludedSum')
excluded['where'] = 'countsInShares && plannedRisk > 0 && !entersRowSums && currencyResolved'
excluded['note'] = (
    'Плановый риск, выведенный из знаменателя. Счётчика мало: читателю нужна ВЕЛИЧИНА '
    'непокрытого риска, чтобы судить, насколько отношение опирается на полную картину. '
    'Конъюнкт currencyResolved несущий, и довод его — не тождество двух валют, а ключ зерна: '
    'единицу строке даёт он, несёт он валюту РЕЗУЛЬТАТА, и второй валютной колонки у строки '
    'нет. На строке с пустым ключом сумма планового риска единицы не имеет ни при каком '
    'исходе резолва валюты риска — и складывала бы величины разных инструментов, то есть '
    'ровно ту операцию, ради запрета которой валюта вынесена в ключ. Мощность той популяции '
    'показывает currencyUnresolvedDeals, а величины у неё нет по той же причине, по которой '
    'её нет у результата.')

# ---------------------------------------------------------------- B5
# Различитель природы — КОД тропы, а не актор строки. Актор отвечает на
# вопрос «кем порождён ход», и у джобы, запущенной ручным триггером, он равен
# принципалу (docs/models/domain/other/Auditable.md, о переносе контекста в
# порождённый тред): автоматическая остановка, найденная в ручном прогоне
# детекции, считалась бы остановкой держателя. Операнд заменяется у ОБОИХ
# счётчиков разреза по природе — предъявленного и соседнего.
if 'actorIsPrincipal' not in ops:
    raise SystemExit('ПРАВКА B5 УЖЕ ПРИМЕНЕНА')
del ops['actorIsPrincipal']
ops['severity'] = ('критичность отчёта о происшествии — поле содержимого AnomalyReported; '
                   'область значений домовая (docs/models/domain/other/AnomalyReport.md), и '
                   'здесь она не переписывается. Пусто у строк прочих классов')
ops['codeIsManualOperation'] = (
    'код строки журнала принадлежит множеству кодов, которыми ручная поверхность метит СВОИ '
    'операции. Область значений домовая (docs/rules/manual-halt.md), и здесь она не '
    'переписывается: операнд несёт различение двух классов кода, а не литерал. Пусто у классов, '
    'чьё содержимое кода не несёт')
ops['records'] = ('строки журнала суток одной строки агрегата второго зерна; элемент несёт '
                  'eventType и, у подъёма ступени и у отчёта о происшествии, операнды их '
                  'разрезов')
manual_holds = value_named('manuallyRaisedHolds')
manual_holds['where'] = "eventType == 'HOLD_RAISED' && codeIsManualOperation"
manual_holds['note'] = (
    'Разрез по природе. «Система остановила себя» — сигнал, что гипотеза не работает; «я её '
    'остановил» — собственное действие держателя. Одно число на оба состояния есть ложный '
    'операнд по определению: собственные остановки держателя считались бы признаком отказа '
    'гипотезы. Различитель — КОД тропы, а не актор строки: актор отвечает на вопрос «кем '
    'порождён ход», и у джобы, запущенной ручным триггером, он равен принципалу '
    '(docs/models/domain/other/Auditable.md), то есть автоматическая остановка, найденная в '
    'ручном прогоне детекции, попала бы в это число.')
value_named('anomalyReports')['note'] = (
    'Счётчик происшествий суток: отчётов о происшествиях, обе природы вместе. Само по себе это '
    'число ни на какой вопрос не отвечает и потому идёт в паре с разрезами ниже — как и '
    'raisedHolds.')
insert_after('anomalyReports', [
    {
        'name': 'criticalAnomalyReports',
        'note': 'Разрез по критичности. «Kill-switch гонялся» и «не гонялся» — разные сутки, а '
                'без разреза они дают одно число; довод тот же, каким обоснован разрез по '
                'ступени у подъёма холда.',
        'over': 'records',
        'op': 'count',
        'where': "eventType == 'ANOMALY_REPORTED' && severity == 'CRITICAL'",
    },
    {
        'name': 'manualOperationReports',
        'note': 'Разрез по природе. Отчёт заводит не только детекция: ручная поверхность '
                'заводит его и на постановке ступени, и на СНЯТИИ (docs/rules/manual-halt.md). '
                'Снятие возвращает контур в работу — состояние, происшествию противоположное, '
                '— и сложенное с происшествиями автоматики оно выдаёт вмешательство держателя '
                'за отказ гипотезы. Довод и операнд те же, что у manuallyRaisedHolds, включая '
                'выбор кода вместо актора: ручной прогон джобы детекции даёт актором '
                'принципала, и по актору находки автоматики считались бы операциями держателя.',
        'over': 'records',
        'op': 'count',
        'where': "eventType == 'ANOMALY_REPORTED' && codeIsManualOperation",
    },
])

# ---------------------------------------------------------------- B6
value_named('orderDecisions')['note'] = (
    'Счётчик происшествий суток: решений о создании ОБЫЧНОЙ заявки. Популяция названа, потому '
    'что она у́же имени: ремодел выражается новой сущностью плюс отменой старой '
    '(docs/rules/replace-not-amend.md) и даёт своё решение, а решение об отдельной условной '
    'заявке не выражено классом события вовсе — производителя у него нет, и в разность '
    'объявленных и построенных классов оно не попадает '
    '(docs/models/domain/other/AuditRecord.md).')

# ---------------------------------------------------------------- популяции
DERIVE_SRC = 'libs/domain-model/src/main/java/com/example/tradingbot/domain/model/aggregate/deal/Deal.java'
COMMON_NOTE = (
    'Перечень выводится из объявления перечня общей доменной модели, а не из значений, '
    'встреченных в примерах: множество встреченного молчит ровно о том значении, которого в '
    'нём нет. Здоровое значение своего счётчика не получает — его число есть остаток популяции '
    'счёта за вычетом прочих, а выводимое вторым носителем не заводится (docs/concept.md, П4).')
EXCLUDES = ('сделка, закрытая без входа: признак у неё пуст и неприменим, а вся популяция '
            'счётчиков отбора — принявшие риск')


def population(axis, enum, keys, rule, members):
    return {
        'axis': axis,
        'note': COMMON_NOTE,
        'rule': rule,
        'derive': {
            'command': 'bash tools/derive/deal-selection-feature-values.sh ' + enum,
            'from': [DERIVE_SRC],
        },
        'where': 'tookRisk',
        'excludes': EXCLUDES,
        'keys': [keys],
        'members': members,
    }


spec['populations'].extend([
    population(
        'исход закрытия торговавшей сделки — у каждого значения назван исход отнесения',
        'CloseOutcome', 'closeOutcome',
        ['countsAsLiquidated', 'countsAsForcedReduction', 'countsAsOutcomeUndetermined'],
        [{'member': ['NORMAL_EXIT']}, {'member': ['LIQUIDATION']},
         {'member': ['FORCED_REDUCTION']}, {'member': ['UNDETERMINED']}]),
    population(
        'исход сверки у торговавшей сделки — у каждого значения назван исход отнесения',
        'ReconciliationStatus', 'reconciliationStatus',
        ['countsAsMismatched', 'countsAsReconciliationNotRun'],
        [{'member': ['NOT_RUN']}, {'member': ['MATCHED']}, {'member': ['MISMATCHED']}]),
    population(
        'полнота разбивки у торговавшей сделки — у каждого значения назван исход отнесения',
        'BreakdownCompleteness', 'breakdownIncomplete',
        ['countsAsBreakdownIncomplete', 'unassessedBreakdownHasNoResult'],
        [{'member': ['COMPLETE']}, {'member': ['INCOMPLETE_BY_WINDOW']},
         {'member': ['NOT_ASSESSED']}]),
    population(
        'доступность базы риска у торговавшей сделки — у каждого значения назван исход отнесения',
        'RiskBenchmarkAvailability', 'riskBenchmarkAvailability',
        ['countsAsRiskBenchmarkMissing'],
        [{'member': ['AVAILABLE']},
         {'member': ['NOT_APPLICABLE'],
          'unreachable': 'значение означает «входа не было, знаменателя нет по построению» '
                         '(docs/models/domain/aggregate/Deal.md); у сделки, принявшей риск, '
                         'вход состоялся, а вся популяция счётчиков отбора — принявшие риск'},
         {'member': ['MISSING']}]),
])


# ---------------------------------------------------------------- примеры
def example_named(fragment):
    for example in examples:
        if fragment in example['case']:
            return example
    raise SystemExit('НЕТ ПРИМЕРА: ' + fragment)


# Пример недоступного результата уже несёт NOT_RUN — правило на нём проверяется.
example_named('торговавшая сделка с недоступным результатом')['expect'].update({
    'countsAsReconciliationNotRun': True,
    'unassessedBreakdownHasNoResult': True,
})

# Строка нерезолвленной валюты: денежных сумм в ней нет НИ ОДНОЙ.
example_named('строка нерезолвленной валюты')['expect']['plannedRiskExcludedSum'] = 0

# Сутки с ликвидацией: новые счётчики отбора нулевые — вторая точка мутации.
example_named('сутки с ликвидацией и несошедшейся сверкой')['expect'].update({
    'forcedReductionDeals': 0,
    'outcomeUndeterminedDeals': 0,
    'reconciliationNotRunDeals': 0,
})

# Сутки происшествий: разрезы отчёта о происшествии.
incidents = example_named('обе природы подъёма ступени и обе ступени')
incidents['case'] = ('счётчики происшествий суток: обе природы подъёма ступени, обе ступени и '
                     'обе природы отчёта о происшествии')
records = incidents['state']['records']
for row in records:
    if 'actorIsPrincipal' in row:
        row['codeIsManualOperation'] = row.pop('actorIsPrincipal')
    if row['eventType'] == 'ANOMALY_REPORTED':
        row['severity'] = 'CRITICAL'
        row['codeIsManualOperation'] = False
records.append({'eventType': 'ANOMALY_REPORTED', 'severity': 'NON_CRITICAL',
                'codeIsManualOperation': False})
records.append({'eventType': 'ANOMALY_REPORTED', 'severity': 'NON_CRITICAL',
                'codeIsManualOperation': True})
incidents['expect'].update({'anomalyReports': 3, 'criticalAnomalyReports': 1,
                            'manualOperationReports': 1})
example_named('происшествий не было')['expect'].update({'criticalAnomalyReports': 0,
                                                        'manualOperationReports': 0})

BASE = {'tookRisk': True, 'plannedRisk': 20, 'currency': 'USDT'}
new_examples = [
    {
        'case': 'принудительное сокращение биржей: сделка в суммах, счётчик свой',
        'state': dict(BASE, priceResult=-30, closeOutcome='FORCED_REDUCTION',
                      reconciliationStatus='MATCHED', breakdownIncomplete='COMPLETE',
                      riskBenchmarkAvailability='AVAILABLE'),
        'expect': {
            'countsInShares': True,
            'entersRowSums': True,
            'countsAsLiquidated': False,
            'countsAsForcedReduction': True,
            'countsAsOutcomeUndetermined': False,
            'countsAsReconciliationNotRun': False,
            'countsAsBreakdownIncomplete': False,
            'countsAsRiskBenchmarkMissing': False,
            'unassessedBreakdownHasNoResult': True,
        },
    },
    {
        'case': 'вошедшая сделка без единого эпизода: исход не установлен, сверка не была '
                'обязана, а результат доступен',
        'state': dict(BASE, priceResult=0, closeOutcome='UNDETERMINED',
                      reconciliationStatus='NOT_RUN', breakdownIncomplete='COMPLETE',
                      riskBenchmarkAvailability='AVAILABLE'),
        'expect': {
            'countsInShares': True,
            'resultClass': 'ZERO',
            'entersRowSums': True,
            'countsAsLiquidated': False,
            'countsAsForcedReduction': False,
            'countsAsOutcomeUndetermined': True,
            'countsAsReconciliationNotRun': True,
            'unassessedBreakdownHasNoResult': True,
        },
    },
    {
        'case': 'добыча движений не выполнялась: полнота не оценивалась, и результата нет',
        'state': dict(BASE, priceResult=None, breakdownIncomplete='NOT_ASSESSED'),
        'expect': {
            'countsInShares': True,
            'countsAsUnavailable': True,
            'countsAsBreakdownIncomplete': False,
            'unassessedBreakdownHasNoResult': True,
        },
    },
    {
        'case': 'контрпример: полнота разбивки не оценивалась, а результат доступен',
        'unreachable': 'единственный триггер значения NOT_ASSESSED — добыча движений не '
                       'выполнялась (docs/models/domain/aggregate/Deal.md); тогда ложен первый '
                       'конъюнкт полноты разбивки (docs/spec/deal-context-load.json), а с ним '
                       'и доступность итога (docs/spec/deal-result.json)',
        'state': dict(BASE, priceResult=4, breakdownIncomplete='NOT_ASSESSED'),
        'expect': {
            'countsInShares': True,
            'countsAsUnavailable': False,
            'countsAsBreakdownIncomplete': False,
            'unassessedBreakdownHasNoResult': False,
        },
    },
    {
        'case': 'контрпример: сделка приняла риск, а база риска неприменима',
        'unreachable': 'значение NOT_APPLICABLE означает «входа не было, знаменателя нет по '
                       'построению» (docs/models/domain/aggregate/Deal.md); у сделки, принявшей '
                       'риск, вход состоялся',
        'state': dict(BASE, priceResult=2, riskBenchmarkAvailability='NOT_APPLICABLE'),
        'expect': {
            'countsInShares': True,
            'countsAsRiskBenchmarkMissing': False,
        },
    },
    {
        'case': 'сутки: принудительное сокращение, неустановленный исход и непроверенная '
                'сверка — каждое своим счётчиком',
        'state': {
            'rowCurrency': 'USDT',
            'deals': [
                {'tookRisk': True, 'priceResult': -30, 'resultProfit': -31, 'fee': 1,
                 'funding': 1, 'liqPenalty': 0, 'plannedRisk': 20, 'currency': 'USDT',
                 'closeOutcome': 'FORCED_REDUCTION', 'reconciliationStatus': 'MATCHED',
                 'breakdownIncomplete': 'COMPLETE', 'riskBenchmarkAvailability': 'AVAILABLE'},
                {'tookRisk': True, 'priceResult': 0, 'resultProfit': 0, 'fee': 0,
                 'funding': 0, 'liqPenalty': 0, 'plannedRisk': 10, 'currency': 'USDT',
                 'closeOutcome': 'UNDETERMINED', 'reconciliationStatus': 'NOT_RUN',
                 'breakdownIncomplete': 'COMPLETE', 'riskBenchmarkAvailability': 'AVAILABLE'},
                {'tookRisk': True, 'priceResult': 6, 'resultProfit': 6, 'fee': 0.5,
                 'funding': 0, 'liqPenalty': 0, 'plannedRisk': 10, 'currency': 'USDT',
                 'closeOutcome': 'NORMAL_EXIT', 'reconciliationStatus': 'MATCHED',
                 'breakdownIncomplete': 'COMPLETE', 'riskBenchmarkAvailability': 'AVAILABLE'},
            ],
        },
        'expect': {
            'closedDeals': 3,
            'riskBearingDeals': 3,
            'liquidatedDeals': 0,
            'forcedReductionDeals': 1,
            'outcomeUndeterminedDeals': 1,
            'reconciliationMismatchedDeals': 0,
            'reconciliationNotRunDeals': 1,
            'resultBeforeFundingSum': -24,
            'netResultSum': -25,
            'fundingSum': 1,
            'plannedRiskExcludedSum': 0,
            'fundingDecompositionHolds': True,
            'rowCurrencyUniform': True,
        },
    },
]
examples.extend(new_examples)

with io.open(PATH, 'w', encoding='utf-8', newline='\n') as handle:
    handle.write(json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
print('ПРИМЕНЕНО:', PATH)
print('величин:', len(vals), 'популяций:', len(spec['populations']), 'примеров:', len(examples))
```

### Скрипт прозы

```python
# -*- coding: utf-8 -*-
"""Приземление узла 3 GAPS_CLOSE_3 шага 10 фазы 2 в прозу корпуса.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
Каждый блок «Было» обязан находиться в цели РОВНО ОДИН раз; иначе замена не
делается вовсе и скрипт отказывает (конвенция 1 узла 1: пара предъявляет
границу правки, а не описывает её).
"""
import io
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC_ACTOR = 'docs/spec/event-actor-presence.json'
CONTRACTS = 'docs/architecture/contracts.md'
AUDITABLE = 'docs/models/domain/other/Auditable.md'
DEAL = 'docs/models/domain/aggregate/Deal.md'
AUDIT_RECORD = 'docs/models/domain/other/AuditRecord.md'
BACKLOG = '.claude/work/backlog.md'
CHRONICLE = '.claude/work/progress/phase-2-step-10-chronicle.md'

# ---- B2: клейм «денежные суммы нулевые по построению» становится верным
edit(RULE, u"""**Строка нерезолвленной валюты денежных сумм не несёт.** Сделки с пустой
валютой собираются в собственную строку зерна, и в ней денежные суммы
**нулевые по построению**: складывать числа без единицы измерения нельзя,
а группировка их вместе единицу не создаёт. Видны они счётчиком
`currencyUnresolvedDeals` и суммой выведенного риска
`plannedRiskExcludedSum` — то есть не выпадают молча.""",
     u"""**Строка нерезолвленной валюты денежных сумм не несёт.** Сделки с пустой
валютой собираются в собственную строку зерна, и в ней денежные суммы
**нулевые по построению** — все до одной, включая выведенный из знаменателя
плановый риск: складывать числа без единицы измерения нельзя, а группировка
их вместе единицу не создаёт. Исключения плановому риску нет, и довод его —
не тождество двух валют, а **ключ зерна**: единицу строке даёт он, несёт он
валюту результата, второй валютной колонки у строки нет, а сделки в такую
строку попадают разных инструментов. Видна эта популяция счётчиком
`currencyUnresolvedDeals` — то есть не выпадает молча; величины у неё нет по
той же причине, по которой её нет у результата.""")

# ---- B1: строки таблицы хранимого
edit(RULE, u"""| `liquidatedDeals` | из принявших риск — закрытые биржей принудительно |
| `reconciliationMismatchedDeals` | из принявших риск — с несошедшейся сверкой |""",
     u"""| `liquidatedDeals` | из принявших риск — закрытые биржей **по марже** |
| `forcedReductionDeals` | из принявших риск — **принудительно сокращённые** биржей |
| `outcomeUndeterminedDeals` | из принявших риск — с **неустановленным** торговым исходом |
| `reconciliationMismatchedDeals` | из принявших риск — с несошедшейся сверкой |
| `reconciliationNotRunDeals` | из принявших риск — с **непроверенной** сверкой |""")

# ---- B2: строка таблицы, называющая область выведенного риска
edit(RULE, u"| `plannedRiskExcludedSum` | плановый риск сделок, **выведенных** из этих сумм |",
     u"| `plannedRiskExcludedSum` | плановый риск сделок, **выведенных** из этих сумм; "
     u"считается на строке известной валюты |")

# ---- B1: диспозиция каждого значения признака отбора
edit(RULE, u"""Четыре признака едут в терминальном событии как признаки **отбора**
(`docs/architecture/contracts.md`), и отчёт, ради которого они туда
положены, обязан по ним отбирать.""",
     u"""Четыре признака едут в терминальном событии как признаки **отбора**
(`docs/architecture/contracts.md`), и отчёт, ради которого они туда
положены, обязан по ним отбирать.

**Каждое значение признака отбора получает исход, и исход проверяется
прогоном.** Счётного клейма о числе признаков и значений здесь нет — оно
старело бы молча; диспозиция у каждого значения ровно одна: **свой
счётчик** — когда значение достижимо у
сделки, вошедшей в денежные суммы, и там неотличимо от штатного;
**выводимый остаток** — у здорового значения, чьё число есть популяция счёта
за вычетом прочих (выводимое вторым носителем не заводится, `docs/concept.md`
П4); **объявленная недостижимость** — когда значение несовместимо с принятием
риска. Прозой диспозиция не держится: перечень значений каждого признака
объявлен популяцией исполнимой формы
(`docs/spec/statistics-aggregates.json`) и **выводится из перечня общей
доменной модели**, а не из значений, встреченных в примерах. До этого
отнесение не мерил никто — и у каждого признака счётчик стоял ровно на одном
его значении, то есть остальные читались как штатный выход.

**Значение, чья популяция уже названа другим счётчиком, своего не получает,
а импликация становится инвариантом.** «Полнота разбивки не оценивалась»
означает, что добыча движений не выполнялась, — а тогда итога у сделки нет
вовсе, и она уже посчитана `resultUnavailableDeals`. Второй счётчик той же
популяции был бы вторым носителем; вместо него в исполнимой форме стои́т
охранный инвариант с контрпримером, потому что импликация, на которой
диспозиция держится, обязана быть проверяемой, а не прочитанной.""")

# ---- B5, B6: таблица счётчиков происшествий
edit(RULE, u"""| `orderDecisions` | решений о заявке |""",
     u"""| `orderDecisions` | решений о создании **обычной** заявки |""")
edit(RULE, u"""| `anomalyReports` | отчётов о происшествиях |""",
     u"""| `anomalyReports` | отчётов о происшествиях — **обе природы вместе** |
| `criticalAnomalyReports` | из них критичных |
| `manualOperationReports` | из них заведённых **операцией держателя**, а не детекцией |""")

# ---- B5, B6: доводы разрезов
edit(RULE, u"""Операнды различения едут в содержимом события (ступень — сегодня, актор —
дельтой `CODE` шага 10 фазы 2, `docs/rules/manual-halt.md`).""",
     u"""Операнды различения едут в содержимом события сегодня — и ступень, и код
тропы. Что из ручной тропы сегодня не исполнено и чем закрывается —
`docs/rules/manual-halt.md`.

**Природу различает код тропы, а не актор строки, и это не вкус.** Актор
отвечает на вопрос «кем порождён ход», и у джобы, запущенной ручным
триггером, он равен принципалу — контекст обязан переживать смену треда
(`docs/models/domain/other/Auditable.md`). По актору автоматическая
остановка, найденная в ручном прогоне детекции, попала бы в число остановок
держателя, то есть разрез давал бы ровно ту ошибку, против которой заведён.
Код же метит **операцию** ручной поверхности, а не происхождение хода, и им
же дом различает ручную остановку от автоматической.

**Те же два разреза обязательны у отчёта о происшествии, и по тем же
доводам.** Отчёт заводит не только детекция: ручная поверхность заводит его
и на постановке ступени, и на **снятии** (`docs/rules/manual-halt.md`).
Снятие возвращает контур в работу — состояние, происшествию
противоположное; сложенное с происшествиями автоматики, оно выдаёт
вмешательство держателя за отказ гипотезы, то есть даёт ровно ту ошибку, от
которой разрез по природе защищает у подъёма ступени. Второй разрез — по
критичности: «kill-switch гонялся» и «не гонялся» в одном числе
неразличимы. Операнды обоих разрезов едут в содержимом события сегодня.

**Счётчик решений о заявке считает у́же своего имени, и это названо.** Класс
события порождает исполнитель создания **обычной** заявки; решение об
отдельной условной заявке классом события не выражено вовсе, а перечень
непокрытого есть разность объявленных и построенных классов
(`docs/models/domain/other/AuditRecord.md`) — класс, который не объявлялся,
в эту разность не попадает и читателю о себе не сообщает ничем. Сверх того
перестановка ноги выражается новой сущностью плюс отменой старой
(`docs/rules/replace-not-amend.md`) и даёт своё решение, поэтому число несёт
и перестановки. Разреза у счётчика нет, и это не пропуск: операнд, который
развёл бы вход и перестановку, в содержимом не едет, а завести его — правка
чужого производителя (дом ожидания — `.claude/work/backlog.md`).""")

# ---- B1, B5: колонки обеих таблиц
edit(RULE, u"| `closed_deals`, `risk_bearing_deals`, `winning_deals`, `losing_deals`, "
           u"`neutral_deals`, `result_unavailable_deals`, `currency_unresolved_deals`, "
           u"`risk_unsized_deals`, `liquidated_deals`, `reconciliation_mismatched_deals`, "
           u"`breakdown_incomplete_deals`, `risk_benchmark_missing_deals`, "
           u"`r_denominator_deals` | `integer` | `not null` |",
     u"| `closed_deals`, `risk_bearing_deals`, `winning_deals`, `losing_deals`, "
     u"`neutral_deals`, `result_unavailable_deals`, `currency_unresolved_deals`, "
     u"`risk_unsized_deals`, `liquidated_deals`, `forced_reduction_deals`, "
     u"`outcome_undetermined_deals`, `reconciliation_mismatched_deals`, "
     u"`reconciliation_not_run_deals`, `breakdown_incomplete_deals`, "
     u"`risk_benchmark_missing_deals`, `r_denominator_deals` | `integer` | `not null` |")
edit(RULE, u"| `opened_deals`, `order_decisions`, `raised_holds`, `hard_raised_holds`, "
           u"`manually_raised_holds`, `anomaly_reports` | `integer` | `not null` |",
     u"| `opened_deals`, `order_decisions`, `raised_holds`, `hard_raised_holds`, "
     u"`manually_raised_holds`, `anomaly_reports`, `critical_anomaly_reports`, "
     u"`manual_operation_reports` | `integer` | `not null` |")

# ---- стык B5: у отчёта о происшествии ручная тропа ЕСТЬ
edit(SPEC_ACTOR, u"""      "case": "AnomalyReported — отчёт заводит проход детекции",
      "state": {
        "eventClass": "AnomalyReported",
        "hasManualPath": false,
        "eventClassBuilt": true
      },
      "expect": {
        "actorTravelsInContent": false
      }""",
     u"""      "case": "AnomalyReported — отчёт заводит не только детекция: ручная остановка счёта заводит его и на постановке ступени, и на снятии, и обе тропы доходят до писателя события",
      "state": {
        "eventClass": "AnomalyReported",
        "hasManualPath": true,
        "eventClassBuilt": true
      },
      "expect": {
        "actorTravelsInContent": true
      }""")

# ---- свип того же стыка: поверхность ручных операций ядра ПОСТРОЕНА
# (SafetyController: POST /halts и POST /halt-clearances -> ManualHaltService),
# и снятие ступени журнал фиксирует строкой отчёта о происшествии с кодом
# ручной тропы. Не построен КЛАСС события, а не поверхность.
edit(SPEC_ACTOR, u'"unreachable": "ручная тропа объявлена, класса в перечне производителя нет: '
                 u'снятие ступени приезжает поверхностью ручных операций ядра, а её не '
                 u'построено — содержимого у класса не существует"',
     u'"unreachable": "ручная тропа объявлена, класса в перечне производителя нет: поверхность '
     u'ручных операций ядра построена, но до писателя этого класса не доходит — содержимого у '
     u'класса не существует"')
edit(SPEC_ACTOR, u'"unreachable": "снятие ступени приезжает поверхностью ручных операций ядра, '
                 u'а её не построено: содержимого у класса не существует, и поле приезжает '
                 u'вместе с классом",',
     u'"unreachable": "снятие ступени идёт построенной поверхностью ручных операций ядра, а до '
     u'писателя этого класса не доходит: содержимого у класса не существует, и поле приезжает '
     u'вместе с классом",')

edit(AUDITABLE, u"""**Где тропа объявлена, но не построена, следствие названо.** У снятия
safety-ступени ручная тропа объявлена, класса события в перечне
производителя нет — и журнал зафиксирует, что торговля счёта остановлена,
и **не зафиксирует**, что она возобновлена. Актор приезжает вместе с
классом, не раньше; перечень непокрытых классов и условия их возврата — у
владельца журнала (`docs/models/domain/other/AuditRecord.md`).""",
     u"""**Где тропа построена, а класс — нет, следствие названо.** У снятия
safety-ступени ручная тропа **построена**, а класса события в перечне
производителя нет: журнал фиксирует возобновление торговли строкой отчёта о
происшествии с кодом ручной тропы (`docs/rules/manual-halt.md`) — и не
фиксирует его **своим классом**, то есть отвечает на «счёт вернули в
работу», но не на «ступень снята». Актор приезжает вместе с классом, не
раньше; перечень непокрытых классов и условия их возврата — у владельца
журнала (`docs/models/domain/other/AuditRecord.md`).""")

edit(AUDIT_RECORD,
     u"`HoldReleased`: писатель объявлен, значения перечня нет — снятие ступени приезжает "
     u"поверхностью ручных операций.",
     u"`HoldReleased`: писатель объявлен, значения перечня нет — снятие ступени идёт "
     u"построенной поверхностью ручных операций, а до писателя класса не доходит.")
edit(AUDIT_RECORD,
     u"`HoldReleased` — ход, заводящий тропу снятия (`.claude/work/backlog.md` "
     u"§«Класс события `HoldReleased` и его ручная тропа»)",
     u"`HoldReleased` — ход, заводящий класс: значение перечня, содержимое и зов писателя с "
     u"ручной тропы (`.claude/work/backlog.md` §«Класс события `HoldReleased` и его ручная "
     u"тропа»)")

edit(BACKLOG, u"""**Что сделать.** Завести поверхность ручных операций ядра, снимающую
safety-ступень, и вместе с ней — класс события `HoldReleased`, его
содержимое и поле актора.

**Состояние.** Писатель класса объявлен (`docs/architecture/contracts.md`,
таблица «класс события → писатель → момент»), хода к нему в коде нет:
`CoreEventType` значения `HOLD_RELEASED` не содержит, содержимого у класса
нет. Следствие для журнала названо у владельца
(`docs/models/domain/other/AuditRecord.md`): журнал зафиксирует, что
торговля счёта остановлена, и **не зафиксирует**, что она возобновлена, —
читатель истории увидит бессрочный холд там, где он был снят руками.

**Что оживит.** Ход, заводящий поверхность ручных операций ядра. Актор""",
     u"""**Что сделать.** Завести класс события `HoldReleased`, его содержимое и
поле актора и довести до писателя класса **уже построенную** поверхность
ручных операций ядра (`SafetyController`: постановка и снятие ступени).

**Состояние.** Писатель класса объявлен (`docs/architecture/contracts.md`,
таблица «класс события → писатель → момент»), хода к нему в коде нет:
`CoreEventType` значения `HOLD_RELEASED` не содержит, содержимого у класса
нет. Следствие для журнала названо у владельца
(`docs/models/domain/other/AuditRecord.md`): возобновление торговли журнал
фиксирует строкой отчёта о происшествии с кодом ручной тропы, а **своим
классом** — нет, то есть на вопрос «снята ли ступень» история отвечает
косвенно.

**Что оживит.** Ход, доводящий ручную тропу снятия до писателя класса. Актор""")

edit(CONTRACTS, u"""**Ручные тропы среди построенных классов есть — у переходов жизненного
цикла определения стратегии и у подъёма safety-ступени.** Признак ручной""",
     u"""**Ручные тропы среди построенных классов есть, и перечень их здесь не
воспроизводится.** Признак ручной""")

edit(AUDITABLE, u"""**Ручные тропы среди построенных классов уже есть** — переходы жизненного
цикла определения стратегии и подъём safety-ступени: их порождает команда
пользователя, и писатель получает токен предъявителя. Смена актора""",
     u"""**Ручные тропы среди построенных классов уже есть**, и перечня их здесь
нет: класс попадает в них, когда команда пользователя доходит до писателя,
и писатель получает токен предъявителя. Смена актора""")

# ---- парковка cross-cutting половин B6: обе правят чужого производителя
edit(BACKLOG, u"""## Состав точек чтения `audit-statistics` — по появлению их потребителя""",
     u"""## Решение о защите в журнале и разрез активности заявок

**Что сделать.** Две связанные позиции у счётчика `orderDecisions` зерна
происшествий (`docs/rules/statistics-aggregates.md` §«Счётчики происшествий —
своё зерно, а не строка сделочного агрегата»):

1. **Класс события под решение об отдельной условной заявке.** Исполнитель
   создания algo-заявки не публикует ничего, и класса под это решение в
   таблице `docs/architecture/contracts.md` §События не объявлено вовсе —
   значит и в разность объявленных и построенных классов оно не попадает
   (`docs/models/domain/other/AuditRecord.md`): журнал молчит о защите,
   стоящей отдельной заявкой, и не сообщает, что молчит.
2. **Операнд, разводящий вход и перестановку.** Перестановка ноги
   выражается новой сущностью плюс отменой старой
   (`docs/rules/replace-not-amend.md`), и каждая даёт своё `ORDER_DECIDED`;
   различителя в содержимом нет — `orderType` разводит защиту, встроенную в
   заявку, а не первичное решение от повторного.

**Что оживит.** Ближайший кодовый заход по `trading-core`, правящий
исполнителей заявок.

**Причина парковки.** Обе позиции правят **производителя**, то есть чужой
для шага 10 предмет: шаг потребляет построенные классы и чужих производителей
не строит, иначе его дельта перестаёт сводиться к его предмету
(`.claude/work/roadmap/phase-2.md`). Пока позиций нет, число `orderDecisions`
названо своей популяцией в доме агрегатов — читатель не выводит из него
больше, чем есть.

**Владелец** — `solution-designer` (класс и состав содержимого),
`code-writer` (писатель).

## Состав точек чтения `audit-statistics` — по появлению их потребителя""")

# ---- дельта CODE шага: четыре позиции, которых узел касается
edit(CHRONICLE,
     u"| 7а | **состав хранимых величин сделочного зерна вырос с двенадцати до двадцати трёх**: "
     u"счётчики четырёх признаков отбора, счётчик нерезолвленной валюты, штраф ликвидации, "
     u"суммы выигрышей и убытков по отдельности, сумма R-мультипликаторов с её счётчиком, "
     u"выведенный из знаменателя риск | `GAPS_CLOSE_2`, узел 3 (B1, B2, B4, B8) |",
     u"| 7а | **состав хранимых величин сделочного зерна вырос с двенадцати до двадцати "
     u"шести**: счётчики признаков отбора, счётчик нерезолвленной валюты, штраф ликвидации, "
     u"суммы выигрышей и убытков по отдельности, сумма R-мультипликаторов с её счётчиком, "
     u"выведенный из знаменателя риск. **Счётчик у каждого значения признака отбора, "
     u"достижимого в популяции счёта** — сверх ликвидации это принудительное сокращение, "
     u"неустановленный исход и непроверенная сверка; перечень величин и их формы — "
     u"`docs/spec/statistics-aggregates.json`, состав колонок — §Персистентность дома "
     u"агрегатов, и здесь они не пересказываются | `GAPS_CLOSE_2`, узел 3 (B1, B2, B4, B8); "
     u"три счётчика — `GAPS_CLOSE_3`, узел 3 (B1) |")
edit(CHRONICLE,
     u"| 7б | **шесть именованных счётчиков зерна происшествий** с двумя разрезами подъёма "
     u"ступени (жёсткие; поднятые командой держателя) | `GAPS_CLOSE_2`, узел 3 (A8, B6) |",
     u"| 7б | **восемь именованных счётчиков зерна происшествий**: два разреза подъёма ступени "
     u"(жёсткие; поднятые операцией держателя) и два разреза отчёта о происшествии (критичные; "
     u"заведённые операцией держателя). **Природу различает КОД тропы, а не актор строки** — "
     u"довод в `docs/rules/statistics-aggregates.md`; отсюда джоба пересчёта читает из "
     u"содержимого код и критичность | `GAPS_CLOSE_2`, узел 3 (A8, B6); два разреза "
     u"происшествия и смена операнда природы — `GAPS_CLOSE_3`, узел 3 (B5) |")
edit(CHRONICLE,
     u"| 16 | `actor` в `StrategyLifecycleContent`, `StrategyActivatedContent` **и "
     u"`HoldRaisedContent`** — область значений домовая; ручных троп среди построенных классов "
     u"четыре, не три | часть 2, Р10-2; расширено `GAPS_CLOSE_2`, узел 2 (B6) |",
     u"| 16 | `actor` в `StrategyLifecycleContent`, `StrategyActivatedContent`, "
     u"`HoldRaisedContent` **и `AnomalyReportedContent`** — область значений домовая; ручных "
     u"троп среди построенных классов **пять, не четыре**: отчёт о происшествии заводит и "
     u"ручная поверхность — на постановке ступени и на снятии (`docs/rules/manual-halt.md`), и "
     u"обе тропы доходят до писателя события. **Операндом разреза агрегата актор при этом не "
     u"служит** — там код тропы; поле заводится по правилу `docs/architecture/contracts.md` "
     u"§«У классов с ручной тропой содержимое несёт актора» | часть 2, Р10-2; расширено "
     u"`GAPS_CLOSE_2`, узел 2 (B6); пятый класс — `GAPS_CLOSE_3`, узел 3 (B5) |")
edit(CHRONICLE,
     u"| 19 | **`version`+1 у ШЕСТИ изменённых форм содержимого** (`DealClosedContent`, "
     u"`DealOpenedContent`, `OrderDecidedContent`, `StrategyLifecycleContent`, "
     u"`StrategyActivatedContent`, **`HoldRaisedContent`**)",
     u"| 19 | **`version`+1 у СЕМИ изменённых форм содержимого** (`DealClosedContent`, "
     u"`DealOpenedContent`, `OrderDecidedContent`, `StrategyLifecycleContent`, "
     u"`StrategyActivatedContent`, `HoldRaisedContent`, **`AnomalyReportedContent`**)")

# ---- B1: у объявленной корзины появился носитель
edit(DEAL, u"| `UNDETERMINED` | торговый исход **не установлен** — значение, а не пустота: "
           u"сделка остаётся в популяции и счётна отдельной корзиной |",
     u"| `UNDETERMINED` | торговый исход **не установлен** — значение, а не пустота: "
     u"сделка остаётся в популяции и счётна отдельной корзиной "
     u"(`docs/rules/statistics-aggregates.md`) |")


def apply_all(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        with io.open(path, encoding='utf-8') as handle:
            text = handle.read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:120].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            with io.open(path, 'w', encoding='utf-8', newline='\n') as handle:
                handle.write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


apply_all('--dry' in sys.argv)
```

### Скрипт реестра и дайджеста

```python
# -*- coding: utf-8 -*-
"""Записи реестра снятых редакций и дайджеста решений узла 3 GAPS_CLOSE_3.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import sys

NEW_ENTRIES = u'''    {
        'name': 'выведенный из знаменателя риск виден суммой на строке без валюты',
        # Снято GAPS_CLOSE_3 шага 10 фазы 2, узел 3 (B2). Прежняя редакция
        # объявляла денежные суммы строки нерезолвленной валюты нулевыми по
        # построению и тут же называла одну из них носителем видимости этой
        # популяции. Область plannedRiskExcludedSum есть ДОПОЛНЕНИЕ области
        # прочих сумм, поэтому на строке с пустым ключом валюты считалась
        # ровно она — сумма без единицы измерения, то есть та операция, ради
        # запрета которой валюта вынесена в ключ зерна. Пришедшая редакция:
        # величина считается только на строке известной валюты, а популяция
        # видна счётчиком.
        'pattern': r'суммой\\s+выведенного\\s+риска',
        'arrived': r'ключ\\s+зерна\*\*:\\s+единицу\\s+строке\\s+даёт\\s+он',
        'date': '2026-09-07',
        'source': 'GAPS_CLOSE_3 шага 10 фазы 2, узел 3',
        'allowed': ('.claude/work/decision-digest.md',),
        'population': (
            ('docs/rules/statistics-aggregates.md', None),
            ('docs/spec/statistics-aggregates.json', r'не\\s+тождество\\s+двух\\s+валют'),
        ),
    },
    {
        'name': 'операнд итога сделки зовётся gross',
        # Снято GAPS_CLOSE_3 шага 10 фазы 2, узел 3 (C5). Операнд, из
        # которого складывается netResultSum, звался grossResult — тем самым
        # именем, которое нота этой величины запрещает («назвать это число
        # gross значило бы звать gross ровно то, что корпус зовёт net»).
        # Домового имени он при этом не носил: дом зовёт число
        # Deal.resultProfit. Пришедшая редакция — домовое имя, как у
        # соседнего операнда priceResult.
        'pattern': r'"grossResult"',
        'arrived': r'"resultProfit"',
        'date': '2026-09-07',
        'source': 'GAPS_CLOSE_3 шага 10 фазы 2, узел 3',
        'allowed': ('.claude/work/decision-digest.md',),
        'population': (
            ('docs/spec/statistics-aggregates.json', None),
        ),
    },
    {
        'name': 'природу подъёма ступени различает актор строки журнала',
        # Снято GAPS_CLOSE_3 шага 10 фазы 2, узел 3 (B5). Актор отвечает на
        # вопрос «кем порождён ход», и у джобы, запущенной ручным триггером,
        # он равен принципалу (перенос контекста в порождённый тред —
        # требование docs/models/domain/other/Auditable.md). По актору
        # автоматическая остановка, найденная в ручном прогоне детекции,
        # считалась бы остановкой держателя — то есть разрез давал бы ровно
        # ту ошибку, против которой заведён. Пришедшая редакция: различитель
        # природы — КОД тропы, которым ручная поверхность метит свои
        # операции, и он же различитель у отчёта о происшествии.
        'pattern': r'actorIsPrincipal',
        'arrived': r'codeIsManualOperation',
        'date': '2026-09-07',
        'source': 'GAPS_CLOSE_3 шага 10 фазы 2, узел 3',
        'allowed': ('.claude/work/decision-digest.md',),
        'population': (
            ('docs/spec/statistics-aggregates.json', None),
            ('docs/rules/statistics-aggregates.md', r'Природу\\s+различает\\s+код\\s+тропы'),
        ),
    },
    {
        'name': 'поверхность ручных операций ядра не построена',
        # Снято GAPS_CLOSE_3 шага 10 фазы 2, узел 3 (свип стыка B5).
        # Поверхность построена: SafetyController несёт постановку и снятие
        # ступени, обе доходят до писателя отчёта о происшествии и через него
        # до outbox. Не построен КЛАСС события HoldReleased — значения
        # перечня и содержимого у него нет. Прежняя редакция путала эти две
        # вещи в четырёх носителях и выводила из них, что журнал не
        # фиксирует возобновление торговли вовсе.
        'pattern': r'а\\s+её\\s+не\\s+построено',
        'arrived': r'построенн\\w*\\s+поверхност\\w*\\s+ручных\\s+операций'
                   r'|поверхность\\s+ручных\\s+операций\\s+ядра\\s+построена',
        'date': '2026-09-07',
        'source': 'GAPS_CLOSE_3 шага 10 фазы 2, узел 3',
        'allowed': ('.claude/work/decision-digest.md',),
        'population': (
            ('docs/spec/event-actor-presence.json', None),
            ('docs/models/domain/other/Auditable.md',
             r'ручная\\s+тропа\\s+\*\*построена\*\*'),
            ('docs/models/domain/other/AuditRecord.md', None),
            ('.claude/work/backlog.md', r'уже\\s+построенную\*\*\\s+поверхность'),
        ),
    },
]'''

DIGEST = u'''
## Итерация 2026-09-07 (68) — шаг 10 фазы 2, `GAPS_CLOSE_3` узел 3 (состав и семантика счётчиков)

Решения машины при закрытии узла 3 прогона `DOCS_CHECK_3` (находки B1, B2,
B5, B6, C5). Отчёт — `.claude/work/progress/phase-2-step-10-gaps-close-3-node-3.md`.
Общее правило узла: **число агрегата не говорит о себе неправды — имя и нота
называют ту популяцию, которую оно считает, а перечислимый операнд отбора
покрыт целиком и отнесение проверяется прогоном.**

| # | Решение | Альтернативы | Почему так |
|---|---|---|---|
| Д1053 | **Диспозиций у значения признака отбора три:** свой счётчик — если значение достижимо у сделки, вошедшей в денежные суммы; выводимый остаток — у здорового значения; объявленная недостижимость — если значение несовместимо с принятием риска | счётчик на каждое значение; счётчик только там, где предъявила находка | счётчик на каждое дал бы колонку здоровому значению, число которого есть остаток, — второй носитель выводимого (`docs/concept.md` П4). Правка только по предъявленному оставила бы три признака из четырёх с тем же дефектом: замер показал 1/4, 1/3, 1/3, 1/3 |
| Д1054 | **Отнесение проверяется популяцией исполнимой формы, а перечень значений выводится из объявления перечня общей доменной модели** (`tools/derive/deal-selection-feature-values.sh`) | перечислить значения прозой правила; собрать перечень из значений, встреченных в примерах | проза стареет молча — ровно то, чем дефект и держался: перечень объявлен доменной моделью, а счётчик стоял на одном значении. Перечень из примеров самореферентен: он молчит о значении, которого в примерах нет, а недостижимые значения и есть то, о чём правило обязано высказаться |
| Д1055 | **У `NOT_ASSESSED` счётчика нет: его популяция уже названа `resultUnavailableDeals`, а импликация переведена в охранный инвариант с контрпримером** | свой счётчик; молчание | «полнота не оценивалась» имеет единственный триггер — добыча движений не выполнялась, — а тогда ложна доступность итога, и сделка уже посчитана счётчиком отсутствия. Второй счётчик той же популяции был бы вторым носителем; молчание оставило бы диспозицию непроверяемой. Инвариант делает импликацию, на которой диспозиция держится, падающей при нарушении |
| Д1056 | **Операнд итога переименован в `resultProfit` — домовое имя**, а не в `netResult` под конвенцию величины | оставить `grossResult` с нотой-объяснением; назвать `netResult` | нота-объяснение есть третий носитель одного различения. `netResult` снял бы противоречие, но завёл бы **третье** имя одного числа (`resultProfit` у дома, `netResultSum` у величины, `netResult` у операнда). Домовое имя снимает противоречие и вводит ту же конвенцию, по которой соседний операнд зовётся `priceResult` |
| Д1057 | **Область `plannedRiskExcludedSum` сужена конъюнктом `currencyResolved`**, а довод — ключ зерна, а не тождество валют | ввести колонку валюты риска в ключ; снять клейм «денежные суммы нулевые по построению» | вторая валюта в ключе множит строки произведением, а расхождение двух валют на одной сделке не достижимо ни на одной названной тропе. Снятие клейма оставило бы человеку сумму без единицы измерения, показанную как мера риска. Довод через ключ зерна не опирается на непроверенную посылку о совпадении двух валют: единицу строке даёт ключ, а он несёт валюту результата |
| Д1058 | **Природу подъёма ступени и отчёта о происшествии различает КОД тропы, а не актор строки** — операнд `actorIsPrincipal` снят у обоих счётчиков | оставить актор у подъёма, ввести код только у отчёта; ввести перечень кодов литералом | актор отвечает «кем порождён ход», а ручной триггер джобы обязан доносить контекст принципала до записи (`Auditable.md`): по актору находки автоматики в ручном прогоне детекции считались бы операциями держателя. Разные операнды у двух счётчиков одного разреза дали бы два ответа на один вопрос. Литеральный перечень копировал бы область, чей дом открыт (`AnomalyReport.md`: закрытого реестра кодов нет) |
| Д1059 | **У класса `AnomalyReported` ручная тропа ЕСТЬ, и актор обязан ехать в его содержимом** | оставить `hasManualPath` ложным; объявить исключение | признак ручной тропы — **точка входа** (`docs/architecture/contracts.md`): `SafetyController` → `ManualHaltService` → `AnomalyReportService` → писатель события, на постановке и на снятии. Ложный признак делал бы прогон `event-actor-presence.json` зелёным на неверном ответе. Актор при этом не операнд разреза агрегата — он требуется правилом содержимого, и это названо в дельте `CODE` |
| Д1060 | **Обе cross-cutting половины B6 припаркованы одной секцией бэклога**, а число `orderDecisions` названо своей популяцией в доме | завести класс события под защиту в этом шаге; оставить имя без пояснения | обе половины правят **производителя** — чужой для шага 10 предмет, тот же довод, по которому шаг не заводит производителя у `auth`. Имя без пояснения продолжало бы читаться шире, чем есть, а по этому числу судят об активности |
'''


def main():
    path = 'tools/retired-check.py'
    text = io.open(path, encoding='utf-8').read()
    tail = u"        ),\n    },\n]"
    if text.count(tail) != 1:
        sys.stderr.write(u'ОТКАЗ: хвост реестра найден %d раз\n' % text.count(tail))
        raise SystemExit(2)
    if u"'операнд итога сделки зовётся gross'" in text:
        sys.stderr.write(u'ОТКАЗ: записи узла 3 уже внесены\n')
        raise SystemExit(2)
    text = text.replace(tail, u"        ),\n    },\n" + NEW_ENTRIES)
    io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'ПРИМЕНЕНО: %s — записей 4' % path)

    digest = '.claude/work/decision-digest.md'
    body = io.open(digest, encoding='utf-8').read()
    if u'Д1053' in body:
        sys.stderr.write(u'ОТКАЗ: дайджест узла 3 уже внесён\n')
        raise SystemExit(2)
    io.open(digest, 'a', encoding='utf-8', newline='\n').write(DIGEST)
    print(u'ПРИМЕНЕНО: %s — решений 8' % digest)


main()
```

## Диспозиция находок узла

| Находка | Исход |
|---|---|
| B1 | **закрыта.** Счётчик у каждого нездорового значения всех четырёх признаков, охранный инвариант, связывающий неоценённую полноту разбивки с недоступностью итога, объявленная недостижимость у `NOT_APPLICABLE`, четыре популяции с выводом перечня из объявления перечня доменной модели. Проза ликвидации сужена до «по марже» — она была шире своего предиката |
| B2 | **закрыта.** Область `plannedRiskExcludedSum` сужена конъюнктом `currencyResolved`; клейм §«Зерно строки» стал верным; довод опирается на ключ зерна, а не на непроверенную посылку о совпадении двух валют |
| B5 | **закрыта.** Два разреза отчёта о происшествии (`criticalAnomalyReports`, `manualOperationReports`) плюс смена операнда природы у **обоих** счётчиков разреза: различитель — код тропы, а не актор строки |
| B6 | **закрыта в части носителя, cross-cutting часть припаркована.** Популяция `orderDecisions` названа в ноте и в правиле; класс события под решение о защите и операнд «вход против перестановки» — секция `.claude/work/backlog.md` §«Решение о защите в журнале и разрез активности заявок» с домом, оживителем и причиной |
| C5 | **закрыта.** Операнд переименован в домовое имя `resultProfit` |

**Побочно закрыта B7** (находка узла 5): правимый ею пассаж
`docs/rules/statistics-aggregates.md` — тот самый, где стоя́л операнд разреза,
— приведён к ссылке на дом без пересказа состава неисполненного. Узел 5
находку не переоткрывает; здесь она названа, чтобы исход не потерялся.

**Стык, найденный узлом и закрытый им же.** Правило «у классов с ручной
тропой содержимое несёт актора» применялось к `AnomalyReported` с ложным
операндом: `hasManualPath` стоял ложным, тогда как ручная поверхность ядра
доходит до писателя события на обеих операциях. Отсюда пятый класс в дельте
`CODE` и седьмая форма содержимого с поднятой версией. Тем же ходом снят
класс «поверхность не построена» в четырёх носителях: построена поверхность,
не построен **класс события**, и журнал возобновление торговли фиксирует —
но не своим классом.

### Скрипт доработки по раунду 2

Семь блокирующих дефектов раунда 2 и пять неблокирующих закрыты одним ходом;
скрипт — тот же формат пары «Было / Стало». Сверх него двумя точечными
правками поправлены шаблон пришедшей редакции в записи реестра (пассаж
переименован этой же доработкой) и **объявление области** `retired-check`:
рабочие реестры `.claude/work/*.json` названы вне области по тому же
признаку, по которому вне неё `progress/` — они цитируют снятую редакцию как
**закрытую находку**. Признак не предполагался, а **измерен**: расширение
области дало три такие цитаты и ни одного живого клейма, после чего
расширение снято, а причина записана в шапку инструмента.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по раунду 2 критики.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
Каждый блок «Было» обязан находиться в цели РОВНО ОДИН раз.
"""
import io
import json
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC = 'docs/spec/statistics-aggregates.json'
CONTRACTS = 'docs/architecture/contracts.md'
AUDITABLE = 'docs/models/domain/other/Auditable.md'
BACKLOG = '.claude/work/backlog.md'
LEDGER = '.claude/work/code-gate-ledger.json'
REGISTRY = 'tools/retired-check.py'
DIGEST = '.claude/work/decision-digest.md'

# ---- Р2-1: главный носитель снятой редакции — строка таблицы писателей
edit(CONTRACTS,
     u"**Писатель объявлен, хода к нему в коде пока нет:** снятие ступени приезжает "
     u"поверхностью ручных операций, и событие заводится тем же ходом, что и его тропа",
     u"**Писатель объявлен, хода к нему в коде пока нет:** поверхность ручных операций ядра "
     u"построена и снятие ступени идёт ею, а до этого писателя не доходит — класс заводится "
     u"ходом, который её до него доведёт")

# ---- Р2-8: та же снятая редакция в двух рабочих носителях
edit(BACKLOG,
     u"а закрыть её может только\n  ход, строящий тропу снятия, — журнал её не строит.",
     u"а закрыть её может только\n  ход, доводящий построенную тропу снятия до писателя "
     u"класса, — журнал её не строит.")
edit(LEDGER,
     u"HOLD_RELEASED не пишется: тропы снятия ещё нет — она приезжает с ручной операцией "
     u"остановки (компонент поверхности ядра).",
     u"HOLD_RELEASED не пишется: тропа снятия построена (SafetyController), а до писателя "
     u"класса не доходит — значения перечня и содержимого у класса нет.")

# ---- Р2-3, Р2-6: разрез зовётся по ПРОИСХОЖДЕНИЮ, а считает ручную ТРОПУ
edit(RULE, u"| `raisedHolds` | поднятых ступеней — **обе природы вместе** |",
     u"| `raisedHolds` | поднятых ступеней — **обе тропы вместе** |")
edit(RULE, u"| `manuallyRaisedHolds` | из них поднятых **командой держателя**, а не проходом |",
     u"| `manuallyRaisedHolds` | из них поднятых **ручной тропой**, а не проходом |")
edit(RULE, u"| `anomalyReports` | отчётов о происшествиях — **обе природы вместе** |",
     u"| `anomalyReports` | отчётов о происшествиях — **оба происхождения вместе** |")
edit(RULE, u"| `manualOperationReports` | из них заведённых **операцией держателя**, а не "
           u"детекцией |",
     u"| `manualOperationReports` | из них заведённых **ручной тропой**, а не детекцией |")
edit(RULE, u"""Без разреза по природе одно число складывает
противоположные состояния: «система остановила себя» есть сигнал, что
гипотеза не работает, «я её остановил» — собственное действие держателя;
сложенные, они выдают остановки держателя за признак отказа гипотезы.""",
     u"""Без разреза по происхождению одно число складывает
противоположные состояния: «система остановила себя» есть сигнал, что
гипотеза не работает, «остановили её снаружи» — вмешательство в контур;
сложенные, они выдают вмешательства за признак отказа гипотезы.""")
edit(RULE, u"""**Природу различает код тропы, а не актор строки, и это не вкус.** Актор
отвечает на вопрос «кем порождён ход», и у джобы, запущенной ручным
триггером, он равен принципалу — контекст обязан переживать смену треда
(`docs/models/domain/other/Auditable.md`). По актору автоматическая
остановка, найденная в ручном прогоне детекции, попала бы в число остановок
держателя, то есть разрез давал бы ровно ту ошибку, против которой заведён.
Код же метит **операцию** ручной поверхности, а не происхождение хода, и им
же дом различает ручную остановку от автоматической.""",
     u"""**Разрез зовётся по происхождению и различает его КОДОМ тропы, а не
актором строки.** Имя выбрано так, потому что слово «природа» у отчёта о
происшествии занято своей осью (`docs/models/domain/other/AnomalyReport.md`),
и второго смысла ему не даётся. Различитель — код: актор отвечает на вопрос
«кем порождён ход», и у джобы, запущенной ручным триггером, он равен
принципалу — контекст обязан переживать смену треда
(`docs/models/domain/other/Auditable.md`). По актору автоматическая
остановка, найденная в ручном прогоне детекции, попала бы в число ручных, то
есть разрез давал бы ровно ту ошибку, против которой заведён.

**Отсюда и популяция обоих ручных счётчиков — ТРОПА, а не человек.** Код
метит операцию ручной поверхности и одинаков у человека и у скрипта, до неё
дошедшего (`docs/models/domain/other/Auditable.md`); «кто именно» отвечает
актор, и он едет своим носителем в содержимое, а операндом разреза не
служит.""")
edit(RULE, u"""**Те же два разреза обязательны у отчёта о происшествии, и по тем же
доводам.** Отчёт заводит не только детекция: ручная поверхность заводит его
и на постановке ступени, и на **снятии** (`docs/rules/manual-halt.md`).""",
     u"""**Те же два разреза обязательны у отчёта о происшествии, и по тем же
доводам.** Отчёт заводит не только детекция: ручная поверхность заводит его
и на постановке ступени, и на **снятии** (`docs/rules/manual-halt.md`).
Направление операции третьим разрезом не заводится, и это решение, а не
пропуск: постановка уже счётна `manuallyRaisedHolds`, а «когда именно
сняли» — вопрос к журналу, где коды двух операций различны, не к суточному
числу.""")

# ---- Р2-5: абзац признаков ненадёжности держал снятую редакцию рядом с пришедшей
edit(RULE, u"""**Признак ненадёжности — тоже счётчик, и популяция при этом остаётся в
суммах.** Ликвидация, несошедшаяся сверка, неполная разбивка и потерянная
база риска не делают число отсутствующим — они делают его **ненадёжным**.
Такая сделка из сумм не выводится (иначе суммы перестали бы покрывать
закрытые сделки), но её ненадёжность видна счётчиком: «не проверяли»
обязано отличаться от «проверили, всё в порядке» (`docs/concept.md` П1).
Четыре признака едут в терминальном событии как признаки **отбора**
(`docs/architecture/contracts.md`), и отчёт, ради которого они туда
положены, обязан по ним отбирать.""",
     u"""**Признак ненадёжности — тоже счётчик, и популяция при этом остаётся в
суммах.** Принудительное закрытие, несошедшаяся или непроведённая сверка,
неполная разбивка и потерянная база риска не делают число отсутствующим —
они делают его **ненадёжным**. Такая сделка из сумм не выводится (иначе
суммы перестали бы покрывать закрытые сделки), но её ненадёжность видна
счётчиком: «не проверяли» обязано отличаться от «проверили, всё в порядке»
(`docs/concept.md` П1). Перечня значений здесь нет — он живёт в исполнимой
форме и выводится прогоном (абзац ниже). Признаки отбора едут в
терминальном событии (`docs/architecture/contracts.md`), и отчёт, ради
которого они туда положены, обязан по ним отбирать.""")

# ---- Р2-10: дом парковки называется секцией, а не файлом
edit(RULE, u"развёл бы вход и перестановку, в содержимом не едет, а завести его — правка\n"
           u"чужого производителя (дом ожидания — `.claude/work/backlog.md`).",
     u"развёл бы вход и перестановку, в содержимом не едет, а завести его — правка\n"
     u"чужого производителя (дом ожидания — `.claude/work/backlog.md`\n"
     u"§«Решение о защите в журнале и разрез активности заявок»).")

# ---- Р2-12: у «активации» вернулся референт
edit(AUDITABLE, u"""класс попадает в них, когда команда пользователя доходит до писателя,
и писатель получает токен предъявителя. Смена актора""",
     u"""класс попадает в них, когда команда пользователя доходит до писателя,
и писатель получает токен предъявителя — так устроены, среди прочих,
переходы жизненного цикла определения стратегии. Смена актора""")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:120].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


def apply_spec(dry):
    spec = json.load(io.open(SPEC, encoding='utf-8'))
    values = {v['name']: v for v in spec['values']}
    clause = (' Популяция — принявшие риск, а не вошедшие в денежные суммы: признак достижим и '
              'у сделки с недоступным результатом, а такая в суммы не входит.')
    # Р2-4: клауза популяции доводится до ВСЕХ счётчиков признаков отбора.
    values['liquidatedDeals']['note'] = (
        'Сколько принявших риск сделок суток закрыла биржа по марже.' + clause)
    values['riskBenchmarkMissingDeals']['note'] = (
        'Сколько принявших риск сделок суток потеряло базу риска.' + clause)
    # Р2-3, Р2-6: разрез зовётся по происхождению, популяция его — ручная тропа.
    values['raisedHolds']['note'] = (
        'Счётчик происшествий суток: поднятых ступеней, обе тропы вместе. Само по себе это '
        'число ни на какой вопрос не отвечает и потому идёт в паре с разрезами ниже.')
    values['manuallyRaisedHolds']['note'] = (
        'Разрез по происхождению. «Система остановила себя» — сигнал, что гипотеза не '
        'работает; «остановили её снаружи» — вмешательство в контур. Одно число на оба '
        'состояния есть ложный операнд по определению: вмешательства считались бы признаком '
        'отказа гипотезы. Различитель — КОД тропы, а не актор строки: актор отвечает на вопрос '
        '«кем порождён ход», и у джобы, запущенной ручным триггером, он равен принципалу '
        '(docs/models/domain/other/Auditable.md), то есть автоматическая остановка, найденная в '
        'ручном прогоне детекции, попала бы в это число. Отсюда и популяция: счётчик мерит '
        'ТРОПУ, а не человека — код одинаков у человека и у скрипта, до поверхности дошедшего.')
    values['anomalyReports']['note'] = (
        'Счётчик происшествий суток: отчётов о происшествиях, оба происхождения вместе. Само по '
        'себе это число ни на какой вопрос не отвечает и потому идёт в паре с разрезами ниже — '
        'как и raisedHolds.')
    values['manualOperationReports']['note'] = (
        'Разрез по происхождению. Отчёт заводит не только детекция: ручная поверхность заводит '
        'его и на постановке ступени, и на СНЯТИИ (docs/rules/manual-halt.md). Снятие '
        'возвращает контур в работу — состояние, происшествию противоположное, — и сложенное с '
        'происшествиями автоматики оно выдаёт вмешательство за отказ гипотезы. Довод и операнд '
        'те же, что у manuallyRaisedHolds, включая выбор кода вместо актора: ручной прогон '
        'джобы детекции даёт актором принципала, и по актору находки автоматики считались бы '
        'ручными. Направление операции третьим разрезом не разводится: постановка уже счётна '
        'manuallyRaisedHolds, а «когда сняли» — вопрос к журналу, где коды двух операций '
        'различны.')
    values['criticalAnomalyReports']['note'] = (
        'Разрез по критичности. «Kill-switch гонялся» и «не гонялся» — разные сутки, а без '
        'разреза они дают одно число; довод тот же, каким обоснован разрез по ступени у '
        'подъёма холда.')
    if not dry:
        io.open(SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — нот 7' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', SPEC))


def apply_registry(dry):
    # Р2-2: шаблон снятой редакции — предметный, популяция полна.
    text = io.open(REGISTRY, encoding='utf-8').read()
    before = r"        'pattern': r'а\s+её\s+не\s+построено',"
    if text.count(before) != 1:
        sys.stderr.write(u'ОТКАЗ: шаблон записи реестра найден %d раз\n' % text.count(before))
        raise SystemExit(2)
    after = (r"        'pattern': r'приезжает\s+поверхностью\s+ручных\s+операций'" + u"\n"
             + r"                   r'|тропы\s+снятия\s+ещё\s+нет'" + u"\n"
             + r"                   r'|ход,\s+строящий\s+тропу\s+снятия'" + u"\n"
             + r"                   r'|а\s+её\s+не\s+построено',")
    text = text.replace(before, after)
    pop_before = r"            ('.claude/work/backlog.md', r'уже\s+построенную\*\*\s+поверхность'),"
    if text.count(pop_before) != 1:
        sys.stderr.write(u'ОТКАЗ: строка популяции найдена %d раз\n' % text.count(pop_before))
        raise SystemExit(2)
    pop_after = (pop_before + u"\n"
                 + u"            ('docs/architecture/contracts.md',\n"
                 + r"             r'поверхность\s+ручных\s+операций\s+ядра\s+построена'),")
    text = text.replace(pop_before, pop_after)
    if not dry:
        io.open(REGISTRY, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'%s: %s — шаблон и популяция записи о поверхности'
          % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', REGISTRY))


def apply_digest(dry):
    # Р2-11: два довода дайджеста внутренне расходились.
    text = io.open(DIGEST, encoding='utf-8').read()
    pairs = [
        (u'вторая валюта в ключе множит строки произведением, а расхождение двух валют на '
         u'одной сделке не достижимо ни на одной названной тропе',
         u'вторая валюта в ключе множит строки произведением, а состояния, ради которого её '
         u'заводят, никто не наблюдал'),
        (u'Литеральный перечень копировал бы область, чей дом открыт (`AnomalyReport.md`: '
         u'закрытого реестра кодов нет)',
         u'Литеральный перечень копировал бы область, чей дом — тропа-производитель кода '
         u'(`docs/rules/manual-halt.md` у ручных операций; закрытого реестра кодов нет вовсе — '
         u'`AnomalyReport.md`)'),
    ]
    for before, after in pairs:
        if text.count(before) != 1:
            sys.stderr.write(u'ОТКАЗ: довод дайджеста найден %d раз\n' % text.count(before))
            raise SystemExit(2)
        text = text.replace(before, after)
    if not dry:
        io.open(DIGEST, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'%s: %s — доводов 2' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', DIGEST))


DRY = '--dry' in sys.argv
apply_prose(DRY)
apply_spec(DRY)
apply_registry(DRY)
apply_digest(DRY)
```

## Исход мини-петли

**Два раунда, оба на приземлённом состоянии; правки корпуса чисты.**

| Раунд | Дефектов | Блокирующих | Что поймал |
|---|---|---|---|
| 1 | 12 | 10 | ноты счётчиков говорили «вошло в суммы» при популяции «принявшие риск»; операнд разреза природы (`actorIsPrincipal`) ложен на ручном триггере джобы; дельта `CODE`, реестр, бэклог и дайджест заявлены сводкой и не опубликованы; свип по классу «поверхность не построена» не проведён; счётное клеймо «тринадцать»; указатель дома единицы измерения вёл не в дом; нота `liqPenalty` объявляла фактом ненаблюдённое |
| 2 | 12 | 7 | свип раунда 1 пропустил **главный** носитель — строку таблицы писателей `contracts.md`; шаблон записи реестра родовой и не ловил ни одной живой формулировки; смена операнда не доведена до имён и нот (числа звались «действиями держателя», а считают ручную **тропу**); клауза популяции доведена до четырёх счётчиков из семи; соседний абзац правила держал снятую редакцию рядом с пришедшей; «природа» у отчёта о происшествии занята своей осью его домом |

**Семь дефектов раунда 2 из двенадцати внесены доработкой раунда 1** — тот
самый класс, ради которого потолок раундов не отменяет верификации финальной
правки. Механизм у всех семи один: доработка снимала предъявленный экземпляр
и не переносила критерий на однородные позиции того же текста (свип — на
таблицу писателей, клаузу популяции — на два счётчика из шести, смену
операнда — на имена и ноты).

**Что из этого годно следующим узлам.** Смена **операнда** обязана
доводиться до **имён и нот** тем же ходом: операнд отвечает на один вопрос,
имя обещает читателю другой, и расхождение между ними — ровно тот дефект,
который узел закрывает. И второе: **термин, вводимый закрытием, проверяется
на занятость у дома-владельца соседней сущности** — «природа» была занята
осью «состояние против происшествия» у `AnomalyReport.md`, и разрез, названный
так же, дал бы одному слову два несовместимых смысла на одной сущности.

### Скрипт доработки по верификации (заход 3)

Верификация финальной правки нашла **семь** дефектов, шесть из которых едут в
корпус, — то есть узел чистым не был, и потолок двух раундов исчерпался
внутри второго захода. Узел остался в работе третьим заходом
(`.claude/decisions/per-node-closure-frame.md`: «не сошлось за два раунда —
узел остаётся в работе»); прецедент того же рода — девять адверсариальных
заходов узла 1.

Сверх скрипта ниже сделаны две правки реестра `tools/retired-check.py`:

- **рабочие реестры `.claude/work/*.json` ВВЕДЕНЫ в область свипа**, а три
  цитаты закрытых находок объявлены разрешённым местом у своих записей
  (ключ `allowed` — там же, где уже стоя́ли дом решения и исходник
  инструмента). Прежний ход — объявить реестр вне области — снят: он выводил
  из-под свипа носитель, который гейтит `CODE`, и опирался на измерение,
  снятое **после** собственной правки живого клейма (счёт был назван «три»,
  а расширение области даёт четыре попадания, из которых одно — живой клейм,
  снятый этим же узлом);
- **заведена запись под переименование разреза** («по природе» → «по
  происхождению», «держатель» → «ручная тропа»): у переименования не было
  энфорсера, и остатки его свипа прогон не ловил. Шаблон предметный —
  родовое «обе природы» законно живёт в `docs/rules/absent-value-semantics.md`
  и находкой быть не должно.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по верификации финальной правки.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import json
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC = 'docs/spec/statistics-aggregates.json'
MANUAL = 'docs/rules/manual-halt.md'
CHRONICLE = '.claude/work/progress/phase-2-step-10-chronicle.md'
REGISTRY = 'tools/retired-check.py'

# ---- В-1: довод отказа от третьего разреза опирался на ложное равенство.
# manuallyRaisedHolds считает класс HOLD_RAISED, а он не производится при
# поглощении (HoldService: ступень не переставлена — события нет), тогда как
# строка отчёта заводится; сверх того ручная постановка сегодня до писателя
# класса не доходит вовсе. Вычесть постановку из числа нельзя.
edit(RULE, u"""Направление операции третьим разрезом не заводится, и это решение, а не
пропуск: постановка уже счётна `manuallyRaisedHolds`, а «когда именно
сняли» — вопрос к журналу, где коды двух операций различны, не к суточному
числу.""",
     u"""Направление операции третьим разрезом не разводится, и это решение, а не
пропуск: число отвечает на «сколько раз в контур вмешались руками», а обе
операции — вмешательства. Какая именно и когда — вопрос к журналу, где коды
двух операций различны (`docs/rules/manual-halt.md`); суточное число на него
не отвечает и не обязано. Вычесть постановку из этого числа, кстати, нельзя:
`manuallyRaisedHolds` считает **класс подъёма ступени**, а он на поглощённом
запросе не производится, тогда как строка отчёта заводится.""")

# ---- В-2: свип переименования не дошёл до соседнего предложения и до примера
edit(RULE, u"""вмешательство держателя за отказ гипотезы, то есть даёт ровно ту ошибку, от
которой разрез по природе защищает у подъёма ступени.""",
     u"""вмешательство за отказ гипотезы, то есть даёт ровно ту ошибку, от
которой разрез по происхождению защищает у подъёма ступени.""")

# ---- В-4: дом ручной остановки держит клейм, ложный по своему же коду
edit(MANUAL, u"""не доходит ни на одной ветви: мягкая ставит статус и строку журнала
  сама, полная идёт через координатора реакции. Следствие крупнее
  отсутствия актора: журнал не фиксирует ручную остановку **никак** — ни с
  актором, ни без, — и клейм «тем же механизмом» на уровне события не
  держится.""",
     u"""не доходит ни на одной ветви: мягкая ставит статус и строку журнала
  сама, полная идёт через координатора реакции. Следствие точное: **классом
  подъёма ступени** журнал ручную остановку не фиксирует, и клейм «тем же
  механизмом» на уровне события не держится. Сама остановка в журнале при
  этом есть — её несёт строка отчёта о происшествии с кодом ручной тропы
  (§Наблюдаемость), — но счётчики, отбирающие по классу подъёма, её не
  видят.""")

# ---- В-6: перечень значений в абзаце признаков ненадёжности снимается целиком
edit(RULE, u"""**Признак ненадёжности — тоже счётчик, и популяция при этом остаётся в
суммах.** Принудительное закрытие, несошедшаяся или непроведённая сверка,
неполная разбивка и потерянная база риска не делают число отсутствующим —
они делают его **ненадёжным**.""",
     u"""**Признак ненадёжности — тоже счётчик, и популяция при этом остаётся в
суммах.** Ненадёжность — не отсутствие: число есть, но опереться на него
как на проверенное нельзя.""")

# ---- В-2: дельта CODE несла снятые термины
edit(CHRONICLE,
     u"два разреза подъёма ступени (жёсткие; поднятые операцией держателя) и два разреза "
     u"отчёта о происшествии (критичные; заведённые операцией держателя). **Природу различает "
     u"КОД тропы, а не актор строки**",
     u"два разреза подъёма ступени (жёсткие; поднятые ручной тропой) и два разреза отчёта о "
     u"происшествии (критичные; заведённые ручной тропой). **Происхождение различает КОД "
     u"тропы, а не актор строки**")
edit(CHRONICLE, u"два разреза происшествия и смена операнда природы — `GAPS_CLOSE_3`, узел 3 "
                u"(B5) |",
     u"два разреза происшествия и смена операнда происхождения — `GAPS_CLOSE_3`, узел 3 (B5) |")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:120].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


def apply_spec(dry):
    spec = json.load(io.open(SPEC, encoding='utf-8'))
    # В-2: имя примера несло снятый термин в исполнимой форме
    for example in spec['examples']:
        if example['case'].startswith(u'счётчики происшествий суток: обе природы'):
            example['case'] = (u'счётчики происшествий суток: оба происхождения подъёма '
                               u'ступени, обе ступени и оба происхождения отчёта о происшествии')
    # В-5: excludes популяции базы риска противоречил операнду — у неё СВОЙ довод
    for population in spec['populations']:
        if population.get('keys') == ['riskBenchmarkAvailability']:
            population['excludes'] = (
                'сделка, закрытая без входа: признак у неё принимает NOT_APPLICABLE, а вся '
                'популяция счётчиков отбора — принявшие риск')
    # В-1: та же поправка довода в ноте величины
    for value in spec['values']:
        if value['name'] == 'manualOperationReports':
            value['note'] = value['note'].replace(
                'Направление операции третьим разрезом не разводится: постановка уже счётна '
                'manuallyRaisedHolds, а «когда сняли» — вопрос к журналу, где коды двух '
                'операций различны.',
                'Направление операции третьим разрезом не разводится: число отвечает на '
                '«сколько раз в контур вмешались руками», а обе операции — вмешательства; '
                'какая именно и когда — вопрос к журналу, где коды двух операций различны. '
                'Вычесть постановку из числа нельзя: manuallyRaisedHolds считает класс подъёма '
                'ступени, а он на поглощённом запросе не производится, тогда как строка отчёта '
                'заводится.')
    if not dry:
        io.open(SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — имя примера, excludes и нота' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', SPEC))


def apply_registry(dry):
    # В-3: рабочие реестры ВВОДЯТСЯ в область (живой клейм там был и был снят
    # этим же узлом), а цитаты закрытых находок объявляются разрешённым местом
    # у своих записей — как это и предписано ключом allowed.
    text = io.open(REGISTRY, encoding='utf-8').read()
    declared = (u"библиотеки и отчётов прогонов (`progress/` цитирует снятые редакции как\n"
                u"находки — это их предмет, а не рецидив). ПО ТОМУ ЖЕ ПРИЗНАКУ вне области\n"
                u"рабочие реестры `.claude/work/*.json`: поля «суть» и «чем» реестра\n"
                u"компонентов цитируют снятую редакцию как ЗАКРЫТУЮ находку захода —\n"
                u"проверено расширением области, давшим три таких цитаты и ни одного живого\n"
                u"клейма. Суффикс `md` тех же каталогов в области остаётся.")
    if text.count(declared) != 1:
        sys.stderr.write(u'ОТКАЗ: объявление области найдено %d раз\n' % text.count(declared))
        raise SystemExit(2)
    text = text.replace(declared, (
        u"библиотеки и отчётов прогонов (`progress/` цитирует снятые редакции как\n"
        u"находки — это их предмет, а не рецидив). РАБОЧИЕ РЕЕСТРЫ `.claude/work/*.json`\n"
        u"в области ЕСТЬ, и это измерено, а не предположено: расширение области дало\n"
        u"четыре попадания — три цитаты закрытых находок в полях «суть» и «чем» и один\n"
        u"ЖИВОЙ клейм в поле «примечание». Живой снят, три цитаты объявлены разрешённым\n"
        u"местом у своих записей (ключ `allowed`) — так же, как объявляется дом решения.\n"
        u"Исключить реестр целиком значило бы вывести из-под свипа носитель, который\n"
        u"гейтит `CODE` и адресован исполнителю следующего под-шага."))
    root = u"          '.claude/**/*.md',\n"
    if text.count(root) != 1:
        sys.stderr.write(u'ОТКАЗ: строка области найдена %d раз\n' % text.count(root))
        raise SystemExit(2)
    text = text.replace(root, u"          '.claude/**/*.md', '.claude/work/*.json',\n")
    ledger = u"'.claude/work/code-gate-ledger.json'"
    for name in (u"'отмена родителя переводит встроенную защиту в терминал'",
                 u"'в биржевом эхе тип триггера не наблюдается'"):
        marker = u"        'name': " + name + u",\n"
        if text.count(marker) != 1:
            sys.stderr.write(u'ОТКАЗ: запись %s найдена %d раз\n' % (name, text.count(marker)))
            raise SystemExit(2)
        start = text.index(marker)
        allowed_at = text.index(u"        'allowed': (", start)
        end = text.index(u"),\n", allowed_at)
        head = text[allowed_at:end]
        if ledger in head:
            continue
        text = text[:end] + u" " + ledger + u"," + text[end:]
    if not dry:
        io.open(REGISTRY, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'%s: %s — область и два allowed' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', REGISTRY))


DRY = '--dry' in sys.argv
apply_prose(DRY)
apply_spec(DRY)
apply_registry(DRY)
```

## Что верификация подтвердила и что осталось названным

**Регресс подтверждён на каждом заходе:** десять команд гейта корпуса
возвращают 0 после приземления, включая мутационный замер (468 величин, 0
переживающих нейтрализацию) и вывод перечней четырёх новых популяций.

**Названный остаток — один, и он не корпусной.** Верификация отдельно
указала: чистота гейта здесь ничего не удостоверяет по существу прозаических
клеймов — прогон их по построению не мерит. Отсюда две конвенции, годные
следующим узлам, обе записаны выше по тексту: переименование обязано
получать **запись реестра** тем же ходом, а термин, вводимый закрытием,
проверяется на занятость у дома-владельца соседней сущности.

### Скрипт доработки захода 4 — субтрактивный

Верификация захода 3 нашла семь дефектов, шесть корпусных, и назвала
механизм прямо: «правка снимает предъявленный экземпляр и переносит рядом с
ним непроверенное утверждение». Ряд долей корпусных дефектов по заходам —
10/12, 7/12, 6/7, 6/7 — не убывал, и убывать он не мог, пока каждая доработка
добавляла текст.

**Отсюда род доработки захода 4: субтрактивный.** Снимается текст, а не
заменяется другим текстом:

- счётное клеймо о **собственном измерении** в шапке инструмента — целиком
  (оно было ложно: попаданий девять, печатаемых дефектов пять, а объявлено
  «четыре/три»);
- обещание «число отвечает на «сколько раз в контур вмешались руками»» — оно
  шире считаемого: постановка журналируется с дедупом по стоящему состоянию,
  снятие — без;
- безусловная посылка «тогда как строка отчёта заводится» — она верна не на
  всех тропах;
- клейм «снятие возвращает контур в работу» — полное снятие ведёт объект в
  `HOLD`, а не в торговлю;
- **объяснение выбора имени** разреза: имя взято буквальным («по тропе»,
  потому что различитель — код тропы), и объяснять больше нечего. Заодно
  снята коллизия со словом «происхождение», занятым в том же файле
  §«Имя суммы называет её состав, а не происхождение».

Прибавлены при этом две вещи, обе механические: область свипа `tools/**`
подтянута к объявленной (`tools/*.py` не видел `tools/derive/`, куда узел
положил новый вывод перечней) и переставлен шаблон пришедшей редакции у
записи реестра — вслед за переименованием.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по верификации захода 3 — СУБТРАКТИВНАЯ.

Род доработки выбран по механизму, который петля предъявила трижды: всякое
новое утверждение, добавленное доработкой, становится новой поверхностью для
дефекта. Поэтому здесь снимается текст, а не заменяется на другой текст:
счётные клеймы о собственном измерении, обещания популяции шире считаемой,
безусловные посылки и объяснение выбора имени, которое перестаёт быть нужным,
когда имя буквально.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import json
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC = 'docs/spec/statistics-aggregates.json'
AUDITABLE = 'docs/models/domain/other/Auditable.md'
BACKLOG = '.claude/work/backlog.md'
CHRONICLE = '.claude/work/progress/phase-2-step-10-chronicle.md'
REGISTRY = 'tools/retired-check.py'

# ---- В2-1: счётное клеймо о собственном измерении снимается целиком
edit(REGISTRY, u"""библиотеки и отчётов прогонов (`progress/` цитирует снятые редакции как
находки — это их предмет, а не рецидив). РАБОЧИЕ РЕЕСТРЫ `.claude/work/*.json`
в области ЕСТЬ, и это измерено, а не предположено: расширение области дало
четыре попадания — три цитаты закрытых находок в полях «суть» и «чем» и один
ЖИВОЙ клейм в поле «примечание». Живой снят, три цитаты объявлены разрешённым
местом у своих записей (ключ `allowed`) — так же, как объявляется дом решения.
Исключить реестр целиком значило бы вывести из-под свипа носитель, который
гейтит `CODE` и адресован исполнителю следующего под-шага.""",
     u"""библиотеки и отчётов прогонов (`progress/` цитирует снятые редакции как
находки — это их предмет, а не рецидив). РАБОЧИЕ РЕЕСТРЫ `.claude/work/*.json`
в области ЕСТЬ: реестр компонентов и цитирует снятые редакции как закрытые
находки, и несёт живые клеймы. Первое объявляется у своих записей ключом
`allowed` — так же, как объявляется дом решения; второе свипается. Исключить
реестр целиком значило бы вывести из-под свипа носитель, который гейтит
`CODE` и адресован исполнителю следующего под-шага.""")

# ---- В2-5: область tools объявлена шире фактической — фактику подтягиваем
edit(REGISTRY, u"          'tools/*.py', 'tools/*.sh', 'tools/*.txt')",
     u"          'tools/**/*.py', 'tools/**/*.sh', 'tools/**/*.txt')")

# ---- В2-6: имя разреза берётся буквальным, а объяснение выбора СНИМАЕТСЯ
edit(RULE, u"""**Разрез зовётся по происхождению и различает его КОДОМ тропы, а не
актором строки.** Имя выбрано так, потому что слово «природа» у отчёта о
происшествии занято своей осью (`docs/models/domain/other/AnomalyReport.md`),
и второго смысла ему не даётся. Различитель — код: актор отвечает на вопрос
«кем порождён ход», и у джобы, запущенной ручным триггером, он равен
принципалу — контекст обязан переживать смену треда
(`docs/models/domain/other/Auditable.md`). По актору автоматическая
остановка, найденная в ручном прогоне детекции, попала бы в число ручных, то
есть разрез давал бы ровно ту ошибку, против которой заведён.""",
     u"""**Разрез зовётся по тропе и различает её КОДОМ, а не актором строки.**
Актор отвечает на вопрос «кем порождён ход», и у джобы, запущенной ручным
триггером, он равен принципалу — контекст обязан переживать смену треда
(`docs/models/domain/other/Auditable.md`). По актору автоматическая
остановка, найденная в ручном прогоне детекции, попала бы в число ручных, то
есть разрез давал бы ровно ту ошибку, против которой заведён.""")
edit(RULE, u"| `anomalyReports` | отчётов о происшествиях — **оба происхождения вместе** |",
     u"| `anomalyReports` | отчётов о происшествиях — **обе тропы вместе** |")
edit(RULE, u"Без разреза по происхождению одно число складывает",
     u"Без разреза по тропе одно число складывает")
edit(RULE, u"которой разрез по происхождению защищает у подъёма ступени.",
     u"которой разрез по тропе защищает у подъёма ступени.")

# ---- В2-4: «снятие возвращает контур в работу» неверно (полное снятие ведёт
# в HOLD, инструментное — в онбординговый HOLD). Клейм снимается, остаётся
# то, что верно и достаточно: снятие есть действие, обратное постановке.
edit(RULE, u"""Снятие возвращает контур в работу — состояние, происшествию
противоположное; сложенное с происшествиями автоматики, оно выдаёт
вмешательство за отказ гипотезы, то есть даёт ровно ту ошибку, от
которой разрез по тропе защищает у подъёма ступени.""",
     u"""Снятие — действие, обратное постановке; сложенное с происшествиями
автоматики, оно выдаёт ручную операцию за находку детектора, то есть даёт
ровно ту ошибку, от которой разрез по тропе защищает у подъёма ступени.""")

# ---- В2-2, В2-3: нота и правило обещали популяцию шире считаемой и несли
# безусловную посылку. Обещание снимается, посылка — тоже.
edit(RULE, u"""Направление операции третьим разрезом не разводится, и это решение, а не
пропуск: число отвечает на «сколько раз в контур вмешались руками», а обе
операции — вмешательства. Какая именно и когда — вопрос к журналу, где коды
двух операций различны (`docs/rules/manual-halt.md`); суточное число на него
не отвечает и не обязано. Вычесть постановку из этого числа, кстати, нельзя:
`manuallyRaisedHolds` считает **класс подъёма ступени**, а он на поглощённом
запросе не производится, тогда как строка отчёта заводится.""",
     u"""Направление операции третьим разрезом не разводится, и это решение, а не
пропуск: какая именно операция и когда — вопрос к журналу, где коды двух
операций различны (`docs/rules/manual-halt.md`), и суточное число на него не
отвечает. Вычесть постановку из этого числа при этом нельзя:
`manuallyRaisedHolds` считает **класс подъёма ступени**, а его условия
производства — свои (`docs/components/HoldService.md`).""")

# ---- В2-4: «возобновление торговли» переписывается на то, что верно
edit(AUDITABLE, u"""производителя нет: журнал фиксирует возобновление торговли строкой отчёта о
происшествии с кодом ручной тропы (`docs/rules/manual-halt.md`) — и не
фиксирует его **своим классом**, то есть отвечает на «счёт вернули в
работу», но не на «ступень снята».""",
     u"""производителя нет: ручное снятие ступени журнал фиксирует строкой отчёта о
происшествии с кодом ручной тропы (`docs/rules/manual-halt.md`) — и не
фиксирует его **своим классом**, поэтому счётчики, отбирающие по классу, его
не видят.""")
edit(BACKLOG, u"""(`docs/models/domain/other/AuditRecord.md`): возобновление торговли журнал
фиксирует строкой отчёта о происшествии с кодом ручной тропы, а **своим
классом** — нет, то есть на вопрос «снята ли ступень» история отвечает
косвенно.""",
     u"""(`docs/models/domain/other/AuditRecord.md`): ручное снятие ступени журнал
фиксирует строкой отчёта о происшествии с кодом ручной тропы, а **своим
классом** — нет, поэтому счётчики, отбирающие по классу, его не видят.""")

# ---- В2-6: дельта CODE несёт то же имя разреза
edit(CHRONICLE, u"**Происхождение различает КОД тропы, а не актор строки**",
     u"**Тропу различает КОД, а не актор строки**")
edit(CHRONICLE, u"два разреза происшествия и смена операнда происхождения — `GAPS_CLOSE_3`, "
                u"узел 3 (B5) |",
     u"два разреза происшествия и смена операнда тропы — `GAPS_CLOSE_3`, узел 3 (B5) |")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:120].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


def apply_spec(dry):
    spec = json.load(io.open(SPEC, encoding='utf-8'))
    values = {v['name']: v for v in spec['values']}
    values['manuallyRaisedHolds']['note'] = (
        'Разрез по тропе. «Система остановила себя» — сигнал, что гипотеза не работает; '
        '«остановили её снаружи» — вмешательство в контур. Одно число на оба состояния есть '
        'ложный операнд по определению: вмешательства считались бы признаком отказа гипотезы. '
        'Различитель — КОД тропы, а не актор строки: актор отвечает на вопрос «кем порождён '
        'ход», и у джобы, запущенной ручным триггером, он равен принципалу '
        '(docs/models/domain/other/Auditable.md), то есть автоматическая остановка, найденная в '
        'ручном прогоне детекции, попала бы в это число. Отсюда и популяция: счётчик мерит '
        'ТРОПУ, а не человека — код одинаков у человека и у скрипта, до поверхности дошедшего.')
    values['anomalyReports']['note'] = (
        'Счётчик происшествий суток: отчётов о происшествиях, обе тропы вместе. Само по себе '
        'это число ни на какой вопрос не отвечает и потому идёт в паре с разрезами ниже — как и '
        'raisedHolds.')
    values['manualOperationReports']['note'] = (
        'Разрез по тропе. Отчёт заводит не только детекция: ручная поверхность заводит его и на '
        'постановке ступени, и на СНЯТИИ (docs/rules/manual-halt.md). Снятие — действие, '
        'обратное постановке, и сложенное с происшествиями автоматики оно выдаёт ручную '
        'операцию за находку детектора. Довод и операнд те же, что у manuallyRaisedHolds, '
        'включая выбор кода вместо актора: ручной прогон джобы детекции даёт актором '
        'принципала, и по актору находки автоматики считались бы ручными. Популяция счётчика — '
        'строки отчёта, заведённые ручной тропой, обе операции вместе; направление третьим '
        'разрезом не разводится, потому что «какая именно операция» есть вопрос к журналу, где '
        'коды двух операций различны.')
    for example in spec['examples']:
        if example['case'].startswith('счётчики происшествий суток: оба происхождения'):
            example['case'] = ('счётчики происшествий суток: обе тропы подъёма ступени, обе '
                               'ступени и обе тропы отчёта о происшествии')
    if not dry:
        io.open(SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — три ноты и имя примера' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', SPEC))


def apply_registry_arrived(dry):
    text = io.open(REGISTRY, encoding='utf-8').read()
    before = r"        'arrived': r'разрез\s+по\s+происхождению|по\s+происхождению',"
    if text.count(before) != 1:
        sys.stderr.write(u'ОТКАЗ: шаблон пришедшей редакции найден %d раз\n' % text.count(before))
        raise SystemExit(2)
    text = text.replace(before, r"        'arrived': r'разрез\s+по\s+тропе',")
    if not dry:
        io.open(REGISTRY, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'%s: %s — шаблон пришедшей редакции'
          % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', REGISTRY))


DRY = '--dry' in sys.argv
apply_prose(DRY)
apply_spec(DRY)
apply_registry_arrived(DRY)
```

### Скрипт доработки захода 5

Верификация захода 4 нашла восемь дефектов, все корпусные, и разложила их
честно: три внесены самим заходом 4, пять — непройденные части того же стыка
и той же оси. Заход 5 закрывает обе группы; род прежний — субтрактивный, с
одним прибавлением, которое **снимает** текст.

**Прибавление — счётчик `breakdownNotAssessedDeals`, и он убирает вторую
доктрину.** Ось полноты разбивки была единственной, где значение получало
диспозицию «поглощено более сильным счётчиком», а значит: (1) диспозиций
оказывалось четыре при объявленных трёх, (2) здоровое значение `COMPLETE` не
выводилось остатком, потому что числа `NOT_ASSESSED` не хранило ничто.
Колонка выравнивает ось с тремя остальными, и абзац доктрины уходит целиком —
остаётся охранный инвариант, у которого теперь одна работа: связь двух
признаков, счётчиком не выражаемая.

Прочее снято: указатель, ведший в дом без предмета; потерянный квалификатор
«подъёма» у клейма о счётчиках; инструкция дельты `CODE`, пересказывавшая
критерий дома шире, чем он есть. Приведены к фактике два носителя того же
стыка, которых свипы прошлых заходов не достали: компонент-док писателя
ступени (`docs/components/HoldService.md` утверждал тропу, которой в коде
нет) и имя примера исполнимой формы `event-actor-presence.json`. Перечень
разрешённых мест в шапке `retired-check` дополнен полем рабочего реестра —
он был закрытым и не назвал места, которое эта же правка ввела.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по верификации захода 4.

Род тот же — субтрактивный, с одним исключением: у оси полноты разбивки
заводится недостающий счётчик, и ровно этим СНИМАЕТСЯ вторая доктрина
(«популяция поглощена более сильным счётчиком»), из-за которой ось выпадала
из объявленных трёх диспозиций и лишала здоровое значение выводимости.
Прибавление одной колонки убирает абзац доктрины и оговорку в ноте: текста
становится меньше, а конструкция — однородной на всех четырёх осях.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import json
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC = 'docs/spec/statistics-aggregates.json'
ACTOR_SPEC = 'docs/spec/event-actor-presence.json'
HOLD_SERVICE = 'docs/components/HoldService.md'
AUDITABLE = 'docs/models/domain/other/Auditable.md'
BACKLOG = '.claude/work/backlog.md'
CHRONICLE = '.claude/work/progress/phase-2-step-10-chronicle.md'
REGISTRY = 'tools/retired-check.py'

# ---- В3-1: ось полноты разбивки выравнивается с тремя остальными
edit(RULE, u"""| `breakdownIncompleteDeals` | из принявших риск — с неполной разбивкой движений |""",
     u"""| `breakdownIncompleteDeals` | из принявших риск — с неполной разбивкой движений |
| `breakdownNotAssessedDeals` | из принявших риск — с **неоценённой** полнотой разбивки |""")
edit(RULE, u"""**Значение, чья популяция уже названа другим счётчиком, своего не получает,
а импликация становится инвариантом.** «Полнота разбивки не оценивалась»
означает, что добыча движений не выполнялась, — а тогда итога у сделки нет
вовсе, и она уже посчитана `resultUnavailableDeals`. Второй счётчик той же
популяции был бы вторым носителем; вместо него в исполнимой форме стои́т
охранный инвариант с контрпримером, потому что импликация, на которой
диспозиция держится, обязана быть проверяемой, а не прочитанной.

""",
     u"""**Импликация, связывающая два признака, охраняется инвариантом, а не
прозой.** «Полнота разбивки не оценивалась» означает, что добыча движений не
выполнялась, — а тогда итога у сделки нет вовсе. Связь двух признаков живёт
в исполнимой форме охранным инвариантом с контрпримером: сама она счётчиком
не выражается, а нарушенная — молча испортила бы обе оси.

""")
edit(RULE, u"`risk_unsized_deals`, `liquidated_deals`, `forced_reduction_deals`, "
           u"`outcome_undetermined_deals`, `reconciliation_mismatched_deals`, "
           u"`reconciliation_not_run_deals`, `breakdown_incomplete_deals`, "
           u"`risk_benchmark_missing_deals`, `r_denominator_deals` | `integer` | `not null` |",
     u"`risk_unsized_deals`, `liquidated_deals`, `forced_reduction_deals`, "
     u"`outcome_undetermined_deals`, `reconciliation_mismatched_deals`, "
     u"`reconciliation_not_run_deals`, `breakdown_incomplete_deals`, "
     u"`breakdown_not_assessed_deals`, `risk_benchmark_missing_deals`, "
     u"`r_denominator_deals` | `integer` | `not null` |")

# ---- В3-3: указатель вёл в дом, где предмета нет; оговорка снимается целиком
edit(RULE, u""" Вычесть постановку из этого числа при этом нельзя:
`manuallyRaisedHolds` считает **класс подъёма ступени**, а его условия
производства — свои (`docs/components/HoldService.md`).""", u"")

# ---- В3-4: у клейма потерян квалификатор «подъёма»
edit(AUDITABLE, u"фиксирует его **своим классом**, поэтому счётчики, отбирающие по классу, его\nне видят.",
     u"фиксирует его **классом снятия ступени**, поэтому счётчики, отбирающие по\nэтому классу, его не видят.")
edit(BACKLOG, u"классом** — нет, поэтому счётчики, отбирающие по классу, его не видят.",
     u"классом** — нет, поэтому счётчики, отбирающие по классу снятия, его не\nвидят.")

# ---- В3-2: компонент-док писателя утверждает тропу, которой в коде нет
edit(HOLD_SERVICE, u"""**Ручная постановка — та же тропа, только решение принимает человек.**
Отдельного пути у неё нет: она собирает сигнал фабрикой и заходит в эту
же идемпотентную точку входа, наследуя анкер, согласование эскалации и
состав реакции; собственного механизма остановки в системе не
появляется.""",
     u"""**Ручная постановка объявлена той же тропой, только решение принимает
человек.** Отдельного пути ей правилом не заводится: она собирает сигнал
фабрикой и обязана заходить в эту же идемпотентную точку входа, наследуя
анкер, согласование эскалации и состав реакции; собственного механизма
остановки в системе не появляется. **Сегодня ручная поверхность сюда не
заходит**, и это названное ограничение её дома
(`docs/rules/manual-halt.md`), а не свойство этого компонента.""")

# ---- В3-5: инструкция дельты CODE была шире правила-дома
edit(CHRONICLE,
     u"**Счётчик у каждого значения признака отбора, достижимого в популяции счёта** — сверх "
     u"ликвидации это принудительное сокращение, неустановленный исход и непроверенная "
     u"сверка; перечень величин и их формы — `docs/spec/statistics-aggregates.json`, состав "
     u"колонок — §Персистентность дома агрегатов, и здесь они не пересказываются",
     u"**Счётчики значений признаков отбора** — критерий, по которому значение его получает, "
     u"живёт в доме (`docs/rules/statistics-aggregates.md`) и здесь не пересказывается; "
     u"перечень величин и их формы — `docs/spec/statistics-aggregates.json`, состав колонок — "
     u"§Персистентность того же дома")

# ---- В3-8: перечень разрешённых мест в шапке инструмента был закрыт и неполон
edit(REGISTRY, u"""ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Только носители пайплайна
(`.claude/decisions/`, дом процесса, исходник инструмента, этот файл): там
снятая редакция называется затем, чтобы сказать, чем она заменена.""",
     u"""ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Только носители пайплайна
(`.claude/decisions/`, дом процесса, исходник инструмента, этот файл, а также
поля рабочего реестра, где снятая редакция названа как ЗАКРЫТАЯ находка
захода): там снятая редакция называется затем, чтобы сказать, чем она
заменена.""")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:120].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


def apply_spec(dry):
    spec = json.load(io.open(SPEC, encoding='utf-8'))
    values = spec['values']
    names = [v['name'] for v in values]
    if 'countsAsBreakdownNotAssessed' in names:
        sys.stderr.write(u'ОТКАЗ: правка спеки уже применена\n')
        raise SystemExit(2)
    at = names.index('countsAsBreakdownIncomplete') + 1
    values[at:at] = [{
        'name': 'countsAsBreakdownNotAssessed',
        'note': 'Полнота разбивки не оценивалась: добыча движений не выполнялась — единственный '
                'триггер значения (docs/models/domain/aggregate/Deal.md). Счётчик свой, как у '
                'каждого нездорового значения признака отбора: без него число здорового '
                'значения перестаёт быть остатком, а состояние «не проверяли» складывается с '
                '«проверили, полна».',
        'expr': "countsInShares && breakdownIncomplete == 'NOT_ASSESSED'",
    }]
    names = [v['name'] for v in values]
    at = names.index('breakdownIncompleteDeals') + 1
    values[at:at] = [{
        'name': 'breakdownNotAssessedDeals',
        'over': 'deals',
        'op': 'count',
        'where': 'countsAsBreakdownNotAssessed',
        'note': 'Сколько принявших риск сделок суток закрылось с неоценённой полнотой разбивки. '
                'Популяция — принявшие риск, а не вошедшие в денежные суммы: признак достижим и '
                'у сделки с недоступным результатом, а такая в суммы не входит.',
    }]
    for value in values:
        if value['name'] == 'unassessedBreakdownHasNoResult':
            value['note'] = (
                'ОХРАННЫЙ ИНВАРИАНТ признака полноты разбивки: у сделки, чья полнота не '
                'оценивалась, итога нет. Единственный триггер значения NOT_ASSESSED — добыча '
                'движений не выполнялась (docs/models/domain/aggregate/Deal.md); тогда ложен '
                'первый конъюнкт полноты разбивки (docs/spec/deal-context-load.json), а с ним и '
                'доступность итога (docs/spec/deal-result.json). Инвариант связывает две оси '
                'отбора: нарушенный, он молча испортил бы обе — сам по себе счётчиком он не '
                'выражается.')
    for population in spec['populations']:
        if population.get('keys') == ['breakdownIncomplete']:
            population['rule'] = ['countsAsBreakdownIncomplete', 'countsAsBreakdownNotAssessed',
                                  'unassessedBreakdownHasNoResult']
    for example in spec['examples']:
        case = example['case']
        if case.startswith('добыча движений не выполнялась'):
            example['expect']['countsAsBreakdownNotAssessed'] = True
        elif case.startswith('контрпример: полнота разбивки не оценивалась'):
            example['expect']['countsAsBreakdownNotAssessed'] = True
        elif case.startswith('сутки с ликвидацией'):
            example['expect']['breakdownNotAssessedDeals'] = 0
        elif case.startswith('сутки: принудительное сокращение'):
            example['state']['deals'].append({
                'tookRisk': True, 'priceResult': None, 'resultProfit': None, 'fee': 0,
                'funding': 0, 'liqPenalty': 0, 'plannedRisk': 10, 'currency': 'USDT',
                'closeOutcome': 'NORMAL_EXIT', 'reconciliationStatus': 'NOT_RUN',
                'breakdownIncomplete': 'NOT_ASSESSED', 'riskBenchmarkAvailability': 'AVAILABLE',
            })
            example['expect'].update({
                'closedDeals': 4,
                'riskBearingDeals': 4,
                'resultUnavailableDeals': 1,
                'reconciliationNotRunDeals': 2,
                'breakdownNotAssessedDeals': 1,
                'plannedRiskExcludedSum': 10,
            })
    if not dry:
        io.open(SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — величина, счётчик, правило популяции и четыре примера'
          % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', SPEC))


def apply_actor_spec(dry):
    # В3-6: исполнимая форма держала клейм, снятый узлом из прозы
    spec = json.load(io.open(ACTOR_SPEC, encoding='utf-8'))
    changed = 0
    for example in spec['examples']:
        if example['state'].get('eventClass') == 'HoldRaised':
            example['case'] = (
                'HoldRaised — класс объявлен классом с ручной тропой: держатель ставит ту же '
                'ступень тем же механизмом (docs/rules/manual-halt.md), поэтому актор едет '
                'содержимым. Что ручная тропа сегодня до писателя класса не доходит — названное '
                'ограничение того же дома, и оно снимается дельтой CODE шага 10 фазы 2')
            changed += 1
    if changed != 1:
        sys.stderr.write(u'ОТКАЗ: пример HoldRaised найден %d раз\n' % changed)
        raise SystemExit(2)
    if not dry:
        io.open(ACTOR_SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — имя примера HoldRaised' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', ACTOR_SPEC))


DRY = '--dry' in sys.argv
apply_prose(DRY)
apply_spec(DRY)
apply_actor_spec(DRY)
```

**Названный остаток — один, и он не ложное утверждение.** Ручное-против-
автоматического у дома ручной остановки зовётся «происхождением стоящей
ступени» (`docs/rules/manual-halt.md`), а разрез счётчика узел зовёт «по
тропе». Предметы разные — стоящая ступень объекта против строки журнала, — и
переименование в чужом доме стоило бы дороже расхождения имён; расхождение
названо здесь, чтобы не переоткрываться как находка.

### Скрипт доработки захода 6

Верификация захода 5 нашла двенадцать дефектов, восемь корпусных. Три из них
внесены заходом 5 (критерий «свой счётчик» и лид раздела не согласованы с
заведённым счётчиком; нота инварианта назвала доступность итога признаком
отбора), остальные — непройденные места того же класса: клейм
`HoldService.md` в таблице «Кто зовёт» и в замыкающей фразе, закрытый
перечень разрешённых мест в шапке `retired-check`, два адресных перекрёстья,
внесённые в `docs/**` вопреки `docs/concept.md` §Ссылки, и счётные клеймы
дельты `CODE`, которые узел за свой ход правил дважды.

Все восемь закрыты. Снятием текста закрыты счётные клеймы дельты (в пользу
указателя на дом), закрытый перечень разрешённых мест и оба адресных
перекрёстья; переписыванием — критерий «свой счётчик у всякого нездорового
значения» (он и есть построенное), лид раздела и строка таблицы вызывающих
`HoldService`.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по верификации захода 5.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import json
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC = 'docs/spec/statistics-aggregates.json'
MANUAL = 'docs/rules/manual-halt.md'
HOLD_SERVICE = 'docs/components/HoldService.md'
CHRONICLE = '.claude/work/progress/phase-2-step-10-chronicle.md'
REGISTRY = 'tools/retired-check.py'
DIGEST = '.claude/work/decision-digest.md'

# ---- В4-1: критерий «свой счётчик» был у́же, чем построенное по нему
edit(RULE, u"""диспозиция у каждого значения ровно одна: **свой
счётчик** — когда значение достижимо у
сделки, вошедшей в денежные суммы, и там неотличимо от штатного;
**выводимый остаток** — у здорового значения, чьё число есть популяция счёта
за вычетом прочих (выводимое вторым носителем не заводится, `docs/concept.md`
П4); **объявленная недостижимость** — когда значение несовместимо с принятием
риска.""",
     u"""диспозиция у каждого значения ровно одна: **свой
счётчик** — у всякого нездорового значения, потому что без него его число не
хранит ничто, а «не проверяли» складывается с «проверили, всё в порядке»;
**выводимый остаток** — у здорового значения, чьё число есть популяция счёта
за вычетом прочих (выводимое вторым носителем не заводится, `docs/concept.md`
П4); **объявленная недостижимость** — когда значение несовместимо с принятием
риска.""")

# ---- В4-2: лид раздела верен не для всех признаков
edit(RULE, u"""**Признак ненадёжности — тоже счётчик, и популяция при этом остаётся в
суммах.** Ненадёжность — не отсутствие: число есть, но опереться на него
как на проверенное нельзя. Такая сделка из сумм не выводится (иначе
суммы перестали бы покрывать закрытые сделки), но её ненадёжность видна
счётчиком: «не проверяли» обязано отличаться от «проверили, всё в порядке»
(`docs/concept.md` П1).""",
     u"""**Признак ненадёжности — тоже счётчик.** Ненадёжность — не отсутствие:
число есть, но опереться на него как на проверенное нельзя. Сам признак
сделку из сумм не выводит — выводит только недоступность результата, иначе
суммы перестали бы покрывать закрытые сделки, — а ненадёжность видна
счётчиком: «не проверяли» обязано отличаться от «проверили, всё в порядке»
(`docs/concept.md` П1).""")

# ---- В4-6: адресное перекрёстье в docs/** запрещено (docs/concept.md §Ссылки)
edit(MANUAL, u"""  (§Наблюдаемость), — но счётчики, отбирающие по классу подъёма, её не
  видят.""",
     u"""  — но счётчики, отбирающие по классу подъёма, её не видят.""")
edit(RULE, u"""чужого производителя (дом ожидания — `.claude/work/backlog.md`
§«Решение о защите в журнале и разрез активности заявок»).""",
     u"""чужого производителя (дом ожидания — секция `.claude/work/backlog.md` о
решении о защите в журнале и разрезе активности заявок).""")

# ---- В4-3: клейм о ручной тропе стои́т ещё в двух местах того же файла
edit(HOLD_SERVICE,
     u"| **ручная операция остановки** | когда так решил держатель | ступень по паре "
     u"«радиус × режим», код причины `MANUAL_HALT_REQUESTED`, отчёт "
     u"(`docs/rules/manual-halt.md`) |",
     u"| **ручная операция остановки** — объявлена правилом, сегодня сюда не заходит "
     u"(`docs/rules/manual-halt.md`) | когда так решил держатель | ступень по паре "
     u"«радиус × режим», код причины `MANUAL_HALT_REQUESTED`, отчёт |")
edit(HOLD_SERVICE, u"**Ручное снятие — вторая операция той же поверхности, и вот\nона сюда не заходит**.",
     u"**Ручное снятие — вторая операция той же поверхности, и ей заходить сюда\nне полагается вовсе**: снятие ступени — не этот путь.")

# ---- В4-11: счётные клеймы дельты CODE стареют молча — снимаются
edit(CHRONICLE, u"**состав хранимых величин сделочного зерна вырос с двенадцати до двадцати семи**",
     u"**состав хранимых величин сделочного зерна расширен**")
edit(CHRONICLE, u"| 7б | **восемь именованных счётчиков зерна происшествий**",
     u"| 7б | **именованные счётчики зерна происшествий**")

# ---- В4-4: перечень разрешённых мест был объявлен закрытым и ложен
edit(REGISTRY, u"""ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Только носители пайплайна
(`.claude/decisions/`, дом процесса, исходник инструмента, этот файл, а также
поля рабочего реестра, где снятая редакция названа как ЗАКРЫТАЯ находка
захода): там снятая редакция называется затем, чтобы сказать, чем она
заменена.""",
     u"""ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Носитель, где снятая редакция
называется затем, чтобы сказать, чем она заменена, — дом решения, дом
процесса, исходник инструмента, этот файл, поле рабочего реестра с закрытой
находкой захода, отчёт прогона. Перечня мест здесь нет и быть не может:
место называется у СВОЕЙ записи ключом `allowed`, и это объявление и есть
перечень.""")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:130].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


def apply_spec(dry):
    # В4-5: доступность итога признаком отбора корпус не зовёт
    spec = json.load(io.open(SPEC, encoding='utf-8'))
    hit = 0
    for value in spec['values']:
        if value['name'] == 'unassessedBreakdownHasNoResult':
            value['note'] = value['note'].replace(
                'Инвариант связывает две оси отбора: нарушенный, он молча испортил бы обе — '
                'сам по себе счётчиком он не выражается.',
                'Инвариант связывает признак отбора с доступностью итога: нарушенный, он молча '
                'испортил бы обе величины — сам по себе счётчиком он не выражается.')
            hit += 1
    if hit != 1:
        sys.stderr.write(u'ОТКАЗ: инвариант найден %d раз\n' % hit)
        raise SystemExit(2)
    if not dry:
        io.open(SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — нота инварианта' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', SPEC))


def apply_digest(dry):
    # В4-8: дайджест фиксировал решение, снятое заходом 5
    text = io.open(DIGEST, encoding='utf-8').read()
    pairs = [
        (u'| Д1055 | **У `NOT_ASSESSED` счётчика нет: его популяция уже названа '
         u'`resultUnavailableDeals`, а импликация переведена в охранный инвариант с '
         u'контрпримером** | свой счётчик; молчание | «полнота не оценивалась» имеет '
         u'единственный триггер — добыча движений не выполнялась, — а тогда ложна доступность '
         u'итога, и сделка уже посчитана счётчиком отсутствия. Второй счётчик той же популяции '
         u'был бы вторым носителем; молчание оставило бы диспозицию непроверяемой. Инвариант '
         u'делает импликацию, на которой диспозиция держится, падающей при нарушении |',
         u'| Д1055 | **У `NOT_ASSESSED` свой счётчик, как у всякого нездорового значения, а '
         u'импликация «полнота не оценивалась ⇒ итога нет» держится охранным инвариантом с '
         u'контрпримером** | считать популяцию поглощённой `resultUnavailableDeals` и счётчика '
         u'не заводить; молчание | поглощение было **не** равенством популяций: недоступность '
         u'итога достижима и при полной разбивке, поэтому число `NOT_ASSESSED` не хранило бы '
         u'ничто, а здоровое `COMPLETE` переставало быть выводимым остатком — и ось выпадала из '
         u'трёх объявленных диспозиций четвёртой, не названной. Молчание оставило бы связь двух '
         u'признаков непроверяемой; инвариант делает её падающей при нарушении |'),
        (u'счётчик — если значение достижимо у сделки, вошедшей в денежные суммы; выводимый '
         u'остаток — у здорового значения; объявленная недостижимость — если значение '
         u'несовместимо с принятием риска |',
         u'счётчик — у всякого нездорового значения; выводимый остаток — у здорового; '
         u'объявленная недостижимость — если значение несовместимо с принятием риска |'),
        (u'счётчик на каждое дал бы колонку здоровому значению, число которого есть остаток, — '
         u'второй носитель выводимого (`docs/concept.md` П4). Правка только по предъявленному '
         u'оставила бы три признака из четырёх с тем же дефектом: замер показал 1/4, 1/3, 1/3, '
         u'1/3 |',
         u'счётчик на каждое дал бы колонку здоровому значению, число которого есть остаток, — '
         u'второй носитель выводимого (`docs/concept.md` П4). Правка только по предъявленному '
         u'оставила бы три признака из четырёх с тем же дефектом: замер показал 1/4, 1/3, 1/3, '
         u'1/3. Критерий «достижимо в денежных суммах» отвергнут верификацией захода 5: под '
         u'него не подходило значение, которому счётчик всё же нужен |'),
    ]
    for before, after in pairs:
        if text.count(before) != 1:
            sys.stderr.write(u'ОТКАЗ: пункт дайджеста найден %d раз\n' % text.count(before))
            sys.stderr.write(u'  ' + before[:110] + u'\n')
            raise SystemExit(2)
        text = text.replace(before, after)
    if not dry:
        io.open(DIGEST, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'%s: %s — Д1053 и Д1055' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', DIGEST))


DRY = '--dry' in sys.argv
apply_prose(DRY)
apply_spec(DRY)
apply_digest(DRY)
```

### Скрипт доработки захода 7

Верификация захода 6 дала тринадцать позиций и сама разложила их по
происхождению — и разложение это и есть исход мини-петли узла. Два корпусных
дефекта внесены заходом 6; пять — расхождения между носителями одной истины,
не сведённые прежними заходами; остальное — стык, обнажённый решением
Д1059, и системный долг с домом в бэклоге, которым в петле не место.

Заход 7 закрывает первые две группы (позиции 1-4, 6 верификации), уточняет
операнд `hasManualPath` одной клаузой (нормативен, а не фактичен — иначе
один операнд читается фактически у одного класса и нормативно у соседнего) и
заводит недостающую запись реестра под критерий, снятый заходом 6. Своя
добавка узла к слою опровержения в `docs/**` снята.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по верификации захода 6.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import json
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
SPEC = 'docs/spec/statistics-aggregates.json'
ACTOR_SPEC = 'docs/spec/event-actor-presence.json'
MANUAL = 'docs/rules/manual-halt.md'
HOLD_SERVICE = 'docs/components/HoldService.md'
REGISTRY = 'tools/retired-check.py'
DIGEST = '.claude/work/decision-digest.md'

# ---- В5-1: ветвей выхода из денежных сумм три, а клейм называл одну
edit(RULE, u"""Сам признак
сделку из сумм не выводит — выводит только недоступность результата, иначе
суммы перестали бы покрывать закрытые сделки, — а ненадёжность видна
счётчиком:""",
     u"""Сам признак
сделку из сумм не выводит — иначе суммы перестали бы покрывать закрытые
сделки, — а ненадёжность видна счётчиком:""")

# ---- В5-2: клейм об инварианте расходился в трёх носителях
edit(RULE, u"""**Импликация, связывающая два признака, охраняется инвариантом, а не
прозой.** «Полнота разбивки не оценивалась» означает, что добыча движений не
выполнялась, — а тогда итога у сделки нет вовсе. Связь двух признаков живёт
в исполнимой форме охранным инвариантом с контрпримером: сама она счётчиком
не выражается, а нарушенная — молча испортила бы обе оси.""",
     u"""**Связь признака отбора с доступностью итога охраняется инвариантом, а не
прозой.** «Полнота разбивки не оценивалась» означает, что добыча движений не
выполнялась, — а тогда итога у сделки нет вовсе. Связь живёт в исполнимой
форме охранным инвариантом с контрпримером: сама она счётчиком не
выражается, а нарушенная — молча испортила бы обе величины.""")
edit(DIGEST, u"Молчание оставило бы связь двух признаков непроверяемой; инвариант делает "
             u"её падающей при нарушении |",
     u"Молчание оставило бы связь признака отбора с доступностью итога непроверяемой; "
     u"инвариант делает её падающей при нарушении |")

# ---- В5-7: узел добавил к слою опровержения в docs/** — своя добавка снимается
edit(RULE, u""" До этого
отнесение не мерил никто — и у каждого признака счётчик стоял ровно на одном
его значении, то есть остальные читались как штатный выход.""", u"")

# ---- В5-3: перечень родов снимается целиком, объявление остаётся одно
edit(REGISTRY, u"""ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Носитель, где снятая редакция
называется затем, чтобы сказать, чем она заменена, — дом решения, дом
процесса, исходник инструмента, этот файл, поле рабочего реестра с закрытой
находкой захода, отчёт прогона. Перечня мест здесь нет и быть не может:
место называется у СВОЕЙ записи ключом `allowed`, и это объявление и есть
перечень.""",
     u"""ЧТО МОЖЕТ ПОПАСТЬ В РАЗРЕШЁННЫЕ МЕСТА. Носитель, где снятая редакция
называется затем, чтобы сказать, чем она заменена. Перечня родов здесь нет:
место называется у СВОЕЙ записи ключом `allowed`, и это объявление и есть
перечень — родовой список стареет молча, а ключ проверяется прогоном.""")

# ---- В5-4: третье вхождение клейма о ручной постановке
edit(HOLD_SERVICE, u"""- Снятие холда — ручная сервисная операция, не этот путь; **ручная
  постановка — этот**. Обе операции описаны в
  `docs/rules/manual-halt.md`: снятие идёт своей тропой, пишущей статус
  самостоятельно,""",
     u"""- Снятие холда — ручная сервисная операция, и этим путём не идёт; **ручной
  постановке идти им предписано**, а сегодня она им не идёт. Обе операции
  описаны в `docs/rules/manual-halt.md`: обе идут своей тропой, пишущей
  статус самостоятельно,""")

# ---- В5-6: носитель актора в таймлайне назван одним классом, а их два
edit(MANUAL, u"""**таймлайне** — поле содержимого события `HoldRaised`: журнал аудита
собственных audit-полей записи не имеет, он хранит строку события, и
ответить «кто остановил торговлю» может только содержимое
(`docs/spec/event-actor-presence.json`).""",
     u"""**таймлайне** — поле содержимого события: журнал аудита собственных
audit-полей записи не имеет, он хранит строку события, и ответить «кто
остановил торговлю» может только содержимое. У каких классов актор едет
содержимым, выводится прогоном (`docs/spec/event-actor-presence.json`);
перечня здесь нет.""")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:130].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


def apply_actor_operand(dry):
    # В5-5: операнд применялся по двум критериям — нормативный называется явно
    spec = json.load(io.open(ACTOR_SPEC, encoding='utf-8'))
    before = spec['operands']['hasManualPath']
    if 'НОРМАТИВЕН' in before:
        sys.stderr.write(u'ОТКАЗ: операнд уже уточнён\n')
        raise SystemExit(2)
    spec['operands']['hasManualPath'] = before + (
        '. Операнд НОРМАТИВЕН: он мерит, объявлена ли у класса ручная тропа, а не исполнена ли '
        'она сегодня — исполнение есть свойство самой тропы и называется у её дома. Иначе один '
        'операнд читался бы фактически у одного класса и нормативно у соседнего, а примеры '
        'остались бы самосогласованными и расхождения не показали')
    if not dry:
        io.open(ACTOR_SPEC, 'w', encoding='utf-8', newline='\n').write(
            json.dumps(spec, ensure_ascii=False, indent=2) + '\n')
    print(u'%s: %s — операнд hasManualPath'
          % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', ACTOR_SPEC))


def apply_registry_entry(dry):
    # В5-11: у снятого критерия не было записи реестра
    text = io.open(REGISTRY, encoding='utf-8').read()
    anchor = (u"        'population': (\n"
              u"            ('docs/spec/statistics-aggregates.json', None),\n"
              u"            ('docs/rules/statistics-aggregates.md', None),\n"
              u"        ),\n    },")
    if text.count(anchor) != 1:
        sys.stderr.write(u'ОТКАЗ: якорь реестра найден %d раз\n' % text.count(anchor))
        raise SystemExit(2)
    entry = anchor + u"""
    {
        'name': 'счётчик значению нужен, когда оно достижимо в денежных суммах',
        # Снято GAPS_CLOSE_3 шага 10 фазы 2, узел 3 (верификация захода 5).
        # Критерий исключал ровно то значение, которому счётчик всё же нужен:
        # «полнота разбивки не оценивалась» в денежные суммы недостижимо по
        # собственному охранному инварианту, а числа его не хранило бы ничто,
        # и здоровое значение той же оси переставало быть выводимым остатком.
        # Пришедшая редакция: счётчик — у всякого НЕЗДОРОВОГО значения.
        'pattern': r'достижимо\\s+у\\s+сделки,\\s+вошедшей\\s+в\\s+денежные\\s+суммы',
        'arrived': r'у\\s+всякого\\s+нездорового\\s+значения',
        'date': '2026-09-07',
        'source': 'GAPS_CLOSE_3 шага 10 фазы 2, узел 3',
        'allowed': ('.claude/work/decision-digest.md',),
        'population': (
            ('docs/rules/statistics-aggregates.md', None),
        ),
    },"""
    text = text.replace(anchor, entry)
    if not dry:
        io.open(REGISTRY, 'w', encoding='utf-8', newline='\n').write(text)
    print(u'%s: %s — запись о снятом критерии'
          % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', REGISTRY))


DRY = '--dry' in sys.argv
apply_prose(DRY)
apply_actor_operand(DRY)
apply_registry_entry(DRY)
```

## Что осталось названным, а не закрытым

**Две позиции узел не закрывает, и это решение, а не пропуск.**

| Позиция | Почему не в петле | Дом |
|---|---|---|
| Слой опровержения в `docs/**` вырос за шаг, а объявленный замер парковки устарел вдвое | системный долг корпуса, узлом не порождён; своя добавка узла снята заходом 7 | `.claude/work/backlog.md` §«Слой опровержения в продуктовом корпусе — свести»; находка C4 прогона — узел 6 |
| Счётное клеймо дельты `CODE` в позиции 6 (число индексов) | того же класса, что снятые заходом 6 в позициях 7а/7б, но введено **прежним** узлом шага, и его предмет — миграция журнала, не предмет узла 3 | дельта `CODE` хроники; ближайший ход, правящий позицию 6 |

### Скрипт доработки захода 8

Верификация захода 7 дала один существенный корпусный дефект (правка §Границы
`HoldService.md` приписала обеим ручным операциям собственную идемпотентность,
тогда как дом отдаёт её только снятию, а постановка её **наследует**), три
мелких — разорванный вставкой аргумент, длинная строка, счётное клеймо в
собственной дельте узла — и две поправки к рационалу: премиса о позиции 16
была ложна (клейм ввёл этот узел, а не прежний), а второй названный остаток
оказался фантомным и припаркованным в запрещённом доме — рабочем файле шага.

Заход 8 закрывает все шесть. Отдельно адресован живой носитель кода:
javadoc `ManualHaltService` и `HoldService` несут ровно тот клейм, который
узел свёл по пяти доковым носителям, — и ход, делающий его истинным, уже
объявлен дельтой `CODE` (позиция 16б), поэтому починка javadoc'ов названа
там же, а не заведена отдельным носителем.

```python
# -*- coding: utf-8 -*-
"""Доработка узла 3 GAPS_CLOSE_3 по верификации захода 7.

Файл временный: удаляется до git add (ловушка 3 §Среда снапшота v155).
"""
import io
import sys

EDITS = []


def edit(path, before, after):
    EDITS.append((path, before, after))


RULE = 'docs/rules/statistics-aggregates.md'
HOLD_SERVICE = 'docs/components/HoldService.md'
CHRONICLE = '.claude/work/progress/phase-2-step-10-chronicle.md'
REPORT = '.claude/work/progress/phase-2-step-10-gaps-close-3-node-3.md'

# ---- В6-1: заход 7 приписал обеим операциям собственную идемпотентность,
# тогда как дом отдаёт её только снятию, а постановка её НАСЛЕДУЕТ.
edit(HOLD_SERVICE, u"""- Снятие холда — ручная сервисная операция, и этим путём не идёт; **ручной
  постановке идти им предписано**, а сегодня она им не идёт. Обе операции
  описаны в `docs/rules/manual-halt.md`: обе идут своей тропой, пишущей
  статус самостоятельно,""",
     u"""- Снятие холда — ручная сервисная операция, и этим путём не идёт; **ручной
  постановке идти им предписано**, а сегодня она им не идёт. Обе операции
  описаны в `docs/rules/manual-halt.md`: снятие идёт своей тропой, пишущей
  статус самостоятельно,""")

# ---- В6-7: посылка и её следствие были разорваны вставкой
edit(RULE, u"""и на постановке ступени, и на **снятии** (`docs/rules/manual-halt.md`).
Направление операции третьим разрезом не разводится, и это решение, а не
пропуск: какая именно операция и когда — вопрос к журналу, где коды двух
операций различны (`docs/rules/manual-halt.md`), и суточное число на него не
отвечает.
Снятие — действие, обратное постановке; сложенное с происшествиями
автоматики, оно выдаёт ручную операцию за находку детектора, то есть даёт
ровно ту ошибку, от которой разрез по тропе защищает у подъёма ступени. Второй разрез — по
критичности: «kill-switch гонялся» и «не гонялся» в одном числе
неразличимы. Операнды обоих разрезов едут в содержимом события сегодня.""",
     u"""и на постановке ступени, и на **снятии** (`docs/rules/manual-halt.md`).
Снятие — действие, обратное постановке; сложенное с происшествиями
автоматики, оно выдаёт ручную операцию за находку детектора, то есть даёт
ровно ту ошибку, от которой разрез по тропе защищает у подъёма ступени.
Второй разрез — по критичности: «kill-switch гонялся» и «не гонялся» в одном
числе неразличимы. Операнды обоих разрезов едут в содержимом события
сегодня.

**Направление операции третьим разрезом не разводится, и это решение, а не
пропуск:** какая именно операция и когда — вопрос к журналу, где коды двух
операций различны, и суточное число на него не отвечает.""")

# ---- В6-6: строка вышла за перенос корпуса
edit(RULE, u"""сделки, — а ненадёжность видна счётчиком: «не проверяли» обязано отличаться от «проверили, всё в порядке»
(`docs/concept.md` П1).""",
     u"""сделки, — а ненадёжность видна счётчиком: «не проверяли» обязано
отличаться от «проверили, всё в порядке» (`docs/concept.md` П1).""")

# ---- В6-3: счётный клейм в собственной дельте узла
edit(CHRONICLE, u"три счётчика — `GAPS_CLOSE_3`, узел 3 (B1) |",
     u"счётчики нездоровых значений — `GAPS_CLOSE_3`, узел 3 (B1) |")

# ---- В6-4: живой носитель кода несёт клейм, который узел свёл по докам;
# ход, делающий его истинным, уже назван дельтой — там же он и адресуется.
edit(CHRONICLE, u"outbox пишет тот код, который переставляет ступень |",
     u"outbox пишет тот код, который переставляет ступень. **Тем же ходом чинятся два "
     u"javadoc'а, забежавшие вперёд кода:** `ManualHaltService` объявляет, что постановка "
     u"зовёт общего исполнителя блокировки (не зовёт ни на одной ветви), а `HoldService` — что "
     u"вызывающих у его точки входа пока нет (их три) |")

# ---- В6-2, В6-5: рационал — ложная премиса и фантомный остаток
edit(REPORT, u"""| Ручное-против-автоматического у дома ручной остановки зовётся «происхождением стоящей ступени», а разрез счётчика — «по тропе» | предметы разные (стоящая ступень объекта против строки журнала); переименование в чужом доме стоило бы дороже расхождения имён | названо здесь, чтобы не переоткрываться находкой |
| Счётные клеймы дельты `CODE` в позициях 6 и 16 (число индексов, число ручных троп) | того же класса, что снятые заходом 6 в позициях 7а/7б, но введены **прежними** узлами шага и их предмет — не предмет узла 3 | дельта `CODE` хроники; ближайший ход, правящий эти позиции |""",
     u"""| Счётное клеймо дельты `CODE` в позиции 6 (число индексов) | того же класса, что снятые заходом 6 в позициях 7а/7б, но введено **прежним** узлом шага, и его предмет — миграция журнала, не предмет узла 3 | дельта `CODE` хроники; ближайший ход, правящий позицию 6 |""")


def apply_prose(dry):
    by_path = {}
    for path, before, after in EDITS:
        by_path.setdefault(path, []).append((before, after))
    ready, refused = {}, 0
    for path, pairs in by_path.items():
        text = io.open(path, encoding='utf-8').read()
        for position, (before, after) in enumerate(pairs, 1):
            found = text.count(before)
            if found != 1:
                sys.stderr.write(u'ОТКАЗ: %s, правка %d — блок «Было» найден %d раз\n'
                                 % (path, position, found))
                sys.stderr.write(u'  ' + before[:130].replace(u'\n', u' ') + u'\n')
                refused += 1
                continue
            text = text.replace(before, after)
        ready[path] = (text, len(pairs))
    if refused:
        raise SystemExit(2)
    for path, (text, count) in ready.items():
        if not dry:
            io.open(path, 'w', encoding='utf-8', newline='\n').write(text)
        print(u'%s: %s — правок %d' % (u'СВЕРЕНО' if dry else u'ПРИМЕНЕНО', path, count))


apply_prose('--dry' in sys.argv)
```

### Заход 9 — две механические позиции верификации 7

- `docs/rules/manual-halt.md`: дословная цитата пассажа `HoldService.md`
  была переписана заходом 7 в источнике и осталась старой в цитирующем —
  приведена к нынешнему тексту источника;
- `docs/rules/statistics-aggregates.md`: перенос строки, объявленный
  заходом 8 закрытым, был сдвинут, а не сделан — абзац перевёрстан, и
  вместе с ним перевёрстаны три прозаические строки узла в
  `docs/components/HoldService.md` и `docs/rules/manual-halt.md`, вышедшие
  за обёртку корпуса (строки таблиц длиннее по построению и не трогались);
- рационал: «Три позиции» при двух строках таблицы — счёт приведён к
  фактике.

## Исход мини-петли — девять заходов, семь верификаций

| Заход | Дефектов | Корпусных | Что несущего поймано |
|---|---|---|---|
| 1 | 12 | 10 | популяция нот; операнд разреза ложен на ручном триггере джобы; свипы и артефакты не опубликованы |
| 2 | 12 | 7 | свип пропустил главный носитель; смена операнда не доведена до имён; клауза популяции — до части счётчиков |
| В1 | 7 | 6 | клеймы, которых прогон не мерит: довод отказа, посылка без условия, счёт собственного измерения |
| В2 | 8 | 8 | четвёртая диспозиция, не названная четвёртой; ось выпадала из выводимости остатка |
| В3 | 12 | 8 | критерий и лид не согласованы с заведённым счётчиком; адресные перекрёстья в `docs/**` |
| В4 | 13 | 8 | расхождение носителей одной истины; закрытый перечень, объявленный открытым |
| В5 | 8 | 2 | правка, приписавшая идемпотентность обеим операциям; разорванный аргумент |
| В6 | 8 | 2 | битая дословная цитата; невыполненный перенос строки |

**Ряд корпусных находок: 10, 7, 6, 8, 8, 8, 2, 2.** Перелом пришёлся на
заход 7 и на **разложение находок по происхождению**, которое верификация
дала сама: пока каждая доработка добавляла утверждение, доля не убывала;
как только заходы стали субтрактивными, а позиции, узлом не порождённые,
были названы остатком вместо того чтобы входить в петлю, ряд упал вчетверо.

**Три конвенции, годные следующим узлам** (сверх четырёх, введённых узлом 1):

1. **Смена операнда доводится до имён и нот тем же ходом.** Операнд отвечает
   на один вопрос, имя обещает читателю другой; расхождение между ними и есть
   тот дефект, который узел закрывает.
2. **Термин, вводимый закрытием, проверяется на занятость** — и у
   дома-владельца соседней сущности, и в собственном файле. Дешевле взять имя
   буквальным (различитель — код тропы ⇒ «разрез по тропе»), чем объяснять
   выбор небуквального.
3. **Род доработки после второго раунда — субтрактивный.** Всякое новое
   утверждение есть новая поверхность для дефекта; переписывание ложного
   клейма на другой клейм воспроизводит петлю, снятие — обрывает.
