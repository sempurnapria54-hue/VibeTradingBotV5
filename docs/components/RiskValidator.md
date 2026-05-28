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
`Position`, `InstrumentExternalRules`, `RiskSettings` (структура —
RISK-Q1, `.claude/work/questions/open-questions.md`).

## Метрики (считает сам)

risk amount; risk percent от депозита; estimated leverage; estimated
margin; notional; SL distance; liquidation guard distance; position
exposure после действия. Метрики могут попасть в `RiskCheckResult.details`,
логи или аудит, но **не** входят в `CalculatedStrategyAction`.

## Границы

- **Не** переводит сделку в другой статус и **не** создаёт
  `ServiceCommand`.
- **Не** обновляет баланс: не вызывает `REFRESH_BALANCE`, `ClientService`
  или OKX adapter. При absent/stale/invalid `BalanceContainer` возвращает
  `BLOCKED` (коды `BALANCE_NOT_FRESH` / `BALANCE_INVALID`), а не чинит
  snapshot сам.
- Вызывается только для risk-creating / risk-increasing / risk-weakening
  actions, после расчёта цены/размера и до создания торговой команды (см.
  `docs/rules/risk-validator-scope.md`).

Превращение `BLOCKED` в действие handler'а — у
`docs/components/RiskBlockResolver.md`.
