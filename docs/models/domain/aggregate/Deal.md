# Deal

## На какой вопрос отвечает этот файл

Что это за торговая модель `Deal` (lifecycle root и runtime graph
сделки): структура, енумы, runtime graph, итоговый PnL.

Статусы и переходы — в `docs/lifecycles/Deal.md`.

## Назначение

`Deal` — lifecycle root и runtime graph торговой сделки. Фиксирует,
что система начала сопровождать торговый сценарий по конкретному
`Instrument`, по pinned `StrategyDetail`, в ожидаемом направлении,
с FSM-статусом, причиной создания, причиной завершения и итоговым
profit/loss.

`Deal` **не** является биржевой сущностью: нет external id, нет
external status, OKX mapping-документ не нужен. `Deal` **не**
отвечает за: сырые exchange responses, историю команд, историю
изменений сущностей, подробный entry context, risk-check details,
свежие market/calculation data, raw fills archive, полную финансовую
отчётность.

## Структура

Java-класс `com.example.tradingbot.domain.model.core.deal.Deal`,
расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `internalId` | `String` | Безопасный внешний/межсервисный id (API, логи, timeline). |
| `instrumentId` | `Long` | Инструмент (полный `Instrument` — в `DealContext`). |
| `strategyDetailId` | `Long` | Pinned `StrategyDetail`: даже если `Strategy` изменится / станет INACTIVE / DELETED, открытая сделка ведётся по этой pinned-версии. |
| `status` | `Status` | FSM-статус (см. lifecycle). |
| `direction` | `StrategyTradeDirection` | Expected direction (`LONG`/`SHORT`), фиксируется при создании; `Position.direction` должен ему соответствовать. |
| `entryReason` | `EntryReason` | Короткая причина создания (не управляет FSM). |
| `entryStepType` | `EntryStepType` | Тип entry-step (`ENTRY`/`GRID_ENTRY`/null; не управляет FSM). |
| `shutdownReason` | `ShutdownReason` | Причина graceful shutdown / controlled close (если запущен). Не заменяет `closeReason`. |
| `closeReason` | `CloseReason` | Итоговая бизнес-причина завершения. |
| `plannedRiskAmount` | `BigDecimal` | **Плановый риск сделки (`R`)** — убыток на стопе, посчитанный при постановке входа. Знаменатель R-мультипликатора (см. §«Плановый риск»). |
| `plannedRiskCurrency` | `String` | Валюта планового риска (та же, что у `resultProfitCurrency` — иначе отношение не считается). Источник значения у писателя — **расчётная валюта инструмента** (дом — `InstrumentExternalRules`, `docs/decisions/instrument-currencies-home.md`; имя поля валюты — открытый хвост CCY-Q2). |
| `resultProfit` | `BigDecimal` | Итоговый PnL (см. ниже). |
| `resultProfitCurrency` | `String` | Валюта результата (для `ETH-USDT-SWAP` обычно `USDT`). |
| `billsWindowBegin` | `OffsetDateTime` | **Нижняя граница окна линковки bills** — собственное поле сделки, заполняет наблюдатель факта открытия (см. §«Окно линковки bills»). |
| `billsWindowEnd` | `OffsetDateTime` | **Верхняя граница окна линковки bills** — заполняет наблюдатель факта закрытия (вторая нога `REFRESH_POSITION_COMMAND`). Пусто = факт закрытия не добыт → привязка ждёт. |
| `orders` | `List<Order>` | Ordinary orders сделки (attached protection — внутри `Order`). |
| `algoOrders` | `List<AlgoOrder>` | Standalone algo-orders сделки. |
| `position` | `Position` | Текущая позиция (≤1 на `Deal`). |

`Deal.direction` имеет тип `StrategyTradeDirection` (енум `Strategy`,
`docs/models/domain/aggregate/Strategy.md`).

## Енумы

