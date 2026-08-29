# GAPS_CLOSE_2 — шаг 7 фазы 1 «Сделки и P&L»

## На какой вопрос отвечает этот файл

Как закрыты 13 пробелов `DOCS_CHECK_2` (стадии 1-2 под выбранный путь) — какая
механика финализации P&L материализована и какие носители заведены.

## Контекст

- **Под-шаг:** `GAPS_CLOSE_2` (процесс `roadmap-step-execution.md`), descend-закрытие
  стадий 1-2 после того как стадия 0 (источник) закрыта на `GAPS_CLOSE_1`.
- **Якорь:** новый `docs/decisions/pnl-finalization-mechanics.md` (механика:
  добыча фактов, staged-число, аварийный терминал, fee-seam, сверка, инвариант).
- **Исполнение:** ядро (командный слой, финализация, контракт, N7/N8/N9) — CC;
  независимые артефакты (native/снапшот, `DealCashFlow`, executor'ы, каскад снятия
  `REFRESH_FILLS`) — 4 параллельных субагента на **непересекающихся** наборах файлов.

## Закрытие по пробелам

### N6 (центр) — механика добычи P&L-фактов — ЗАКРЫТ

Выбор (реш.1): **новые refresh-команды `REFRESH_POSITIONS_HISTORY`** (наполняет
positions-history-снапшот — число) **+ `REFRESH_BILLS`** (наполняет `DealCashFlow`
— разбивка), по одной на новую сущность (паритет CMD-Q3). Эмитят `ExitPendingHandler`
(штатно) / `ErrorHandler` (аварийно) **до** финализации. `FinalizeDealExitExecutor`
**остаётся off-exchange** — читает готовые факты. Латентное противоречие снято.
Альтернативы (integration read вне command-layer; fetch внутри executor'а) — отвергнуты
(обоснование в decision). **Объединено с N12** (см. ниже): `REFRESH_FILLS` заменён.

### N1-N5 — носители пути — МАТЕРИАЛИЗОВАНЫ

- **N1:** native `docs/models/integrations/okx/OkxPositionsHistoryResponse.md` —
  used-минимум (`realizedPnl`, `ccy`, `closeAvgPx`/`openAvgPx`, `triggerPx`, `type`,
  `posId`, `uTime`); форвард-указатель в `OkxPositionResponse.md` снят.
- **N2:** boundary-снапшот **`PositionCloseResultExternalSnapshot`** (транзитный,
  не persisted; отдельной persisted доменной сущности нет — число → `Deal.resultProfit`)
  + маппинги native→snapshot→`Deal` — `docs/models/mapping/PositionCloseResult.md`.
- **N3:** модель **`DealCashFlow`** (`docs/models/domain/other/DealCashFlow.md`) —
  поля + enum `CashFlowCategory` (`TRADE_FEE`/`FUNDING`/`REBATE`/`LIQ_PENALTY`/
  `REALIZED_PNL`/`OTHER`); `ccy` обязателен (cross-ccy).
- **N4:** маппинг bills→`DealCashFlow` (`docs/models/mapping/DealCashFlow.md`) —
  поле-в-поле + резолв категории (`type`/`subType`→enum) в вызывающем коде.
- **N5:** персистенция — реляционная таблица **`deal_cash_flows`**, FK `deal_id`,
  **`UNIQUE(external_bill_id)`** (идемпотентность), индекс `deal_id`; **линковка**:
  bills не несут `dealId` → `RefreshBillsExecutor` матчит по окну begin/end + `instId`
  + `ccy` и проставляет `deal_id`. (+ N3 `ccy`-поле.)

### N7 — носитель staged-числа (дефект GAPS_CLOSE_1) — ИСПРАВЛЕН

Разрыв: число считалось в `FINALIZE_EXIT`, писалось `MARK_CLOSED` — слот между
командами не назначен. **Фикс (реш.2):** `FINALIZE_DEAL_EXIT` **пишет
`resultProfit` прямо на `Deal`** в одной транзакции с `DealFinalizationState(FINALIZE_EXIT)
= COMPLETED` → durable-носитель = само поле `Deal` (рестарт-safe: после COMPLETED
число уже на Deal). `MARK_DEAL_CLOSED` **ассертит** непустоту и терминализует (число
не пишет). Реконсилировано: `Deal.md` §Итоговый PnL, `FinalizeDealExitExecutor`,
`MarkDealClosedExecutor`, `DealFinalizationState`, `lifecycles/Deal.md`.

### N8 — аварийное число (дефект GAPS_CLOSE_1) — ИСПРАВЛЕН

Два пробела:
1. **Владелец:** step-6 не назначил запись `EMERGENCY_CLOSED` (нет команды-терминала).
   Введена **`MARK_DEAL_EMERGENCY_CLOSED`** (команда + `MarkDealEmergencyClosedExecutor` +
   `DealFinalizationType.MARK_EMERGENCY_CLOSED`), терминал `ERROR → EMERGENCY_CLOSED`,
   симметрично `MARK_DEAL_CLOSED`; эмитит `ErrorHandler`.
2. **Контракт исполним (провенанс разведён, реш.3):** (a) ликвидация/ADL — net
   доступен → фактический realized net; (b) отказ расчёта — net недоступен по
   определению → `resultProfit = null` с маркером **«неисчислимо»** (**не ноль**),
   сделка терминализуется всё равно, помечается `AnomalyReport`. Маркер = nullability
   (без нового поля). Число **не зануляется** — null исключается из R-выборки как
   unknown, левый хвост не усекается (F-T1). Реконсилировано: `lifecycles/Deal.md`
   §Терминальный контракт + `EMERGENCY_CLOSED`, `Deal.md`, `ErrorHandler`,
   `MarkDealErrorExecutor`, `ServiceCommand`, `DealFinalizationCommandFactory`.

### N9 — поток ставки trade-fee — ЗАКРЫТ (без нового seam)

Ставка **живёт на `InstrumentExternalRules`** (навес инструмента, поля
`externalTakerFeeRate`/`externalMakerFeeRate` + аксессоры), дочитывается
`InstrumentExternalRulesSyncJob`. `SizeCalculator`/`RiskValidator` читают taker через
**уже присутствующий** `CalculationContext.instrumentExternalRules` — **новое поле
контекста и exchange-вызов из калькулятора не нужны** (реш.4). Реконсилировано:
`InstrumentExternalRules`, `SizeCalculator`, `RiskValidator`, `trade-fee.md`,
`per-trade-risk-policy.md`.

### N10 — реакция на расхождение сверки — ЗАКРЫТ

Число **всегда** = positions-history net (bills не подменяют). Расхождение сверх
epsilon / cross-ccy → **`AnomalyReport`** (аудит, `scope = INSTRUMENT`), **не
блокирует** финализацию (реш.5). Epsilon = `max(0.01 settle-ccy, 0.5%·|net|)` —
**провизорный, на подтверждение пользователя**. Cross-ccy (OKB fee) — допущение
«комиссии в settle-ccy» + guard (не отбрасывать молча). Реконсилировано:
`FinalizeDealExitExecutor`, `account-bills.md`, `result-profit-source.md`.

### N11 — инвариант агрегации positions-history — ВЫПИСАН + рантайм-верификация

Инвариант (один `posId` → одна финализированная запись, `realizedPnl` кумулятивен по
partial-закрытиям) выписан в `contracts/position.md` §История. **Гейтит корректность
числа до CODE** → рантайм-верификация: `.claude/tests/source-api/okx/plan.md` §AG1.5
(⏳ **PENDING** — содержательная фикстура-цепочка на demo; если OKX не агрегирует —
путь корректируется). Провенанс — реш.6.

### N12 — диспозиция REFRESH_FILLS — СНЯТ (объединён с N6)

`REFRESH_FILLS` **снят**: убран из `ServiceCommandType`, удалён `RefreshFillsExecutor.md`,
каскад по ~15 докам (enum, evidence-cycle, `fills.md`, `risk-validator-scope`, 6
handler'ов, `Order`/`Position` lifecycle+mapping, `OkxFillResponse`). Order-fill-метрики
подтверждены из `REFRESH_ORDER` (`accFillSz`/`avgPx`); fills-эндпоинты — не-runtime
(справочно, OKX-Q2).

### N13 — funding как holding-cost — форвард зафиксирован

Разделяющий довод «комиссию включаем (round-trip execution-cost в R), funding — нет
(time-accruing holding-cost вне входного R)» зафиксирован в `per-trade-risk-policy.md`
§«Учёт комиссий»; форвард-дом издержки удержания — **шаг ожидаемости/бэктеста (фаза 2)**;
scope — тонкий хвост пользователя. Не гейтит.

## Новые артефакты

- **Решение:** `docs/decisions/pnl-finalization-mechanics.md`.
- **Модели/mapping:** `OkxPositionsHistoryResponse.md`, `mapping/PositionCloseResult.md`,
  `domain/other/DealCashFlow.md`, `mapping/DealCashFlow.md`.
- **Компоненты:** `RefreshPositionsHistoryExecutor.md`, `RefreshBillsExecutor.md`,
  `MarkDealEmergencyClosedExecutor.md`; **удалён** `RefreshFillsExecutor.md`.
- **Команды:** enum `ServiceCommandType` 16 → **18** (+`REFRESH_POSITIONS_HISTORY`,
  +`REFRESH_BILLS`, +`MARK_DEAL_EMERGENCY_CLOSED`, −`REFRESH_FILLS`).

## Реконсилированные доки (ядро)

`ServiceCommand`, `DealFinalizationCommandFactory`, `DealFinalizationState`,
`FinalizeDealExitExecutor`, `MarkDealClosedExecutor`, `MarkDealErrorExecutor`,
`ExitPendingHandler`, `ErrorHandler`, `lifecycles/Deal.md`, `Deal.md`,
`InstrumentExternalRules`, `SizeCalculator`, `RiskValidator`, `trade-fee.md`,
`result-profit-source.md`, `per-trade-risk-policy.md`, `coverage-manifest.md`,
`account-bills.md`/`position.md` (агенты), каскад N12 (~15 доков). Пайплайн:
`backlog.md` §Шаг 7, `roadmap/phase-1.md`, `tests/source-api/okx/plan.md` §AG1.5.

## Два дефекта GAPS_CLOSE_1, пойманных DOCS_CHECK_2 и исправленных здесь

- **N7** — двухкомандный split (`FINALIZE_EXIT` считает / `MARK_CLOSED` пишет) без
  durable-носителя между ними. Внесён на GAPS_CLOSE_1 при назначении владельца расчёта.
- **N8** — контракт G5 «на `EMERGENCY_CLOSED` всегда фактический net» неисполним для
  провенанса отказа расчёта (net недоступен по определению). Внесён на GAPS_CLOSE_1.

Оба пойманы независимыми ревьюерами (не автором реконсиляции) — ради чего они и гонялись
врозь.

## Исход

**13 пробелов закрыты; стадии 1-2 доспецифицированы.** Остаётся исполнительный хвост:
**CODE** (носители/команды/расчёт/удаление `REFRESH_FILLS`) + **N11 рантайм-верификация**
(гейтит CODE) + **N13 форвард** (фаза 2). **→ подтверждающий `DOCS_CHECK_3`.** Дельта
staged для коммита в IDEA.
