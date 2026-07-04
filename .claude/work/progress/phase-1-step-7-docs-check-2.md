# DOCS_CHECK_2 — шаг 7 фазы 1 «Сделки и P&L»

## На какой вопрос отвечает этот файл

Каков исход второй сквозной проверки концепции (`concept-review` ×2 +
`trading-review`) под шаг 7 — что нужно доспецифицировать по выбранному пути
(positions-history + bills), прежде чем писать код расчёта `resultProfit`.

## Контекст

- **Под-шаг:** `DOCS_CHECK_2` (процесс `roadmap-step-execution.md`). Стадия 0
  (источник числа) закрыта на `GAPS_CLOSE_1` (`docs/decisions/result-profit-source.md`);
  этот прогон **descend на стадии 1-2** (процессы + модели/mapping/native).
- **Прогон:** три независимых ревьюер-субагента — concept линза-1 (стадия 1,
  механика/процессы), concept линза-2 (стадия 2, модели/mapping/native), trading
  (корректность числа/комиссий). CC верифицировал все несущие атрибуции грепом/`ls`
  (V1-V6): отсутствие refresh-команд под positions-history/bills; отсутствие
  `MARK_DEAL_EMERGENCY_CLOSED` в enum; `MarkDealErrorExecutor` пишет только `ERROR`;
  `ErrorHandler` command-set без `FINALIZE_DEAL_EXIT`; отсутствие
  `OkxPositionsHistoryResponse`/positions-history-снапшота/`DealCashFlow`; отсутствие
  fee-поля в `CalculationContext`.

## Охват

**Проверено (стадия 1):** `deal-management.md`, `lifecycles/Deal.md`,
`FinalizeDealExitExecutor`, `MarkDealClosedExecutor`, `MarkDealErrorExecutor`,
`ErrorHandler`, `ExitPendingHandler`, `RefreshFillsExecutor`, `ServiceCommandExecutor`,
`DealFinalizationCommandFactory`, `ServiceCommand`, `DealFinalizationState`,
`risk-evaluation.md`, `RiskValidator`, `SizeCalculator`, `per-trade-risk-policy.md`,
`CalculationContext`. **(стадия 2):** `Deal.md` (§Итоговый PnL/§Структура/
§Персистентность), `Position.md`+mapping, `BalanceContainer`, листинги
`domain/other/`/`mapping/`/`integrations/okx/`/`externalSnapshot/`, native
`OkxPositionResponse`/`OkxAccountBillResponse`/`OkxFillResponse`, contracts
`position.md` §История/`account-bills.md`/`trade-fee.md`/`funding-rate.md`,
`coverage-manifest.md`, `persistence-representation.md`. **Грунт trading** —
дистиллят `.claude/library/trading/distilled/` (risk-and-sizing, system-design,
strategy-patterns, microstructure).

**Вне охвата:** структура истории/timeline/audit (backlog п.6, вне шага 7);
deep-архив bills/fills (OKX-Q2).

## Стадия остановки

**Descend на стадии 1-2 выполнен; прогон НЕ чист.** Гейт стоит одновременно на
**стадии 1** (механика добычи P&L-фактов не назначена — от неё зависят
аварийная тропа и сверка) и **стадии 2** (все три носителя выбранного пути
существуют только name-level — их структуру `GAPS_CLOSE_1` сознательно отложил
на этот прогон). Плюс два независимых стадия-1 seam'а (носитель staged-числа,
поток ставки `trade-fee`) и два торговых блокера (провенанс аварийного числа,
непроверенный инвариант агрегации positions-history), всплывших при descend.

## Пробелы

Нумерация `N#`. Ссылки на находки субагентов: `F-A#` (concept-1), `F-B#`
(concept-2), `F-T#` (trading).

### Кластер A — носители выбранного пути (стадия 2, отложены GAPS_CLOSE_1)

