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

`PASSED`, `WARNING` (не блокирует), `BLOCKED`. В фазе 1 `RiskValidator`
строит **только** `BLOCKED`-результаты — `PASSED`/`WARNING` определены,
но ни одна проверка фазы 1 их не порождает.

## Енум `RiskCheckCode`

Стартовый набор кодов:
`RISK_PER_ACTION_EXCEEDED`, `RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED`,
`RISK_CREATING_ENTRY_WITHOUT_STOP`,
`EXCHANGE_MAX_LEVERAGE_EXCEEDED`, `MARGIN_MODE_NOT_ISOLATED`,
`BORROW_OR_DEBT_DETECTED`, `BALANCE_NOT_ENOUGH`, `BALANCE_NOT_FRESH`,
`BALANCE_INVALID`, `SIZE_BELOW_MIN`, `SIZE_LOT_STEP_INVALID`,
`SIZE_ABOVE_LIMIT`, `STOP_LOSS_INVALID_SIDE`, `TAKE_PROFIT_INVALID_SIDE`,
`STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`, `PARTIAL_EXIT_NOT_REDUCE_ONLY`,
`PARTIAL_EXIT_INCREASES_POSITION`, `DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`,
`MULTIPLE_POSITIONS_DETECTED`, `POSITION_STATE_UNKNOWN`,
`INSTRUMENT_NOT_LIVE`, `INSTRUMENT_RULES_MISSING`, `FEE_RATE_UNAVAILABLE`,
`SETTLE_CURRENCY_UNAVAILABLE`, `CALCULATED_ACTION_INVALID`,
`PROTECTION_COVERAGE_REDUCED`.

### Эмитятся `RiskValidator`'ом в фазе 1

`RISK_PER_ACTION_EXCEEDED`, `RISK_PER_DEAL_CUMULATIVE_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED`,
`RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED`,
`RISK_CREATING_ENTRY_WITHOUT_STOP`,
`EXCHANGE_MAX_LEVERAGE_EXCEEDED`,
`MARGIN_MODE_NOT_ISOLATED`, `SIZE_BELOW_MIN`, `SIZE_LOT_STEP_INVALID`,
`SIZE_ABOVE_LIMIT`, `STOP_LOSS_INVALID_SIDE`, `TAKE_PROFIT_INVALID_SIDE`,
`STOP_LOSS_TOO_CLOSE_TO_LIQUIDATION`, `INSTRUMENT_NOT_LIVE`,
`INSTRUMENT_RULES_MISSING`, `FEE_RATE_UNAVAILABLE`,
`SETTLE_CURRENCY_UNAVAILABLE`, `BALANCE_INVALID`,
`CALCULATED_ACTION_INVALID`, `PROTECTION_COVERAGE_REDUCED`
(см. `docs/components/RiskValidator.md`).

`PROTECTION_COVERAGE_REDUCED` — защитное действие **уменьшает**
совокупное покрытие живых защит (пост-действенное покрытие меньше
пред-действенного) — C1 `DOCS_CHECK_23`, решение держателя. Ветка
scope — risk-weakening («создание защитного algo-order, не
обеспечивающего требуемый контроль риска»,
`docs/rules/risk-validator-scope.md`); предикат и его три точки
проверки — `docs/rules/risk-creating-entry-protection.md` §«Предикат
покрытия и точки его проверки». **Предикат — монотонность, а не
полнота:** лестница частичных стопов ставится ступень за ступенью, и
промежуточное покрытие законно неполно; полнота требуется на гейтах
троп, после исполнения пакета шага. **Не авария** —
`docs/processes/risk-evaluation.md` §«Карв-аут исчерпанного бюджета
сделки», куда код внесён вместе с сделочными.

`RISK_CREATING_ENTRY_WITHOUT_STOP` — risk-creating вход (открытие/наращивание
позиции) без резолвимого стопа: `BLOCKED` вместо fail-open
allocation-сайзинга в обход `RISK_PER_TRADE` (инвариант
`docs/rules/risk-creating-entry-protection.md`). Reduce-only/закрывающие
действия не затрагивает.

