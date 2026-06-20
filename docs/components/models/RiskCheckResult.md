# RiskCheckResult

## На какой вопрос отвечает этот файл

Что это за runtime value object `RiskCheckResult`: структура, енумы
`RiskCheckStatus` и `RiskCheckCode`.

## Назначение

`RiskCheckResult` — результат одной конкретной risk-проверки внутри
`RiskValidationResult` (см.
`docs/components/models/RiskValidationResult.md`). RVO, не persisted (см.
`.claude/decisions/runtime-value-object.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `code` | `RiskCheckCode` | Машинный код проверки. |
| `status` | `RiskCheckStatus` | Результат конкретной проверки. |
| `actualValue` | `BigDecimal` | Фактическое значение, если проверка числовая. |
| `comment` | `String` | Короткое пояснение. |
| `details` | `Map<String, Object>` | Дополнительные детали для диагностики. |

`limitValue` отдельным полем не вводится: не у каждой проверки один
понятный лимит, для сложных проверок порогов может быть несколько; при
необходимости лимит кладётся в `details`.

## Енум `RiskCheckStatus`

`PASSED`, `WARNING` (не блокирует), `BLOCKED`.

## Енум `RiskCheckCode`

Стартовый набор кодов:
`RISK_PER_TRADE_EXCEEDED`,
`EXCHANGE_MAX_LEVERAGE_EXCEEDED`, `MARGIN_MODE_NOT_ISOLATED`,
`BORROW_OR_DEBT_DETECTED`, `BALANCE_NOT_ENOUGH`, `BALANCE_NOT_FRESH`,
`BALANCE_INVALID`, `SIZE_BELOW_MIN`, `SIZE_LOT_STEP_INVALID`,
`SIZE_ABOVE_LIMIT`, `STOP_LOSS_INVALID_SIDE`, `TAKE_PROFIT_INVALID_SIDE`,
`STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`, `PARTIAL_EXIT_NOT_REDUCE_ONLY`,
`PARTIAL_EXIT_INCREASES_POSITION`, `DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`,
`MULTIPLE_POSITIONS_DETECTED`, `POSITION_STATE_UNKNOWN`,
`INSTRUMENT_NOT_LIVE`, `INSTRUMENT_RULES_MISSING`,
`CALCULATED_ACTION_INVALID`.

Часть кодов — не risk-policy проверки `RiskValidator`, а safety/invariant
violation exit-flow через reduce-only `Order`/`AlgoOrder`:
`PARTIAL_EXIT_NOT_REDUCE_ONLY`, `PARTIAL_EXIT_INCREASES_POSITION`,
`DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN` (см.
`docs/rules/no-partial-close.md`, `docs/rules/risk-validator-scope.md`).

Market data expired/missing — **не** risk-code первого уровня: сначала
обрабатывается через `MarketDataExpirationChecker` и
`StrategyStep.marketDataExpiredSetting` (см.
`docs/rules/market-data-freshness.md`).

### Риск на сделку и экспозиция (фаза 1)

`RISK_PER_TRADE_EXCEEDED` — главная проверка фазы 1 (риск на сделку): убыток
на стопе как % от свободного депозита превышает лимит
`StrategyDetail.riskPerTradePercent`. Срабатывает в том числе когда сделка не
укладывается в лимит **даже на минимальном размере инструмента** (`minSz`) —
строгое блокирование без открытия (см.
`docs/decisions/per-trade-risk-policy.md`).

Отдельного кода контроля **экспозиции/позиционного лимита поверх биржевого
максимума** в фазе 1 **нет**: такой guard относится к уровню риска на
биржу/портфель (фаза 3), в фазе 1 единственная позиция ограничена лимитом
риска на сделку. `EXCHANGE_MAX_LEVERAGE_EXCEEDED` остаётся как предел биржи
(не наш кэп плеча).
