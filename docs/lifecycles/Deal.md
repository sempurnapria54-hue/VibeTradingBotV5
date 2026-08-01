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
  **ошибочный терминал** контракта финализации; ставит
  `MARK_DEAL_EMERGENCY_CLOSED`; число `resultProfit` — **best-effort**
  (фактический realized net если доступен, иначе `null` с маркером
  «неисчислимо», **не ноль**; см. §«Терминальный контракт финализации»;
  DEAL-Q2 закрыт).

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

- **Чистое закрытие.** Число считает и **пишет на `Deal`** `FINALIZE_DEAL_EXIT`
  (net из positions-history + разбивка bills, в одной транзакции с его
  `COMPLETED`; N7). `MARK_DEAL_CLOSED` **ассертит** непустоту `Deal.resultProfit`
  и ставит **чистый терминал `CLOSED`** (число сам не пишет —
  `docs/decisions/pnl-finalization-mechanics.md` реш.2).
- Прибыль не посчиталась после исчерпания retry → это **ошибка** →
  `DealFinalizationState(MARK_CLOSED) = FAILED`, сделка уходит ошибочной
  тропой (`MarkDealErrorExecutor`/`ErrorHandler`) и доходит до **ошибочного
  терминала** (`EMERGENCY_CLOSED`). Сделка **всегда доходит до терминала, не
  зависает живым риском**.
- **Аварийный терминал `EMERGENCY_CLOSED`** ставит **`MARK_DEAL_EMERGENCY_CLOSED`**
  (`docs/components/MarkDealEmergencyClosedExecutor.md`, симметрично
  `MARK_DEAL_CLOSED`) с **best-effort числом** — **два провенанса разведены**
  (`docs/decisions/pnl-finalization-mechanics.md` реш.3):
  - **(a) ликвидация/ADL** (позицию закрыла биржа): `realizedPnl`+`liqPenalty`
    доступны (`type` 3-6) → пишем **фактический realized net**;
  - **(b) отказ расчёта** (чистая тропа не смогла): терминальное действие
    `MARK_DEAL_EMERGENCY_CLOSED` **вложенным шагом** ещё раз пробует добыть
    (`REFRESH_POSITIONS_HISTORY`, H13 `GAPS_CLOSE_6`); net доступен
    → пишем; **genuinely недоступен** → `resultProfit = null` c семантикой
    **«неисчислимо»** (**не ноль**), сделка терминализуется всё равно, факт
    помечается (лог/`AnomalyReport`).
  - **Маркер:** на `EMERGENCY_CLOSED` `resultProfit != null` = фактический net;
    `null` = «неисчислимо» — **отличимо от нуля** (ноль = посчитанный нулевой
    P&L). Инвариант «`resultProfit` обязателен» — только про **чистое** закрытие.
  - **Причина (торговая):** число **не зануляется** — недоступность помечается,
    null-случай исключается из R-выборки как unknown (не считается нулём),
    левый хвост R-распределения не усекается молча.

DEAL-Q2 закрыт в три захода: механика/терминальный контракт — `GAPS_CLOSE_1`
шага 6 (2026-06-22); *число* на ошибочном терминале (остаток DEAL-Q2, G5) —
`GAPS_CLOSE_1` шага 7 (2026-07-03); *провенанс-контракт исполним + владелец
терминала* (`MARK_DEAL_EMERGENCY_CLOSED`) — `GAPS_CLOSE_2` шага 7 (2026-07-04,
N8). *Расчёт* — шаг 7 (`docs/decisions/pnl-finalization-mechanics.md`).

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
