# Жизненный цикл сервисной команды

## На какой вопрос отвечает этот файл

Какое у нас правило жизненного цикла `ServiceCommand`: критерий
атомарности, `CREATE → SUBMIT → REFRESH`, «не persisted queue», одна
команда за проход.

## Правило

- **`ServiceCommand` — runtime object, не persisted entity и не durable
  queue.** Pending-команды в БД не хранятся. После рестарта pending
  `ServiceCommand` как очередь не восстанавливаются: FSM заново определяет
  нужную атомарную команду по актуальным `DealContext`, строкам исполнений
  (`DealActionState`), runtime graph и exchange facts.
- **Критерий атомарности — «одна ответственность за состояние».** Команда
  атомарна ⟺ выполняются все три клаузы
  (`docs/decisions/command-action-boundary.md` §1):
  1. **один владелец исхода** — ровно одна сущность, чьё состояние команда
     обязана привести к факту (остальное — чтение входа либо журнал:
     `AnomalyReport`, аудит);
  2. **повтор с нуля безопасен** — повторное исполнение приводит владельца
     к тому же состоянию;
  3. **нет внутреннего гейта на подтверждение собственной записи** —
     внутри команды нет ожидания, пока биржа подтвердит результат записи
     этого же исполнения; чтение чужих/прежних фактов (evidence-cycle,
     search по client id) клаузу не нарушает.
- **`CREATE_* → SUBMIT_* → REFRESH_*` — это lifecycle, а не пакет.** Для
  создаваемых на бирже сущностей: `CREATE_*` создаёт локальную сущность в
  БД (на биржу не ходит, сущность + строка исполнения одной транзакцией);
  `SUBMIT_*` отправляет или восстанавливает факт отправки по stable client
  id (`internalId → clOrdId`/`algoClOrdId`); `REFRESH_*` подтверждает
  фактическое состояние. FSM двигает статус сделки только после
  подтверждения фактов.
- **Одна актуальная команда за проход.** Всю цепочку заранее никто не
  создаёт: за один проход FSM/handler — одно актуальное исполнение и одна
  актуальная команда, выбранная по свежим фактам. Strategy-команды эмитит
  per-type `StrategyActionExecutor` под диспетчером
  `StrategyActionOrchestrator`; команды системных действий (добыча,
  финализация) — `SystemActionExecutor`
  (`docs/components/SystemActionExecutor.md`).
- **Составное — системное действие, не команда.** Последовательность
  команд (добыча фактов, финализация, аварийное завершение) — **системное
  действие** со строкой исполнения (попытки + идемпотентность), которое
  per-pass ведёт `SystemActionExecutor` по подтверждённым фактам;
  handler'ы добывающие `REFRESH_*` напрямую не эмитят
  (`docs/decisions/command-action-boundary.md` §2). Прочие составные
  процессы (graceful shutdown, protection switch, **REPLACE-ремодел** —
  амендных команд нет, `docs/decisions/replace-not-amend.md`, порядок ног
  по риск-классу, секвенс по фактам; safety-flow) раскладываются на
  атомарные команды и переходы FSM.
- **Атомарность не означает «один HTTP-запрос».** Refresh-команда может
  обойти несколько эндпоинтов биржи **внутри себя** (evidence-cycle
  live → pending → history → archive; у `REFRESH_POSITION_COMMAND` —
  live → positions-history) и сама вынести терминал
  (`MISSING_AFTER_REFRESH`), если исчерпанный цикл и есть его основание —
  клаузу 3 нарушает только ожидание подтверждения **собственной** записи.
  FSM секвенсит *команды*, не эндпоинты внутри refresh-команды (см.
  `docs/decisions/refresh-evidence-cycle-ownership.md`).
  - **«Команда внутри команды» не допускается — следствие клаузы 3.**
    Вложенная команда не имеет ни своего прохода FSM, ни канала возврата
    данных (`ServiceCommandExecutionResult` несёт только
    `success`/`errorCode`/`message`,
    `docs/components/ServiceCommandExecutor.md`). Если факту нужно доехать
    до потребителя — он едет **durable-носителем** (строка сущности), а
    добыча оформляется ногой цикла своей команды. Кейс, на котором граница
    проведена, — положение закрытия позиции (H1/H3 `GAPS_CLOSE_7`).
- **ACK не runtime truth** — факт подтверждается refresh/search/history
  (см. `docs/rules/ack-not-runtime-truth.md`).

## Первоисточник и смежное

Правило сквозное по командам (`.claude/decisions/rule-source-of-truth.md`).
Структура самого `ServiceCommand` (поля, `ServiceCommandType`) — в
`docs/components/models/ServiceCommand.md` (здесь не дублируется).
Эмиссия команд — `docs/components/StrategyActionOrchestrator.md` +
`docs/components/StrategyActionExecutor.md` (strategy-команды) и
`docs/components/SystemActionExecutor.md` (звенья системных действий);
исполнение — `docs/components/ServiceCommandExecutor.md`. Retry опасных команд —
`docs/components/RetryPolicyService.md`. Recovery по статусам —
`docs/lifecycles/Deal.md`, `docs/processes/deal-management.md`. Решение о
границе «команда ↔ действие» —
`docs/decisions/command-action-boundary.md`.
