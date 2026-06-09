# Персистентность дерева Strategy

## На какой вопрос отвечает этот файл

Почему дерево `Strategy` персистится реляционным каркасом с
объектными связями через FK, а листовые настройки рыночных данных и
условие шага — JSONB у владельца, и почему это сознательный отход от
архивной (и от прежней `GAPS_CLOSE_2`) схемы.

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
7 наследников; `MarketStructureParams`) едут внутри того же JSON
(только непустые значения); дискриминатор подтипа `IndicatorParams` в
payload не дублируется — тег несёт `indicatorType` настройки-владельца
(Jackson `EXTERNAL_PROPERTY`). В коде иерархия типов
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

**Следствие — `MarketStructureParams` / `phaseRules`.**
`MarketStructureParams` едет внутри JSONB-настройки.
У `StrategyMarketPhaseSetting` отдельного `params`-объекта нет —
`MarketPhaseParams` распущен редизайном условной фазы
(`docs/decisions/market-phase-conditional-classification.md`). Правила
классификации фазы `phaseRules` (клаузы `StrategyMarketPhaseRule` с
вложенным `condition`) едут **JSONB-колонкой `phase_rules`** на
реляционной строке контейнера (условие внутри клаузы — тот же JSONB, что
и `condition` шага). Отдельного решения «JSONB vs колонки» для них
больше нет — представление задаёт общее правило
(`docs/rules/persistence-representation.md`).

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

Базовая таблица `strategy_actions` (`id`, `strategy_step_id`,
`strategy_detail_id`, `action_kind`, `key`, `action_type`, `level`,
`target_action_key`, `target_action_id`) + таблицы по видам:
`strategy_order_actions`, `strategy_algo_order_actions`,
`strategy_position_actions` (у позиции собственных полей нет —
вырожденная подтаблица, допустимо при `JOINED`). Вложенные настройки
действий (`placement`, `attachedProtection`, `stopLossSettings`,
`trailingSettings`) — JSONB-поля на строках соответствующих видов.

**Родительская FK и денормализация под `UNIQUE` (`GAPS_CLOSE_4`, Н2).**
`strategy_step_id` — FK на родительский `strategy_steps` (действие
принадлежит шагу). `strategy_detail_id` — **денормализованный** FK на
`strategy_details` ради DB-`UNIQUE(strategy_detail_id, key)`:
уникальность `key` действия по правилу валидации №2 — в рамках
`StrategyDetail`, между действием и деталью лежит шаг, `UNIQUE` через
join невозможен. Денормализация безопасна при immutable-записи — тот
же аргумент, что у `target_action_id`. Отклонённая альтернатива —
проверка приложения (как у `key` JSONB-настроек): у действия, в
отличие от JSONB-настройки, есть реляционная строка — DB-защита
доступна, от неё не отказываемся.

