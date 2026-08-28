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

Сигнатура — `validate(CalculatedStrategyAction, DealContext)`. Из
`CalculatedStrategyAction` валидатор извлекает цену/размер
(`CalculatedPrice` / `CalculatedSize`). Из `DealContext` он читает
**runtime graph сделки целиком** — не закрытый список отдельных полей
(N4 `DOCS_CHECK_21`: прежняя редакция перечисляла операнды списком, и
три операнда сделочных лимитов в него не попали):

| Что читает | Откуда | Зачем |
|---|---|---|
| `balanceContainer` | `DealContext` | база риска — `Balance.externalAvailableBalance` расчётной валюты (`docs/decisions/per-trade-risk-policy.md` §«Определение и база») |
| `deal` с графом (`orders`, `algoOrders`, `positions`) | `DealContext.deal` | операнды обоих сделочных лимитов: неисполненная доля живых ног, взятое снятыми ногами, размер и средняя цена живого эпизода, уровни действующих защит |
| `deal.plannedRiskAmount`, `deal.plannedRiskEquityBase` | `DealContext.deal` | операнд и база кумулятивного потолка |
| `strategyDetail` | `DealContext` | `riskPerActionPercent`, `cumulativeRiskPerDealMultiplier`, `strategySimultaneousRiskPerDealPercent` |
| `instrument`, направление сделки | `DealContext` | `marginMode`, плечо, знак дистанции стопа |
| `globalSimultaneousRiskPerDealPercent` | глобальный конфиг (`@ConfigurationProperties`) | жёсткий системный потолок одновременного риска |

`InstrumentExternalRules` **не** входной аргумент — валидатор сам читает
его через `InstrumentExternalRulesDataService.findByInstrumentId`.
Отдельного RVO `RiskSettings` нет (см.
`docs/decisions/per-trade-risk-policy.md`).

**Граф уже собран проходом FSM** (`docs/components/models/DealContext.md`),
поэтому чтение по ногам и эпизодам не добавляет запросов; отдельного
persisted-носителя под операнды лимитов не заводится
(`docs/decisions/per-trade-risk-policy.md` §«Что считается живым риском в
моменте»).

**Отсюда — владелец гидрации ставки** (H1, `GAPS_CLOSE_4`). `CalculationContext`
у валидатора нет: тропа чтения навеса у него **своя**, прямая. Поэтому ставку
наливает `InstrumentExternalRulesDataService` (граница domain ↔ persistence,
через которую проходят обе тропы), а не `CalculationContextFactory`: гидрация в
фабрике накрыла бы только тропу калькуляторов, валидатор получал бы
негидрированный навес → `takerFeeRate()` = `null` → `FEE_RATE_UNAVAILABLE`
блокировал бы **каждый** risk-creating вход. Разбор —
`docs/components/InstrumentExternalRulesDataService.md` §«Гидрация ставки
комиссии».

## Метрики (считает сам)

risk amount (убыток на стопе: `|entry − stop| × sizeContracts × ctVal +
commissions`, где `commissions` = `rate × sizeContracts × ctVal ×
(entryPrice + stopPrice)` — **каждая нога по своей цене**, вход по цене
входа, выход по цене стопа (H10, `GAPS_CLOSE_7`: единая оценка по цене входа
занижала комиссию выхода для SHORT). Валидатор проверяет **уже посчитанный**
размер, поэтому решает то же неравенство в проверочной форме; сайзинг
`SizeCalculator` решает его относительно `contracts` — закрытой формой
(`docs/decisions/per-trade-risk-policy.md` §«Закрытая форма сайзинга»).
Ставка — прогноз вход+выход по taker-ставке из
`instrumentExternalRules.takerFeeRate()` (N9 — не отдельный fetch; **дом
ставки** — `docs/models/domain/other/TradeFeeRate.md`, на инструменте только
ключ группы `externalFeeGroupId`; аксессор гидрирует хранилищный слой —
`docs/components/InstrumentExternalRulesDataService.md` §«Гидрация ставки
комиссии», H1, `GAPS_CLOSE_4`); **включён с шага 7** (G6), согласовано с
`SizeCalculator`, см. `docs/decisions/per-trade-risk-policy.md` §«Учёт
комиссий (включён на шаге 7)»);
**risk percent от базы риска** (свободный остаток расчётной валюты, не
account-level агрегат и не total/adjusted — см.
`docs/decisions/per-trade-risk-policy.md` §«Определение и база»);
SL distance; liquidation guard distance. Метрики могут попасть в `RiskCheckResult.details`, логи или
аудит, но **не** входят в `CalculatedStrategyAction`.

