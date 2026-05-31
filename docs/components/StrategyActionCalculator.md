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

## Границы

- **Не** вызывает `RiskValidator` напрямую и не возвращает
  `RiskValidationResult` / `CalculatedRiskMetrics` как часть успешного
  расчёта. После успешного расчёта handler/orchestration решает, нужна ли
  risk-policy validation (см. `docs/processes/risk-evaluation.md`).
- **Не** создаёт `ServiceCommand` — на базе параметров команды создаёт
  `ServiceCommandFactory`.
- **Не** считает тяжёлые данные (индикаторы, структуру) — читает готовые
  результаты через сервисы (см.
  `docs/processes/market-data-calculation.md`).
- **Не** вызывает `REFRESH_BALANCE` / `IntegrationService` / OKX adapter;
  freshness баланса обеспечивает FSM/handler.
- **Не** рассчитывает data-dependent action, если FSM уже определила, что
  данные step устарели и `marketDataExpiredSetting` запрещает выполнение
  (см. `docs/rules/market-data-freshness.md`).
- Controlled calculation errors возвращает как `error`-result; unexpected
  exceptions в `CalculationError` не превращает — они ловятся на границе
  `DealOrchestratorJob` / FSM (см.
  `docs/rules/runtime-error-classification.md`).

`entryReason` / `entryStepType` в формулах не участвуют. Условность
`PROTECTION_SWITCHED` влияет только на выбор protection-switch flow, не на
формулы.
