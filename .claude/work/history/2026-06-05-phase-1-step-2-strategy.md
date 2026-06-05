# Шаг 2 Фазы 1 — стратегия (DONE)

## На какой вопрос отвечает этот файл

Что сделано в шаге 2 Фазы 1 (Стратегия: абстракция + одна реализация)
и где детальные артефакты.

## Итог

Шаг 2 «Стратегия (абстракция: объявляет нужные индикаторы и условие
сигнала; одна реализация)» доведён до `DONE` по процессу docs-first:
`TOOLING` (×2: ревьюер/фокусы; торговый совет — `trading-specialist`,
дистиллят корпуса, фокус `trading-review`) → 8 итераций
`DOCS_CHECK`/`GAPS_CLOSE` (включая первый прогон торгового фокуса на
`DOCS_CHECK_8`) → `CODE` (написание + конструктивное касание
`trading-specialist` по СТ-1 + ревью conventions/performance/disaster
+ аппрув 2026-06-05) → `SYNC_DOCS_FROM_CODE` → `DONE`.

## Что построено (код, `src/`)

- Domain: полное immutable-дерево `Strategy` (root, детали, шаги,
  настройки рыночных данных с полиморфными params, условия с
  самоописательными операндами, действия ORDER/ALGO_ORDER/POSITION,
  15 enum'ов) + скелеты-носители enum'ов смежных кластеров
  (`Deal.Status`, `MarketPhase.Type`, `IndicatorValue.Type`,
  `MarketStructure.Type`, `Order.Type`, `AttachedAlgoOrder.Type`,
  `AlgoOrder.ConditionType`/`TriggerPriceType`).
- Persistence: 8 entity (JOINED-действия, JSONB-навес `String` +
  `@JdbcTypeCode`), `StrategyRepository` (дерево одним join fetch),
  Flyway `V2` (таблицы во множественном числе; self-FK deferrable +
  CHECK; `UNIQUE(strategy_detail_id, key)`; частичный UNIQUE «одна
  ACTIVE на инструмент»).
- Mapping: `StrategyMapper` (`@SubclassMapping`, stepsByStatus ↔
  плоские строки, порядок действий id ASC) + `StrategyJsonConverter`;
  резолв `targetActionKey` → self-FK в `StrategyDataService`.
- API: `StrategyController` (`POST` / `GET /{internalId}` /
  `PUT /{internalId}/status`), 21 api-модель,
  `StrategyCreateRequestValidator` (структурно-ссылочный create, 400;
  422 — переходы lifecycle и вторая ACTIVE).
- «Одна реализация»:
  `src/main/resources/strategy-examples/trend-following-ema.json` —
  EMA-тренд-фоллоинг (фаза 1H / сигнал 15m / тайминг 5m;
  BULL/BEAR FOLLOW_PHASE, RANGE/UNKNOWN NO_TRADE; ATR-стоп → OCO →
  безубыток +1% → трейлинг +2% со снятием OCO; риск 1%, R:R 2.5);
  СТ-1 применён (без CONTRARIAN, трейлинг с порогом, объём не
  основание ENTRY).

## Конвенции, зафиксированные на этом шаге

`codestyle.md`: §Схема БД (имена таблиц — множественное число;
FK/constraint'ы — единственное); §Строгие правила — запрет deprecated
API (`UNPROCESSABLE_CONTENT`, `setDefaultPropertyInclusion`; проверка
`-Dmaven.compiler.showDeprecation=true`).
`persistence-representation.md`: механика единственного тега
полиморфного JSONB (EXTERNAL_PROPERTY + WRITE_ONLY + visible).

## Доки, синхронизированные под код (SYNC_DOCS_FROM_CODE)

`Strategy.md` (нейминг ссылок `indicatorKey`/`structureKey`; литерал
CONSTANT `valueType`+`value`; типы числовых полей; «все 4 детали»;
таблицы), `strategy-tree-persistence.md`,
`strategy-condition-authoring-contract.md` (per-ruleType минимум),
`strategy-materialization-and-validation.md` (форма API),
`lifecycles/Strategy.md` (таблица переходов), новый
`models/mapping/Strategy.md`.

## Не выполнено осознанно

- **Runtime-прогон** (PostgreSQL + V2 → POST «одной реализации» →
  GET → PUT-переходы) — PostgreSQL не поднят; шаг закрыт по аппруву
  кода и статическим проверкам (компиляция, Jackson round-trip).
  Прогон — хвост в backlog п.8 (выполнить при поднятом PostgreSQL,
  естественная точка — старт шага 3).
- Семантика действий (правила 4-7, 9-12) — отложена до шагов 4/7 /
  activate (по decision).
- Известные ограничения до error-конвенции (TBD): гонка активаций →
  500 от частичного UNIQUE; дубль POST internalId → 500.

## Открытые вопросы шага (живут в open-questions.md)

STRAT-Q4 (percent-anchor; негейтящий), RISK-Q2 (worst-case guard —
шаг 5), IND-Q1 (надёжность объёма — шаг 3).

## Детальные артефакты

Подпапка `2026-06-05-phase-1-step-2-strategy/`: 8×`DOCS_CHECK`,
7×`GAPS_CLOSE`, `CODE` (решения уровня кода, находки ревью),
`SYNC_DOCS_FROM_CODE` (список расхождений), tasks-файл (СТ-1 —
чек-лист торгово-наивного авторинга, применён на `CODE`).
