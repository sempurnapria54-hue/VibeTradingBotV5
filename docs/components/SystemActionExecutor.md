# SystemActionExecutor

## На какой вопрос отвечает этот файл

Кто выдаёт следующую команду системного действия за проход (компонент):
контракт, вывод стадии из фактов, подтверждённый факт звена по каждому
действию.

## Назначение

`SystemActionExecutor` — per-pass исполнитель **системных действий**
(вид SYSTEM, `docs/models/domain/other/DealActionState.md`): добыча
фактов и финализация. За проход читает живую строку исполнения (или
материализует её), выводит стадию **из подтверждённых durable-фактов
звеньев** и выдаёт **следующую** команду (либо пусто: ждём backoff /
действие завершено). Обобщает и замещает прежний
`DealFinalizationCommandFactory` (упразднён вместе с
`DealFinalizationState`); паритет со `StrategyActionOrchestrator` +
per-type `StrategyActionExecutor` для STRATEGY-вида
(`docs/decisions/fsm-execution-layering.md`). Зовётся handler'ами через
`DealFsmSupport`; **handler'ы добывающие `REFRESH_*`-команды напрямую не
эмитят** — только через действие (иначе исполнение остаётся без анкера
попыток, узел 3 `DOCS_CHECK_8`).

## Контракт

```java
Optional<ServiceCommand> next(SystemActionType type, DealContext dealContext);
```

- Живой строки исполнения нет → материализует (`PLANNED`) под частичным
  ключом (`deal_id`, `system_action_type`) — второе живое исполнение того
  же действия на сделке не заводится.
- `RETRY_PENDING` → команда только по наступлении `nextRetryAt` (иначе
  пусто); re-arm — в `PLANNED`, следующее звено заново выводится из
  фактов.
- `FAILED`/`COMPLETED` строки — терминальны; новая надобность — новое
  исполнение (новая строка).
- Статус исполнения сам не пишет (паритет с оркестратором STRATEGY-вида)
  — его двигают executor'ы команд и retry-учёт.

## Вывод стадии: подтверждённый факт звена

Общее требование: факт **durable** (читается из БД), не память прохода.
Звено, пишущее в `Deal`, двигает исполнение той же транзакцией — иначе
вывод из фактов ломается на рестарте
(`docs/decisions/command-action-boundary.md` §5).

### `REFRESH_DEAL_CONTEXT_ACTION`

Звенья — добывающие `REFRESH_*`-команды по известным сущностям сделки
(`REFRESH_POSITION_COMMAND`, `REFRESH_ORDER_COMMAND`,
`REFRESH_ALGO_ORDER_COMMAND`, `REFRESH_BALANCE_COMMAND`,
`REFRESH_BILLS_COMMAND`; состав конкретного цикла задаёт handler-контекст).
Подтверждённый факт звена:

- для `REFRESH_BILLS_COMMAND` — **терминальный исход пагинации** (цикл
  дошёл до конца окна), не «строки появились»: их может не быть законно;
- для прочих `REFRESH_*` — **метка подтверждения чтения** на
  строке-владельце факта. ⚠ **Механика метки гейтится узлом 7
  `DOCS_CHECK_8`** (измеритель свежести): refresh не меняет данных, если
  факт не изменился, поэтому «звено сделано» по значению невыразимо;
  до решения узла 7 носитель метки не финализируется
  (`docs/decisions/command-action-boundary.md` §Отложено).

Исчерпание бюджета исполнения → `FAILED` → сделка ошибочной тропой +
**холд инструмента** (управление-сайд серия,
`docs/rules/instrument-hold.md` §«Серия неудач»; довод отладки и условие
пересмотра — там же).

### `FINALIZE_DEAL_ENTRY_ACTION`

Одно звено — `FINALIZE_DEAL_ENTRY_COMMAND`. Эмитится, когда handler
(`EntrySubmittedHandler`) подтвердил готовность фактов входа. Факт
завершения = `Deal.status = ENTRY_FINALIZED` — ребро пишет **само звено в
одной транзакции** со своим завершением (В4.1; второй экземпляр паттерна
N7). Идемпотентность: сделка уже в `ENTRY_FINALIZED` → действие не
заводится.

### `FINALIZE_DEAL_EXIT_ACTION`

Звенья — `FINALIZE_DEAL_EXIT_COMMAND` → `MARK_DEAL_CLOSED_COMMAND`.
Подтверждённые факты: `Deal.resultProfit` непуст (звено 1 сделано; пишется
транзакционно — N7); `Deal.status = CLOSED` (звено 2 сделано, действие
завершено). Предикат эмиссии звена 1 — **терминальный исход добычи**
(`REFRESH_DEAL_CONTEXT_ACTION` довёл цикл), не «данные выглядят готовыми».
Без числа действие **не завершается** (узел 4, вариант (а)): исчерпание
бюджета уводит сделку ошибочной тропой + холд.

### `FINALIZE_DEAL_ERROR_ACTION`

Два исполнения одного действия: (1) звено `MARK_DEAL_ERROR_COMMAND` —
факт = `Deal.status = ERROR`; (2) позже, после teardown и best-effort
добычи, звено `MARK_DEAL_EMERGENCY_CLOSED_COMMAND` — факт =
`Deal.status = EMERGENCY_CLOSED`. На аварийной тропе жёсткий отказ чтения
приравнивается к «недоступно» — терминал ставится
(`docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп»).

## Что не входит

- **Cleanup** (`CANCEL_*`/`CLOSE_POSITION_COMMAND` как дочистка) —
  эмитится handler'ами напрямую, без анкера: его серия неудач считается
  на инструмент-scope, второй счётчик — двойной учёт.
- **Kill-switch** — не действие и не команда: аварийный side-executor,
  наблюдается `AnomalyReport`
  (`docs/components/KillSwitchExecutor.md`,
  `docs/decisions/command-action-boundary.md` §2).
- **Служебная сборка `DealContext`** перед проходом FSM
  (`docs/components/DealContextService.md`) — не действие: читает уже
  приземлённое, между проходами переживать нечего.

## Связи

- Носитель исполнений — `docs/models/domain/other/DealActionState.md` +
  `docs/lifecycles/DealActionState.md`.
- Executor'ы звеньев — `docs/components/FinalizeDealEntryExecutor.md`,
  `docs/components/FinalizeDealExitExecutor.md`,
  `docs/components/MarkDealClosedExecutor.md`,
  `docs/components/MarkDealErrorExecutor.md`,
  `docs/components/MarkDealEmergencyClosedExecutor.md`, refresh-executor'ы.
- Слоение — `docs/decisions/fsm-execution-layering.md`; решение —
  `docs/decisions/command-action-boundary.md`.
- Retry — `docs/components/RetryPolicyService.md`.