- **`Status`**: `PRECHECK`, `ENTRY_SUBMITTED`, `ENTRY_FINALIZED`,
  `PROTECTION_SWITCHED`, `MANAGING`, `EXIT_PENDING`, `CLOSED`,
  `ERROR`, `EMERGENCY_CLOSED`. Описывает бизнес-этап сделки, **не**
  статус `Order`/`AlgoOrder`/`Position`/command execution/exchange
  ACK. Значения, группы, переходы — в `docs/lifecycles/Deal.md`.
- **`EntryReason`**: `STRATEGY` (создана `EntryScannerJob` по
  условиям), `MANUAL`, `RECOVERY` (восстановление существующего
  runtime risk), `UNKNOWN` (fallback, не для normal flow).
- **`EntryStepType`**: `ENTRY`, `GRID_ENTRY` (или null, если создана
  не через strategy entry-step). Комбинации с `entryReason` — в
  lifecycle/справке. Подробный entry context — в аудите, не в `Deal`.
- **`ShutdownReason`**: `STRATEGY_DELETED`, `MARKET_DATA_EXPIRED`
  (только если policy решила завершать сделку controlled-exit, не
  при любом stale), `MANUAL_STOP`, `RISK_POLICY`, `EXCHANGE_HOLD`,
  `UNKNOWN`. Заполняется только при реальном запуске graceful
  shutdown (см. lifecycle).
- **`CloseReason`**: `ENTRY_CONDITION_EXPIRED` (candidate закрыт в
  PRECHECK до live risk), `STRATEGY_EXIT`, `TAKE_PROFIT`,
  `STOP_LOSS` (включая fixed и trailing SL; конкретный механизм — в
  `Order`/`AlgoOrder`/`DealActionState`/audit), `TIME_STOP`,
  `RISK_CONTROL` (штатное risk-control завершение, включая risk-block
  в PRECHECK), `MANUAL_CLOSE`, `EMERGENCY_CLOSE` (только для
  `EMERGENCY_CLOSED`), `UNKNOWN`. Не используются:
  `ENTRY_RISK_BLOCKED`, `TRAILING_STOP`. Описывает бизнес-причину, не
  технический механизм закрытия позиции.

`entryReason`/`entryStepType` не управляют FSM. `shutdownReason`
(почему перевели в graceful shutdown — не значит, что закрылась) и
`closeReason` (итоговая причина завершения) — разные поля.

## Итоговый PnL (resultProfit)

Первоисточник правила — здесь (`Deal` владеет полем,
`.claude/decisions/rule-source-of-truth.md`); источник данных числа и
разбивки — `docs/decisions/result-profit-source.md`:

- **Число** `resultProfit` = **net realized P&L**, берётся **готовым** из
  positions-history (`realizedPnl = pnl + fee + fundingFee + liqPenalty`,
  посчитан биржей), **плюс cross-ccy-слагаемое** Σ(`amount` ×
  `appliedRate`) по строкам `DealCashFlow` чужой `ccy` (биржевой net
  считается в settle-ccy и издержку вне неё не содержит — без слагаемого
  число завышалось бы молча; реш.5
  `docs/decisions/pnl-finalization-mechanics.md`,
  `docs/models/mapping/DealCashFlow.md` §«Число — в settle-ccy»). **Не**
  через fills/`TradeFill` и **не** через
  `BalanceContainer` diff. `REFRESH_BALANCE_COMMAND` после выхода нужен для
  актуального account snapshot, не для PnL сделки.
- **Категорийная разбивка** (торговая комиссия / funding / rebate /
  ликвидационный штраф) — из bills (`DealCashFlow`); сумма bills-flows
  сверяется с net из positions-history (контроль целостности). В `Deal`
  разбивка не хранится (см. ниже).
- Для **чистого** terminal `CLOSED` `resultProfit` и
  `resultProfitCurrency` обязательны; для аварийного `EMERGENCY_CLOSED`
  число — **best-effort**: фактический realized net если доступен, иначе
  `resultProfit = null` c маркером «неисчислимо» (**не ноль**;
  `docs/lifecycles/Deal.md` §«Терминальный контракт финализации»; DEAL-Q2
  закрыт). На `EMERGENCY_CLOSED` `null` = «число не достать» (отличимо от нуля).