**Исключение — risk amount входного действия** (H9, `GAPS_CLOSE_7`): он
**персистится** как `Order.plannedRiskAmount` **создаваемой ноги**, а
производные числа на `Deal` той же транзакцией пересчитываются
писателем — `docs/components/CreateOrderExecutor.md`, тот же проход.
Состав, счёт и формулы — место истины
`docs/models/domain/aggregate/Deal.md` §«Взятый риск»; здесь не
пересказываются (прежняя редакция перечисляла три числа и приписывала их
все `CreateOrderExecutor`'у, расходясь с домом — B3 `DOCS_CHECK_19`). **Вместе с риском
валидатор отдаёт и `ctVal` момента постановки** — он и так читает навес,
чтобы посчитать риск, а значение садится на ногу пятым числом
(`Order.plannedContractValue`, H5 `DOCS_CHECK_16`). Это не отменяет
правила выше: метрика по-прежнему не едет в `CalculatedStrategyAction` —
она уходит в **поле сделки**, у которого свой торговый смысл
(`docs/models/domain/aggregate/Deal.md` §«Плановый риск»).

Аксессор отдаёт ставку **издержкой** — знак биржевой конвенции снят при
маппинге (`docs/models/domain/other/TradeFeeRate.md` §«Знак ставки»). Поэтому
`+ commissions` верно как написано, `abs` вызывающему не нужен: положительная
издержка увеличивает убыток на стопе, отрицательная (ребейт) уменьшает.

`position exposure после действия` — метрика **уровня риска на биржу/портфель**
(форвард к фазе 3); в фазе 1 (только риск на сделку) кода-блокера по экспозиции
нет (`docs/decisions/per-trade-risk-policy.md`).

## Конкретные проверки (фаза 1)

Fail-fast (возвращают `BLOCKED` сразу, без остальных проверок):

- `CALCULATED_ACTION_INVALID` — размер отсутствует / непозитивен;
- `INSTRUMENT_RULES_MISSING` — `InstrumentExternalRules` не
  материализованы;
- `BALANCE_INVALID` — база риска не резолвится: свежий
  `BalanceContainer` не содержит строки расчётной валюты либо её
  `externalAvailableBalance` отсутствует / непозитивен.

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
  позиции. **Проверка знает ценовую базу стопа** (C1 `DOCS_CHECK_20`):
  ликвидация у источника вычисляется по `mark`, поэтому сравнение верно
  только когда стоп триггерится в той же базе. Дом ограничения —
  `docs/rules/risk-creating-entry-protection.md` §«Ценовая база триггера
  защиты объявляется стратегией и доезжает до биржи».
  **Исходы ветки перечислены явно** (C5 `DOCS_CHECK_22`; прежняя
  редакция называла запас обязательным, его величину — недобытым
  грунтом, а поведение до добычи не определяла ни здесь, ни в доме):

  | Ценовая база стопа | Что делает проверка |
  |---|---|
  | `MARK` | сравнение **прямое**, запаса нет: обе величины в одной базе |
  | `LAST` / `INDEX` | **недостижимо**: стратегия с такой базой не создаётся (create-реджект `STRATEGY_TRIGGER_PRICE_TYPE_NOT_MARK`, `docs/decisions/strategy-materialization-and-validation.md`). Провизорного запаса **не вводится** |
  | база не резолвится | `triggerPriceType` — **обязательное** поле стратегии (`docs/models/domain/aggregate/Strategy.md` §StopLossSettings), пустым до валидатора не доезжает |

  После измерения базиса (`MG9.5`) ограничение снимается сервисной
  операцией, и здесь появляется строка с назначенным запасом — правка
  идёт **через дом**, эта таблица её отражает;
- `RISK_CREATING_ENTRY_WITHOUT_STOP` — risk-creating вход
  (открытие/наращивание позиции) без **резолвимого стопа**: `BLOCKED`,
  **без** fail-open allocation-сайзинга в обход `RISK_PER_TRADE`
  (инвариант `docs/rules/risk-creating-entry-protection.md`). Проверяется
  до риск-на-сделку: нет стопа → risk-amount нечем посчитать → блок, не
  сайзинг по allocation. Reduce-only/закрывающие действия не затрагивает
  (риск снимают);
- `FEE_RATE_UNAVAILABLE` — прогнозная ставка комиссии не резолвится
  (`instrumentExternalRules.takerFeeRate()` → `null`): `BLOCKED`. Проверяется
  до риск-на-сделку — комиссия входит в убыток на стопе, без ставки
  risk-amount неполон. Только для risk-creating / risk-increasing действий (там,
  где прогноз комиссии входит в сайзинг, `docs/rules/risk-validator-scope.md`);
  reduce-only/закрывающие не затрагивает;
- `RISK_PER_ACTION_EXCEEDED` — риск **одного действия** (%) выше
  `StrategyDetail.riskPerActionPercent` от **текущей** базы риска;
- `RISK_PER_DEAL_CUMULATIVE_EXCEEDED` — **кумулятивный потолок сделки**:
  `dealRiskTaken` плюс риск проверяемого действия выше
  `cumulativeRiskPerDealMultiplier × riskPerActionPercent ×
  min(Deal.plannedRiskEquityBase, база риска текущая)`;
- `RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED` — **одновременный риск на
  сделку против максимума стратегии**: `liveRiskNow` плюс риск
  проверяемого действия выше
  `StrategyDetail.strategySimultaneousRiskPerDealPercent × база риска`;
- `RISK_PER_DEAL_SIMULTANEOUS_GLOBAL_EXCEEDED` — тот же операнд против
  **глобального** максимума (`globalSimultaneousRiskPerDealPercent ×
  база риска`). Достижим, только если инвариант «максимум стратегии ≤
  глобального» нарушен задним числом (конфиг изменён после create) —
  это **страховка сверху**, и её реджект разведён отдельным кодом,
  чтобы «стратегия выбрала свой бюджет» и «система изменила потолок» не
  читались одинаково.

**Все четыре неравенства обязаны выполниться**; формулы операндов
(`dealRiskTaken`, `liveRiskNow`) и дом политики —
`docs/decisions/per-trade-risk-policy.md` §«Три лимита внутри уровня
„риск на сделку“», здесь не пересказываются. **Исход `BLOCKED` по
сделочным кодам аварией не является**
(`docs/processes/risk-evaluation.md` §«Карв-аут исчерпанного бюджета
сделки»).

**Пола дистанции стопа среди проверок нет** — названное ограничение, не
пропуск: worst-case **за** стоп-ценой системой в фазе 1 не
ограничивается (`docs/decisions/per-trade-risk-policy.md`
§«Worst-case открывающего входа», решение держателя C5
`DOCS_CHECK_21`).

Агрегация: любой `BLOCKED` ⇒ `BLOCKED`; путь `WARNING` в коде есть
(аггрегатор его учитывает), но **ни одна проверка фазы 1 `WARNING` не
порождает** — все проверки строят `BLOCKED`.

## Null-политика ставки комиссии

Два разных «нет ставки» — два разных ответа:

- **Ставка была, но чтение упало** → последняя известная **не затирается**:
  на отказе синк **не пишет ничего** — история `TradeFeeRate` append-only,
  актуальная = последняя строка по `createdAt`, она просто остаётся последней и
  стареет (H10, `GAPS_CLOSE_4`; дом — `docs/models/mapping/TradeFeeRate.md`
  §«Error policy»). Устаревание известной ставки ведёт к **холду инструментов
  группы** (`docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»), не к
  реджекту.
- **Ставки не было никогда** (ни одной строки `TradeFeeRate` по группе
  инструмента либо у инструмента нет `externalFeeGroupId`) → **реджект**
  `FEE_RATE_UNAVAILABLE`.

**Почему реджект, а не fallback-ставка из конфига.** Fallback отвергнут:
подставленное число **выглядит фактом, не будучи им**, и ошибается
асимметрично — заниженная ставка даёт заниженный прогноз комиссии → бюджет
риска «свободнее» → позиция **больше положенной**. Это та же болезнь, что H6
(null-drop в ожидаемости) и H2 (недосчёт комиссии): тихое **оптимистичное
смещение**, которое не видно ни в одном логе. Цена реджекта — пропуск одной
сделки с громкой причиной; цена fallback'а — тихо превышенный риск на
**каждой**.

## Границы

- **Не** переводит сделку в другой статус и **не** создаёт
  `ServiceCommand`.
- **Не** обновляет баланс: не вызывает `REFRESH_BALANCE_COMMAND`, `IntegrationService`
  или OKX adapter. При absent/stale/invalid `BalanceContainer` он **по
  контракту** возвращает `BLOCKED` (коды `BALANCE_NOT_FRESH` /
  `BALANCE_INVALID`), а не чинит snapshot сам.
  - **В фазе 1 эти коды фактически не эмитятся** (H17, `GAPS_CLOSE_6`):
    свежесть баланса обеспечивается **до** вызова — при absent/stale она
    добывается звеном `REFRESH_BALANCE_COMMAND` через
    `REFRESH_DEAL_CONTEXT_ACTION` (handler добывающие `REFRESH_*` напрямую
    не эмитит, `docs/components/SystemActionExecutor.md`), и FSM уходит на
    новый проход, на котором
    валидатор не вызывается (`docs/processes/risk-evaluation.md` §«Когда
    вызывается»; реестр кодов —
    `docs/components/models/RiskCheckResult.md` §«Определены, но в фазе 1 не
    эмитятся»). Противоречия между «возвращает» и «не эмитится» нет:
    здесь — **граница ответственности** (валидатор снапшот не чинит), там —
    фактическая достижимость ветки при текущем порядке вызова.
- Вызывается только для risk-creating / risk-increasing / risk-weakening
  actions, после расчёта цены/размера и до создания торговой команды (см.
  `docs/rules/risk-validator-scope.md`).

Превращение `BLOCKED` в действие handler'а — у
`docs/components/RiskBlockResolver.md`.
