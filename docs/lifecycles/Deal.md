# Deal lifecycle

## На какой вопрос отвечает этот файл

Через какие FSM-статусы проходит `Deal`, какие из них terminal, какие
инварианты переходов и как считается live risk сделки.

Структура модели — в `docs/models/domain/aggregate/Deal.md`.

## Кто управляет

`Deal` управляется FSM. Per-status handlers (`PrecheckHandler`,
`EntrySubmittedHandler`, …, `ErrorHandler`) и оркестратор
`DealStateMachine` **материализованы** как компоненты (`docs/components/`;
размещение handler'ов — `.claude/decisions/fsm-handler-as-component.md`).
Живую петлю гоняет `docs/components/DealOrchestratorJob.md`. Здесь —
статусная механика, которой владеет сам `Deal`.

## Статусы

- **`PRECHECK`** — создана локально, live risk ещё нет; FSM
  перепроверяет входные условия, свежесть данных, баланс, risk-policy,
  готовность к entry action.
- **`ENTRY_SUBMITTED`** — entry-flow начат; entry `Order`/`AlgoOrder`
  мог быть создан/отправлен, но открытие позиции не финализировано.
  ACK не считается завершением этапа.
- **`ENTRY_FINALIZED`** — вход подтверждён (entry order / fills /
  position facts).
- **`PROTECTION_SWITCHED`** — temporary attached protection заменена
  на основную standalone protection. Только если switch реально был;
  иначе `ENTRY_FINALIZED → MANAGING` напрямую.
- **`MANAGING`** — основное сопровождение: перенос SL, trailing,
  partial exit (reduce-only `Order`/`AlgoOrder`), grid management,
  strategy exit и др.
- **`EXIT_PENDING`** — штатный выход: снять/обновить защиту, закрыть
  live risk, refresh `Position`/`Orders`/`AlgoOrders`/fills, подготовка
  к `CLOSED`.
- **`CLOSED`** — штатный terminal-финал. Live risk отсутствует
  (подтверждено facts); обязательны `resultProfit` /
  `resultProfitCurrency` — **со смягчением по валюте на тропах без
  входа** (: валюта не
  резолвилась ⇒ ассерт проверяет только `resultProfit`); FSM handler не
  запускается.
- **`ERROR`** — ошибочное runtime-состояние (не terminal, не закрытая
  сделка). Обычные strategy steps не выполняются; разрешены только
  safety / recovery / refresh / kill-switch действия.
- **`EMERGENCY_CLOSED`** — аварийный terminal-финал после safety-flow
  (сделка была в `ERROR`, live risk снят/доказано отсутствие). Это и есть
  **ошибочный терминал** контракта финализации; ставит
  `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`; число `resultProfit` — **best-effort
  по доступности, но не по составу**: доступен net — считается по той же
  формуле, что на чистой тропе; иначе `null` с маркером «неисчислимо»,
  **не ноль**.

## Группы статусов

```text
Active / runtime: PRECHECK, ENTRY_SUBMITTED, ENTRY_FINALIZED,
                  PROTECTION_SWITCHED, MANAGING, EXIT_PENDING, ERROR
Terminal:         CLOSED, EMERGENCY_CLOSED
```

`ERROR` — active runtime status, но **не** normal active trading
status: требует обработки, но не через обычные strategy steps.

## Инварианты переходов

```text
CLOSED            — только штатное завершение.
EMERGENCY_CLOSED  — только аварийное завершение после safety-flow.
ERROR -> CLOSED   — запрещён.
ERROR -> EMERGENCY_CLOSED — только после подтверждения отсутствия live
                  risk (resultProfit — по терминальному контракту).
Terminal statuses не имеют FSM handlers.
Live risk после terminal status -> зона AnomalyJob / ReconciliationJob,
                  не обычный FSM-flow.
```

## graceful shutdown (когда заполняется shutdownReason)

`shutdownReason` заполняется **только** при реальном запуске graceful
shutdown / controlled close активной сделки:

```text
Strategy.DELETED                    -> shutdownReason = STRATEGY_DELETED
Instrument.TRADE_BLOCKED (холд      -> shutdownReason = RISK_POLICY
  инструмента, риск-триггер)
Exchange.TRADE_BLOCKED / DISABLED   -> shutdownReason = EXCHANGE_HOLD
  (ступень 2 лестницы / отключение,
  каскад)
Market data expired (по policy)     -> shutdownReason = MARKET_DATA_EXPIRED
```

**Инструмент-холд и биржевой холд разведены**.
Прежняя строка склеивала «Exchange/Instrument/Account HOLD» в один
`EXCHANGE_HOLD` — это расходилось и с правилом
(`docs/rules/instrument-hold.md`,
`docs/models/domain/core/Instrument.md`), и с кодом
(`DealOrchestratorJob.enforceHold`), где холд **инструмента** проставляет
`RISK_POLICY`, а `EXCHANGE_HOLD` приходит только каскадом от биржевой
строки. Эта таблица — носитель, в который полезет писатель шага 7, поэтому
расхождение снято здесь, а не «где-нибудь ещё».

**Сделки гасит только ступень 2 биржевой лестницы**
(`Exchange.TRADE_BLOCKED`) и `DISABLED`. `Exchange.HOLD` — мягкий холд,
ступень 1 — сделки **не гасит**: активные сделки не перехватываются и
ведутся штатным FSM в полном объёме (ремодел защиты, управление,
закрытие); `shutdownReason = EXCHANGE_HOLD` производится **только**
каскадом ступени 2 (`docs/rules/exchange-hold.md`).

**Не** заполняется (`shutdownReason = null`) при обычном выходе:
strategy exit → `closeReason = STRATEGY_EXIT`; TP/SL → `TAKE_PROFIT`/
`STOP_LOSS`; entry condition expired в `PRECHECK` до live risk →
`CLOSED` + `ENTRY_CONDITION_EXPIRED`; risk-block в `PRECHECK` до live
risk → `CLOSED` + `RISK_CONTROL`.

## Terminal semantics и live risk

`Deal` active, если не в terminal status (`ERROR` — active, не
terminal). Terminal — `CLOSED`/`EMERGENCY_CLOSED`: нет FSM handler. Для
**чистого** `CLOSED` `resultProfit`/`resultProfitCurrency` обязательны —
**со смягчением по валюте на тропах без входа** ; для
ошибочного `EMERGENCY_CLOSED` — по
(не блокируется инвариантом чистого закрытия).

Live risk сделки (не хранится boolean-полем; вычисляется через
runtime graph, `DealActionState`, refresh/search/history facts,
anomaly/safety context) — есть, если хотя бы одно:

```text
active Position с live market risk (status == ACTIVE && externalSize > 0)
live Order
live AlgoOrder
unknown external live-сущность на бирже
расхождение, не позволяющее доказать отсутствие live risk
```

Если после terminal status найден live risk — зона `AnomalyJob /
ReconciliationJob`.

## Терминальный контракт финализации (DEAL-Q2)

Финализация использует **общий механизм повторов** — строки исполнений
системных действий (`docs/models/domain/other/DealActionState.md`, вид
SYSTEM; `docs/rules/command-lifecycle.md`). Граничный контракт
между механикой финализации (шаг 6) и расчётом прибыли (шаг 7):

- **Чистое закрытие.** Число считает и **пишет на `Deal`** `FINALIZE_DEAL_EXIT_COMMAND`
  (net из положения закрытия на `Position` + разбивка bills, в одной
  транзакции с продвижением своего исполнения; N7). `MARK_DEAL_CLOSED_COMMAND`
  **ассертит** непустоту `Deal.resultProfit` и ставит **чистый терминал
  `CLOSED`** (число сам не пишет —
  `docs/rules/pnl-reconciliation.md` реш.2).
- Прибыль не посчиталась после исчерпания бюджета добычи/финализации → это
  **ошибка** → исполнение (`REFRESH_DEAL_CONTEXT_ACTION` /
  `FINALIZE_DEAL_EXIT_ACTION`) в `FAILED`, сделка уходит ошибочной тропой
  (`MarkDealErrorExecutor`/`ErrorHandler`) + **холд инструмента** и доходит
  до **ошибочного терминала** (`EMERGENCY_CLOSED`). Сделка **всегда доходит
  до терминала, не зависает живым риском**.
- **Число неполно → тоже ошибочный терминал**. `resultProfit` считается по строкам
  `DealCashFlow`, а они усекаемы глубиной конвейера добычи и нерезолвившимся
  курсом; оба усечения **завышают** число. Предикат неполноты собран из уже
  существующих фактов и записан **перечнем значений**: `breakdownIncomplete ∈ {INCOMPLETE_BY_WINDOW,
  NOT_ASSESSED}` **либо** прилинкованная строка `DealCashFlow` с
  `rateStatus ∈ {RATE_UNAVAILABLE, SETTLE_CURRENCY_UNAVAILABLE}`;
  отдельного признака полноты числа **не заводится**. **Актор — выходная
  проверка `ExitPendingHandler`** (`docs/components/ExitPendingHandler.md`): она уводит сделку `EXIT_PENDING → ERROR`, живая
  строка `FINALIZE_DEAL_EXIT_ACTION` закрывается `SKIPPED`, холд не
  поднимается. Число пишется и остаётся наблюдаемым, но чистого `CLOSED`
  такая сделка не получает
  (`docs/components/FinalizeDealExitExecutor.md`).
- **Аварийный терминал `EMERGENCY_CLOSED`** ставит **`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`**
  (`docs/components/MarkDealEmergencyClosedExecutor.md`, симметрично
  `MARK_DEAL_CLOSED_COMMAND`) с **best-effort числом** — **два провенанса разведены**
  (`docs/rules/pnl-reconciliation.md` реш.3):
  - **(a) ликвидация/ADL** (позицию закрыла биржа —
    `Position.externalCloseType ∈ 3..6`): net доступен полями
    `Position.externalRealizedProfit` строк эпизодов → число считается
    **по той же
    формуле, что на чистой тропе** (Σ net по эпизодам +
    cross-ccy-слагаемое): best-effort
    относится к **доступности** числа, не к его **составу**;
  - **(b) отказ расчёта** (чистая тропа не смогла): перед терминалом
    `ErrorHandler` гоняет `REFRESH_POSITION_COMMAND`, и её **вторая нога**
    (positions-history) ещё раз пробует добыть положение закрытия на
    `Position`; net есть → пишем;
    **genuinely недоступен** → `resultProfit = null` c семантикой
    **«неисчислимо»** (**не ноль**), сделка терминализуется всё равно, факт
    помечается (лог/`AnomalyReport`). **Отказ канала добычи на этой тропе
    приравнивается к «недоступно»** — но **расходует бюджет**, и durable-
    носителем исхода служит `FAILED` строки `REFRESH_DEAL_CONTEXT_ACTION`,
    он же разрешает эмиссию терминала.
    **Контролируемое исключение под приравнивание не подпадает**: дефект содержимого ответа даёт **биржевую
    ступень 2** (`Exchange.TRADE_BLOCKED` + flatten) и здесь тоже,
    параллельно с ошибочным терминалом — ветки не
    конкурируют (`docs/components/ServiceCommandExecutor.md`; `docs/rules/pnl-reconciliation.md`).
  - **Маркер:** на `EMERGENCY_CLOSED` `resultProfit != null` = посчитанное
    число; `null` = «неисчислимо» — **отличимо от нуля** (ноль = посчитанный
    нулевой P&L). Инвариант «`resultProfit` обязателен» — только про
    **чистое** закрытие.
  - **Причина (торговая):** число **не зануляется** — недоступность помечается,
    null-случай в расчёт ожидаемости не входит как unknown (не считается
    нулём), левый хвост R-распределения не усекается молча. Сама сделка при
    этом записана и из популяции не выводится — узел F,
    `docs/models/domain/aggregate/Deal.md`.

### Смягчение по валюте на тропах без входа (H1 `DOCS_CHECK_11`)

Инвариант чистого `CLOSED` — про **два** поля: `resultProfit` и
`resultProfitCurrency`. На **тропах закрытия без входа** (три тропы,
`docs/rules/trading-constraints.md`) число и валюту
пишет `MARK_DEAL_CLOSED_COMMAND` сам, второй веткой
(`docs/components/MarkDealClosedExecutor.md`), и валюта
пишется **только если резолвится**.

**Правило.** Если расчётная валюта инструмента не резолвится, терминал
`CLOSED` ставится с `resultProfit = 0` и **пустой**
`resultProfitCurrency`; ассерт терминального ребра проверяет в этом случае
**только `resultProfit`**, а факт помечается `AnomalyReport`
`SETTLE_CURRENCY_UNAVAILABLE` (`severity = NON_CRITICAL`).

- **Смягчение записано, а не выведено.** Без явной записи `CODE`-писатель
  прочитает безусловный ассерт двух полей и получит недостижимый терминал
  на самом частом сценарии сканера — либо подставит валюту умолчанием, то
  есть ровно «число, выглядящее фактом».
- **Гейтить терминал на непустой валюте нельзя** — это прямо запрещено
  инвариантом объемлющего уровня «сделка всегда доходит до терминала, не
  зависает живым риском»; альтернативы у смягчения нет, поэтому оно и
  оформлено правилом, а не развилкой.
- **Область действия — только тропы без входа.** На штатной тропе валюта
  резолвится до входа (иначе `RiskValidator` дал бы реджект
  `SETTLE_CURRENCY_UNAVAILABLE`), и инвариант двух полей действует
  безусловно.

### Признаки отбора на рёбрах в терминал (H3 `GAPS_CLOSE_13`)

Перечень записываемого на каждом ребре дополняется признаками отбора
(`docs/models/domain/aggregate/Deal.md`):

| Ребро | `closeOutcome` | `reconciliationStatus` | `breakdownIncomplete` | `riskBenchmarkAvailability` |
|---|---|---|---|---|
| `EXIT_PENDING → CLOSED` (штатная) | по `Position.externalCloseType` **последнего эпизода** (`max(externalModifiedAt)`), пусто (записи нет) ⇒ `UNDETERMINED` | исход сверки | **только `COMPLETE`** | `AVAILABLE`, либо `MISSING` при пустом знаменателе |
| `ERROR → EMERGENCY_CLOSED` | то же | **исход сверки** (`MATCHED`/`MISMATCHED`) — только если движения **добывались** (`Deal.billsFetchedThrough` непуст), то есть сделка успела побывать на выходной тропе; иначе `NOT_RUN` | то же сравнение; `billsFetchedThrough` пуст ⇒ `NOT_ASSESSED`, а не `COMPLETE` | то же (`NOT_APPLICABLE` недостижим — операции были) |
| три тропы **закрытия без входа** → `CLOSED` | **пусто** (неприменим) | **пусто** (неприменим) | **пусто** (неприменим) | **`NOT_APPLICABLE`** — единственный признак, который на этих тропах **не пуст**: он и назван, чтобы отличить эту популяцию от аномальной. **Пишет `MarkDealClosedExecutor` той же транзакцией** — финализатор выхода на этих тропах не работает |

**Таблица — ассерт ребра: она перечисляет значения, наблюдаемые у
сделки, которая ребро прошла.** Заголовок «перечень записываемого»
допускал и второе прочтение — «что финализатор пишет **до** ребра», — и
в нём строка 1 была бы верна с тремя значениями `breakdownIncomplete`.
Прочтение выбрано первое: на таблицу ссылаются как на ассерт
(`docs/components/MarkDealClosedExecutor.md`,
`docs/rules/deal-without-operations.md`), и в этой роли она
обязана согласоваться с маршрутизацией.

**Штатное ребро при обязанной и невыполненной сверке недостижимо**: нерезолвимый операнд
обязанной сверки уводит сделку ошибочной тропой
( выше), поэтому
`reconciliationStatus` в строке `EXIT_PENDING → CLOSED` — всегда исход
**выполненной** сверки либо `NOT_RUN` сверки, которая не была обязана.
Значение «вне `1..6`» в первой колонке снято: такая запись не проходит
границу интеграции.

DEAL-Q2 закрыт в три захода: механика/терминальный контракт шага 6 (2026-06-22); *число* на ошибочном терминале шага 7 (2026-07-03); *провенанс-контракт исполним + владелец
терминала* (`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`) шага 7 (2026-07-04,
N8). *Расчёт* — шаг 7 (`docs/rules/pnl-reconciliation.md`).

## Restart / recovery

После рестарта система **не** ищет pending `ServiceCommand`
(`ServiceCommand` — runtime object, не persisted queue). FSM
восстанавливает состояние по: runtime graph `Deal`, external
dependencies `DealContext`, `DealActionState`, exchange refresh/
search/history facts. `DealActionState` показывает, какой
`StrategyAction` материализован, какой runtime target создан, какой
в retry / completed / failed / skipped, какой order/algoOrder нужно
заместить (REPLACE-нога) или отменить. Audit/history **не** является
runtime-source для FSM.