- `resultProfit = 0` допустим только как **результат расчёта** (net вышел
  нулевым), **не** как молчаливый fallback при ошибке. Если временно нельзя
  посчитать — добыча и финализация ретраятся бюджетом своих **системных
  действий** (`REFRESH_DEAL_CONTEXT_ACTION` / `FINALIZE_DEAL_EXIT_ACTION`,
  `docs/models/domain/other/DealActionState.md`); при исчерпании сделка
  уходит ошибочной тропой к терминалу — всегда доходит до терминала, не
  зависает живым риском (**DEAL-Q2, закрыт**, `docs/lifecycles/Deal.md`
  §«Терминальный контракт финализации»).
- **Расчёт и запись (N7).** Число вычисляет и **пишет прямо на `Deal`**
  `FinalizeDealExitExecutor` (net из **`Position.externalRealizedProfit`** —
  положения закрытия, приземлённого второй ногой `REFRESH_POSITION_COMMAND`,
  — плюс разбивка из `DealCashFlow` + сверка; в одной транзакции с
  продвижением своего исполнения — durable-носитель числа = само поле
  `Deal`, рестарт-safe). `MarkDealClosedExecutor` **ассертит** непустоту и
  ставит терминал `CLOSED` (число не пишет). Step-6 писал
  интерим-placeholder `ZERO` (граница 6 ↔ 7); шаг 7 его снял (реальный
  net). Механика — `docs/decisions/pnl-finalization-mechanics.md` реш.2.

## Плановый риск (`R`)

**`plannedRiskAmount` — знаменатель R-мультипликатора** (H9,
`GAPS_CLOSE_7`). Шаг 7 персистит числитель (`resultProfit`) и обязан
персистить знаменатель: без него отношение «сколько R заработала сделка» не
считается, а система в торговом смысле **и есть** распределение
R-мультипликаторов [Tharp гл.6 с.144-146].

- **Что это за число.** Убыток на стопе, посчитанный при постановке входа, —
  та же величина `risk amount`, которую считает `RiskValidator`
  (`|entry − stop| × contracts × ctVal + commissions`,
  `docs/decisions/per-trade-risk-policy.md` §«Закрытая форма сайзинга»).
  Прогнозная комиссия в него **входит** — иначе знаменатель отличался бы от
  величины, под которую подбирался размер.
- **Кто и когда пишет.** Исполнитель `CREATE_ORDER_COMMAND` входного действия
  (`docs/components/CreateOrderExecutor.md`) — в одной транзакции с созданием
  сущности входа. Риск-преконтроль и создание идут **одним проходом**
  (`docs/rules/risk-validator-scope.md`: `RiskValidator` вызывается после
  расчёта цены/размера и **до** создания команды), поэтому метрика доезжает
  до писателя без durable-слота между проходами.
- **Write-once.** Пишется при постановке входа и **не переписывается**:
  трейлинг двигает стоп, но `R` — риск **на входе**, бенчмарк измерения
  [Tharp гл.9 с.234-236]. Текущее состояние защиты знаменателя не даёт.