**N1 — native `OkxPositionsHistoryResponse` не существует** [F-B1]. Тип:
name-level. **Гейтит.** Шаг маппит DTO→снапшот пофилдово (порог превышен).
`OkxPositionResponse.md` форвардит realized-поля «в `OkxPositionsHistoryResponse`
/ stage-2» — указатель на несуществующий док. Субстанция полей уже в `position.md`
§История; работа — перенос контракт→native + split used/unused + конвертация.
Used-минимум числа: `realizedPnl`, `ccy`, `closeAvgPx`/`openAvgPx`, `triggerPx`,
`type`, `posId`, `uTime`.

**N2 — positions-history-снапшот (доменный носитель ЧИСЛА) не существует** [F-B2].
Тип: name-level. **Гейтит.** `FinalizeDealExitExecutor` читает снапшот пофилдово;
это граничный объект за `IntegrationService`. Доки должны задать: (а) **имя
доменной сущности** (не зафиксировано — `Position` занят live-позицией, нужен
новый концепт под `<Entity>ExternalSnapshot`); (б) поля+nullability; (в) mapping
native→snapshot; (г) mapping snapshot→`Deal` (какое поле → `resultProfit`); (д)
транзитный не-persisted статус (как `PositionExternalSnapshot`); (е) слой пересечения
`IntegrationService`.

**N3 — `DealCashFlow` (носитель РАЗБИВКИ) — нет модель-дока** [F-B3]. Тип:
name-level. **Гейтит.** Конструируется из bills, читается пофилдово
(`sum(amount)` в сверке). Доки должны задать: гранулярность (сущность vs
value-object/раздел — `model-granularity.md`); поля+типы (кандидаты по
`OkxAccountBillResponse`: `amount`←`balChg`, `ccy`, `ts`, `billId`,
`type`/`subType`, `ordId`, категорийный enum {комиссия/funding/rebate/liqPenalty});
нужен ли lifecycle (вероятно нет — зафиксировать явно). **`ccy` обязателен**,
иначе cross-ccy-flow теряется молча [F-B6].

**N4 — mapping bills→`DealCashFlow` не специфицирован** [F-B4]. Тип: name-level.
**Гейтит.** `OkxAccountBillResponse` даёт лишь намёк (`balChg`→`amount`), не
таблицу. Доки должны задать: поле-в-поле маппинг + **где живёт резолв категории**
(`type`/`subType`→enum: funding 173/174; знак `fee` минус=комиссия/плюс=rebate;
`liqPenalty` — из positions-history, не bills — как ложится). По codestyle резолв
доменного статуса — в вызывающем коде, не в маппере: для категории cashflow не
зафиксировано.

**N5 — персистенция `DealCashFlow` + линковка к `Deal` не заданы** [F-B5]. Тип:
name-level + неотвеченный вопрос. **Гейтит.** `account-bills.md` требует
«Сохранить как DealCashFlow» ⇒ persisted, но нет таблицы/колонок/FK/кардинальности;
`Deal.md` §Персистентность о коллекции разбивки молчит (§Итоговый PnL: «в `Deal`
разбивка не хранится» → отдельный носитель, не материализован). Доки должны задать:
реляционно (`deal_cash_flows`, FK `deal_id`, строка-на-flow, индекс) vs JSONB-навес
(`persistence-representation.md`); **как связывается с `Deal`** — bills **не несут
`dealId`**, линковка через окно begin/end + `instId` + `ccy`; выход этого матчинга
как persisted `deal_id` не специфицирован. *(Не-находка рядом: `result_profit`/
`result_profit_currency` nullable — для самого ЧИСЛА достаточно.)*

### Кластер B — механика добычи и записи (стадия 1)

