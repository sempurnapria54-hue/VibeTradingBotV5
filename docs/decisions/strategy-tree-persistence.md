# Персистентность дерева Strategy

## На какой вопрос отвечает этот файл

Почему дерево `Strategy` персистится реляционным каркасом с
объектными связями через FK, а листовые настройки рыночных данных —
JSONB внутри контейнера, и почему это сознательный отход от архивной
(и от прежней `GAPS_CLOSE_2`) схемы.

## Контекст

`Strategy` — immutable-дерево (root → `StrategyDetail` →
`StrategyStep` → `StrategyAction` + ветви настроек рыночных данных и
условий; полная структура — `docs/models/domain/aggregate/Strategy.md`).
Шаг 2 Фазы 1 строит и **персистит** полное дерево и сидит одну
заполненную стратегию, поэтому докам нужна схема хранения (как у
моделей шага 1).

До этого решения у `Strategy.md` раздела «Персистентность» не было, а
косвенные признаки представления противоречили друг другу:
реляционные (`id` на узлах, `UNIQUE(strategy_detail_id, key)`,
загрузка через `@EntityGraph` / `JOIN FETCH`) и документные
(JSON-дискриминатор `actionKind`, «`key` задаётся в JSON»,
`Strategy API examples`). Пробел зафиксирован как эскалация Э4 на
`DOCS_CHECK_2`; решение принято на разборе `GAPS_CLOSE_2` и
применяется здесь.

## Принятое решение

Каркас дерева — **реляционный** для узлов-контейнеров и каркасных узлов
(`Strategy` (root), `StrategyMarketPhaseSetting`, `StrategyDetail`,
`StrategyStep`, `StrategyAction`): каждый такой узел — своя строка/
таблица с `id`; объектные связи между ними — через FK; загрузка дерева
целиком — `@EntityGraph` / `JOIN FETCH` (без N+1). **Листовые настройки
рыночных данных (`StrategyIndicatorSetting`,
`StrategyMarketStructureSetting`) хранятся JSONB** внутри строки своего
контейнера — отдельных строк/таблиц у них нет. Состав — по развилкам
ниже.

> **Ревизия (`GAPS_CLOSE_3`, эскалация Н3).** На `GAPS_CLOSE_2`
> настройки были описаны как реляционные узлы (строка с `id` + FK),
> JSONB — только их `params`. `GAPS_CLOSE_3` перевёл сами листовые
> настройки в JSONB внутри контейнера (см. §Настройки рыночных данных).
> Реляционными остаются только узлы-контейнеры и каркасные узлы выше.

**Отклонённая альтернатива (на уровень дерева целиком).** JSON-документ
всего дерева с промотированными/индексируемыми колонками
(`instrumentId`, `status`, `internalId`). Отклонено: теряются FK-связи
и реляционная целостность внутридеревных ссылок, усложняется частичная
выборка и валидация; реляционные сигналы в модели (`id` на узлах,
`UNIQUE`) подтверждены как намеренные.

### Настройки рыночных данных — JSONB внутри контейнера

`StrategyIndicatorSetting` и `StrategyMarketStructureSetting` (в обоих
контейнерах — `StrategyMarketPhaseSetting` и `StrategyDetail`) хранятся
**JSONB**, а не отдельными реляционными строками/таблицами: каждый
контейнер несёт свои `indicatorSettings` / `marketStructureSettings`
JSON-массивами на собственной строке. Их `params` (`IndicatorParams` +
7 наследников; `MarketStructureParams` / `MarketPhaseParams`) едут
внутри того же JSON (только непустые значения). В коде иерархия типов
сохраняется; в БД — JSON, без таблиц настроек и params и без
inheritance-маппинга. Валидацию полей и уникальность `key` настройки в
пределах контейнера (проверка по JSON-массиву, не DB-UNIQUE) берёт
приложение.