- **`R` — риск заявленный, не взятый; разрыв персистится рядом.** Число
  считается по **заявленной** цене входа и **заявленному** размеру, а
  сделка несёт риск по фактической цене исполнения и фактически
  исполненному объёму. Расхождение систематическое по обеим осям
  (проскок входа; частичное исполнение), поэтому вместе с `R` пишутся его
  операнды — **`plannedEntryPrice`** (reference-цена входа, по которой
  считался риск; для market-входа это `ORDER_MARKET_REFERENCE_PRICE`
  калькулятора, на биржу не отправляемая) и **`plannedSizeContracts`**
  (заявленный размер). Имена полей предварительные.
  - **Зачем.** Фактически взятый риск вычислим из уже имеющегося:
    стоп — абсолютный ценовой уровень, рассчитанный от рантайм-цены и
    политики риска, на бирже он не плывёт; цена исполнения и объём
    входа читаются финализацией входа (`avgPx`, `accFillSz`). Не хватало
    **второй половины сравнения** — того, против чего решение
    принималось: reference-цена market-входа не персистилась нигде
    (`CreateOrderExecutor` кладёт `Order.price` только при
    `sendPriceToExchange`), и разрыв «заявлено ↔ взято» был неизмерим
    даже постфактум.
  - **Поправка живёт в аналитике, не в поле.** `plannedRiskAmount`
    остаётся тем числом, под которое подбирался размер (решение
    пользователя, `GAPS_CLOSE_10`); альтернатива «писать в поле риск по
    факту входа» отвергнута — поле сменило бы смысл и появлялось бы на
    такт позже, завися от добычи фактов входа. Взамен разрыв становится
    **наблюдаемым**: (`avgPx` − `plannedEntryPrice`) и (`accFillSz` −
    `plannedSizeContracts`) считаются по persisted-данным.
  - **Смежное:** мониторинг slippage сигналов против исполнений — условие
    валидности утверждения «система работает» [Kaufman гл.21 PDF
    с.1913-1914]; поправка на проскок **выхода** отложена отдельно
    (`docs/decisions/per-trade-risk-policy.md` §«Без поправки на
    проскок»).
- **Почему не реконструкция.** Формально `R` восстановим через append-only
  REPLACE-цепочку (`docs/decisions/replace-not-amend.md` §4) + `avgPx`/
  `accFillSz` + `ctVal` — но это join через три носителя, прогнозная комиссия
  в него уже не входит, и получается не то число, под которое сайзились.
- **Незакрытый смежный вопрос.** При многоногом входе
  (`GRID_ENTRY`/пирамидинг) `R` сделки не определён: лимит риска проверяется
  **по действию**, агрегата по сделке нет. Вопрос открыт (`RISK-Q3`,
  `.claude/work/questions/open-questions.md`); поле планового риска его **не
  закрывает** — оно даёт слот, а не правило агрегации.

Детальный breakdown (fees, fundingFee, gross/netProfit, entry/exit
fills, average prices, partial exits) в `Deal` не хранится: число берётся
готовым из positions-history, категорийная разбивка живёт как `DealCashFlow`
(bills), пофилловая детализация — вне фазы 1 (**OKX-Q1 закрыт**: persisted
`TradeFill` не вводится).

## Рамка R-выборки

Корзина `CLOSED` содержит три семантически разные популяции («сделка
состоялась», «кандидат не вошёл», «вход отменён до исполнения»), поэтому
«взять все `CLOSED`» выборкой не является. Рамка определяется **тем же
предикатом**, что и запись нуля на тропах без входа
(`docs/rules/trading-constraints.md` §«Гейт открытия сделки») — вопрос
статистики, не структуры: новых полей он не требует, требует определения.

- **R-выборку образуют сделки со состоявшимися операциями на бирже.**
  Предикат — факт операций по сделке, а не `closeReason` и не непустота
  `plannedRiskAmount`: последняя истинна уже у входа, отменённого до
  исполнения, и наивный фильтр «есть `R`» затянул бы не-сделки внутрь.
- **Несостоявшиеся входы в R-выборку не входят** — они учитываются
  **отдельным счётчиком частоты**: это ось «частота возможностей», а не
  исход системы [Tharp гл.6 с.130-133]. Включённые нулём они размывали бы
  ожидаемость к нулю, включённые как `unknown` — искажали бы частоту.
- **Неисчислимые исключаются как `unknown` и считаются поштучно.**
  Корректность такого исключения держится ровно на том, что число
  `unknown`'ов **известно**; поэтому оно сцеплено с идемпотентностью
  журнальных отчётов (`RESULT_PROFIT_UNAVAILABLE` по разным сделкам одного
  инструмента не должен схлопываться — H16 `DOCS_CHECK_10`,
  `docs/models/domain/other/AnomalyReport.md` §Инварианты). Разбирается
  вместе с ним.

