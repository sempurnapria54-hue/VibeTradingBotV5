# CODE — шаг 5 фазы 1 (риск-преконтроль)

## На какой вопрос отвечает этот файл

Что написано на под-шаге `CODE` шага 5, каков исход адверсариального ревью и
какие хвосты остаются для `SYNC_DOCS_FROM_CODE` и пост-хок концепт-гейта.

## Контекст

- **Шаг:** 5 фазы 1 — риск-преконтроль. **Под-шаг:** `CODE` (написание +
  ревью-итерации + закрытие находок).
- **Вход:** утверждённая концепция (`DOCS_CHECK_2` — чисто), `tech-radar`,
  `codestyle`.
- **Состояние кода:** 47 файлов в working tree (staged, без коммита);
  `clean test-compile` зелёный, без deprecation-предупреждений в нашем коде.

## Что написано

### Материализация `InstrumentExternalRules` (источник ограничений)

- Доменная модель `InstrumentExternalRules` (+ enum'ы `Status`/`InstrumentType`/
  `ContractType`, parsed-аксессоры спецификации, `isLive()`, `hasSizingSpecs()`);
  граница `InstrumentExternalRulesExternalSnapshot`.
- Персистентность — JSONB-навес на строке `instruments` (миграция `V8`, колонка
  `external_rules`, поле `InstrumentEntity.externalRules`,
  `InstrumentExternalRulesJsonConverter`).
- MapStruct `InstrumentExternalRulesMapper` (integration→snapshot→domain с
  резолвом типов/статуса в `UNKNOWN` при неизвестном сырье).
- `InstrumentExternalRulesDataService` (чтение проекцией навеса, запись
  load-modify строки-владельца — JPA auditing сохраняется).
- `InstrumentExternalRulesSyncJob` + фасад (`@Async`) + конфиг
  (`InstrumentExternalRulesSyncProperties`, enabled+cron, cron по умолчанию
  ежечасно) + блоки в `application-{prod,test}.yaml`. Джоба рефрешит правила
  инструментов в статусах `{SYNC, CANDLES_LOADING, ACTIVE}`.
- Интеграция: `IntegrationService.getInstrumentRules`; домаппинг OKX-полей
  `maxLmtSz/maxMktSz/maxTriggerSz/maxStopSz/ctType/ctValCcy` в `InstrumentOkxResponse`.

### Сборка рыночных цен (`MarketPriceData`)

- RVO `MarketPriceData` (+ `midPrice()`), граница
  `MarketPriceDataExternalSnapshot`, `MarketPriceDataMapper` (ticker→snapshot→
  domain: decimal-строки→`BigDecimal`, `ts`→`OffsetDateTime`), `MarketPriceDataService`
  поверх существующего `getTicker`; `IntegrationService.getMarketPriceData`.

### Расчётный слой

- `Calculated*`-RVO достроены до полной структуры (`CalculatedPrice` с
  purpose/priceMode/base/raw/rounded/send + резолв-подобъекты SL/TP/trailing;
  `CalculatedSize` с sizeContracts/closeFraction/notional/sizeMode); enum'ы
  `PriceMode`/`StrategyPricePurpose`/`PriceRoundingPolicy`/`SizeMode`/
  `CalculationErrorType`; `CalculationContext`, `CalculationError`,
  `StrategyActionCalculationResult`, внутренний `CalculationException`.
- `CalculationContextFactory` (сборка из DealContext + сервисов рыночных данных
  + persisted rules + REST ticker); `PriceCalculator` (limit/market-like entry,
  SL по entry%/ATR%/structure-buffer, TP, trailing; округление по tick,
  политика CONSERVATIVE); `SizeCalculator` (risk-bounded сайзинг под лимит риска
  на сделку, округление по lot, пол по minSz; reduce-only/partial/full-close);
  `StrategyActionCalculator` (оркестрация → SUCCESS/ERROR).
- `ServiceCommandFactory` — обновлены геттеры под новый `CalculatedPrice`
  (`roundedPrice`/`sendPriceToExchange`); заполнение SL/TP/trailing в
  algo-Condition осталось type-only (задокументированная граница шага 6).

### Risk-слой

- `RiskValidator` (читает persisted rules сам; проверки: calculated-action,
  rules-missing, balance-invalid, instrument-not-live, margin-not-isolated,
  size below-min/lot-step/above-limit, exchange-max-leverage, SL/TP side,
  liquidation-guard, risk-per-trade от `externalAvailableEquity`; агрегация —
  один BLOCKED ⇒ BLOCKED). `RiskBlockResolver` (PRECHECK/no-live-risk ⇒
  CLOSE_CANDIDATE_DEAL+RISK_CONTROL; live-risk ⇒ MOVE_DEAL_TO_ERROR;
  balance-not-fresh ⇒ REQUEST_REFRESH). RVO `RiskValidationResult`/
  `RiskCheckResult` (+ `RiskCheckCode`)/`RiskBlockAction`.

