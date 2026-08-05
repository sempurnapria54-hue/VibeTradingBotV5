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
  `MARK_DEAL_EMERGENCY_CLOSED_COMMAND`; число `resultProfit` — **best-effort
  по доступности, но не по составу**: доступен net — считается по той же
  формуле, что на чистой тропе; иначе `null` с маркером «неисчислимо»,
  **не ноль** (см. §«Терминальный контракт финализации»; DEAL-Q2 закрыт).

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
  (биржевой холд, каскад)
Market data expired (по policy)     -> shutdownReason = MARKET_DATA_EXPIRED
```

**Инструмент-холд и биржевой холд разведены** (H21, `GAPS_CLOSE_7`).
Прежняя строка склеивала «Exchange/Instrument/Account HOLD» в один
`EXCHANGE_HOLD` — это расходилось и с правилом
(`docs/rules/instrument-hold.md` §Enforcement,
`docs/models/domain/core/Instrument.md` §Енумы), и с кодом
(`DealOrchestratorJob.enforceHold`), где холд **инструмента** проставляет
`RISK_POLICY`, а `EXCHANGE_HOLD` приходит только каскадом от биржевой
строки. Эта таблица — носитель, в который полезет писатель шага 7, поэтому
расхождение снято здесь, а не «где-нибудь ещё».

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

Финализация использует **общий механизм повторов** — строки исполнений
системных действий (`docs/models/domain/other/DealActionState.md`, вид
SYSTEM; `docs/decisions/command-action-boundary.md`). Граничный контракт
между механикой финализации (шаг 6) и расчётом прибыли (шаг 7):

- **Чистое закрытие.** Число считает и **пишет на `Deal`** `FINALIZE_DEAL_EXIT_COMMAND`
  (net из положения закрытия на `Position` + разбивка bills, в одной
  транзакции с продвижением своего исполнения; N7). `MARK_DEAL_CLOSED_COMMAND`
  **ассертит** непустоту `Deal.resultProfit` и ставит **чистый терминал
  `CLOSED`** (число сам не пишет —
  `docs/decisions/pnl-finalization-mechanics.md` реш.2).
- Прибыль не посчиталась после исчерпания бюджета добычи/финализации → это
  **ошибка** → исполнение (`REFRESH_DEAL_CONTEXT_ACTION` /
  `FINALIZE_DEAL_EXIT_ACTION`) в `FAILED`, сделка уходит ошибочной тропой
  (`MarkDealErrorExecutor`/`ErrorHandler`) + **холд инструмента** и доходит
  до **ошибочного терминала** (`EMERGENCY_CLOSED`). Сделка **всегда доходит
  до терминала, не зависает живым риском**.
- **Аварийный терминал `EMERGENCY_CLOSED`** ставит **`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`**
  (`docs/components/MarkDealEmergencyClosedExecutor.md`, симметрично
  `MARK_DEAL_CLOSED_COMMAND`) с **best-effort числом** — **два провенанса разведены**
  (`docs/decisions/pnl-finalization-mechanics.md` реш.3):
  - **(a) ликвидация/ADL** (позицию закрыла биржа —
    `Position.externalCloseType ∈ 3..6`): net доступен полем
    `Position.externalRealizedProfit` → число считается **по той же
    формуле, что на чистой тропе** (net + cross-ccy-слагаемое): best-effort
    относится к **доступности** числа, не к его **составу** (H12
    `DOCS_CHECK_10`; формулировка «фактический realized net» снята H18
    `DOCS_CHECK_11` — она читалась как «на аварийной тропе слагаемого
    нет» и молча завышала число в корзине, которая и так смещена);
  - **(b) отказ расчёта** (чистая тропа не смогла): перед терминалом
    `ErrorHandler` гоняет `REFRESH_POSITION_COMMAND`, и её **вторая нога**
    (positions-history) ещё раз пробует добыть положение закрытия на
    `Position` (H1/H3 `GAPS_CLOSE_7`); net есть → пишем;
    **genuinely недоступен** → `resultProfit = null` c семантикой
    **«неисчислимо»** (**не ноль**), сделка терминализуется всё равно, факт
    помечается (лог/`AnomalyReport`). **Жёсткий отказ чтения на этой тропе
    приравнивается к «недоступно»**, а не к провалу действия — иначе сделка
    зависает в `ERROR` вопреки инварианту «всегда доходит до терминала»
    (H15, `GAPS_CLOSE_7`; `docs/decisions/pnl-finalization-mechanics.md`
    §«Асимметрия троп отказа добычи»).
  - **Маркер:** на `EMERGENCY_CLOSED` `resultProfit != null` = посчитанное
    число; `null` = «неисчислимо» — **отличимо от нуля** (ноль = посчитанный
    нулевой P&L). Инвариант «`resultProfit` обязателен» — только про
    **чистое** закрытие.
  - **Причина (торговая):** число **не зануляется** — недоступность помечается,
    null-случай исключается из R-выборки как unknown (не считается нулём),
    левый хвост R-распределения не усекается молча.

### Смягчение по валюте на тропах без входа (H1 `DOCS_CHECK_11`)

Инвариант чистого `CLOSED` — про **два** поля: `resultProfit` и
`resultProfitCurrency`. На **тропах закрытия без входа** (три тропы,
`docs/rules/trading-constraints.md` §«Гейт открытия сделки») число и валюту
пишет `MARK_DEAL_CLOSED_COMMAND` сам, второй веткой
(`docs/components/MarkDealClosedExecutor.md` §«Вторая ветка»), и валюта
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

DEAL-Q2 закрыт в три захода: механика/терминальный контракт — `GAPS_CLOSE_1`
шага 6 (2026-06-22); *число* на ошибочном терминале (остаток DEAL-Q2, G5) —
`GAPS_CLOSE_1` шага 7 (2026-07-03); *провенанс-контракт исполним + владелец
терминала* (`MARK_DEAL_EMERGENCY_CLOSED_COMMAND`) — `GAPS_CLOSE_2` шага 7 (2026-07-04,
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