Провенанс числа и довод «`null` ≠ ноль» — `docs/decisions/
pnl-finalization-mechanics.md` реш.3.

## Окно линковки bills (`billsWindowBegin` / `billsWindowEnd`)

Границы окна, по которому `RefreshBillsExecutor` матчит движения к сделке,
— **собственные поля `Deal`**; из чужих колонок
(`Position.externalModifiedAt`) окно **не реконструируется** — та колонка
писалась обеими ногами `REFRESH_POSITION_COMMAND` и предиката «запись
закрытия добыта» не выражала (узел 1 `DOCS_CHECK_8`;
`docs/decisions/command-action-boundary.md` §7). Заполняет **наблюдатель
факта**, write-once:

- `billsWindowBegin` — биржевое время открытия: `cTime` позиции при её
  материализации (пишет live-нога `REFRESH_POSITION_COMMAND`); вопрос
  §AG1.5 (entry-fee раньше `cTime`) может сдвинуть дефолт на
  `externalCreatedAt` первого отправленного `Order` — тогда писатель
  меняется, дом поля нет.
- `billsWindowEnd` — `uTime` записи закрытия positions-history; пишет
  **вторая нога** `REFRESH_POSITION_COMMAND` в одной транзакции с полями
  положения закрытия на `Position`.

`billsWindowEnd` пуст (факт закрытия не добыт) → **привязка bills ждёт**,
окно не закрывается; исчерпание бюджета добычи → ошибочная тропа + холд
инструмента. **Цена решения:** после терминала линковка запрещена
(`docs/models/domain/other/DealCashFlow.md` §«Линковка») ⇒ неподобранные к
этому моменту движения не подберутся никогда — разбивка неполна, сверка
даёт `AnomalyReport` при, возможно, верном числе.

## Runtime graph

`Deal` содержит runtime graph: `orders` (см.
`docs/models/domain/core/Order.md`), `algoOrders` (см.
`docs/models/domain/core/AlgoOrder.md`), `position` (см.
`docs/models/domain/core/Position.md`, ≤1 на `Deal`). Live risk сделки —
вычисляемо (см. lifecycle), отдельным boolean-полем не хранится.

В runtime graph **не** входят и в `Deal` не хранятся:
`DealActionState`, `Exchange`, `Instrument`, `StrategyDetail`,
`BalanceContainer`, `TradeFill` archive, raw exchange facts,
`CalculationContext`, `MarketPriceData`, `IndicatorValue`,
`MarketStructure`, `MarketPhase` runtime data, audit/history, pending
`ServiceCommand`. Также не хранятся `marketPhaseId` (фаза входа
выводится через `StrategyDetail.marketPhaseType`), `openedAt`/
`closedAt`/`errorAt` (даты записи — `Auditable`; торговые моменты —
через `Order`/`Position`/audit). `TradeFill` из перечня носителей
**убран** (H14, `GAPS_CLOSE_6`): persisted `TradeFill` в проекте не
вводится (§выше, OKX-Q1 закрыт). Окно линковки bills из чужих носителей
больше не собирается — его границы суть собственные поля сделки (§«Окно
линковки bills» выше; `docs/models/domain/other/DealCashFlow.md`
§«Линковка к `Deal`»).

## Персистентность

Хранится в БД (entity `DealEntity`, таблица `deals`), наследует
audit-поля (`AuditableEntity`). Runtime graph (`orders`/`algoOrders`/
`position`) — отдельные таблицы по `deal_id`, не cascade-коллекции этой
строки. Категорийная разбивка P&L (`DealCashFlow`) — тоже **отдельная
таблица `deal_cash_flows` по `deal_id`** (не поле `Deal`; число
`resultProfit`/`resultProfitCurrency` — поля `Deal`, разбивка — строки
`deal_cash_flows`; `docs/models/domain/other/DealCashFlow.md`). Ограничения
схемы:

