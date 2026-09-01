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
`SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET`,
`RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED`, `DEAL_NOTIONAL_EXCEEDED`.

**Две тропы одного превышения разведены значением, а не операндом.**
`RISK_PER_ACTION_EXCEEDED` означает **расхождение расчёта**: сайзинг мог
уложить действие в поактный потолок и не уложил.
`SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET` означает **неделимый лот**: размер
поднят до `instrument.minSize`, и на нём потолок превышен — ветвь подъёма
потолком не ограничена вовсе (`docs/rules/risk-policy.md`). Один код на оба
исхода делает карв-аут неразрешимым: у эталона при малой базе неделимый
минимальный лот превышает бюджет **каждым** входом, и сделка без живого
риска уходила бы в аварийный контур по ожидаемому отказу. Различение сделано
значением, чтобы «не проверяли» и «проверили, лот неделим» различались **в
данных**, а не вычислялись операндом (`docs/concept.md` П3).

## Бессрочность отказа

**Признак объявляется рядом со значением** — здесь, а не в карте реакции:
второй носитель разошёлся бы с этим первой же правкой
(`.claude/rules/carrier-levels.md`). Отказ **бессрочен**, когда повторная
проверка того же действия не даст иного исхода без изменения стратегии;
**временный** — когда исход зависит от состояния, которое меняется само.

| Код | Отказ |
|---|---|
| `RISK_PER_ACTION_EXCEEDED` | бессрочный — расчёт разошёлся, повтор даст то же |
| `SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET` | бессрочный — лот неделим, база от прохода к проходу его не поделит |
| `RISK_PER_DEAL_CUMULATIVE_EXCEEDED`, `RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`, `RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED`, `DEAL_NOTIONAL_EXCEEDED` | **временный** — бюджет освободится выходом соседнего транша |
| `BALANCE_NOT_ENOUGH`, `BALANCE_NOT_FRESH`, `FEE_RATE_UNAVAILABLE`, `DEAL_GRAPH_INCOMPLETE`, `LOSS_LIMIT_NOT_CONFIGURED`, `RISK_APPETITE_NOT_CONFIGURED` | **временный** — операнд добудется следующим проходом либо придёт правкой конфигурации |
| `INSTRUMENT_NOT_LIVE`, `INSTRUMENT_RULES_MISSING`, `INSTRUMENT_SETTLE_CURRENCY_MISSING`, `EXCHANGE_MAX_LEVERAGE_EXCEEDED`, `MARGIN_MODE_NOT_ISOLATED`, `BORROW_OR_DEBT_DETECTED` | бессрочные — состояние контура сменится не проходом, а внешним действием |
| прочие (конфигурация, стороны уровней, инварианты частичного выхода) | бессрочные — меняются только правкой стратегии |

Признак читает карта реакции (`docs/components/RiskBlockResolver.md`
§«Карта «вердикт → действие»»); свёртка многокодового вердикта берёт
**конъюнкцию**: вердикт бессрочен, только если бессрочны все его коды.

**Защита:** `RISK_CREATING_ENTRY_WITHOUT_STOP`,
`PROTECTION_COVERAGE_REDUCED`, `PROTECTION_LADDER_STEP_BELOW_MIN_SIZE`.

**Контур и инструмент:** `EXCHANGE_MAX_LEVERAGE_EXCEEDED`,
`MARGIN_MODE_NOT_ISOLATED`, `BORROW_OR_DEBT_DETECTED`,
`INSTRUMENT_NOT_LIVE`, `INSTRUMENT_RULES_MISSING`,
`INSTRUMENT_SETTLE_CURRENCY_MISSING`.

`INSTRUMENT_SETTLE_CURRENCY_MISSING` — не `SETTLE_CURRENCY_UNAVAILABLE`:
последнее имя занято значением `DealCashFlow.RateStatus` и означает другое
(курс не резолвился у строки движения, а не инструмент не торгуем).

**Баланс и ставка:** `BALANCE_INVALID`, `BALANCE_NOT_FRESH`,
`BALANCE_NOT_ENOUGH`, `FEE_RATE_UNAVAILABLE`.

**Числа риск-аппетита:** `LOSS_LIMIT_NOT_CONFIGURED`,
`RISK_APPETITE_NOT_CONFIGURED`. Кодов два, а не один: пустой порог серии
убытков и пустой максимальный риск на сделку — **разные** незаданные
операнды, и П3 требует различать их в данных. Оба **временны́е**:
исход меняется правкой конфигурации, а не стратегии.

**Полнота входа проверки:** `DEAL_GRAPH_INCOMPLETE`. Код отдельный, а не
разновидность `CALCULATED_ACTION_INVALID`: тот говорит о самом
рассчитанном действии, этот — о том, что операнды потолков считать не по
чему, и различить их обязана диагностика. Вердиктом риск-политики он не
является, и реакция на него своя (`docs/processes/risk-evaluation.md`).

**Размер и уровни:** `CALCULATED_ACTION_INVALID`, `SIZE_BELOW_MIN`,
`SIZE_LOT_STEP_INVALID`, `SIZE_ABOVE_LIMIT`, `STOP_LOSS_INVALID_SIDE`,
`TAKE_PROFIT_INVALID_SIDE`, `STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`,
`STOP_DISTANCE_BELOW_FLOOR`, `RISK_CREATING_UNDER_COLLAPSE`.

**Инварианты частичного выхода:** `PARTIAL_EXIT_NOT_REDUCE_ONLY`,
`PARTIAL_EXIT_INCREASES_POSITION`,
`DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`.

Коды отказа создания стратегии живут не здесь: их место — валидация
создания.

## Связи

- Кто строит — `docs/components/RiskValidator.md`.
- Что означают потолки — `docs/rules/risk-policy.md`.
- Покрытие защитой — `docs/rules/live-risk-protection.md`.
