# AnomalyReport lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `AnomalyReport`, кто и при каких
событиях их меняет.

Структура модели — в `docs/models/domain/other/AnomalyReport.md`.

## Кто управляет

- **TradeRuleValidator** — обнаруживает нарушение торгового
  инварианта; по факту срабатывания запускается обработка через
  `AnomalyJob`.
- **AnomalyJob** — создаёт `AnomalyReport` при обнаружении (→
  `CREATED`), оркестрирует обработку (`IN_PROGRESS` →
  `KILL_SWITCH_EXECUTED` → `COMPLETED`), переводит в `ERROR` при
  непредвиденной ошибке.
- **KillSwitchExecutor** — выполняет аварийное снятие риска по
  инструменту; успех → переход `IN_PROGRESS → KILL_SWITCH_EXECUTED`.
  Сам в `AnomalyReport` не пишет — состояние фиксирует `AnomalyJob`
  по результату executor.

> `TradeRuleValidator`, `AnomalyJob`, `KillSwitchExecutor` —
> компоненты anomaly/safety-кластера, мигрируются отдельно
> (форвард-заметки — `.claude/work/history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`;
> backlog «Anomaly / safety / kill-switch»). Здесь — статусная
> механика, которой владеет сам `AnomalyReport`.

## Жизненный цикл

Успешный сценарий (линейная цепочка):

1. Создание (`CREATED`) с заполнением `internalBefore`,
   `externalBefore`.
2. Старт обработки (`IN_PROGRESS`).
3. Выполнение kill-switch (`KILL_SWITCH_EXECUTED`).
4. Сбор финальных снимков `internalAfter`, `externalAfter`.
5. Завершение (`COMPLETED`).

Ветка ошибки: на любом шаге обработки — `ERROR` с записью текста в
`message`; уже собранные snapshot-поля сохраняются.

## Переходы

| От | К | Условие |
|---|---|---|
| (нет) | `CREATED` | Обнаружено нарушение инварианта или orphan-сущность; собраны `internalBefore`, `externalBefore`. |
| `CREATED` | `IN_PROGRESS` | Стартует аварийный сценарий обработки. |
| `IN_PROGRESS` | `KILL_SWITCH_EXECUTED` | `KillSwitchExecutor` успешно отработал. |
| `KILL_SWITCH_EXECUTED` | `COMPLETED` | Собраны `internalAfter`, `externalAfter`; обработка завершена. |
| `IN_PROGRESS` | `ERROR` | Непредвиденная ошибка до выполнения kill-switch. |
| `KILL_SWITCH_EXECUTED` | `ERROR` | Непредвиденная ошибка после kill-switch (например, при сборе финальных снимков). |

Отдельный retryable-статус для неуспешного kill-switch не вводится —
ретрай выполняется на следующем запуске job через подъём
незавершённого отчёта (см. «Обработка сбоев»).

## Инварианты поведения

- **Отчёт переживает рестарт приложения.** При падении во время
  обработки job находит незавершённый отчёт по `status` и продолжает
  с нужного шага.
- При переходе в `ERROR` уже собранные snapshot-поля
  (`internalBefore`, `externalBefore`, при наличии — `internalAfter`,
  `externalAfter`) не теряются.
- `COMPLETED` — терминальный статус успешного сценария; повторно
  отчёт не обрабатывается.
- Частичный успех `KillSwitchExecutor`: `externalAfter` отражает
  фактическое состояние биржи, включая риск, который снять не
  удалось. Дальнейшая политика — открытый вопрос (форвард-заметка;
  backlog «KillSwitchExecutor — детальная спецификация»).

## Какие поля заполняются на каких шагах

| Шаг | Заполняемые поля |
|---|---|
| Создание (`CREATED`) | `exchangeId`, `instrumentId` (при наличии), `severity`, `code`, `internalBefore`, `externalBefore`. |
| Старт обработки (`IN_PROGRESS`) | только `status`. |
| После kill-switch (`KILL_SWITCH_EXECUTED`) | только `status`. |
| Финальные снимки | `internalAfter`, `externalAfter`. |
| Завершение (`COMPLETED`) | только `status`. |
| Ошибка (`ERROR`) | `status`, `message`. |

Структура jsonb-полей — в §Персистентность модели.

## Обработка сбоев

Подъём незавершённых отчётов после рестарта — по статусу:

| Статус при подъёме | Действие |
|---|---|
| `CREATED` | Начать обработку — перевести в `IN_PROGRESS`, продолжить штатно. |
| `IN_PROGRESS` | Продолжить аварийный сценарий с проверкой текущего состояния биржи. |
| `KILL_SWITCH_EXECUTED` | Собрать `internalAfter`, `externalAfter`, перевести в `COMPLETED`. |
| `COMPLETED` | Не обрабатывать повторно. |
| `ERROR` | Повторная обработка — только по отдельной политике (открытый вопрос; форвард-заметка). |

При ошибке обработки: `status → ERROR`; `message` = текст ошибки;
snapshot-поля сохраняются (частичные данные не теряются); ретрай в
рамках одного исполнения не выполняется — повторная обработка на
следующем запуске job через подъём незавершённого отчёта.
