# Жизненный цикл сервисной команды

## На какой вопрос отвечает этот файл

Какое у нас правило жизненного цикла `ServiceCommand`: `CREATE → SUBMIT
→ REFRESH`, «не persisted queue», одна команда за проход.

## Правило

- **`ServiceCommand` — runtime object, не persisted entity и не durable
  queue.** Pending-команды в БД не хранятся. После рестарта pending
  `ServiceCommand` как очередь не восстанавливаются: FSM заново определяет
  нужную атомарную команду по актуальным `DealContext`, `DealActionState`,
  runtime graph и exchange facts.
- **`CREATE_* → SUBMIT_* → REFRESH_*` — это lifecycle, а не пакет.** Для
  создаваемых на бирже сущностей: `CREATE_*` создаёт локальную сущность в
  БД (на биржу не ходит, сущность + `DealActionState` одной транзакцией);
  `SUBMIT_*` отправляет или восстанавливает факт отправки по stable client
  id (`internalId → clOrdId`/`algoClOrdId`); `REFRESH_*` подтверждает
  фактическое состояние. FSM двигает статус сделки только после
  подтверждения фактов.
- **Одна актуальная команда за проход.** Всю цепочку заранее никто не
  создаёт: за один проход FSM/handler — один актуальный action-state и одна
  актуальная команда, выбранная по свежим фактам. Action-команды эмитит
  per-type `StrategyActionExecutor` под диспетчером
  `StrategyActionOrchestrator`; финализационные —
  `DealFinalizationCommandFactory`.
- **Команды атомарны — на уровне команды, не HTTP-запроса.** Одна команда
  — одна простая операция; составные процессы (graceful shutdown,
  protection switch, **REPLACE-ремодел** (амендных команд нет —
  `docs/decisions/replace-not-amend.md`; порядок ног по риск-классу,
  секвенс по фактам), safety-flow) раскладываются на атомарные команды и
  переходы FSM. Атомарность **не** означает «один HTTP-запрос»:
  refresh-команда может обойти несколько эндпоинтов биржи **внутри себя**
  (evidence-cycle live → pending → history → archive; у `REFRESH_POSITION`
  — live → positions-history) и сама вынести терминал
  (`MISSING_AFTER_REFRESH`), если исчерпанный цикл и есть его основание.
  FSM секвенсит *команды*, не эндпоинты внутри refresh-команды (см.
  `docs/decisions/refresh-evidence-cycle-ownership.md`).
  - **Послабление — про эндпоинты, не про команды.** «Команда внутри
    команды» каноном **не допускается**: вложенная команда не имеет ни
    своего прохода FSM, ни канала возврата данных
    (`ServiceCommandExecutionResult` несёт только `success`/`errorCode`/
    `message`, `docs/components/ServiceCommandExecutor.md`). Если факту
    нужно доехать до потребителя — он едет **durable-носителем** (строка
    сущности), а добыча оформляется ногой цикла своей команды. Кейс, на
    котором граница проведена, — положение закрытия позиции (H1/H3
    `GAPS_CLOSE_7`).
- **ACK не runtime truth** — факт подтверждается refresh/search/history
  (см. `docs/rules/ack-not-runtime-truth.md`).

## Первоисточник и смежное

Правило сквозное по командам (`.claude/decisions/rule-source-of-truth.md`).
Структура самого `ServiceCommand` (поля, `ServiceCommandType`) — в
`docs/components/models/ServiceCommand.md` (здесь не дублируется).
Эмиссия команд — `docs/components/StrategyActionOrchestrator.md` +
`docs/components/StrategyActionExecutor.md` (action-команды) и
`docs/components/DealFinalizationCommandFactory.md` (финализация);
исполнение — `docs/components/ServiceCommandExecutor.md`. Retry опасных команд —
`docs/components/RetryPolicyService.md`. Recovery по статусам —
`docs/lifecycles/Deal.md`, `docs/processes/deal-management.md`.
