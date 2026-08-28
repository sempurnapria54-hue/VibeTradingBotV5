# StrategyActionCalculator

## На какой вопрос отвечает этот файл

Кто рассчитывает runtime-параметры действия стратегии (компонент-
оркестратор расчёта): контракт, что объединяет, границы.

## Назначение

`StrategyActionCalculator` — оркестратор расчёта параметров действия
стратегии. Получает `StrategyAction + DealContext`, сам собирает свежие
данные и возвращает готовые параметры команды. Это не только калькулятор
цены: объединяет `CalculationContextFactory`, `PriceCalculator`,
`SizeCalculator` (см. соответствующие компоненты).

## Контракт

Возвращает `StrategyActionCalculationResult` (`SUCCESS` с
`CalculatedStrategyAction` либо `ERROR` с `CalculationError`):

```text
CalculationContext = factory.build(action, dealContext)
price = priceCalculator.calculate(context)
size  = sizeCalculator.calculate(context, price)
-> CalculatedStrategyAction(action, price, size)
```

Суб-калькуляторы при контролируемой ошибке расчёта бросают
`CalculationException`; `StrategyActionCalculator` перехватывает его и
возвращает `ERROR` с `CalculationError` (см.
`docs/components/models/CalculationError.md`).

## Границы

- **Не** вызывает `RiskValidator` напрямую и не возвращает
  `RiskValidationResult` / `CalculatedRiskMetrics` как часть успешного
  расчёта. После успешного расчёта handler/orchestration решает, нужна ли
  risk-policy validation (см. `docs/processes/risk-evaluation.md`).
- **Не** создаёт `ServiceCommand` — на базе рассчитанных параметров команду
  собирает per-type `StrategyActionExecutor` (`CreateOrderActionExecutor` /
  `CreateAlgoOrderActionExecutor`) под диспетчером
  `StrategyActionOrchestrator` (см.
  `docs/components/StrategyActionExecutor.md`,
  `docs/components/StrategyActionOrchestrator.md`).
- **Не** считает тяжёлые данные (индикаторы, структуру) — читает готовые
  результаты через сервисы (см.
  `docs/processes/market-data-calculation.md`).
- **Не** вызывает `REFRESH_BALANCE_COMMAND` / `IntegrationService` / OKX adapter;
  freshness баланса обеспечивает FSM/handler.
- **Не** рассчитывает data-dependent action, если FSM уже определила, что
  данные step устарели и `marketDataExpiredSetting` запрещает выполнение
  (см. `docs/rules/market-data-freshness.md`).
- Controlled calculation errors возвращает как `error`-result, перехватывая
  `CalculationException` суб-калькуляторов; unexpected exceptions в
  `CalculationError` не превращает — они ловятся на границе
  `DealOrchestratorJob` / FSM (см.
  `docs/rules/runtime-error-classification.md`).

`entryReason` / `entryStepType` в формулах не участвуют. Условность
`PROTECTION_SWITCHED` влияет только на выбор protection-switch flow, не на
формулы.
