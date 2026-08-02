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
ногой `REFRESH_POSITION_COMMAND` (H1/H3, `GAPS_CLOSE_7`; симметрично
`docs/components/FinalizeDealExitExecutor.md` §«Положение закрытия —
читается со строки»). Вложенной команды нет: факт durable, границу прохода
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
(`docs/decisions/pnl-finalization-mechanics.md` §3, `docs/lifecycles/Deal.md`
§«Терминальный контракт финализации»):

- **(a) реальная ликвидация/ADL** (позицию закрыла биржа —
  `Position.externalCloseType ∈ 3..6`): `realizedPnl` доступен полем
  `Position.externalRealizedProfit` → пишем **фактический realized net**.
- **(b) net недоступен** (чистая тропа не смогла посчитать → ушла в `ERROR`;
  поля положения закрытия на `Position` пусты — записи нет либо `Position`
  локально не заводилась): `resultProfit = null` с семантикой
  **«неисчислимо»** (**НЕ ноль**); сделка терминализуется **всё равно** (не
  зависает живым риском), факт помечается лог + `AnomalyReport`
  (`severity = NON_CRITICAL` — журнальная тропа без kill-switch,
  `docs/lifecycles/AnomalyReport.md`).
- **Жёсткий отказ чтения приравнивается к «пусто»** на этой тропе — не к
  провалу действия (H15, `GAPS_CLOSE_7`;
  `docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп отказа
  добычи»). Иначе исполнение уходит в `FAILED`, `SystemActionExecutor`
  команду больше не эмитит, и сделка зависает в `ERROR` вопреки инварианту
  «сделка всегда доходит до терминала».

**Маркер — nullability** (без нового поля): на `EMERGENCY_CLOSED` `resultProfit
!= null` = фактический net; `resultProfit == null` = «неисчислимо» — **отличимо
от посчитанного нуля** (ноль = вычисленный нулевой P&L). **Число не зануляется**
— недоступность помечается, не подменяется нулём (F-T1: null-случай исключается
из R-выборки как unknown, левый хвост не усекается молча). Инвариант
«`resultProfit` обязателен» — про чистое `CLOSED`; на аварийном терминале он
**не блокируется**.

## Терминальное ребро

`ERROR → EMERGENCY_CLOSED` (`docs/lifecycles/Deal.md`). `EMERGENCY_CLOSED` —
**ошибочный terminal**: FSM handler'а не имеет. Executor ставит терминал только
после подтверждённого `ErrorHandler`'ом снятия live risk (иначе — не
терминализирует, сделка остаётся под safety-flow в `ERROR`). Команду эмитит
`ErrorHandler`.

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
