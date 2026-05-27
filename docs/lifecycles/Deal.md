# Deal lifecycle

## На какой вопрос отвечает этот файл

Через какие FSM-статусы проходит `Deal`, какие из них terminal, какие
инварианты переходов и как считается live risk сделки.

Структура модели — в `docs/models/core/Deal.md`.

## Кто управляет

`Deal` управляется FSM. Per-status handlers (`PrecheckHandler`,
`EntrySubmittedHandler`, …, `ErrorHandler`) и оркестратор
`DealStateMachine` — компоненты кластера процессов Deal management
(`.claude-archive/.../processes/Deal management/`), мигрируются
отдельно (форвард-заметки — в `.claude/work/questions/tasks/deal.md`;
размещение handler'ов — `.claude/decisions/fsm-handler-as-component.md`).
Здесь — статусная механика, которой владеет сам `Deal`.

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
  (сделка была в `ERROR`, live risk снят/доказано отсутствие,
  `resultProfit` рассчитан).

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
                  risk и расчёта resultProfit.
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
terminal). Terminal — `CLOSED`/`EMERGENCY_CLOSED`: нет FSM handler,
обязательны `resultProfit`/`resultProfitCurrency`.

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

## Restart / recovery

После рестарта система **не** ищет pending `ServiceCommand`
(`ServiceCommand` — runtime object, не persisted queue). FSM
восстанавливает состояние по: runtime graph `Deal`, external
dependencies `DealContext`, `DealActionState`, exchange refresh/
search/history facts. `DealActionState` показывает, какой
`StrategyAction` материализован, какой runtime target создан, какой
в retry / completed / failed / skipped, какой order/algoOrder нужно
amend/cancel. Audit/history **не** является runtime-source для FSM.
