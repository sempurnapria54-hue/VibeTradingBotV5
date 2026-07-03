# Действие-оркестрация vs аварийный self-contained teardown

## На какой вопрос отвечает этот файл

Чем составная операция-**действие** (оркеструется петлёй по фактам)
отличается от **аварийного self-contained teardown** (доводит свой teardown
сам, не завися от петли), и почему REPLACE — действие-оркестрация, а
kill-switch — аварийный side-executor вне реестра команд.

## Контекст

Две составные операции над атомарными командами выглядят похоже, но ведутся
по-разному:

- **REPLACE-ремодел** (`docs/decisions/replace-not-amend.md`) — место
  оркестрации порядка ног было открыто (**CMD-Q5**): фабрика или петля.
- **Kill-switch** — не команда (`ServiceCommandType`), а side-executor
  `KillSwitchExecutor` вне реестра команд, исполняемый реактивно через
  `SafetyHoldCoordinator`. Несёт внутренний многошаговый teardown (close →
  cancel orders → cancel algos → безусловный финальный close) над атомарными
  операциями, доводимый им самим (self-contained синхронный teardown, не
  ведомый петлёй); принцип границы был не сформулирован (**CMD-Q6**).

Оба парк на шаги 6-7; петля — теперь шаг 6.

## Решение

### Принцип различения

- **Действие-оркестрация** — многошаговая последовательность, **ведомая
  петлёй по подтверждённым фактам** (не ACK). Рутинная операция, на которую
  можно опереться на исправную петлю: каждый проход выбирает следующий шаг
  по фактам, recovery — штатный. Пример — **REPLACE**.
- **Аварийный self-contained teardown** — операция, которая **доводит свой
  teardown сама**, не завися от исправности петли. Пример — **kill-switch**
  (side-executor `KillSwitchExecutor` вне реестра команд, исполняемый
  реактивно через `SafetyHoldCoordinator`).

Критерий: *можно ли опереться на петлю?* Рутина (REPLACE) — да, секвенс
ведёт петля. Аварийный тормоз (kill-switch) — нет, он не должен зависеть
от того, жива ли петля.

### Владелец оркестрации порядка ног REPLACE — петля / `DealStateMachine`

Секвенс ног REPLACE по риск-классу (`docs/decisions/replace-not-amend.md`)
вычисляет **петля / `DealStateMachine`** по подтверждённым фактам, выбирая
следующую ногу. Per-pass эмиттер команды (per-type `StrategyActionExecutor`
под `StrategyActionOrchestrator`) остаётся «одна атомарная команда за проход»
и секвенс в себя **не** берёт (`docs/components/StrategyActionExecutor.md`,
`docs/components/DealStateMachine.md`).

Альтернатива — правило порядка ног **в per-pass эмиттере команды** —
отвергнута: без петли, реагирующей на факты, правило **мёртвое** (эмиттеру
некого спросить, подтвердилась ли предыдущая нога; `CANCEL`/`REPLACE` на
шаге 4 и не порождались — forward-debt, `.claude/work/backlog.md` §Хвост
шага 4).

### Kill-switch — аварийный side-executor (не команда, не петля)

Аварийный тормоз исполняется как **side-executor `KillSwitchExecutor` вне
реестра команд** (не `ServiceCommand`), реактивно через
`SafetyHoldCoordinator`, синхронным fire-all teardown'ом — он **не должен
зависеть** от того, жива ли петля. Внутри него жёсткое правило: **защиту
снимать последней и только после подтверждённого закрытия позиции** —
никогда не оголять живую позицию (`docs/components/KillSwitchExecutor.md`).
Именно эта природа (self-contained синхронный teardown, не ведомый петлёй)
и отделяет его от действия-оркестрации, секвенс которой ведёт петля.

## Альтернативы (отвергнуты)

- **Правило ног REPLACE в per-pass эмиттере команды
  (`StrategyActionExecutor`)** — без петли правило никем не вызывается
  (мёртвый код); факт-driven секвенс — природа оркестратора петли, не
  per-pass эмиттера.
- **Kill-switch как действие-оркестрация** (секвенс ведёт петля) —
  отвергнуто: аварийный teardown не должен зависеть от исправности петли;
  fire-all быстрее снимает риск.

## Закрытие вопросов

CMD-Q5 (владелец оркестрации REPLACE) и CMD-Q6 (принцип «действие-оркестрация
vs аварийный self-contained teardown» + классификация kill-switch) закрыты на
`GAPS_CLOSE_1` шага 6 (2026-06-22). Таксономия уточнена реактивным дизайном
холдов: kill-switch материализован как аварийный side-executor
`KillSwitchExecutor` вне реестра команд (не команда), триггерится реактивно
через `SafetyHoldCoordinator` (`docs/decisions/fsm-execution-layering.md`);
рациональ CMD-Q6-исключения (self-contained синхронный teardown, не завися от
петли) сохранён.

## Связи

- REPLACE-ремодел и порядок ног — `docs/decisions/replace-not-amend.md`.
- Владелец секвенса — `docs/components/DealStateMachine.md`; per-pass
  эмиссия команды — `docs/components/StrategyActionExecutor.md`,
  `docs/components/StrategyActionOrchestrator.md`.
- Аварийный side-executor тормоза — `docs/components/KillSwitchExecutor.md`.
- Канон командного слоя — `docs/rules/command-lifecycle.md`,
  `docs/components/models/ServiceCommand.md`.
