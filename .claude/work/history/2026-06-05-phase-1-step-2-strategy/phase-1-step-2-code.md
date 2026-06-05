# CODE — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

На каком шаге мы в под-шаге `CODE` шага 2 Фазы 1 (написание кода
стратегии по утверждённой концепции) и что осталось до его закрытия.

## Контекст

- Шаг роадмапа: Фаза 1, шаг 2 — «Стратегия».
- Под-шаг: `CODE` (`.claude/processes/roadmap-step-execution.md`,
  под-шаг 5). Вход: вердикт `DOCS_CHECK_8` «готов к CODE», все
  находки закрыты на `GAPS_CLOSE_8`.
- Написание — роль `code-writer` (опора: `Strategy.md`, lifecycle,
  четыре decision'а шага, codestyle, tech-radar, шаблон Controller);
  конструктивное касание `trading-specialist` при авторинге «одной
  реализации» (чек-лист СТ-1); ревью — роль `reviewer`, фокусы
  `conventions` / `performance` / `disaster` (`security`
  деактивирован до шага 9).

## Статус: ЗАВЕРШЁН — аппрув получен (2026-06-05)

Код написан, ревью-итерация пройдена, аппрув пользователя получен;
статус шага переведён в `SYNC_DOCS_FROM_CODE`. Компиляция чистая
(`mvn compile`, JDK 25, deprecation-предупреждений в нашем коде нет);
Jackson-представление JSONB-настроек проверено round-trip'ом: один
ключ `indicatorType`, подтип params резолвится, дискриминатор в
payload не дублируется. Runtime-прогон против PostgreSQL/OKX в рамках
под-шага не выполнялся (аппрув дан по коду и статическим проверкам) —
выполним отдельно по запросу.

Дополнения после ревью по замечаниям пользователя (вошли в аппрув):
имена таблиц во множественном числе (правило — `codestyle.md`
§Схема БД); deprecated API заменены
(`HttpStatus.UNPROCESSABLE_CONTENT`,
`ObjectMapper.setDefaultPropertyInclusion`; правило — `codestyle.md`
§Строгие правила).

## Состав артефактов

- **Домен** (`domain/model/aggregate/strategy/**`): дерево
  `Strategy` целиком — root + `StrategyDetail`, `StrategyStep`,
  настройки рыночных данных (`setting/`: `StrategyMarketPhaseSetting`,
  `MarketPhaseParams`, `StrategyIndicatorSetting`, `IndicatorParams` +
  7 наследников, `StrategyMarketStructureSetting`,
  `MarketStructureParams`, `Destiny`), условия (`condition/`:
  `StrategyCondition`, `StrategyConditionRule`, операнд, 4 enum'а),
  действия (`action/`: интерфейс `StrategyAction` + Order/AlgoOrder/
  Position, placement, attached protection, StopLoss/Trailing
  settings, 6 enum'ов), `StrategyStepType`,
  `StrategyMarketDataExpiredSetting`, `MarketDataExpiredAction`,
  `PhaseEntryPolicy` (с матрицей `isAllowedFor`).
- **Скелеты-носители enum'ов смежных кластеров** (дозревают на своих
  шагах): `aggregate/deal/Deal` (Status), `trade/market_phase/
  MarketPhase` (Type), `trade/indicator/IndicatorValue` (Type),
  `trade/market_structure/MarketStructure` (Type), `core/order/Order`
  (Type), `core/order/AttachedAlgoOrder` (Type),
  `core/algo_order/AlgoOrder` (ConditionType, TriggerPriceType).
- **Persistence**: 8 entity (`persistence/model/strategy/**`; JOINED
  для действий, дискриминатор `action_kind`; JSONB — `String` +
  `@JdbcTypeCode(SqlTypes.JSON)`), `StrategyRepository` (root /
  дерево одним join-fetch-запросом / exists-ACTIVE), миграция
  `V2__create_strategy_tables.sql` (FK; `UNIQUE(strategy_detail_id,
  key)`; self-FK `target_action_id` deferrable + CHECK; частичный
  UNIQUE «одна ACTIVE на инструмент»).
- **Маппинг**: `StrategyMapper` (api↔domain↔persistence;
  `@SubclassMapping` для видов действий и подтипов params;
  `stepsByStatus` ↔ плоские строки `deal_status`+`step_index`;
  порядок действий — LinkedHashSet → id ASC) +
  `StrategyJsonConverter` (JSONB-навес, NON_NULL, ISO-Duration).
- **Сервисы**: `StrategyDataService` (save с резолвом
  `targetActionKey` → self-FK после вставки, в одной транзакции;
  updateStatus без перезаписи дерева), `StrategyService` (create в
  CREATED; переходы статуса через `canTransitionTo` модели; инвариант
  одной ACTIVE — 422 + DB-страховка). `InstrumentDataService` /
  `InstrumentRepository` / `InstrumentService` дополнены проекциями
  id ↔ internalId.
- **API**: `StrategyController` (`POST` / `GET /{internalId}` /
  `PUT /{internalId}/status`), 21 api-модель (вложенные shared между
  request/response в `api/model/strategy/`; полиморфизм по
  `actionKind` и `indicatorType` — Jackson-аннотации api-слоя),
  `StrategyCreateRequestValidator` (структурно-ссылочная
  create-валидация, 400).
- **«Одна реализация»**:
  `src/main/resources/strategy-examples/trend-following-ema.json` —
  payload для POST. EMA-тренд-фоллоинг: фаза 1H (EMA 20/80 +
  RANGE-структура), сигнал 15m (EMA 10/50), подтверждение 5m
  (RSI 14, `CANDLE_CLOSED`); BULL/BEAR → FOLLOW_PHASE (вход
  market-like c attached ATR-стопом 1.5 ATR; OCO SL+TP 3%; на +1% —
  AMEND стопа к безубытку; на +2% — CANCEL OCO + трейлинг с порогом
  активации; EXIT по `TREND_CHANGED`); RANGE/UNKNOWN → NO_TRADE;
  риск 1%/сделку, R:R 2.5.

## Касание trading-specialist (СТ-1) — применено

Без `CONTRARIAN` вообще (вместо «CONTRARIAN с guard'ами» — не торгуем
против фазы; режимность RANGE/UNKNOWN → NO_TRADE [Kaufman гл. 1;
Carver AFTS 13]); трейлинг строго с порогом активации +2%, не с входа
[Kaufman гл. 23]; объём не используется как основание ENTRY (IND-Q1);
риск 1% ≤ ориентира 3% [Tharp гл. 12]; ATR-стоп [Tharp гл. 9];
`CANDLE_CLOSED` против look-ahead; мультитаймфрейм-триада [Kaufman
гл. 19]; при включении трейлинга OCO снимается тем же пакетом (не две
полные защиты разом). Предположение вне корпуса: 15m-сигнал на перпах
шумный — компенсируется гейтом фазы 1H; проверка — бэктест-гейт
Фазы 2.

## Решения уровня CODE (сигналы для SYNC_DOCS_FROM_CODE)

1. Нейминг «мягких» ссылок унифицирован per-source: `indicatorKey` /
   `structureKey` — в операндах, `StopLossSettings` (док называл
   `indicatorSetting`/`marketStructureSetting`) и
   `StrategyPricePlacement` (док: `marketStructureSetting`).
2. Литерал CONSTANT консолидирован: `valueType`
   (`ConstantValueType`: NUMBER/PERCENT/ENUM/BOOLEAN) + единое
   `value: String` (вместо `name`/`stringValue`/`numberValue` формы
   ввода).
3. PRICE-операнд несёт `priceSource: StrategyPriceSource`.
4. `CANDLE_CLOSED` — плоское доменное правило с простым полем
   `timeframe` на правиле (rule-level timeframe в остальном снят).
5. «Ровно одна detail на один MarketPhase.Type» реализовано
   буквально: на create обязательны все 4 фазы (неторгуемая —
   явный NO_TRADE-detail). Риск-поля детали nullable (NO_TRADE без
   риска).
6. Числовые типы: `*Percents`/`*Score`/`*Ratio`/`*Multiplier` →
   `BigDecimal` (numeric(36,18)); `*Bars`/`*Period`/`level`/`warmup`
   → `Integer`.
7. Warmup-floor валидатора — упрощённый минимум шага 2 (окно/
   рекурсивные → period; MACD → slow+signal; стохастик → сумма окон;
   OBV → 1); настоящий derive — у реализаций индикаторов (шаг 3).
8. Матрица политика×фаза — метод доменного enum'а
   `PhaseEntryPolicy.isAllowedFor(phase)`.
9. PUT-форма статуса: `PUT /{internalId}/status` + тело `{status}`;
   `CREATED` руками не ставится (400).
10. 400/422 — `ResponseStatusException` как осознанный минимум до
    error-конвенции (TBD в codestyle; коды заданы decision'ом).
11. JSONB-хранение: `String`-поля entity + `@JdbcTypeCode`;
    (де)сериализация на границе DataService через
    `StrategyJsonConverter`; дискриминатор `indicatorType` пишется
    один раз (EXTERNAL_PROPERTY + WRITE_ONLY + visible) — проверено
    round-trip'ом.
12. Self-FK действий deferrable; `targetActionKey` резолвится в
    `StrategyDataService.save` после вставки дерева
    (managed-update в той же транзакции).
13. `MarketPhaseParams.AlgorithmType` — вложенный enum params.
14. Ключи indicator- и structure-настроек — раздельные пространства
    (уникальность в своём списке контейнера).
15. Api-модели вложенных узлов shared между request и response;
    аудит — только на корне ответа (`AuditableApiResponse`).
16. **Имена таблиц — во множественном числе** (правило пользователя,
    общее для проекта; зафиксировано в `codestyle.md` §Схема БД):
    `strategy_details`, `strategy_steps`, `strategy_actions`,
    `strategy_order_actions`, `strategy_algo_order_actions`,
    `strategy_position_actions`, `strategy_market_phase_settings`.
    Доки (`Strategy.md` §Персистентность,
    `strategy-tree-persistence.md`) именуют эти таблицы в
    единственном — выровнять на `SYNC_DOCS_FROM_CODE`. FK-колонки и
    constraint'ы — в единственном (как в V1).

## Находки ревью (fixed в этой итерации)

- `conventions`: матрица политика×фаза дублировалась
  валидатором при неиспользуемых методах модели → перенесена в
  `PhaseEntryPolicy.isAllowedFor`, неиспользуемые методы
  `StrategyDetail` удалены; `StrategyDataService.updateStatus` →
  `void` (возврат не использовался).
- `performance`: загрузка дерева — один join-fetch-запрос (без N+1);
  PUT статуса делает 4 запроса — осознанно (редкая админ-операция);
  FK-индексы не создавались (объёмы стратегий малы) — кандидат на
  будущее.
- `disaster`: create атомарен (дерево+резолв в одной транзакции);
  гонка двух активаций ловится частичным UNIQUE (500 при коллизии —
  до error-конвенции); повторный POST того же internalId → 500 от
  UNIQUE (хотелось бы 409 — упирается в error-конвенцию, TBD);
  битый JSONB из БД → IllegalStateException (500, честно).

## Осталось до закрытия под-шага

1. **Аппрув пользователя** (по процессу — часть `CODE`).
2. Runtime-прогон (PostgreSQL + миграция V2, POST «одной реализации»,
   GET, PUT-переходы) — после/в рамках аппрува.
3. После аппрува — переход к `SYNC_DOCS_FROM_CODE` (сигналы — раздел
   выше).

## Затронутые файлы (staged)

`src/main/java/...` — 78 новых/правленых файлов (домен 33,
persistence 9, api 26, mapping 2, сервисы 3, валидатор 1, контроллер
1 + правки Instrument-слоя), `src/main/resources/db/migration/
V2__create_strategy_tables.sql`,
`src/main/resources/strategy-examples/trend-following-ema.json`,
`.claude/work/roadmap/phase-1.md` (статус), этот файл.
