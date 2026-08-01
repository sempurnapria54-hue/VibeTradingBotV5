# AnomalyReport lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит `AnomalyReport`, кто и при каких
событиях их меняет.

Структура модели — в `docs/models/domain/other/AnomalyReport.md`.

## Кто управляет

Управляющих — две группы, по тропам обработки (§Жизненный цикл).

**Тропа `CRITICAL` (с kill-switch):**

- **SafetyHoldCoordinator** — координатор **реактивной** тропы над сделкой
  (`docs/components/SafetyHoldCoordinator.md`): открывает отчёт
  (`CREATED` + before-слепок), ведёт `IN_PROGRESS` →
  `KILL_SWITCH_EXECUTED` → `COMPLETED`/`ERROR`, гейтит терминал
  подтверждением снятия риска и эскалирует инструмент → биржа. Построен на
  шаге 6; прежняя редакция этого раздела его не знала.
- **TradeRuleValidator** — обнаруживает нарушение торгового
  инварианта; по факту срабатывания запускается обработка через
  `AnomalyJob`.
- **AnomalyJob** — **проактивная** тропа (шаг 8): создаёт `AnomalyReport`
  при обнаружении (→ `CREATED`), оркестрирует обработку (`IN_PROGRESS` →
  `KILL_SWITCH_EXECUTED` → `COMPLETED`), переводит в `ERROR` при
  непредвиденной ошибке.
- **KillSwitchExecutor** — выполняет аварийное снятие риска по
  инструменту; успех → переход `IN_PROGRESS → KILL_SWITCH_EXECUTED`.
  Сам в `AnomalyReport` не пишет — состояние фиксирует координатор/джоба
  по результату executor.

**Тропа `NON_CRITICAL` (журнальная, без kill-switch)** — производители
шага 7. Каждый **создаёт отчёт уже завершённым по существу**: слепки
собираются при создании, обработки нет:

- `docs/components/FinalizeDealExitExecutor.md` — расхождение сверки
  bills ↔ net сверх epsilon; cross-ccy движение;
- `docs/components/MarkDealEmergencyClosedExecutor.md` — пометка
  «неисчислимо» (`resultProfit = null`) на аварийном терминале;
- `docs/components/InstrumentExternalRulesSyncJob.md` — несвежесть ставки
  или ключа комиссионной группы (мягкий холд).

> `TradeRuleValidator`, `AnomalyJob` —
> компоненты anomaly/safety-кластера, мигрируются отдельно
> (форвард-заметки — `.claude/work/history/2026-05-27-миграция-anomaly-report/tasks-anomaly-report.md`;
> backlog «Anomaly / safety / kill-switch»). Здесь — статусная
> механика, которой владеет сам `AnomalyReport`.

## Жизненный цикл

**Троп две; выбор — по `severity`** (H6, `GAPS_CLOSE_6`;
`docs/models/domain/other/AnomalyReport.md` §Енумы).

**Тропа `CRITICAL` — реактивная обработка** (линейная цепочка):

1. Создание (`CREATED`) с заполнением `internalBefore`,
   `externalBefore`.
2. Старт обработки (`IN_PROGRESS`).
3. Выполнение kill-switch (`KILL_SWITCH_EXECUTED`).
4. Сбор финальных снимков `internalAfter`, `externalAfter`.
5. Завершение (`COMPLETED`).

**Тропа `NON_CRITICAL` — журнальная, kill-switch не выполняется:**

1. Создание (`CREATED`) со слепками на момент обнаружения.
2. Завершение (`COMPLETED`) — сразу, если обработки нет (типовой случай
   производителей шага 7: факт зафиксирован, реагировать нечем), либо
   через `IN_PROGRESS`, если обработка есть, но kill-switch в неё не
   входит.

`KILL_SWITCH_EXECUTED` на этой тропе **не проходится** — статус
принадлежит только `CRITICAL`-тропе. Отсюда несущее следствие: путь к
`COMPLETED` **не единственный**, и матрица ниже это выражает; прежняя
редакция знала лишь путь через `KILL_SWITCH_EXECUTED`, из-за чего
журнальная аномалия не имела тропы к терминалу вовсе.

Ветка ошибки: на любом шаге обработки — `ERROR` с записью текста в
`message`; уже собранные snapshot-поля сохраняются.

## Переходы

| От | К | Тропа | Условие |
|---|---|---|---|
| (нет) | `CREATED` | обе | Обнаружено нарушение инварианта / orphan-сущность / журнальный факт; собраны `internalBefore`, `externalBefore`. |
| `CREATED` | `COMPLETED` | `NON_CRITICAL` | Обработки нет: факт зафиксирован слепками при создании (журнальная аномалия шага 7). |
| `CREATED` | `IN_PROGRESS` | обе | Стартует обработка (аварийный сценарий — на `CRITICAL`). |
| `IN_PROGRESS` | `KILL_SWITCH_EXECUTED` | `CRITICAL` | `KillSwitchExecutor` успешно отработал. |
| `IN_PROGRESS` | `COMPLETED` | `NON_CRITICAL` | Обработка без kill-switch завершена; собраны финальные снимки, если они есть. |
| `KILL_SWITCH_EXECUTED` | `COMPLETED` | `CRITICAL` | Собраны `internalAfter`, `externalAfter`; закрытие риска подтверждено (гейт терминала — `docs/components/SafetyHoldCoordinator.md`). |
| `IN_PROGRESS` | `ERROR` | обе | Непредвиденная ошибка до выполнения kill-switch. |
| `KILL_SWITCH_EXECUTED` | `ERROR` | `CRITICAL` | Непредвиденная ошибка после kill-switch (например, при сборе финальных снимков). |

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
| Создание (`CREATED`) | `exchangeId`, `instrumentId` (при наличии), `scope`, `severity`, `code`, `internalBefore`, `externalBefore`. |
| Старт обработки (`IN_PROGRESS`) | только `status`. |
| После kill-switch (`KILL_SWITCH_EXECUTED`) | только `status`. |
| Финальные снимки | `internalAfter`, `externalAfter`. |
| Завершение (`COMPLETED`) | только `status`. |
| Ошибка (`ERROR`) | `status`, `message`. |

**На `NON_CRITICAL`-тропе `internalAfter`/`externalAfter` остаются
пустыми**, если обработки не было: «после» отсутствует не по потере
данных, а потому что состояние не менялось. Слепки «до» при этом
обязательны — они и есть содержание журнальной аномалии.

Структура jsonb-полей — в §Персистентность модели.

## Обработка сбоев

Подъём незавершённых отчётов после рестарта — по статусу:

| Статус при подъёме | Действие |
|---|---|
| `CREATED` | `CRITICAL` — начать обработку (перевести в `IN_PROGRESS`, продолжить штатно); `NON_CRITICAL` — обработки нет, завершить (`COMPLETED`). |
| `IN_PROGRESS` | Продолжить аварийный сценарий с проверкой текущего состояния биржи. |
| `KILL_SWITCH_EXECUTED` | Собрать `internalAfter`, `externalAfter`, перевести в `COMPLETED`. |
| `COMPLETED` | Не обрабатывать повторно. |
| `ERROR` | Повторная обработка — только по отдельной политике (открытый вопрос; форвард-заметка). |

При ошибке обработки: `status → ERROR`; `message` = текст ошибки;
snapshot-поля сохраняются (частичные данные не теряются); ретрай в
рамках одного исполнения не выполняется — повторная обработка на
следующем запуске job через подъём незавершённого отчёта.
