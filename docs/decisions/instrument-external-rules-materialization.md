# Материализация `InstrumentExternalRules` (шаг 5)

## На какой вопрос отвечает этот файл

Как материализуется `InstrumentExternalRules` (источник ограничений
инструмента для риск-преконтроля) — представление, персистентность,
маппинг — и почему так.

## Контекст

Закрывает пробелы шага 5, найденные на `DOCS_CHECK_1`: **N1** (модель
отложена за пределы шага 1, а риск-преконтроль — её потребитель), **N2**
(трёхсторонняя несогласованность: модель объявляет per-order max-size и
`externalMaxLeverage` поля и заявляет их в использовании, mapping их «пока не
используем», нативный DTO утверждает обратное), **INSTR-Q1** (снапшот-концепция
vs persisted rules, дом справочных полей, возможный ренейм) и часть **INSTR-Q2**
(источник биржевого потолка плеча). Шаг 5 — первый реальный потребитель
ограничений инструмента (`SizeCalculator`, `PriceCalculator`, `RiskValidator`),
поэтому модель материализуется здесь.

## Решение

### Модель материализуется на шаге 5

Пометка «Отложено за пределы шага 1» снимается. `InstrumentExternalRules`
(`docs/models/domain/other/InstrumentExternalRules.md`) — самостоятельная
доменная модель справочных правил инструмента (tick/lot/min size, per-order
max sizes, `ctVal`, max leverage, торгуемость), материализуемая из снапшота
биржи джобой `InstrumentExternalRulesSyncJob`. Ренейм не требуется: модель
остаётся `InstrumentExternalRules`, снапшот-граница —
`InstrumentExternalRulesExternalSnapshot` (закрытие INSTR-Q1).

### Персистентность — JSONB-навес на строке `Instrument`

По дефолту правила персистентности
(`docs/rules/persistence-representation.md`): на `InstrumentExternalRules`
**нет FK-ссылок из других мест** (результаты расчёта ключуются на
strategy-scope настройки, не на rules) → реляционная строка пользы не несёт.
Правила хранятся **JSONB-навесом на строке владельца** (`instruments`),
один актуальный набор на инструмент. Собственной таблицы/`id` у модели нет;
доступ — только через `Instrument`. Это уточняет прежнюю формулировку
«persisted snapshot ... наследует `Auditable`» (отдельной таблицы не
заводим).

### Маппинг — домаппить ограничители (закрытие N2)

OKX `GET /api/v5/public/instruments` несёт ограничители, нужные
преконтролю; они **домаппливаются** в snapshot/модель:

- per-order max sizes: `maxLmtSz → externalMaxLimitSize`,
  `maxMktSz → externalMaxMarketSize`, `maxTriggerSz → externalMaxTriggerSize`,
  `maxStopSz → externalMaxStopSize` (источник проверки
  `RiskCheckCode.SIZE_ABOVE_LIMIT`);
- `lever → externalMaxLeverage` (биржевой максимум плеча инструмента;
  источник `EXCHANGE_MAX_LEVERAGE_EXCEEDED`).

Три дока приводятся к одному утверждению: модель объявляет и использует поля
(`InstrumentExternalRules.md`), mapping их **маппит** (снять из «не
используем», `mapping/InstrumentExternalRules.md`), нативный DTO ссылается на
rules как потребителя (`InstrumentOkxResponse.md`) — без расхождения.

### Источник потолка плеча и дубль с `Instrument.externalLeverage` (INSTR-Q2)

OKX `lever` (из `/public/instruments`) — **максимальное плечо инструмента**.
Авторитетный источник биржевого потолка для риск-преконтроля —
`InstrumentExternalRules.externalMaxLeverage` (дом спецификации-лимитов).
`Instrument.externalLeverage` (заведён на шаге 1 из того же `lever`) несёт то
же сырое значение, но для преконтроля **не авторитетен**; устранение дубля
(удаление `Instrument.externalLeverage`) — мелкая чистка, не блокирует и может
остаться форвардом. Наш guard плеча отсутствует (плечо связано лимитом риска,
`docs/decisions/per-trade-risk-policy.md`), поэтому прежний под-вопрос
INSTR-Q2 «нарушение рабочего плеча → `HOLD` инструмента» **снимается**:
единственное правило плеча — биржевой максимум, и это precontrol-блок
(`EXCHANGE_MAX_LEVERAGE_EXCEEDED`), не статус инструмента.

### Per-tier лимиты и live-эндпоинты — вне валидатора фазы 1 (закрытие N5)

`RiskValidator` читает **persisted** `InstrumentExternalRules`, в биржу за
ограничениями **не ходит** (`docs/components/RiskValidator.md`). Поэтому:

- **собственный преконтроль — основной**; серверный `order-precheck` OKX вне
  нашего режима маржи (isolated/Futures, `acctLv=2`) **неприменим** и в фазе 1
  не используется (door-open при смене режима);
- per-tier лимиты `position-tiers` (`maxLever`/`maxSz`) и динамический
  `price-limit` (требуют live-вызова) в валидатор фазы 1 **не входят**: для
  потолка плеча достаточно инструмент-уровневого `externalMaxLeverage`;
  per-tier потолок и позиционные лимиты — форвард к уровню риска на
  биржу/портфель (`docs/decisions/per-trade-risk-policy.md`, фаза 3).

## Альтернативы (отвергнуты)

- **Отдельная таблица `instrument_external_rules`** — отвергнуто: нет
  FK-целей, по правилу персистентности — JSONB-навес.
- **Ренейм к снапшот-неймингу** (INSTR-Q1 вар. 2) — отвергнуто: текущее имя
  отражает суть (правила инструкции), граница уже выражена снапшотом.
- **Источник потолка плеча — per-tier `position-tiers.maxLever`** (крен Э1
  `DOCS_CHECK_1`) — отложено: в фазе 1, где нашего кэпа плеча нет, достаточно
  инструмент-уровневого максимума; per-tier — форвард к экспозиционным лимитам
  уровня 2.

## Связи

- Модель — `docs/models/domain/other/InstrumentExternalRules.md`.
- Маппинг — `docs/models/mapping/InstrumentExternalRules.md`.
- Нативный DTO — `docs/models/integrations/okx/InstrumentOkxResponse.md`.
- Sync-job — `docs/components/InstrumentExternalRulesSyncJob.md`.
- Владелец-инструмент — `docs/models/domain/core/Instrument.md`.
- Правило персистентности — `docs/rules/persistence-representation.md`.
- Риск-политика (потолок плеча) — `docs/decisions/per-trade-risk-policy.md`.
