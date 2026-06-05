# Snapshot v30

**Дата:** 2026-06-05.

## На какой вопрос отвечает этот файл

Где мы сейчас и как сюда пришли (срез после закрытия шага 2: `CODE`
с аппрувом → `SYNC_DOCS_FROM_CODE` → `DONE`; шаг 2 заархивирован в
history).

## Состояние

Фаза 1 — `IN_PROGRESS`; **шаги 1-2 — `DONE`**; шаги 3-11 — `HOLD`.
Фронтир v29 (`CODE` шага 2) — **выполнен и закрыт**: код написан,
отревьюен, заапрувлен (2026-06-05), доки синхронизированы, шаг
закрыт. Runtime-прогон Strategy API — осознанный хвост в backlog
п.8 (PostgreSQL не поднимался).

## Что изменилось относительно v29

### `CODE` шага 2 — написан, отревьюен, заапрувлен

Детали — `history/2026-06-05-phase-1-step-2-strategy/
phase-1-step-2-code.md`. Построено: полное immutable-дерево
`Strategy` в домене (33 класса) + скелеты-носители enum'ов смежных
кластеров; persistence (8 entity, JOINED-действия, JSONB-навес,
Flyway `V2`, self-FK deferrable, частичный UNIQUE «одна ACTIVE на
инструмент»); `StrategyMapper` + `StrategyJsonConverter`;
`StrategyDataService` (резолв `targetActionKey`) / `StrategyService`;
Strategy API (`POST` / `GET /{internalId}` / `PUT /{internalId}/status`,
21 api-модель); create-валидатор (структурно-ссылочный, 400; 422 —
lifecycle-переходы и вторая ACTIVE). «Одна реализация» заавторена с
конструктивным касанием `trading-specialist` по СТ-1
(`strategy-examples/trend-following-ema.json`: EMA-тренд, фаза 1H /
сигнал 15m / тайминг 5m, RANGE/UNKNOWN → NO_TRADE, риск 1%).
Решения уровня кода (нейминг ссылок `indicatorKey`/`structureKey`,
литерал CONSTANT `valueType`+`value`, типы числовых полей, «все 4
детали на create», warmup-floor шага 2 и др.) — перечнем в
прогресс-файле `CODE`. Ревью-итерация
conventions/performance/disaster пройдена (находки исправлены);
проверки: `mvn compile` чистый, Jackson round-trip JSONB-настроек.

### Конвенции по замечаниям пользователя на ревью

`codestyle.md` дополнен: **§Схема БД** (имена таблиц — во
множественном числе; FK-колонки/constraint'ы — в единственном) —
применено к `V2` и entity; **§Строгие правила** — запрет deprecated
API (`HttpStatus.UNPROCESSABLE_CONTENT` вместо `UNPROCESSABLE_ENTITY`,
`ObjectMapper.setDefaultPropertyInclusion`; проверка — компиляция с
`showDeprecation` без предупреждений).

### `SYNC_DOCS_FROM_CODE` — доки приведены к коду

Детали — `.../phase-1-step-2-sync-docs-from-code.md`: 10 change +
4 add, remove 0. Правлены: `Strategy.md` (ссылки-ключи, литерал,
таблицы, типы полей, инвариант деталей), `strategy-tree-persistence.md`
(+ревизия `CODE`), `strategy-condition-authoring-contract.md`
(инкрементальные пункты закрыты; per-ruleType минимум),
`strategy-materialization-and-validation.md` (форма API),
`lifecycles/Strategy.md` (таблица переходов; `CREATED→INACTIVE`
запрещён), `rules/persistence-representation.md` (механика
единственного тега: EXTERNAL_PROPERTY + WRITE_ONLY + visible).
Новый — `docs/models/mapping/Strategy.md` (api↔domain↔persistence).

### Шаг 2 → `DONE`, архивация

По конвенции шага 1: summary
`history/2026-06-05-phase-1-step-2-strategy.md` + подпапка с 18
артефактами (8×`DOCS_CHECK`, 7×`GAPS_CLOSE`, `CODE`, `SYNC`,
tasks-файл СТ-1). `progress/` очищен от шага 2. Backlog п.8
обновлён (построенное; **runtime-прогон** — хвост: `V2` → POST
JSON-реализации → GET → PUT-переходы, точка — старт шага 3); живая
ссылка IND-Q1 → СТ-1 перенаправлена в архив.

## Активные задачи

- `trading-council-materialization.md`,
  `trading-library-distillation.md` — без изменений (завершены,
  остаётся ревью).

## Текущий фронтир / следующее действие

**Шаг 3 «Индикаторы»** (расчёт/чтение/сохранение значений,
запрошенных стратегией): старт с `TOOLING` → `DOCS_CHECK_1` по
процессу. Там ждут: IND-Q1 (надёжность объёма), warmup-derive у
реализаций индикаторов (вместо floor шага 2), runtime-прогон
Strategy API при поднятом PostgreSQL (живая стратегия нужна шагу 3).

Коммит — за пользователем (всё staged, CC не коммитит).

## Открытые общие вопросы

`open-questions.md`: **17** (без изменений с v29; код вопросов не
закрывал и не открывал; STRAT-Q4 жив — код его не решал).

## Что в работе / PK

- Шаг 3 — к старту.
- **Project Knowledge:** последний снапшот теперь **`snapshot-v30`**
  (заменяет v29 в префлайте). `CLAUDE.md` / `structure.md` /
  `naming.md` / `place-knowledge.md` не менялись. `codestyle.md`
  менялся дважды (§Схема БД, §Строгие правила) — если он есть в PK,
  обновить вместе со снапшотом.
- Staged сейчас: код шага 2 (~106 файлов src/), синк-правки 7 доков
  + новый `mapping/Strategy.md`, закрытие шага (history summary +
  подпапка, roadmap `DONE`, backlog, open-questions), `codestyle.md`,
  этот снапшот.
