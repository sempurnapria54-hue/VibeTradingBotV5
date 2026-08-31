# Расчёт действия стратегии (strategy-action-calculation)

## На какой вопрос отвечает этот файл

Как устроен процесс расчёта параметров одного `StrategyAction`.

## Назначение

Между выбором действия (FSM) и созданием команды
(`StrategyActionOrchestrator` через per-type `StrategyActionExecutor`)
находится расчёт параметров. FSM выбирает допустимое действие, но не
считает цену/размер/риск; executor исполняет команду, но не пересчитывает
параметры. Процесс — точка композиции, вызывается из
`docs/processes/deal-management.md` (см.
`.claude/decisions/process-materialization-criterion.md`).

## Поток

```text
FSM / StateHandler
  -> выбрал StrategyAction
  -> StrategyActionCalculator
     -> CalculationContextFactory.build(action, dealContext)   # свежий context
     -> PriceCalculator.calculate(context)        -> CalculatedPrice
     -> SizeCalculator.calculate(context, price)  -> CalculatedSize
     -> StrategyActionCalculationResult (SUCCESS: CalculatedStrategyAction)
  -> handler решает, нужна ли risk-policy validation
  -> RiskValidator, если action создаёт / увеличивает риск или ослабляет контроль
  -> StrategyActionOrchestrator (per-type StrategyActionExecutor) -> ServiceCommand
```

Компоненты: `docs/components/StrategyActionCalculator.md`,
`CalculationContextFactory.md`, `PriceCalculator.md`, `SizeCalculator.md`.
RVO: `CalculationContext`, `CalculatedPrice`, `CalculatedSize`,
`CalculatedStrategyAction`, `StrategyActionCalculationResult`,
`CalculationError` (см. `docs/components/models/`).

## Scope

Один рассчитываемый action = один свежий `CalculationContext` (не один на
`StrategyStep`/проход): после каждого action могут измениться
Order/AlgoOrder/Position/Balance/market facts; `StrategyStep` не atomic
transaction.

## Границы

- Калькулятор **не** вызывает `RiskValidator`, не возвращает risk-policy
  результат и risk-метрики; решение `ALLOWED/WARNING/BLOCKED` — у
  `docs/processes/risk-evaluation.md`.
- **Не** создаёт команды (это `StrategyActionOrchestrator` через per-type
  `StrategyActionExecutor`) и не вызывает
  `REFRESH_BALANCE_COMMAND`/`IntegrationService`.
- Controlled calculation errors → `CalculationError` (суб-калькуляторы бросают
  `CalculationException`, `StrategyActionCalculator` перехватывает →
  `ERROR`-результат; `TEMPORARY`→RETRY_PENDING / `PERMANENT`→FAILED→Deal ERROR,
  **кроме отказа по стороне уровня** — он есть сработавший контроль и
  ведёт к неисполнению шага, а не к аварии
  (`docs/processes/risk-evaluation.md` — дом развязки исходов));
  unexpected exceptions ловятся на границе FSM (см.
  `docs/rules/runtime-error-classification.md`).
- Для reduce-only partial exit `RiskValidator` не вызывается; handler
  делает safety/invariant checks (`docs/rules/no-partial-close.md`,
  `docs/rules/risk-validator-scope.md`).
