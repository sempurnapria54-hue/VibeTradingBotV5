# Локальные вопросы: миграция AnomalyReport

## На какой вопрос отвечает этот файл

Что неясно / отложено по миграции архивной сущности AnomalyReport.

## Контекст

Источник: `.claude-archive/2026-05-21/docs/spec/models/core/AnomalyReport.md`
+ `.../docs/spec/lifecycle/AnomalyReport.md` (spec-формат). Стратегия:
парковать cross-cutting, создавать только владение AnomalyReport
(модель + lifecycle). Размещён в `docs/models/other/` (не `core`) по
`.claude/decisions/models-core-vs-other.md` — аудит/инцидент, не
торговая модель про бизнес-цикл сделки.

## Форвард-заметки (мигрируются с владельцем)

- **ANOM-Q1. `AnomalyJob` (anomaly/safety-кластер).** Создаёт
  `AnomalyReport`, оркестрирует обработку и переходы, подъём
  незавершённых отчётов после рестарта, политика повторной обработки
  `ERROR`-отчётов (открытый вопрос). Источник — `Жизненный цикл
  сделки`/anomaly-доки; спецификация в архиве TBD. Компонент/процесс.
  Относится к backlog «Anomaly / safety / kill-switch».

- **ANOM-Q2. `KillSwitchExecutor` (anomaly/safety-кластер).**
  Аварийное снятие риска по инструменту;
  `IN_PROGRESS → KILL_SWITCH_EXECUTED`. Частичный успех (риск не
  снят полностью) → `externalAfter` отражает фактическое состояние;
  политика обработки — открытый вопрос (backlog «KillSwitchExecutor —
  детальная спецификация»). Источники — `docs/context/KillSwitchService
  — ...md`, `docs/context/Аварийные executors — ...md`. Компонент.

- **ANOM-Q3. `TradeRuleValidator` (anomaly/safety-кластер).**
  Обнаруживает нарушение торгового инварианта → запуск обработки
  через `AnomalyJob`. Источник — `docs/context/TradeRuleValidator —
  модель, роль и flow.md`. Компонент.

- **ANOM-Q4. Severity → блокировка торговли (Instrument/Exchange).**
  `CRITICAL` → торговля по инструменту остаётся запрещённой до
  ручного разбора; `NON_CRITICAL` → после kill-switch может быть
  разрешена. Фактическая блокировка живёт в **статусе инструмента**
  (не в `AnomalyReport`). Enforcement зафиксировать при миграции
  Exchange/Instrument-модели (backlog «Exchange модель/lifecycle»).

- **ANOM-Q5. Сериализация jsonb-снимков.** Формат и версионирование
  `internalBefore`/`externalBefore`/`internalAfter`/`externalAfter` —
  отдельная задача «Стандарт описания персистентности доменных
  моделей» (общий методологический/реализационный стандарт, шире
  одной модели). Зафиксировать при проработке стандарта
  персистентности.

## Открытые вопросы

Открытых вопросов, требующих немедленного решения, по AnomalyReport
нет. Открытые политики (повторная обработка `ERROR`-отчётов,
частичный kill-switch) — внутри anomaly/safety-кластера, разбираются
при его миграции (ANOM-Q1, ANOM-Q2). Архивный frontmatter
`related_adrs: [ADR-0001]` — ссылка на архивный инфра-ADR, не
переносится (не доменное знание).
