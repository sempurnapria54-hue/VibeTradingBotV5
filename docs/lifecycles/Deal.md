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
  `resultProfitCurrency`; FSM handler не запускается.
- **`ERROR`** — ошибочное runtime-состояние (не terminal, не закрытая
  сделка). Обычные strategy steps не выполняются; разрешены только
  safety / recovery / refresh / kill-switch действия.
- **`EMERGENCY_CLOSED`** — аварийный terminal-финал после safety-flow
  (сделка была в `ERROR`, live risk снят/доказано отсутствие). Это и есть
  **ошибочный терминал** контракта финализации; число `resultProfit` —
  по §«Терминальный контракт финализации» (DEAL-Q2; деталь шага 7), не
  блокируется инвариантом чистого закрытия.

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
Strategy.DELETED                 -> shutdownReason = STRATEGY_DELETED
Exchange/Instrument/Account HOLD -> shutdownReason = EXCHANGE_HOLD
Market data expired (по policy)  -> shutdownReason = MARKET_DATA_EXPIRED
```

**Не** заполняется (`shutdownReason = null`) при обычном выходе:
strategy exit → `closeReason = STRATEGY_EXIT`; TP/SL → `TAKE_PROFIT`/
`STOP_LOSS`; entry condition expired в `PRECHECK` до live risk →
`CLOSED` + `ENTRY_CONDITION_EXPIRED`; risk-block в `PRECHECK` до live
risk → `CLOSED` + `RISK_CONTROL`.

## Terminal semantics и live risk

`Deal` active, если не в terminal status (`ERROR` — active, не
terminal). Terminal — `CLOSED`/`EMERGENCY_CLOSED`: нет FSM handler. Для
**чистого** `CLOSED` `resultProfit`/`resultProfitCurrency` обязательны; для
ошибочного `EMERGENCY_CLOSED` — по §«Терминальный контракт финализации»
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

Финализация использует **общий механизм повторов**
(`DealFinalizationState`, `docs/models/domain/other/DealFinalizationState.md`).
Граничный контракт между механикой финализации (шаг 6) и расчётом прибыли
(шаг 7):

- Прибыль посчиталась → `MARK_DEAL_CLOSED` ставит **чистый терминал
  `CLOSED`** с числом (`resultProfit`/`resultProfitCurrency`).
- Прибыль не посчиталась после исчерпания retry → это **ошибка** →
  `DealFinalizationState(MARK_CLOSED) = FAILED`, сделка уходит ошибочной
  тропой (`MarkDealErrorExecutor`/`ErrorHandler`) и доходит до **ошибочного
  терминала** (`EMERGENCY_CLOSED`). Сделка **всегда доходит до терминала, не
  зависает живым риском**.
- Инвариант «`resultProfit`/`resultProfitCurrency` обязательны» — про
  **чистое закрытие** (`CLOSED`). Ошибочный терминал на нём **не
  блокируется**: что именно с числом прибыли на ошибочном терминале — деталь
  **шага 7** (здесь не решается).

DEAL-Q2 закрыт этим контрактом на `GAPS_CLOSE_1` шага 6 (2026-06-22);
*расчёт* прибыли — шаг 7.

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
