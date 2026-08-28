# DealActionState lifecycle

## На какой вопрос отвечает этот файл

Через какие статусы проходит исполнение действия (`DealActionState`, оба
вида — STRATEGY и SYSTEM), кто и при каких фактах их меняет.

Структура модели — в `docs/models/domain/other/DealActionState.md`.

## Кто управляет

Статус ведёт command-layer, не FSM напрямую:

- **STRATEGY-строки:** `StrategyActionOrchestrator` (диспетчер) —
  **читает** `status` (+ target) и через per-type
  `StrategyActionExecutor` выбирает одну актуальную команду за проход;
  статус не пишет, кроме re-arm `RETRY_PENDING` на стадию пере-эмиссии.
- **SYSTEM-строки:** `SystemActionExecutor` — **читает** `status` и
  подтверждённые факты звеньев, эмитит следующую команду системного
  действия за проход (`docs/components/SystemActionExecutor.md`); re-arm
  `RETRY_PENDING` — аналогично. **Плюс пишет `SKIPPED`** — см. ниже.
- **Executor'ы команд** — **пишут** `status` и target по результату
  исполнения и подтверждённым фактам
  (`docs/components/ServiceCommandExecutor.md`). Звено, пишущее в `Deal`,
  двигает исполнение **той же транзакцией** (клауза —
  `docs/rules/command-lifecycle.md`).
- **`RetryPolicyService`** — при падении звена переводит в
  `RETRY_PENDING` (инкремент `attemptCount`, `nextRetryAt`, `lastError`)
  либо в `FAILED` при исчерпании бюджета исполнения
  (`docs/components/RetryPolicyService.md`).

## Статусы

Значения — в `docs/models/domain/other/DealActionState.md`.
Live (не финальные): `PLANNED`, `CREATED`, `SUBMITTED`, `RETRY_PENDING`.
Финальные: `COMPLETED`, `FAILED`, `SKIPPED`. **`COMPLETED` жёстко
терминален** — строка-исполнение не переиспользуется; новая надобность в
том же действии — новое исполнение (частичные ключи держат только живые
строки).

## Матрица переходов (STRATEGY)

```text
(нет)          -> PLANNED
PLANNED        -> CREATED | SKIPPED
CREATED        -> SUBMITTED | RETRY_PENDING | FAILED | SKIPPED
SUBMITTED      -> COMPLETED | RETRY_PENDING | FAILED
RETRY_PENDING  -> PLANNED | CREATED | FAILED
COMPLETED | FAILED | SKIPPED -> (терминальные, переходов нет)
```

- `PLANNED → CREATED` — `CREATE_*` создал локальную сущность, target
  заполнен.
- `CREATED → SUBMITTED` — `SUBMIT_*` (для `CLOSE_POSITION_COMMAND` —
  отправка close).
- `SUBMITTED → COMPLETED` — факт подтверждён `REFRESH_*`-контуром (не по
  ACK, `docs/rules/ack-not-runtime-truth.md`).
- `RETRY_PENDING → PLANNED|CREATED` — re-arm к последнему
  durable-свидетельству (`target == null` → `PLANNED`, target есть →
  `CREATED`); факт уже подтверждён — executor сразу пишет `COMPLETED`.
  Опасные команды перед повтором делают refresh/search
  (`docs/components/RetryPolicyService.md`).
- `* → FAILED` — бюджет исчерпан либо non-retryable; сделка идёт в
  `ERROR`/safety-flow.
- `* → SKIPPED` — исполнение стало неактуальным до подтверждения.

## Матрица переходов (SYSTEM)

Системное действие сущностей не создаёт — стадий `CREATED`/`SUBMITTED`
у него нет; «где мы» выводится из подтверждённых фактов звеньев
(`docs/components/SystemActionExecutor.md`), статус строки несёт только
исполнение целиком:

```text
(нет)          -> PLANNED
PLANNED        -> COMPLETED | RETRY_PENDING | FAILED | SKIPPED
RETRY_PENDING  -> PLANNED | FAILED | SKIPPED
COMPLETED | FAILED | SKIPPED -> (терминальные, переходов нет)
```

- `(нет) → PLANNED` — handler затребовал действие; `SystemActionExecutor`
  материализует строку исполнения и эмитит первое звено по фактам.
- `PLANNED → COMPLETED` — все звенья подтверждены фактами; для
  завершающих действий — в одной транзакции с записью в `Deal`
  (`ENTRY_FINALIZED`; число+терминал — по звеньям, см. клаузу
  `command-action-boundary.md`).