**Почему не реляционные узлы (отход от стойки `GAPS_CLOSE_2`).** На
настройку ничего не ссылается жёсткой FK: операнд условия
(`indicatorKey`) и мягкие ссылки JSON-листьев (`stopLossSettings` с
`ATR_PERCENT`) адресуют её **по `key`**, резолвит приложение; `id`
настройки в рантайме не используется (там работает только
`StrategyAction.id` через `DealActionState`). Настройки immutable и
читаются всегда вместе с родителем. Исходный мотив реляционности —
FK-целостность внутридеревных ссылок — к ним не применяется.
Согласуется с тем, что `params` и так JSONB.

**Следствие — `MarketStructureParams` / `MarketPhaseParams`.** Едут
внутри JSONB-настройки; отдельного решения «JSONB vs колонки» для них
больше нет — оно растворяется в этом решении.

**Сознательный отход от архива (params).** В архиве `IndicatorParams` —
отдельная сущность с наследованием. Отказались: мелкие наследники
(1-3 числовых поля каждый) не оправдывают отдельных таблиц и
inheritance-маппинга; params immutable, читаются всегда вместе с
настройкой, самостоятельных выборок по ним нет.

**Отклонённые альтернативы.** Реляционные строки настроек с FK (стойка
`GAPS_CLOSE_2`) — `id` не нужен ни ссылкам (по `key`), ни рантайму;
таблица(ы) `indicator_params` с inheritance-стратегией (как в архиве) —
избыточно для immutable value-настроек без самостоятельных запросов.

### `timeframe` — провизорная асимметрия размещения

При JSONB-хранении персистентная асимметрия `timeframe` (колонка vs
`params`) исчезает — всё JSON. Доменное размещение оставлено как есть:
у индикатора `timeframe` внутри `params`, у структуры/фазы — прямое
поле настройки. К единому виду сейчас **не** сводим: интент (первичный
атрибут vs параметр) не определён, потребитель (candle-loading)
запаркован. Это осознанный провизорный выбор, не необъяснённая
нестыковка; ревизия — вместе с candle-loading.

### Действия (`StrategyAction`) — реляционно, наследование `JOINED`

Базовая таблица `strategy_action` (`id`, `action_kind`, `key`,
`action_type`, `level`, `target_action_key`, `target_action_id`) +
таблицы по видам: `strategy_order_action`, `strategy_algo_order_action`,
`strategy_position_action` (у позиции собственных полей нет —
вырожденная подтаблица, допустимо при `JOINED`). Вложенные настройки
действий (`placement`, `attachedProtection`, `stopLossSettings`,
`trailingSettings`) — JSONB-поля на строках соответствующих видов.

Self-ссылка действия хранится **двумя** колонками базовой таблицы:
`target_action_key` (логический ключ — форма ввода и чтения) и
`target_action_id` (self-FK `→ strategy_action.id`, резолвится при
сохранении). Денормализация принята как безопасная при immutable-записи;
защита БД — FK + CHECK `target_action_id <> id` (нет self-loop). FK
здесь — в отличие от мягкой ссылки операнд→настройка (по `key`) — ради
БД-защиты ссылки действие→действие.

Общие поля (`key`, `action_type`, `level`, `target_action_key`,
`target_action_id`) живут в базовой таблице один раз; `action_kind` —
дискриминатор.

**Отклонённые альтернативы.** `SINGLE_TABLE` — разреженная таблица с
nullable-колонками всех видов, хуже выражает специфичные поля видов;
`TABLE_PER_CLASS` — дублирует общие поля и усложняет self-FK
`target_action_id`.

### `stepsByStatus` — плоские строки, не map-таблица

`Map<Deal.Status, List<StrategyStep>>` хранится плоскими строками
`strategy_step` с колонками `strategy_detail_id` (FK), `deal_status`
(ключ map), `step_index` (порядок в списке). В домене Map
пересобирается группировкой по `deal_status` и сортировкой по
`step_index`.

**Отклонённая альтернатива.** Отдельная join/map-таблица «detail →
status → step». Отклонено: step и так принадлежит detail; колонки
`deal_status` + `step_index` на строке шага несут и ключ, и порядок
без лишней таблицы.

### Внутридеревные ссылки — FK / «мягкие» / self-FK по типу ссылки