Self-ссылка действия хранится **двумя** колонками базовой таблицы:
`target_action_key` (логический ключ — форма ввода и чтения) и
`target_action_id` (self-FK `→ strategy_actions.id`, deferrable;
резолвится при
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
`strategy_steps` с колонками `strategy_detail_id` (FK), `deal_status`
(ключ map), `step_index` (порядок в списке). В домене Map
пересобирается группировкой по `deal_status` и сортировкой по
`step_index`.

**Отклонённая альтернатива.** Отдельная join/map-таблица «detail →
status → step». Отклонено: step и так принадлежит detail; колонки
`deal_status` + `step_index` на строке шага несут и ключ, и порядок
без лишней таблицы.

### Условие (`StrategyCondition`) — JSONB на строке шага (`GAPS_CLOSE_5`, STRAT-Q5)

Условие шага (`StrategyCondition` с `rules` и операндами) персистится
**целиком JSONB-полем `condition` на строке `strategy_steps`**: массив
правил (`level` / `ruleType` / `operator` + простые поля), операнды —
JSONB внутри того же объекта. Отдельных таблиц `strategy_conditions` /
`strategy_condition_rules` нет; перечень реляционных каркасных узлов не
пополняется.

**Почему.** Условие точно ложится под дефолт правила
`docs/rules/persistence-representation.md`: навешано на каркасный
`strategy_steps`, FK внутрь правила/условия ниоткуда нет, операнды и
так JSONB, дерево immutable и грузится агрегатом целиком; evaluator
десериализует условие в объектную модель независимо от формы хранения.
Симметрично с ревизией `GAPS_CLOSE_3` (листовые настройки → JSONB в
контейнере, не реляционные строки).

**Отклонённая альтернатива.** Реляционные `strategy_condition` (1:1 на
шаге) + дочерние строки `strategy_condition_rule` (`level` /
`rule_type` / `operator`, операнды JSONB) — это осознанное исключение
из правила, которое требовало бы storage-специфичной причины:
SQL-запросов по правилам между стратегиями или внешнего FK на правило.
Для immutable-агрегата, грузящегося целиком, такой причины нет;
вдобавок `strategy_conditions` оказалась бы 1:1-таблицей без полезной
нагрузки.

### Внутридеревные ссылки — FK / «мягкие» / self-FK по типу ссылки

- **Операнд условия → настройка** (индикаторный операнд по
  `indicatorKey`, market-structure операнд по `structureKey`) —
  «мягкая» ссылка: ключ внутри структуры операнда, резолвит
  приложение. STRAT-Q1 перенёс ссылку с правила на операнд
  (`docs/decisions/strategy-condition-authoring-contract.md`); прежний
  rule-level FK `StrategyConditionRule.indicatorSetting` снят вместе с
  объектными ссылками на правиле.
- **Ссылки изнутри JSON-листьев** (напр. `stopLossSettings` с
  `ATR_PERCENT` → индикаторная настройка по `indicatorKey`) —
  «мягкие»: ключ внутри
  JSON, резолвит приложение (FK из JSONB невозможен).
- **`targetActionKey`** при сохранении стратегии резолвится в self-FK
  `target_action_id → strategy_actions.id`; базовая таблица хранит **и**
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
- **`GAPS_CLOSE_4` — общее правило + Н2/Н3/Н4; условие (Н1) открыто.**
  Решение обобщено в общее правило проекта
  `docs/rules/persistence-representation.md` (реляционно — каркас и
  FK-адресуемые сущности; всё навешанное — JSONB у владельца; `params`
  всегда JSONB; полиморфный JSONB — дискриминатор на владельце,
  Jackson `EXTERNAL_PROPERTY`; счётчик полей — не критерий). По
  правилу закрыты: Н2 — `strategy_action` несёт `strategy_step_id`
  (родитель) и денормализованный `strategy_detail_id` под
  `UNIQUE(strategy_detail_id, key)` (см. §Действия); Н3 —
  `MarketPhaseParams` разведён с листовыми params (JSONB-колонка
  контейнера) — *тип позже распущен редизайном условной фазы, его слот
  занял `phaseRules`; см. §Следствие и
  `docs/decisions/market-phase-conditional-classification.md`*; Н4 —
  `indicatorType` снят с базы `IndicatorParams`,
  дискриминатор — поле настройки-владельца. Представление условия
  (`StrategyCondition`/`StrategyConditionRule`) сознательно **не**
  закрыто — открытый вопрос STRAT-Q5.
- **`GAPS_CLOSE_5` — условие → JSONB (STRAT-Q5 закрыт).** Представление
  условия зафиксировано по дефолту правила: JSONB-поле `condition` на
  строке `strategy_steps` (см. §Условие). В `Strategy.md` снята
  формулировка «операнды — JSONB на строке `strategy_condition_rule`»
  (§Внутридеревные ссылки теперь указывает на condition-JSONB шага),
  добавлен §Условие в §Персистентность, метка STRAT-Q5 убрана из
  §Не зафиксировано; entity/миграция условия разблокированы.
- **`CODE` шага 2 (2026-06-05) — схема материализована.** Имена таблиц
  приведены к общему правилу множественного числа
  (`.claude/rules/codestyle.md` §Схема БД): `strategies`,
  `strategy_market_phase_settings`, `strategy_details`,
  `strategy_steps`, `strategy_actions`, `strategy_order_actions`,
  `strategy_algo_order_actions`, `strategy_position_actions`
  (FK-колонки/constraint'ы — в единственном). Self-FK
  `target_action_id` объявлен deferrable (порядок вставки строк внутри
  транзакции не ограничивает); резолв `targetActionKey` → id выполняет
  `StrategyDataService` после вставки дерева. Типы числовых полей
  зафиксированы (`Strategy.md` §Типы числовых полей). На `strategies` —
  частичный UNIQUE «одна ACTIVE на инструмент». Нейминг «мягких» ссылок
  унифицирован: `indicatorKey` / `structureKey` во всех носителях
  (операнды, `StopLossSettings`, `StrategyPricePlacement`).

## Связи

- Модель и схема — `docs/models/domain/aggregate/Strategy.md`
  (§Персистентность, §key / targetActionKey и валидация, §Связь с
  DealActionState).
- Контракт авторинга условия (операнд → настройка по ключу) —
  `docs/decisions/strategy-condition-authoring-contract.md`.
- Общее правило представления сущностей в БД (обобщение этого
  решения) — `docs/rules/persistence-representation.md`.
- Открытые вопросы шага 2 — `.claude/work/questions/open-questions.md`
  (STRAT-Q4 — percent-anchor).
