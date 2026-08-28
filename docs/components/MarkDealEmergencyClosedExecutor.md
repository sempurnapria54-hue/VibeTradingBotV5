# MarkDealEmergencyClosedExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` (компонент-executor): аварийное
терминальное ребро, что читает/пишет, best-effort число и его провенанс,
идемпотентность, retry-anchor.

## Назначение

Получает `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` — **аварийное терминальное ребро**
`ERROR → EMERGENCY_CLOSED`, **симметрично `MARK_DEAL_CLOSED_COMMAND`** (штатному
терминалу). **Читает** подтверждённое отсутствие live risk (снято/доказано
`ErrorHandler` перед терминалом, `docs/components/ErrorHandler.md`) и
**положение закрытия на `Position`** — persisted-поля, наполненные второй
ногой `REFRESH_POSITION_COMMAND`. Вложенной команды нет: факт durable, границу прохода
FSM пересекает штатно, добыл его тот же `REFRESH_POSITION_COMMAND`, которым
`ErrorHandler` доказывал отсутствие позиции. **Пишет** терминал
`Deal.status = EMERGENCY_CLOSED` +
`closeReason = EMERGENCY_CLOSE` + **best-effort число** `resultProfit`/
`resultProfitCurrency` — в одной транзакции с завершением своего
исполнения `FINALIZE_DEAL_ERROR_ACTION` (второе исполнение действия —
терминал; `docs/components/SystemActionExecutor.md`). Торговых решений не
принимает; `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Число — best-effort, провенанс (реш.3)

Число на аварийном терминале — **best-effort**, два провенанса разведены
(`docs/rules/pnl-reconciliation.md`, `docs/lifecycles/Deal.md`):

- **(a) запись закрытия добыта**:
  `realizedPnl` доступен полями `Position.externalRealizedProfit` строк
  эпизодов → число считается **по той же формуле,
  что на чистой тропе**: Σ net по эпизодам **плюс cross-ccy-слагаемое**
  Σ(`amount` × `appliedRate`) по строкам cross-ccy-области
  (конъюнктивный предикат —).
- **(b) net недоступен** (чистая тропа не смогла посчитать → ушла в `ERROR`;
  поля положения закрытия на `Position` пусты — записи нет либо `Position`
  локально не заводилась): `resultProfit = null` с семантикой
  **«неисчислимо»** (**НЕ ноль**); сделка терминализуется **всё равно** (не
  зависает живым риском), факт помечается лог + `AnomalyReport`
  (`severity = NON_CRITICAL` — журнальная тропа без kill-switch,
  `docs/lifecycles/AnomalyReport.md`).

## Состав числа — тот же, что у штатной тропы (H12 `DOCS_CHECK_10`)

- **Операнды на аварийной тропе есть настолько, насколько добыты до неё.**
  Bills-звено в аварийный цикл добычи **не входит вовсе**. Значит движения аварийная тропа **не добывает**, и
  сверка на ветви (a) гоняется тогда и только тогда, когда движения
  **добывались**, пока сделка шла выходной тропой (`Deal.billsFetchedThrough`
  непуст — третий конъюнкт обязанности); иначе признак остаётся
  `NOT_RUN` + журнальный `AnomalyReport`. **Операнд — не наличие
  строк:** пустой набор движений тоже добытый факт, и «строки
  залинкованы» в этой роли запрещено
  (`docs/components/SystemActionExecutor.md`,
  `docs/models/domain/aggregate/Deal.md`). `appliedRate` зафиксирован **на записи**
  движения (`docs/components/RefreshBillsExecutor.md`) —
  терминал за котировкой на биржу **не ходит**, контракт «терминал
  off-exchange» держится. Цена складывается из одного: чтение строк
  `deal_cash_flows` по `deal_id` (индекс уже есть). Новых полей, вызовов
  биржи и миграций правка не добавляет.

**Маркер — nullability** (без нового поля): на `EMERGENCY_CLOSED` `resultProfit
!= null` = фактический net; `resultProfit == null` = «неисчислимо» — **отличимо
от посчитанного нуля** (ноль = вычисленный нулевой P&L). **Число не зануляется**
— недоступность помечается, не подменяется нулём (F-T1: null-случай в расчёт
ожидаемости не входит как unknown, левый хвост не усекается молча; сама сделка
из популяции не выводится). Инвариант
«`resultProfit` обязателен» — про чистое `CLOSED`; на аварийном терминале он
**не блокируется**.

## Реакция на расхождение сверки — симметрично штатной тропе

**Расхождение сверки за калиброванным допуском поднимает биржевую
ступень 1**. Правило одно на обе
тропы и живёт в доме — `docs/rules/pnl-reconciliation.md`; здесь только исполнительская половина: при
`Deal.reconciliationStatus = MISMATCHED` и боевом режиме допуска
executor зовёт `HoldService.hold(HoldSignal.exchange(
PNL_RECONCILIATION_MISMATCH, exchangeId))` **после коммита** транзакции,
применившей терминал.
Разбор, почему актор — терминальное звено, а не финализатор, и почему
вызов вынесен за коммит:
`docs/components/MarkDealClosedExecutor.md`.

**На ветви (b)** (сверка не была обязана — `NOT_RUN`) ветка не
срабатывает по построению: расхождения не измеряли.

## Терминальное ребро

`ERROR → EMERGENCY_CLOSED` (`docs/lifecycles/Deal.md`). `EMERGENCY_CLOSED` —
**ошибочный terminal**: FSM handler'а не имеет. Executor ставит терминал только
после подтверждённого `ErrorHandler`'ом снятия live risk (иначе — не
терминализирует, сделка остаётся под safety-flow в `ERROR`). **Действие
инициирует `ErrorHandler`; команду эмитит звено
`FINALIZE_DEAL_ERROR_ACTION`** (второе исполнение) — handler `MARK_*`
напрямую не эмитит, канон `docs/processes/fsm-execution-layering.md`; симметрично `MARK_DEAL_CLOSED_COMMAND`
(`docs/components/MarkDealClosedExecutor.md`).

**Терминал закрывает живые SYSTEM-исполнения сделки — своей
транзакцией.** Применив терминал, executor зовёт
`SystemActionExecutor.reviseLiveExecutions(dealContext)`: терминализованная
сделка в выборку следующего прохода **не попадает**, поэтому её живые
SYSTEM-строки не закроет никто, кроме ребра, применившего терминал.
Предикат неактуальности и довод —
`docs/components/SystemActionExecutor.md`.

## Идемпотентность и retry

- **Retry-anchor** — строка исполнения `FINALIZE_DEAL_ERROR_ACTION`
  (второе исполнение действия; вид SYSTEM, база `Retryable`;
  `docs/models/domain/other/DealActionState.md`).
- **Идемпотентность** — факт `Deal.status = EMERGENCY_CLOSED`: повтор на
  уже терминализованной сделке — no-op.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; эмиссия звеньев —
`docs/components/SystemActionExecutor.md`.
