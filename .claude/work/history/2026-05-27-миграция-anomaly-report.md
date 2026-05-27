# Миграция AnomalyReport в `docs/`

## На какой вопрос отвечает этот файл

Что мы сделали в задаче миграции AnomalyReport и где лежат детальные
артефакты.

## Итог

Завершена 2026-05-27. Мигрирован `AnomalyReport` из spec-формата
архива (`.claude-archive/2026-05-21/docs/spec/models/core/AnomalyReport.md`
+ `.../docs/spec/lifecycle/AnomalyReport.md`) в `docs/`. 16
фрагментов.

## Создано в `docs/`

- `docs/models/other/AnomalyReport.md` — модель (поля,
  `Status`/`Severity`, инварианты, связи, персистентность jsonb).
- `docs/lifecycles/AnomalyReport.md` — FSM (CREATED → IN_PROGRESS →
  KILL_SWITCH_EXECUTED → COMPLETED; ветка ERROR), переходы, обработка
  сбоев/рестарта.

## Решения

- **core vs other:** размещён в `docs/models/other/` (не `core`) по
  `models-core-vs-other.md` — аудит/инцидент, не торговая модель про
  бизнес-цикл сделки (архив держал его в `spec/models/core/`).
- Cross-cutting запаркован (`cross-cutting-parking.md`): компоненты
  `AnomalyJob`, `KillSwitchExecutor`, `TradeRuleValidator` —
  форвард-заметки.

## Детальные артефакты (архив)

В подпапке `2026-05-27-миграция-anomaly-report/`:
- `progress-anomaly-report.md` — пофрагментный отчёт (16 фрагментов).
- `tasks-anomaly-report.md` — форвард-заметки ANOM-Q1…Q5.

Указатели на форвард-заметки развёрнуты в `.claude/work/backlog.md`
(ANOM-Q1…Q3 — п.7 anomaly/safety; ANOM-Q4 — п.9 Exchange/Instrument;
ANOM-Q5 — отложенные, стандарт персистентности). По
`forward-notes-after-task-closure.md`.

## Не сделано намеренно

Архивные spec-файлы AnomalyReport в `.claude-archive/` не удалены.
