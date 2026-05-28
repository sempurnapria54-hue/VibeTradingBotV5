# Оценка рисков (risk-evaluation)

## На какой вопрос отвечает этот файл

Как устроен процесс оценки риска: когда вызывается, как обрабатывается
результат, какова реакция на BLOCKED.

## Назначение

Risk-layer отвечает на вопрос: можно ли выполнить уже рассчитанное
действие при текущем состоянии сделки, позиции, баланса, инструмента и
биржи. Не выбирает стратегию, не проверяет сигнал, не исполняет команды.
Процесс — точка композиции, вызывается из
`docs/processes/deal-management.md` (см.
`.claude/decisions/process-materialization-criterion.md`).

## Поток (risk-creating / risk-increasing action)

```text
FSM handler
  -> выбрал StrategyStep, проверил условие, выбрал StrategyAction
  -> StrategyActionCalculator -> CalculatedPrice / CalculatedSize
  -> RiskValidator -> RiskValidationResult
  -> RiskBlockResolver -> RiskBlockAction (если BLOCKED)
  -> ServiceCommandFactory -> одна актуальная ServiceCommand, если разрешено
```

Компоненты: `docs/components/RiskValidator.md`,
`docs/components/RiskBlockResolver.md`. RVO: `RiskValidationResult`,
`RiskCheckResult`, `RiskBlockAction` (см. `docs/components/models/`).

## Когда вызывается

См. `docs/rules/risk-validator-scope.md`: только для risk-creating /
risk-increasing / risk-weakening actions; не для refresh/search/history,
cleanup/safety, finalization и reduce-only partial exit. Перед
risk-sensitive action handler обеспечивает fresh `BalanceContainer`; при
absent/stale — `REFRESH_BALANCE` и новый проход FSM (на этой итерации
`RiskValidator` не вызывается).

## Реакция на результат

```text
PRECHECK + BLOCKED + live risk ещё нет
  -> Deal.status = CLOSED, closeReason = RISK_CONTROL   # не авария

ENTRY_SUBMITTED / ENTRY_FINALIZED / PROTECTION_SWITCHED / MANAGING + BLOCKED
  (для risk-creating / risk-weakening action)
  -> Deal.status = ERROR -> ErrorHandler / safety-flow

WARNING  -> action не блокируется; фиксируется в логах / истории
ALLOWED  -> ServiceCommandFactory создаёт команду
```

`RISK_CONTROL` отличается от `ENTRY_CONDITION_EXPIRED`: первое — вход
запрещён risk-policy до live risk; второе — входное условие стало false.
Отдельный `ENTRY_RISK_BLOCKED` не используется (но ср. конфликт ENUM-Q1,
`.claude/work/questions/open-questions.md`). Реакция на BLOCKED как
статусная механика — `docs/lifecycles/Deal.md`.

## Несколько actions в одном StrategyStep

Actions выполняются последовательно; для каждого — свежий
`CalculationContext` → расчёт → (risk-validation либо minimal
safety/invariant checks) → одна актуальная команда. Если любое
risk-creating действие step заблокировано — остальные actions step не
выполняются (для `PRECHECK` без live risk → закрытие candidate Deal без
ошибки; для этапов с live risk → `ERROR` и safety-flow). Если exit /
cleanup / safety действие не прошло minimal checks — step останавливается,
flow уходит в recovery / ERROR / safety по контексту.

## Границы с ошибками

`CalculationError` (не смогли безопасно рассчитать) ≠ `RiskValidationResult`
(рассчитали, проверили по policy) ≠ unexpected exception
(`RuntimeErrorCode`, см. `docs/rules/runtime-error-classification.md`).
`CalculatedRiskMetrics` из calculator-layer не передаётся — risk-layer сам
считает нужные метрики.