**N6 — механика добычи фактов positions-history и bills не назначена** [F-A1].
Тип: name-level + латентная doc↔doc несогласованность. **Гейтит (центр
тяжести).** В наборе `REFRESH_*` (CMD-Q3) нет команды под positions-history и bills
(верифицировано: таких команд в доках нет вовсе). `FinalizeDealExitExecutor` «на
биржу сам не ходит — факты приходят готовыми снапшотами», `ServiceCommandExecutor`
«финализационные опираются на уже добытые `REFRESH_*`-факты» — но **ни одна
`REFRESH_*` их не производит** (утверждение ложно для этих двух фактов).
`ExitPendingHandler` §Выходные проверки **требует** «P&L-факты готовы», а §Рабочая
логика механизма добычи **не даёт**. Доки должны задать: чем/когда добываются
(новая refresh-команда → пополнить enum/handler/evidence-cycle; **или** инструмент/
сделко-скоупный integration read вне command-layer, по образцу CMD-Q4 вар.(1);
**или** внутри `FinalizeDealExitExecutor` → снять «на биржу сам не ходит»), кто
триггерит, куда кладёт снапшот, как встаёт в проход FSM перед `FINALIZE_DEAL_EXIT`.

**N7 — носитель staged-`resultProfit` между `FINALIZE_EXIT` и `MARK_CLOSED` не
задан** [F-A5]. Тип: name-level + doc↔doc несогласованность. **Гейтит.**
`FinalizeDealExitExecutor` «стейджит на runtime graph сделки»,
`MarkDealClosedExecutor` «читает готовый результат и пишет» — две разные команды в
разных проходах FSM. Но durable-слот нигде не назван: `DealFinalizationState` его не
несёт (§Чего не хранит: «число хранится полем `Deal`»); runtime graph =
orders/algoOrders/position (слота P&L нет); поля `Deal.resultProfit` заявлены как
то, что пишет `MARK_CLOSED` на терминале. **Конфликт идемпотентности:** после
рестарта `FINALIZE_EXIT` уже `COMPLETED` → no-op, не пересчитает; `MARK_CLOSED`
число не считает → читать нечего. (В шаге 6 разрыва не было — писался
placeholder-ZERO без носителя; перенос расчёта в `FINALIZE_EXIT` его создал.)

**N8 — производство числа на `EMERGENCY_CLOSED`: владелец не назначен + провенанс-
контракт неисполним** [F-A6 + F-T1]. Тип: name-level + неотвеченный вопрос +
**торговый блокер (жёсткий гейт)**. **Гейтит.** Механика: контракт требует число на
`EMERGENCY_CLOSED` (фактический realized net, G5), но на error-тропе никто его не
считает/пишет — `ErrorHandler` command-set (верифицировано) = `MARK_DEAL_ERROR`/
`REFRESH_*`/`CANCEL_*`/`CLOSE_POSITION`, **без** `FINALIZE_DEAL_EXIT` (владельца
расчёта); в enum **нет** `MARK_DEAL_EMERGENCY_CLOSED` (только `MARK_DEAL_ERROR`→ERROR);
`MarkDealErrorExecutor` пишет `status=ERROR`, не терминал. **Торговый блокер (F-T1):**
`EMERGENCY_CLOSED` достигается двумя несводимыми провенансами — (a) реальная
ликвидация/ADL (`realizedPnl`+`liqPenalty` доступны, `type` 3-6) и (b) **отказ
расчёта после исчерпания retry** (мы там именно потому, что число НЕ достаётся).
Контракт «всё равно поставь фактический realized net» для (b) **неисполним** →
число обнуляется/пропадает ровно на failure-кейсах → **левый хвост R-распределения
усекается** [risk-and-sizing.md §12 Vince с.295-296; §9 Tharp гл.6 с.158-159]. Доки
должны: (1) назначить владельца расчёта+записи `EMERGENCY_CLOSED`-числа (аналог
`FINALIZE_EXIT`+терминальная команда на error-тропе); (2) **развести провенансы** —
best-effort net при (a), явная семантика при (b) (null-с-маркером «неисчислимо» vs
ещё-один-fetch), чтобы контракт стал исполнимым и хвост не терялся.

### Кластер C — комиссия в сайзинге (стадия 1)