## Аппрув-гейт — адверсариальное ревью

Прогнаны три **независимых** ревьюер-фокуса (фокус `security` деактивирован до
шага 9):

- **conventions:** 8 находок, все minor → закрыты (unused import/overload/
  factory/method, прямое отрицание в `applyDistance`, избыточные `@Mapping`).
- **performance:** 2 major + 2 minor. Дубль чтения фазы устранён (фаза не
  собирается — не потребляется фазой 1); ctVal=0 закрыт; перечитка rules
  валидатором — by-design (валидатор самодостаточен, читает persisted rules,
  `instrument-external-rules-materialization.md`); two-Set fetch-join — как в
  существующем `findAllWithSettingsByStatusNot` (малая кардинальность).
- **disaster:** 2 major + 3 minor. Закрыты: ctVal=0 → controlled error через
  `hasSizingSpecs`; `NumberFormatException` на сырье → null; гард SL/TP/trailing
  `> 0` после округления; clamp reduce-fraction в `[0,1]`. Блокеров нет.

## Хвосты

### Форвард-концепт (пост-хок концепт-гейт §6a / шаг 6)

- **Risk-creating вход без резолвимого стопа.** При `Order.Type.ENTRY` без
  attached-SL `SizeCalculator` сайзит только по allocation, `RiskValidator`
  пропускает `RISK_PER_TRADE` (оба no-op). Риск-политика обуславливает сайзинг
  «входом **со стопом**»; risk-managed входы фазы 1 — `ENTRY_ATTACHED_STOP_LOSS`
  (attached SL даёт стоп). Кейс «вход + отдельный standalone SL» — оркестрация
  FSM (шаг 6) и вопрос концепта; кодом не over-блокируем (риск over-restrict).
- **Концепт-инкременты на CODE** (требуют `concept-review` по пост-sync докам):
  `CalculationContext` несёт каталоги `indicatorSettings`/`marketStructureSettings`
  для резолва готовых значений по «мягкому» ключу (в доке — плоские списки);
  внутренний `CalculationException` как механизм controlled-ошибки калькуляторов.

### Расхождения код↔доки (для `SYNC_DOCS_FROM_CODE`, фокус `divergence`)

- `InstrumentExternalRulesDataService` вместо doc-имени
  `InstrumentExternalRulesService` (конвенция `*DataService`).
- `StrategyPriceSource` **не** расширен до 30-значного словаря калькулятора:
  `PriceCalculator` резолвит цену через структурный конфиг (`baseType`/
  `StopLossCalculationType`/`TrailingSettings`), не через плоский source-enum.
- Логический `id` у `InstrumentExternalRules` опущен (нет таблицы/источника);
  оставлен `instrumentId`. Аксессоры `maxTriggerSize()/maxStopSize()` опущены
  (сырые поля сохранены); `SIZE_ABOVE_LIMIT` проверяет maxLmtSz/maxMktSz.
- `MarketPriceData`: mark/index-источники проксированы на last (OKX ticker не
  несёт markPx/idxPx); MID вычисляется.
- Комиссии в risk-amount/сайзинге не учитываются (нет fee-модели) —
  согласованно в `SizeCalculator` и `RiskValidator`; запас на проскок не
  закладывается (как в доке, фаза 1).
- База allocation = `externalAvailableEquity` (якорь процента — открытый
  STRAT-Q4).
- `EXCHANGE_MAX_LEVERAGE_EXCEEDED` сравнивает `Instrument.leverage` с
  `externalMaxLeverage`.
- Коды `BORROW_OR_DEBT_DETECTED`/`BALANCE_NOT_FRESH`/`MULTIPLE_POSITIONS_DETECTED`/
  `POSITION_STATE_UNKNOWN`/`PARTIAL_EXIT_*` определены в `RiskCheckCode`, но
  `RiskValidator` их не эмитит (freshness/anomaly/handler-safety — их владельцы).
- `marketPhase` в `CalculationContext` не заполняется (нет потребителя фазы 1).

## Дальше

Финальный аппрув `CODE` и перевод в `SYNC_DOCS_FROM_CODE` (фокус `divergence`
→ реконсиляция; затем пост-хок концепт-гейт §6a по концепт-инкрементам) —
за пользователем.
