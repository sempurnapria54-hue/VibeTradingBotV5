# SYNC_DOCS_FROM_CODE — шаг 2 Фазы 1 (Стратегия)

## На какой вопрос отвечает этот файл

На каком шаге мы в под-шаге `SYNC_DOCS_FROM_CODE` шага 2 Фазы 1
(доки ← утверждённый код) и что изменено.

## Контекст

- Вход: аппрув `CODE` (2026-06-05) + сигналы расхождений из
  `progress/phase-1-step-2-code.md` (п.1-16).
- Детект — фокус `divergence` (`.claude/skills/divergence-review.md`),
  направление docs←code; реконсиляция — `knowledge-curator`
  (`.claude/skills/reconcile-knowledge.md` для change/remove, штатное
  размещение для add).

## Статус: выполнен (одиночный проход)

## Список расхождений и их закрытие

### change (доки приведены к коду)

1. **Нейминг «мягких» ссылок** — `indicatorKey` / `structureKey` во
   всех носителях: `Strategy.md` (§инварианты, §операнд,
   §StopLossSettings, §StrategyPricePlacement,
   §StrategyMarketStructureSetting, §Внутридеревные ссылки),
   `strategy-tree-persistence.md`,
   `strategy-condition-authoring-contract.md`.
2. **Литерал CONSTANT** — `valueType: ConstantValueType`
   (NUMBER/PERCENT/ENUM/BOOLEAN) + единое `value: String`; PRICE-операнд
   несёт `priceSource`. Инкрементальные пометки сняты; отвергнутые
   альтернативы зафиксированы (authoring-contract).
3. **Имена таблиц — множественное число** (правило `codestyle.md`
   §Схема БД): `Strategy.md` §Персистентность,
   `strategy-tree-persistence.md` (+ ревизия-пометка «CODE шага 2» в
   Следствиях). FK-колонки/constraint'ы — в единственном.
4. **Инвариант деталей** — create требует детали всех 4 фаз (явный
   NO_TRADE), матрица политика×фаза — `PhaseEntryPolicy.isAllowedFor`
   (`Strategy.md` §root/§StrategyDetail,
   `strategy-materialization-and-validation.md`).
5. **Типы числовых полей** — `Strategy.md` §«Не зафиксировано»
   заменён фиксацией (`BigDecimal` ↔ `Integer`, nullable риск-полей
   NO_TRADE-детали).
6. **CANDLE_CLOSED** — плоское правило с простым полем `timeframe`
   (`Strategy.md` §StrategyConditionRule).
7. **Warmup-floor шага 2** — упрощённый минимум create-валидации,
   настоящий derive — шаг 3 (`Strategy.md` §IndicatorParams,
   materialization-decision).
8. **Форма API** — `PUT /{internalId}/status` `{status}`; CREATED
   руками нельзя; 422 = UNPROCESSABLE_CONTENT
   (materialization-decision).
9. **Per-ruleType контракт** — зафиксированный минимум использованных
   типов перенесён в authoring-contract (новый раздел).
10. **Self-FK deferrable** — резолв в DataService после вставки
    (`Strategy.md`, `strategy-tree-persistence.md`).

### add (новое знание из кода)

11. **`docs/models/mapping/Strategy.md`** (новый) — api↔domain↔
    persistence: полиморфизм actionKind/indicatorType, JSONB-навес,
    stepsByStatus↔плоские строки, порядок действий (id ASC), резолв
    targetActionKey, резолв instrumentInternalId.
12. **Допустимые переходы статуса** — таблица в
    `docs/lifecycles/Strategy.md` (CREATED→ACTIVE|DELETED,
    ACTIVE↔INACTIVE, *→DELETED, DELETED терминален,
    CREATED→INACTIVE запрещён) + инвариант одной ACTIVE с
    БД-страховкой.
13. **Механика единственного тега** (EXTERNAL_PROPERTY + WRITE_ONLY +
    visible) — `docs/rules/persistence-representation.md`.
14. **Частичный UNIQUE «одна ACTIVE на инструмент»** — `Strategy.md`
    §Персистентность.

### remove

Нет: код ничего из задокументированного не удалял (сняты только
«инкрементальные» пометки — покрыто change п.1-2, 5).

### Не доки-расхождения (отмечено, без правок docs/)

- Скелеты-носители enum'ов смежных кластеров — деталь реализации,
  модельные доки уже описывают enum'ы.
- Api-модели/контроллер/сервисы как отдельные доки компонентов — по
  прецеденту шага 1 не заводятся.
- `trend-following-ema.json` — ресурс кода, не файл знания; backlog
  п.8 (воспроизведение `Strategy API examples` как файла знания)
  остаётся открытым.

## Референты (проверены)

Свипы по `docs/`: имён таблиц в единственном числе — 0; `structure-key`
/ `priceKey` / «инкрементальная деталь» — 0 (оставшиеся
`indicatorSetting`/`marketStructureSetting` — исторический контекст
снятых rule-level ссылок в decision'ах, корректны). Открытые вопросы
не затронуты (STRAT-Q4 остаётся, код его не решал).

## Затронутые файлы (staged)

- `docs/models/domain/aggregate/Strategy.md`
- `docs/models/mapping/Strategy.md` (новый)
- `docs/lifecycles/Strategy.md`
- `docs/rules/persistence-representation.md`
- `docs/decisions/strategy-tree-persistence.md`
- `docs/decisions/strategy-condition-authoring-contract.md`
- `docs/decisions/strategy-materialization-and-validation.md`
- `.claude/work/roadmap/phase-1.md` (статус шага 2 →
  SYNC_DOCS_FROM_CODE)
- `.claude/work/progress/phase-1-step-2-code.md` (закрытие CODE)
- этот файл

## Дальше

Под-шаг 7 процесса — перевод шага 2 в `DONE` («код утверждён и доки
синхронизированы»). Перед закрытием остаётся незакрытый хвост scope:
«одна реализация», **заведённая через POST** — runtime-прогон
(PostgreSQL + V2, POST JSON, GET, PUT-переходы) не выполнялся.
Решение о моменте прогона и закрытии шага — за пользователем.