**N9 — поток ставки `trade-fee` в сайзинг не задан** [F-A3]. Тип: name-level.
**Гейтит.** Формула `+ commissions` согласована, но **откуда/когда** калькулятор
берёт ставку — нигде: `per-trade-risk-policy`/`trade-fee.md` сводят к «wiring — CODE».
Это концепт-seam, не деталь CODE: ставка — биржевой факт, а оба потребителя от биржи
отрезаны (`RiskValidator` не вызывает `IntegrationService`; фабрика `CalculationContext`
тоже), и в `CalculationContext` **поля под fee-rate нет** (верифицировано). Доки
должны задать: кто читает `trade-fee` (data-service/стартовый read/навес на
инструмент), входит ли ставка в `CalculationContext` отдельным полем, её свежесть.

### Кластер D — сверка целостности (стадия 1, частью торговое)

**N10 — реакция на расхождение «сумма bills ↔ net» не описана** [F-A4 + F-T5 + F-T4].
Тип: name-level + неотвеченный вопрос. **Гейтит узко** (ветка исхода `FINALIZE_EXIT`).
«Сигнал (лог/аномалия)» — нерешённый either/or: какой уровень 4-ступенчатой градации
(`runtime-error-classification`), **блокирует ли** финализацию (→ RETRY/FAILED/error-
тропа) или число (= net positions-history) всё равно ставится и сделка идёт в `CLOSED`,
заводится ли `AnomalyReport`. Плюс не заданы **epsilon/толеранс** (округление между
эндпоинтами → шумовые ложные сигналы) [F-T5] и **cross-ccy-край** (комиссии в OKB на
аккаунте → `ccy≠USDT` отсекается фильтром → ложный mismatch/тихая дыра) [F-T4].
Достаточно зафиксировать допущение «комиссии в settle-ccy» + guard.

### Кластер E — торговая корректность числа (trading)

**N11 — инвариант агрегации partial-close на `posId` не выписан и не верифицирован**
[F-T2]. Тип: неотвеченный вопрос + торговый блокер. **Гейтит (корректность числа).**
Весь путь опирается на неявный инвариант: *одна сделка ↔ один `posId` ↔ одна
финализированная запись positions-history, чей `realizedPnl` кумулятивен по ВСЕМ
partial-закрытиям и доборам*. Тезис «готовый net одним запросом» верен, только если
OKX агрегирует partial-выходы в одну запись на `posId`, а мы читаем её
**финализированной**. Риск: partial TP (`type` 1) → финальный SL (`type` 2) — если
биржа эмитит запись на слайс, чтение «последней» = недосчёт realized; timing —
запись может быть не финализирована в момент чтения. Оба → систематически заниженное
число [risk-and-sizing.md §9 Tharp гл.6 с.144-146]. Доки должны: выписать инвариант +
**рантайм-верификация** (контур source-api, demo) семантики агрегации/финализации
positions-history для partial/re-add.

### Кластер F — гигиена и форвард (не гейтят)

**N12 — диспозиция `REFRESH_FILLS` не решена** [F-A2]. Тип: неотвеченный вопрос +
doc↔doc несогласованность. **Не гейтит** (контур числа от fills независим).
Команда «кандидат на снятие», но её единственная функция (пересчёт
`accumulatedFillSize`/`averagePrice`/`fee` ordinary `Order`) объявлена избыточной
(эти метрики — из `OkxOrderResponse`/`REFRESH_ORDER`), при этом остаётся в enum,
evidence-cycle, `risk-validator-scope`, `ServiceCommandExecutor` и **6 handler'ах**
(верифицировано грепом). Доки должны: снять (каскад по enum + 6 handler'ов +
evidence-cycle + `fills.md`, с подтверждением что `REFRESH_ORDER` полностью покрывает
order-fill-метрики) либо оставить с явной остаточной функцией.