- `id` — identity (autoincrement).
- `internal_id`, `instrument_id`, `strategy_detail_id`, `status`,
  `direction` — `NOT NULL`; `entry_reason`, `entry_step_type`,
  `shutdown_reason`, `close_reason`, `planned_risk_amount`,
  `planned_risk_currency`, `result_profit`, `result_profit_currency`,
  `bills_window_begin`, `bills_window_end` — nullable (`planned_risk_*`
  пусты до постановки входа; `bills_window_*` — до наблюдения фактов
  открытия/закрытия).
- **Колонки шага 7 — `ALTER`, в `V6`/`V9` их нет** (H21, `DOCS_CHECK_8`):
  `planned_risk_amount`, `planned_risk_currency`, `bills_window_begin`,
  `bills_window_end` добавляются миграцией шага 7; полная schema-дельта
  шага — `docs/decisions/pnl-finalization-mechanics.md` §Следствия.
- `internal_id` — `updatable = false` (неизменен после создания).
- Enum-поля (`status`, `direction`, `entry_reason`, `entry_step_type`,
  `shutdown_reason`, `close_reason`) хранятся строкой (имя enum); enum —
  только в домене (codestyle: enum'ы — в доменном слое).

Ключевые индексы/constraints (миграция
`V9__create_deal_finalization_states.sql`):

- **`uk_deal_active_instrument`** — частичный уникальный индекс
  `on deals (instrument_id) where status not in ('CLOSED',
  'EMERGENCY_CLOSED')`. DB-уровень инварианта «одна незакрытая сделка на
  инструмент» — **defense-in-depth** к app-gatekeeper'у (`EntryScannerJob`/
  `DealOpeningService`; см. `docs/rules/trading-constraints.md`). Предикат
  «активная/незакрытая» = любой `Status` **кроме** `CLOSED` и
  `EMERGENCY_CLOSED`: `PRECHECK`, `ENTRY_SUBMITTED`, `ENTRY_FINALIZED`,
  `PROTECTION_SWITCHED`, `MANAGING`, `EXIT_PENDING` **и `ERROR`** считаются
  активными — сделка в `ERROR` всё ещё блокирует новую сделку по
  инструменту, пока не дойдёт до терминала `CLOSED`/`EMERGENCY_CLOSED`.
- **`ix_deal_status`** `on deals (status)` — support-индекс под горячую
  выборку активных сделок за проход оркестратора (`DealOrchestratorJob`).
- **`ix_deal_instrument_status`** `on deals (instrument_id, status)` —
  support-индекс под gatekeeper входа по инструменту (`EntryScannerJob`/
  `DealOpeningService`). Таблица `deals` не пруним — индексы по
  `status`/`instrument_id` держат выборку активных дешёвой по мере роста
  истории закрытых сделок.
- **Benign insert-race:** вставку новой сделки `EntryScannerJob` делает в
  try/catch — конкурентная вставка, нарушающая `uk_deal_active_instrument`,
  ловится как benign skip (лог по инструменту, не фатальная ошибка прохода),
  а не как сбой. Гонку между двумя проходами закрывает DB-инвариант, приложение
  её не эскалирует.

## Границы с DealActionState / DealContext

- `DealActionState` **не** поле `Deal`: persisted строка **исполнения
  действия** (оба вида — STRATEGY и SYSTEM; recovery/retry/idempotency,
  для STRATEGY — связь `StrategyAction → runtime target`). Связь:
  `Deal.id → DealActionState.dealId → strategyActionId | systemActionType
  → (targetEntityType, targetEntityId)`.
- `DealContext` **не** часть модели `Deal`: процессный runtime-context
  одного прохода FSM (добавляет `Exchange`, `Instrument`, pinned
  `StrategyDetail`, последний persisted `BalanceContainer`, список строк
  исполнений).

Полные модели — `docs/models/domain/other/DealActionState.md`,
`docs/components/models/DealContext.md`; FSM-handlers (материализованы) —
`docs/components/` (`PrecheckHandler` … `ErrorHandler`,
`.claude/decisions/fsm-handler-as-component.md`); правила сборки —
`docs/processes/deal-management.md`.