- `* → RETRY_PENDING` — звено упало на retryable-ошибке; ждёт
  `nextRetryAt`; re-arm возвращает в `PLANNED` — следующее звено заново
  выводится из фактов (не из памяти прохода).
- `* → FAILED` — бюджет исполнения исчерпан (сквозной, β) либо
  non-retryable; сделка идёт ошибочной тропой; для добычи — плюс холд
  инструмента (`docs/rules/instrument-hold.md`).
- `* → SKIPPED` — действие стало неактуальным (например, добыча при уже
  терминализованной сделке; живое `FINALIZE_DEAL_EXIT_ACTION`, когда
  сделка ушла с выходной тропы по предикату неполноты числа).
  **Пишет `SystemActionExecutor`** — при выводе стадии **либо ревизией живых строк на проходе**.

## Recovery после рестарта

Pending `ServiceCommand` как очередь не восстанавливаются
(`docs/rules/command-lifecycle.md`). Command-layer поднимает живые строки
исполнений по `status` + target (+ вид/тип действия), добирает exchange
facts и выбирает нужную команду заново; стадия системного действия
выводится из durable-фактов звеньев, поэтому флага «рестарт с нуля» нет.
Audit/история источником не является
(`docs/rules/audit-not-runtime-source.md`).

## Писатель `SKIPPED` — `SystemActionExecutor`, двумя точками входа

**Актуальность исполнения выводится там же, где стадия**. `SystemActionExecutor` на каждом проходе читает строку
исполнения и подтверждённые факты звеньев, чтобы выбрать следующую
команду; если факты говорят, что действие стало неактуальным (сделка
ушла с тропы, на которой действие имело смысл), он закрывает строку
`SKIPPED` вместо эмиссии.

**Этого одного входа недостаточно, и вторая точка входа заведена**. Вывод стадии происходит внутри `next(type,
DealContext)`, то есть **по названному типу действия**, а на обоих
сценариях, ради которых писатель и назван, нужный тип уже никто не
называет:

| Сценарий | Почему `next(type, …)` не вызывается |
|---|---|
| добыча при терминализованной сделке | проход оркестратора отбирает **активные** `Deal` (`docs/components/DealOrchestratorJob.md`); терминализованная в выборку не попадает |
| живое `FINALIZE_DEAL_EXIT_ACTION` при уходе с выходной тропы | сделка идёт ошибочной тропой, и `ErrorHandler` называет только `REFRESH_DEAL_CONTEXT_ACTION` и `FINALIZE_DEAL_ERROR_ACTION` (`docs/components/ErrorHandler.md`) |

Поэтому у контракта есть **вторая точка входа — ревизия живых
SYSTEM-строк сделки на проходе**: `SystemActionExecutor` просматривает
**все** живые SYSTEM-исполнения из `DealContext.actionStates` (они уже
загружены — `docs/components/models/DealContext.md`) и закрывает
неактуальные. Контракт и предикат неактуальности —
`docs/components/SystemActionExecutor.md`.

- **Самоприменимость, которую находка вскрыла.** Правило «у каждого
  значения назван писатель» было применено к енуму, но не к
  **достижимости момента вызова** назначенного актора: писатель назван,
  а точка, в которой он получает управление, на обоих сценариях не
  наступает. Требование распространено:
  `docs/rules/writer-named-for-every-value.md`.

- **Почему это не отменяет «статус исполнения сам не пишет».** Исключение
  у оркестраторов **уже есть** — re-arm `RETRY_PENDING`; правка
  расширяет существующую оговорку, а не выдаёт новое право новому слою.
- **Цена «не закрывать вовсе» названа и отвергнута:** живая строка на
  терминализованной сделке поднимается по статусу, а частичный
  ключ по живым статусам держит слот действия занятым.

**Пробел был системным, а не единичным** — принцип «у каждого
объявленного значения и каждого перехода назван писатель» записан
классом в `docs/rules/writer-named-for-every-value.md`.

## Связи

- Жизненный цикл команды — `docs/rules/command-lifecycle.md`.
- Per-pass исполнитель системных действий —
  `docs/components/SystemActionExecutor.md`.
- Retry-механика — `docs/components/RetryPolicyService.md`.
- Классификация runtime-ошибок —
  `docs/rules/runtime-error-classification.md`.
- Решение о границе «команда ↔ действие» —
  `docs/rules/command-lifecycle.md`.
