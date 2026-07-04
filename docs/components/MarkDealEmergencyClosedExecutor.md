# MarkDealEmergencyClosedExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `MARK_DEAL_EMERGENCY_CLOSED` (компонент-executor): аварийное
терминальное ребро, что читает/пишет, best-effort число и его провенанс,
идемпотентность, retry-anchor.

## Назначение

Получает `MARK_DEAL_EMERGENCY_CLOSED` — **аварийное терминальное ребро**
`ERROR → EMERGENCY_CLOSED`, **симметрично `MARK_DEAL_CLOSED`** (штатному
терминалу). **Читает** подтверждённое отсутствие live risk (снято/доказано
`ErrorHandler` перед терминалом, `docs/components/ErrorHandler.md`) и
`PositionCloseResultExternalSnapshot` (добыт `REFRESH_POSITIONS_HISTORY` на
аварийной тропе). **Пишет** терминал `Deal.status = EMERGENCY_CLOSED` +
`closeReason = EMERGENCY_CLOSE` + **best-effort число** `resultProfit`/
`resultProfitCurrency` + `DealFinalizationState(MARK_EMERGENCY_CLOSED).status =
COMPLETED`. Торговых решений не принимает; `RiskValidator` не вызывается
(`docs/rules/risk-validator-scope.md`).

## Число — best-effort, провенанс (реш.3)

Число на аварийном терминале — **best-effort**, два провенанса разведены
(`docs/decisions/pnl-finalization-mechanics.md` §3, `docs/lifecycles/Deal.md`
§«Терминальный контракт финализации»):

- **(a) реальная ликвидация/ADL** (позицию закрыла биржа, `type` 3-6):
  `realizedPnl` + `liqPenalty` доступны из positions-history-снапшота → пишем
  **фактический realized net**.
- **(b) net недоступен** (чистая тропа не смогла посчитать → ушла в `ERROR`;
  повторная добыча `ErrorHandler`'ом тоже пуста): `resultProfit = null` с
  семантикой **«неисчислимо»** (**НЕ ноль**); сделка терминализуется **всё
  равно** (не зависает живым риском), факт помечается лог + `AnomalyReport`.

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

- **Retry-anchor** — `DealFinalizationState(deal, MARK_EMERGENCY_CLOSED)` (база
  `Retryable`, см.
  `docs/decisions/deal-finalization-state-materialization.md`).
- **Идемпотентность** — через `UNIQUE(deal_id, finalization_type)`: повтор на
  уже `EMERGENCY_CLOSED`-сделке — no-op → `COMPLETED`.
- Падение → `RETRY_PENDING`/`FAILED` (`docs/components/RetryPolicyService.md`,
  `docs/rules/runtime-error-classification.md`).

Общая семантика финализационной группы —
`docs/components/ServiceCommandExecutor.md`; модель retry-state —
`docs/models/domain/other/DealFinalizationState.md`.
