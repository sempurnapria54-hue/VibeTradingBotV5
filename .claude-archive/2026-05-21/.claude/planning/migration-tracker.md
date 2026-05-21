# Чеклист прогресса миграции

Карта миграции `docs/domain/` → `docs/spec/`. Каждая запись показывает статус legacy-документа.

Стратегия миграции — [ADR-0009](../adr/0009-domain-migration-strategy.md).  
Скилл единичной миграции документа — [`spec-document-migration`](../skills/spec-document-migration/SKILL.md).

## Статусы

- `planned` — миграция не начата.
- `in-progress` — миграция идёт. Возможны частично разнесённые блоки и открытые Q-N в журнале.
- `done` — миграция завершена, MIGRATED-маркер проставлен на legacy-файле.

Целевые spec-документы в трекере не предзаявляются. Они перечисляются по факту создания — при миграции соответствующего legacy-документа.

## Фаза 1. Миграция домена

Восемь кластеров. Порядок и обоснование — в [ADR-0009 §2](../adr/0009-domain-migration-strategy.md).

### Кластер C-NEW: Exchange + Instrument (фундамент)

Статус кластера: `planned`.

**Документы:**

- `Справочник по доменным моделям.md` (фрагменты по Exchange и Instrument) — `planned`.
- `Статусы торговых сущностей.md` (фрагменты по Exchange и Instrument) — `planned`.
- `Расчёт индикаторов и рыночных данных.md` (фрагменты по Instrument) — `planned`.

### Кластер C4: Balance

Статус кластера: `planned`.

**Документы:**

- `Balance.md` — `planned`.
- `Статусы торговых сущностей.md` (фрагменты по Balance) — `planned`.

### Кластер C2: Order, AlgoOrder, Position

Статус кластера: `planned`.

**Документы:**

- `Order.md` — `planned`.
- `AlgoOrder.md` — `planned`.
- `Position.md` — `planned`.
- `Статусы торговых сущностей.md` (фрагменты по Order, AlgoOrder, Position) — `planned`.
- `Сервисные команды.md §12` (дубль таксономии Order/AlgoOrder/Position) — `planned`.

### Кластер C6: Market data

Статус кластера: `planned`.

**Документы:**

- `Расчёт индикаторов и рыночных данных.md` — `planned`.

### Кластер C5: Strategy

Статус кластера: `planned`.

**Документы:**

- `Strategy.md` — `planned`.
- `Strategy API examples.md` — `planned`.

### Кластер C1: Deal + FSM + DealContext + DealActionState

Статус кластера: `planned`.

**Документы:**

- `Deal.md` — `planned`.
- `Жизненный цикл сделки.md` — `planned`.
- `FSM этапы сделки.md` — `planned`.

### Кластер C7: Calculator + Risk + Command

Статус кластера: `planned`.

**Документы:**

- `Калькуляторы действий стратегии.md` — `planned`.
- `Оценка рисков.md` — `planned`.
- `Сервисные команды.md` — `planned`.

### Кластер C8: Audit (финал)

Статус кластера: `planned`.

**Документы:**

- `Аудит и история исполнения.md` — `planned`.
- `Статусы торговых сущностей.md` (общие правила резолвера) — `planned`.

## Фаза 2. Миграция OKX-маппингов

Статус: `planned`.

`docs/domain/models/mapping/okx/*` → `docs/spec/integrations/okx/{models,mapping}/`. Описание внешних DTO OKX, маппинг DTO ↔ домен, конкретные таблицы резолва статусов.

## Фаза 3. Обработка `Открытые вопросы по движку.md`

Статус: `planned`.

Перенос вопросов из корневого `docs/domain/Открытые вопросы по движку.md` в журнал `.claude/questions/open-questions.md` и разбор. После переноса исходный документ помечается MIGRATED-маркером.

## Фаза 4. Реорганизация `docs/api/`

Статус: `planned`.

Жанр API остаётся самостоятельным. Форма реорганизации решается отдельным ADR при старте фазы.

## Фаза 5. Цикл ревью смигрированной документации

Статус: `planned`.

Итеративная фаза. Ревью полной карты `docs/spec/`, заведение Q-N по неясным местам, разбор, уточнение или создание недостающих документов. Фаза заканчивается в состоянии насыщения.

## Фаза 6. Упразднение `docs/domain/`

Статус: `planned`.

Проверка MIGRATED-маркеров на всех legacy-файлах, проверка отсутствия ссылок на `docs/domain/` в spec/коде. Удаление `docs/domain/` целиком одним коммитом. Обновление CLAUDE.md и `docs/README.md`.