**N13 — funding как holding-cost без форвард-дома** [F-T3]. Тип: торговая находка,
**cross-cutting/форвард (не гейтит)**. В числе funding учтён (внутри net + bills) —
ретроспектива корректна. Но на **форварде** funding как издержка удержания не
смоделирован нигде (в сайзинг входит только комиссия; экспектанси/бэктест-гейта в
фазе 1 нет). Для многодневного удержания трендового SWAP funding способен доминировать
над комиссией → пост-costs ожидаемость может уйти в минус при пройденном per-trade
risk [system-design.md §Издержки Carver ST гл.12 с.225-246; carry=yield−funding,
strategy-patterns.md:178 Carver ST гл.7 с.153-154]. Асимметрия «комиссию включаем,
funding нет» защитима (комиссия = round-trip execution-cost в R; funding =
time-accruing holding-cost вне заданного на входе R), но **в доках не проговорена** —
читается как произвол. Владелец — шаг ожидаемости/бэктеста (фаза 2); зафиксировать
разделяющий довод + завести форвард-дом издержки удержания.

## Блокирующие открытые вопросы

Из `open-questions.md` новых блокеров нет (OKX-Q1/Q3 закрыты на `GAPS_CLOSE_1`;
OKX-Q2 — deep-архив, вне шага). Находки N1-N13 — кандидаты в узкий `GAPS_CLOSE_2`
(часть станет решениями/decision, часть — рантайм-верификацией).

## Владельцы / маршрутизация (для `GAPS_CLOSE_2`)

- **`solution-designer`** (конструкция концепции): N2 (снапшот + имя сущности), N3
  (`DealCashFlow` модель), N5 (персистенция/линковка), N6 (fetch-механика — выбор
  seam'а), N7 (staged-носитель), N8 (владелец+провенанс аварийного числа — с
  `trading-specialist`), N9 (fee-rate seam), N10 (уровень реакции), N12 (`REFRESH_FILLS`).
- **`integrator`** (native/контракты/маппинг): N1 (`OkxPositionsHistoryResponse`),
  N4 (bills→`DealCashFlow`), N11 (**рантайм-верификация** агрегации positions-history —
  контур source-api).
- **`trading-specialist`**: N8 (провенанс/хвост), N11 (торговая значимость инварианта),
  N13 (funding-издержка удержания, форвард).
- **Хвост пользователя** (тонкий): N10 epsilon-величина (`подтверждение`); N13
  форвард-scope владельца (фаза 2 vs step-7-adjacent decision).

## Сводка

**13 находок** (свёрнуты из 6+6+5 находок трёх ревьюеров; дедуп: F-A6+F-T1→N8,
F-A4+F-T5+F-T4→N10, F-B6→в N3). **Гейтят CODE — 11:** N1, N2, N3, N4, N5, N6, N7,
N8, N9, N11 (полные) + N10 (узко — ветка исхода `FINALIZE_EXIT`). **Не гейтят — 2:**
N12 (`REFRESH_FILLS`-гигиена), N13 (funding-форвард).

- **Торговых блокеров — 2** (N8 провенанс аварийного числа = жёсткий гейт «контракт
  неисполним»; N11 недоказанный инвариант агрегации), оба бьют по достоверности
  `resultProfit`/целостности R-распределения.
- **Агрегация по ярлыкам дефицита:** `работа` (проектирование/материализация) —
  N1-N10, N12 (основная масса — это отложенная GAPS_CLOSE_1 стадия-1/2 работа +
  доопределения seam'ов); `подтверждение` (рантайм/величина) — N11 (семантика OKX),
  N10 (epsilon); `грунт` — нет (корпус покрыл торговые тезисы).
- Ярлык исхода по большинству — `принято-в-работу` (design/integration задачи с
  ясным субстратом в контракт-доках), не `неразрешимо`. Настоящего product/policy-
  остатка мало (N10 epsilon, N13 scope).

**Исход: `DOCS_CHECK_2` НЕ чист → `GAPS_CLOSE_2`.** Приоритет: N6 (fetch-механика —
центр, от неё зависят N8/N10/N11) → носители N1-N5 → seam'ы N7/N9 → аварийный
провенанс N8 → сверка N10 → рантайм-инвариант N11 → гигиена N12 / форвард N13.
После закрытия — подтверждающий `DOCS_CHECK_3`.
