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

Сигнатура — `validate(CalculatedStrategyAction, DealContext)`. Из этих
двух входов `RiskValidator` сам извлекает цену/размер
(`CalculatedPrice`/`CalculatedSize` из `CalculatedStrategyAction`),
баланс/позицию/направление/`StrategyDetail`/`Instrument` (из
`DealContext`). `InstrumentExternalRules` **не** входной аргумент —
валидатор сам читает его через
`InstrumentExternalRulesDataService.findByInstrumentId`. Риск-настройки
(`riskPerTradePercent`) — из `StrategyDetail`; отдельного RVO
`RiskSettings` нет (см. `docs/decisions/per-trade-risk-policy.md`).

## Метрики (считает сам)

risk amount (убыток на стопе: `|entry − stop| × sizeContracts × ctVal +
commissions`, где `commissions` — прогноз вход+выход по taker-ставке из
`instrumentExternalRules.takerFeeRate()` (навес инструмента, N9 — не отдельный
fetch); **включён с шага 7** (G6), согласовано с `SizeCalculator`, см.
`docs/decisions/per-trade-risk-policy.md` §«Учёт комиссий (включён на шаге 7)»);
**risk percent от свободного депозита** (база —
`BalanceContainer.externalAvailableEquity`, не total/adjusted, см.
`docs/decisions/per-trade-risk-policy.md`); SL distance; liquidation guard
distance. Метрики могут попасть в `RiskCheckResult.details`, логи или
аудит, но **не** входят в `CalculatedStrategyAction`.

`position exposure после действия` — метрика **уровня риска на биржу/портфель**
(форвард к фазе 3); в фазе 1 (только риск на сделку) кода-блокера по экспозиции
нет (`docs/decisions/per-trade-risk-policy.md`).

## Конкретные проверки (фаза 1)

Fail-fast (возвращают `BLOCKED` сразу, без остальных проверок):

- `CALCULATED_ACTION_INVALID` — размер отсутствует / непозитивен;
- `INSTRUMENT_RULES_MISSING` — `InstrumentExternalRules` не
  материализованы;
- `BALANCE_INVALID` — `externalAvailableEquity` отсутствует /
  непозитивен.

Далее накапливаются (любой `BLOCKED` ⇒ итог `BLOCKED`):

- `INSTRUMENT_NOT_LIVE` — `rules.isLive()` ложно;
- `MARGIN_MODE_NOT_ISOLATED` — `Instrument.marginMode != ISOLATED`;
- `SIZE_BELOW_MIN` — размер ниже `minSize`;
- `SIZE_LOT_STEP_INVALID` — размер не кратен `lotSize`;
- `SIZE_ABOVE_LIMIT` — размер выше per-order лимита (лимит по
  `PriceMode`: `EXPLICIT` → `maxLimitSize`, иначе → `maxMarketSize`);
- `EXCHANGE_MAX_LEVERAGE_EXCEEDED` — `Instrument.leverage` >
  `externalMaxLeverage`;
- `STOP_LOSS_INVALID_SIDE` — стоп на неверной стороне относительно
  входа;
- `TAKE_PROFIT_INVALID_SIDE` — тейк на неверной стороне относительно
  входа;
- `STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION` — стоп за/у цены ликвидации
  позиции;
- `RISK_CREATING_ENTRY_WITHOUT_STOP` — risk-creating вход
  (открытие/наращивание позиции) без **резолвимого стопа**: `BLOCKED`,
  **без** fail-open allocation-сайзинга в обход `RISK_PER_TRADE`
  (инвариант `docs/rules/risk-creating-entry-protection.md`). Проверяется
  до риск-на-сделку: нет стопа → risk-amount нечем посчитать → блок, не
  сайзинг по allocation. Reduce-only/закрывающие действия не затрагивает
  (риск снимают);
- `RISK_PER_TRADE_EXCEEDED` — риск на сделку (%) выше
  `StrategyDetail.riskPerTradePercent`.

Агрегация: любой `BLOCKED` ⇒ `BLOCKED`; путь `WARNING` в коде есть
(аггрегатор его учитывает), но **ни одна проверка фазы 1 `WARNING` не
порождает** — все проверки строят `BLOCKED`.

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