- **Операнд условия → настройка** (индикаторный операнд по
  `indicatorKey`, market-structure операнд по ключу настройки) —
  «мягкая» ссылка: ключ внутри структуры операнда, резолвит
  приложение. STRAT-Q1 перенёс ссылку с правила на операнд
  (`docs/decisions/strategy-condition-authoring-contract.md`); прежний
  rule-level FK `StrategyConditionRule.indicatorSetting` снят вместе с
  объектными ссылками на правиле.
- **Ссылки изнутри JSON-листьев** (напр. `stopLossSettings` с
  `ATR_PERCENT` → индикаторная настройка) — «мягкие»: id/key внутри
  JSON, резолвит приложение (FK из JSONB невозможен).
- **`targetActionKey`** при сохранении стратегии резолвится в self-FK
  `target_action_id → strategy_action.id`; базовая таблица хранит **и**
  ключ, **и** id (денормализация, безопасная при immutable-записи).
  Защита БД — FK + CHECK `target_action_id <> id`. FK здесь, в отличие
  от мягкой ссылки операнд→настройка, — ради БД-защиты ссылки
  действие→действие (валидация ссылки — 12 правил в `Strategy.md`).

## Следствия

- `docs/models/domain/aggregate/Strategy.md` — добавлен раздел
  «Персистентность» с конкретной схемой (таблицы, колонки, JSONB-поля,
  FK, загрузка через `@EntityGraph` / `JOIN FETCH`); ссылается сюда за
  обоснованием.
- **Типы и nullability числовых полей дерева** (Integer vs BigDecimal
  для `*Bars`/`*Period` vs `*Percents`/`*Score`/`*Ratio`/
  `*Multiplier`) на этом разборе **не решались** — проставляются при
  написании entity/Flyway-миграции.
- Это решение закрывает эскалацию **Э4** (`GAPS_CLOSE_2`). Грамматика
  условия (Э2 / STRAT-Q1), терминология «сигнала» (Э3 / STRAT-Q2) и
  создание/валидация одной реализации (Э5 / STRAT-Q3) закрыты
  отдельными решениями
  (`docs/decisions/strategy-condition-authoring-contract.md`,
  `strategy-signal-is-entry-condition.md`,
  `strategy-materialization-and-validation.md`).
- **Ревизия `GAPS_CLOSE_3` (Н1/Н2/Н3).** Листовые настройки переведены
  в JSONB внутри контейнера (Н3, см. §Настройки рыночных данных);
  асимметрия `timeframe` помечена провизорной (Н3/в); `id` убран из
  базы `IndicatorParams` как рудимент архива при JSONB-value-объекте
  (Н2, `Strategy.md` §IndicatorParams); self-ссылка действия хранится
  `target_action_key` + self-FK `target_action_id` с CHECK (Н1, см.
  §Действия). `Strategy.md` §Персистентность / §IndicatorParams
  выровнены под это.
- **Downstream-следствие — направление решено (`GAPS_CLOSE_3`).** Под
  JSONB-настройки больше нет `strategy_indicator_setting_id` /
  `strategy_market_structure_setting_id` как реляционного `id`, на
  который ссылались результаты расчёта `IndicatorValue` (Фаза 3) и
  `MarketStructure` (Фаза 4). Решено: эти результаты ключуются по
  **идентичности считаемого** (тип + `timeframe` + canonical-`params`),
  считаются раз на инструмент и шарятся всеми настройками; ссылка на
  настройку из результата убирается — это и снимает следствие
  (`docs/decisions/market-data-result-identity-keying.md`). Точная схема
  идентичности и правка `UNIQUE` — при построении кластеров Фаз 3/4.
  `MarketPhase` не затронут (ключ — контейнер `StrategyMarketPhaseSetting`,
  у него строка/`id` есть).

## Связи

- Модель и схема — `docs/models/domain/aggregate/Strategy.md`
  (§Персистентность, §key / targetActionKey и валидация, §Связь с
  DealActionState).
- Контракт авторинга условия (операнд → настройка по ключу) —
  `docs/decisions/strategy-condition-authoring-contract.md`.
- Открытые вопросы шага 2 — `.claude/work/questions/open-questions.md`
  (STRAT-Q4 — percent-anchor).
