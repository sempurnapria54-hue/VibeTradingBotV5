# Прогресс: миграция AnomalyReport

## На какой вопрос отвечает этот файл

На каком шаге миграция архивной сущности AnomalyReport и как
классифицирован каждый фрагмент.

## Статус

**Завершено.** Источник:
`.claude-archive/2026-05-21/docs/spec/models/core/AnomalyReport.md` +
`.../docs/spec/lifecycle/AnomalyReport.md`.

Конвертация из spec-формата (frontmatter) в наш формат («На какой
вопрос отвечает», без spec-frontmatter). Сущность со статусной FSM →
модель + lifecycle. Размещение: `docs/models/other/` (не `core`) —
см. ниже Ф1.

## Созданные / изменённые файлы

- `docs/models/other/AnomalyReport.md` — модель (создан).
- `docs/lifecycles/AnomalyReport.md` — lifecycle (создан).
- `.claude/work/questions/tasks/anomaly-report.md` — форвард-заметки
  (создан).
- `.claude/work/backlog.md` — отметка в п.7 (anomaly/safety):
  модель+lifecycle мигрированы (изменён).

OKX-доков нет (не биржевая модель API). Новых сквозных правил нет.

## Отчёт по фрагментам

Область у всех — **продукт**.

| # | Фрагмент | Тип | Размещение / диспозиция |
|---|---|---|---|
| Ф1 | Назначение: журналируемый объект расследования/аудита аномалий (не валидатор, не команда) | модель (other) | `AnomalyReport.md` §Назначение. **core vs other:** по `models-core-vs-other.md` — аудит/инцидент → `other` (не торговая модель про бизнес-цикл сделки), несмотря на `spec/models/core/` в архиве |
| Ф2 | Поля (id, internalId, exchangeId, instrumentId, status, severity, code, message, 4× jsonb-снимка) + Auditable | модель | `AnomalyReport.md` §Структура |
| Ф3 | `Status` (CREATED/IN_PROGRESS/KILL_SWITCH_EXECUTED/COMPLETED/ERROR) | модель + lifecycle | Перечень → `AnomalyReport.md` §Енумы; механика → lifecycle |
| Ф4 | `Severity` (CRITICAL/NON_CRITICAL) + эффект на торговлю | модель | Перечень+смысл → `AnomalyReport.md` §Енумы; enforcement (статус инструмента) → ANOM-Q4 |
| Ф5 | Инварианты структуры (exchangeId req, instrumentId nullable, severity/code независимы, message только с ERROR) | модель | `AnomalyReport.md` §Инварианты структуры |
| Ф6 | Связи (Exchange, Instrument) | модель | `AnomalyReport.md` §Связи |
| Ф7 | Персистентность jsonb-снимков (состав локальных/внешних) | модель | `AnomalyReport.md` §Персистентность; сериализация → ANOM-Q5 |
| Ф8 | Не включаемые поля (dealId, tradingBlocked, boolean-флаги) | модель | Позитив → `AnomalyReport.md` §Чего не хранит; отрицания свёрнуты по `negative-statements-not-fixated.md` |
| Ф9 | Жизненный цикл (линейная цепочка + ветка ошибки) | lifecycle | `AnomalyReport lifecycle` §Жизненный цикл |
| Ф10 | Таблица переходов | lifecycle | `AnomalyReport lifecycle` §Переходы |
| Ф11 | Кто триггерит (TradeRuleValidator/AnomalyJob/KillSwitchExecutor) | компоненты | Ссылки в lifecycle §Кто управляет; компоненты → ANOM-Q1/Q2/Q3 |
| Ф12 | Инварианты поведения (рестарт, snapshots на ERROR, COMPLETED терминал, частичный kill-switch) | lifecycle | `AnomalyReport lifecycle` §Инварианты поведения; частичный kill-switch policy → ANOM-Q2 |
| Ф13 | Какие поля на каких шагах | lifecycle | `AnomalyReport lifecycle` §Какие поля |
| Ф14 | Обработка сбоев (подъём по статусу, error handling) | lifecycle | `AnomalyReport lifecycle` §Обработка сбоев; ERROR-policy → ANOM-Q1 |
| Ф15 | MODELS.md (реестр-агрегатор) | — | Не воспроизводится (`master-index-not-fixated.md`); у нас нет docs/MODELS.md |
| Ф16 | frontmatter related_adrs [ADR-0001] | — | Отброшено: ссылка на архивный инфра-ADR, не доменное знание |

## Итог по AnomalyReport

- Размещено в `docs/`: 2 файла (модель в `other`, lifecycle).
  OKX-доков и новых сквозных правил нет.
- Свёрнуты к позитиву отрицания (Ф8) по
  `negative-statements-not-fixated.md`; агрегатор MODELS.md и
  архивный ADR-frontmatter отброшены.
- В форвард-заметки: ANOM-Q1…Q5 (AnomalyJob, KillSwitchExecutor,
  TradeRuleValidator — anomaly/safety-кластер; Severity-enforcement —
  Instrument; сериализация jsonb — стандарт персистентности).
- Backlog п.7 (anomaly/safety) дополнен отметкой о миграции
  модели+lifecycle.
- Продуктовых открытых вопросов, блокирующих миграцию, нет.
