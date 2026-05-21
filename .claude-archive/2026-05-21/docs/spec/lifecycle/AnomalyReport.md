---
status: current
last_review: 2026-05-15
related_adrs: [ADR-0001]
related_models: [AnomalyReport]
related_processes: []
---

# AnomalyReport — жизненный цикл

`AnomalyReport` создаётся при обнаружении нарушения системного
инварианта или orphan-сущности на бирже, проходит через обработку
аварийного сценария и завершается фиксацией финальных снимков
состояния. Структура модели — в
[AnomalyReport](../models/core/AnomalyReport.md).

## Жизненный цикл

Линейная цепочка успешного сценария:

1. Создание (`status = CREATED`) с заполнением `internalBefore` и
   `externalBefore`.
2. Старт обработки (`status = IN_PROGRESS`).
3. Выполнение kill-switch (`status = KILL_SWITCH_EXECUTED`).
4. Сбор финальных снимков `internalAfter` и `externalAfter`.
5. Завершение (`status = COMPLETED`).

Ветка ошибки: на любом шаге обработки — `status = ERROR` с записью
текста в `message`. Уже собранные snapshot-поля сохраняются.

## Статусы и переходы

| От | К | Условие |
|---|---|---|
| (нет) | CREATED | Обнаружено нарушение инварианта или orphan-сущность; собраны `internalBefore` и `externalBefore`. |
| CREATED | IN_PROGRESS | Стартует аварийный сценарий обработки. |
| IN_PROGRESS | KILL_SWITCH_EXECUTED | `KillSwitchExecutor` успешно отработал. |
| KILL_SWITCH_EXECUTED | COMPLETED | Собраны `internalAfter` и `externalAfter`; обработка завершена. |
| IN_PROGRESS | ERROR | Непредвиденная ошибка обработки до выполнения kill-switch. |
| KILL_SWITCH_EXECUTED | ERROR | Непредвиденная ошибка после kill-switch (например, при сборе финальных снимков). |

Отдельный retryable-статус для неуспешного kill-switch не вводится —
ретрай выполняется на следующем запуске job через подъём незавершённого
отчёта (см. раздел «Обработка сбоев»).

## Кто триггерит переходы

- **TradeRuleValidator** — обнаруживает нарушение торгового инварианта;
  по факту срабатывания запускается обработка через `AnomalyJob`.
- **AnomalyJob** (спецификация — TBD, см. backlog) — создаёт
  `AnomalyReport` при обнаружении (переход в `CREATED`), оркестрирует
  обработку (`IN_PROGRESS` → `KILL_SWITCH_EXECUTED` → `COMPLETED`),
  переводит в `ERROR` при непредвиденной ошибке.
- **KillSwitchExecutor** — выполняет аварийное снятие риска по
  инструменту; успех приводит к переходу
  `IN_PROGRESS → KILL_SWITCH_EXECUTED`. Сам в `AnomalyReport` не пишет
  — состояние фиксирует `AnomalyJob` по результату executor.

## Инварианты поведения

- **Отчёт переживает рестарт приложения.** Если приложение упало во
  время обработки аномалии, job находит незавершённый отчёт по `status`
  и продолжает обработку с нужного шага.
- При переходе в `ERROR` уже собранные snapshot-поля
  (`internalBefore`, `externalBefore`, при наличии — `internalAfter`,
  `externalAfter`) не теряются.
- `COMPLETED` — терминальный статус успешного сценария; повторно
  отчёт не обрабатывается.
- Если `KillSwitchExecutor` отработал с частичным успехом —
  `externalAfter` отражает фактическое состояние биржи, включая
  риск, который снять не удалось. Дальнейшая политика обработки
  таких случаев — открытый вопрос, см. backlog «KillSwitchExecutor
  — детальная спецификация».

## Какие поля заполняются на каких шагах

| Шаг | Заполняемые поля |
|---|---|
| Создание (`CREATED`) | `exchangeId`, `instrumentId` (при наличии), `severity`, `code`, `internalBefore`, `externalBefore`. |
| Старт обработки (`IN_PROGRESS`) | только `status`. |
| После kill-switch (`KILL_SWITCH_EXECUTED`) | только `status`. |
| Финальные снимки | `internalAfter`, `externalAfter`. |
| Завершение (`COMPLETED`) | только `status`. |
| Ошибка обработки (`ERROR`) | `status`, `message`. |

Структурное содержимое jsonb-полей — в разделе «Персистентность»
документа [AnomalyReport](../models/core/AnomalyReport.md).

## Обработка сбоев

**Поведение при подъёме незавершённых отчётов после рестарта**
определяется статусом:

| Статус при подъёме | Действие |
|---|---|
| CREATED | Можно начинать обработку — перевести в `IN_PROGRESS` и продолжить штатно. |
| IN_PROGRESS | Продолжить аварийный сценарий с проверкой текущего состояния биржи. |
| KILL_SWITCH_EXECUTED | Собрать `internalAfter` и `externalAfter`, перевести в `COMPLETED`. |
| COMPLETED | Не обрабатывать повторно. |
| ERROR | Повторная обработка — только по отдельной политике; политика — открытый вопрос, см. backlog «AnomalyJob — полная спецификация». |

**Поведение при ошибке обработки:**

- `status` переводится в `ERROR`.
- `message` заполняется текстом непредвиденной ошибки.
- Snapshot-поля сохраняются в текущем состоянии — частичные данные не
  теряются.
- Ретрай в рамках одного исполнения не выполняется; повторная обработка
  происходит на следующем запуске job через подъём незавершённого
  отчёта.
