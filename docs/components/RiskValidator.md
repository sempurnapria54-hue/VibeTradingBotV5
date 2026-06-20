# RiskValidator

## На какой вопрос отвечает этот файл

Кто проверяет рассчитанное действие по risk-policy (компонент): что
проверяет, что считает сам, чего не делает.

## Назначение

`RiskValidator` проверяет уже рассчитанное действие и отвечает на вопрос
«разрешено ли действие по risk-policy?», возвращая `RiskValidationResult`
(см. `docs/components/models/RiskValidationResult.md`). Сам считает или
запрашивает нужные risk-метрики.

## Входы

`CalculatedPrice`, `CalculatedSize`, `DealContext`, `BalanceContainer`,
`Position`, `InstrumentExternalRules`, `StrategyDetail` (риск-настройки —
`riskPerTradePercent`; отдельного RVO `RiskSettings` нет, см.
`docs/decisions/per-trade-risk-policy.md`).

## Метрики (считает сам)

risk amount (убыток на стопе: `|entry − stop| × sizeContracts × ctVal +
commissions`); **risk percent от свободного депозита** (база —
`BalanceContainer.externalAvailableEquity`, не total/adjusted, см.
`docs/decisions/per-trade-risk-policy.md`); estimated leverage; estimated
margin; notional; SL distance; liquidation guard distance. Метрики могут
попасть в `RiskCheckResult.details`, логи или аудит, но **не** входят в
`CalculatedStrategyAction`.

`position exposure после действия` — метрика **уровня риска на биржу/портфель**
(форвард к фазе 3); в фазе 1 (только риск на сделку) кода-блокера по экспозиции
нет (`docs/decisions/per-trade-risk-policy.md`).

## Границы

- **Не** переводит сделку в другой статус и **не** создаёт
  `ServiceCommand`.
- **Не** обновляет баланс: не вызывает `REFRESH_BALANCE`, `IntegrationService`
  или OKX adapter. При absent/stale/invalid `BalanceContainer` возвращает
  `BLOCKED` (коды `BALANCE_NOT_FRESH` / `BALANCE_INVALID`), а не чинит
  snapshot сам.
- Вызывается только для risk-creating / risk-increasing / risk-weakening
  actions, после расчёта цены/размера и до создания торговой команды (см.
  `docs/rules/risk-validator-scope.md`).

Превращение `BLOCKED` в действие handler'а — у
`docs/components/RiskBlockResolver.md`.