`FEE_RATE_UNAVAILABLE` — прогнозная ставка комиссии недоступна: ставки не было
**никогда** (ни одной строки `TradeFeeRate` по комиссионной группе инструмента
либо у инструмента нет `externalFeeGroupId`) → `BLOCKED`. Срабатывает **только
на `null`**. Не путать с **несвежей** ставкой: устаревание известной ставки
ведёт к **холду инструментов группы**, не к этому коду
(`docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»). Знаковая
нормализация к этому коду тоже не относится — она сделана при маппинге
(`docs/models/mapping/TradeFeeRate.md` §«Знак ставки — снимается здесь»), и до
валидатора ставка доезжает **издержкой**. Fallback-ставка из конфига вместо
реджекта отвергнута — довод в `docs/components/RiskValidator.md`.

`SETTLE_CURRENCY_UNAVAILABLE` — **расчётная валюта инструмента** не
резолвится (навес не несёт её значения: поле новое, на существующих
строках появляется только после ближайшего тика синка) → `BLOCKED` (H10
`DOCS_CHECK_10`). Она — источник и `Deal.plannedRiskCurrency`, и
`Deal.resultProfitCurrency`, а их совпадение — условие того, что
R-мультипликатор вообще считается. Подставить `USDT` «по контуру»
**отвергнуто** тем же доводом, что fallback ставки: подставленное число
выглядит фактом, не будучи им. Ветка **зеркальна** `FEE_RATE_UNAVAILABLE`:
реджект там, где риск ещё не взят; **после** взятия риска отказывать
нельзя, и та же нехватка ведёт к `AnomalyReport`
`SETTLE_CURRENCY_UNAVAILABLE` без блокировки
(`docs/models/domain/aggregate/Deal.md` §«Валюта результата: один
авторитет»).

### Определены, но в фазе 1 не эмитятся

Форвард / handler / аномалия: `BALANCE_NOT_ENOUGH`, `BALANCE_NOT_FRESH`,
`BORROW_OR_DEBT_DETECTED`, `MULTIPLE_POSITIONS_DETECTED`,
`POSITION_STATE_UNKNOWN`, `PARTIAL_EXIT_NOT_REDUCE_ONLY`,
`PARTIAL_EXIT_INCREASES_POSITION`, `DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN`.

Часть кодов — не risk-policy проверки `RiskValidator`, а safety/invariant
violation exit-flow через reduce-only `Order`/`AlgoOrder`:
`PARTIAL_EXIT_NOT_REDUCE_ONLY`, `PARTIAL_EXIT_INCREASES_POSITION`,
`DIRECT_PARTIAL_POSITION_CLOSE_FORBIDDEN` (см.
`docs/rules/no-partial-close.md`, `docs/rules/risk-validator-scope.md`).

Market data expired/missing — **не** risk-code первого уровня: сначала
обрабатывается через `MarketDataExpirationChecker` и
`StrategyStep.marketDataExpiredSetting` (см.
`docs/rules/market-data-freshness.md`).

### Риск и экспозиция (фаза 1)

**Кодов лимита риска четыре — по числу неравенств** (C6 `DOCS_CHECK_20`,
C9 `DOCS_CHECK_21`; дом политики —
`docs/decisions/per-trade-risk-policy.md` §«Три лимита внутри уровня
„риск на сделку“», здесь не пересказывается):

`RISK_PER_ACTION_EXCEEDED` — **поактный** лимит: убыток
на стопе одного risk-creating действия как % от базы риска
превышает `StrategyDetail.riskPerActionPercent`. Срабатывает в том числе
когда действие не укладывается в лимит **даже на минимальном размере
инструмента** (`minSz`) — строгое блокирование без открытия.

`RISK_PER_DEAL_CUMULATIVE_EXCEEDED` — **кумулятивный потолок сделки**:
`dealRiskTaken` плюс риск нового действия превышает
`cumulativeRiskPerDealMultiplier × riskPerActionPercent ×
min(Deal.plannedRiskEquityBase, база риска текущая)`.

`RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED` — **одновременный риск на сделку
против максимума стратегии**: `liveRiskNow` плюс риск нового действия
превышает `StrategyDetail.strategySimultaneousRiskPerDealPercent × база
риска`.

`RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED` — тот же операнд против
**глобального** максимума (`globalSimultaneousRiskPerDealPercent × база
риска`, конфиг). Отдельный код, а не тот же: инвариант «максимум
стратегии ≤ глобального» проверяется на create, поэтому срабатывание
этого кода означает, что потолок изменила **система** после приёма
стратегии, — иной разбор, чем «стратегия выбрала свой бюджет».

**Все три сделочных кода — не авария ни в одном статусе** (карв-аут
несёт **четыре** кода: три сделочных плюс
`PROTECTION_COVERAGE_REDUCED` — C1/C2 `DOCS_CHECK_23`; перечень держит
дом): действие
не исполняется, сделка остаётся в текущем статусе и ведётся до выхода
имеющимися ногами (`docs/processes/risk-evaluation.md` §«Карв-аут
исчерпанного бюджета сделки»).

Отдельного кода контроля **экспозиции/позиционного лимита поверх биржевого
максимума** в фазе 1 **нет**: такой guard относится к уровню риска на
биржу/портфель (фаза 3), а в фазе 1 **торгуется один инструмент** (H8
`DOCS_CHECK_10`, `docs/rules/trading-constraints.md` §Инструменты) —
агрегата нет по ограничению контура, и единственная позиция ограничена
лимитом риска на сделку. `EXCHANGE_MAX_LEVERAGE_EXCEEDED` остаётся как
предел биржи (не наш кэп плеча).
