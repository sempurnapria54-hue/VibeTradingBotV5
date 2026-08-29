# RiskCheckResult

## На какой вопрос отвечает этот файл

Что это за результат одной риск-проверки.

## Назначение

Результат одной конкретной проверки внутри результата преконтроля.
Неизменяемый runtime-объект, не хранится.

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `code` | `RiskCheckCode` | Машинный код проверки. |
| `status` | `RiskCheckStatus` | Исход конкретной проверки. |
| `actualValue` | `BigDecimal` | Фактическое значение, если проверка числовая. |
| `comment` | `String` | Короткое пояснение. |
| `details` | `Map<String, Object>` | Детали для диагностики. |

Отдельного поля предела нет: не у каждой проверки один понятный лимит, а
у сложных их несколько; при надобности предел кладётся в детали.

## Енум `RiskCheckStatus`

`PASSED`, `WARNING` (не блокирует), `BLOCKED`. В фазе 1 преконтроль
строит **только** отказы: прочие значения определены, но ни одна проверка
их не порождает.

## Енум `RiskCheckCode`

**Потолки риска:** `RISK_PER_ACTION_EXCEEDED`,
`RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED`, `DEAL_NOTIONAL_EXCEEDED`.

**Защита:** `RISK_CREATING_ENTRY_WITHOUT_STOP`,
`PROTECTION_COVERAGE_REDUCED`, `PROTECTION_LADDER_STEP_BELOW_MIN_SIZE`.

**Контур и инструмент:** `EXCHANGE_MAX_LEVERAGE_EXCEEDED`,
`MARGIN_MODE_NOT_ISOLATED`, `BORROW_OR_DEBT_DETECTED`,
`INSTRUMENT_NOT_LIVE`, `INSTRUMENT_RULES_MISSING`.

**Баланс и ставка:** `BALANCE_INVALID`, `BALANCE_NOT_FRESH`,
`BALANCE_NOT_ENOUGH`, `FEE_RATE_UNAVAILABLE`.

**Размер и уровни:** `CALCULATED_ACTION_INVALID`, `SIZE_BELOW_MIN`,
`SIZE_LOT_STEP_INVALID`, `SIZE_ABOVE_LIMIT`, `STOP_LOSS_INVALID_SIDE`,
`TAKE_PROFIT_INVALID_SIDE`, `STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`.

**Инварианты частичного выхода:** `PARTIAL_EXIT_NOT_REDUCE_ONLY`,
`PARTIAL_EXIT_INCREASES_POSITION`,
`DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`.

Коды отказа создания стратегии живут не здесь: их место — валидация
создания.

## Связи

- Кто строит — `docs/components/RiskValidator.md`.
- Что означают потолки — `docs/rules/risk-policy.md`.
- Покрытие защитой — `docs/rules/live-risk-protection.md`.
