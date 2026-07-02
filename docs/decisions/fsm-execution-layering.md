# Слоистость исполнения сделки: handler → действие → команда

## На какой вопрос отвечает этот файл

Как разложены слои исполнения сделки (петля → handler → оркестратор
действия → `StrategyActionExecutor` → `CommandExecutor`), почему
`StrategyActionExecutor` — per-pass эмиттер, а не синхронный, и почему
kill-switch стоит **вне** этих слоёв.

## Контекст

Handler'ы FSM накопили смешанную логику: в одном `handle()` — и проверки
входа, и работа, и решение о переходе; исполнение действий размазано по
`DealFsmSupport` + `DealActionPlanner` + `ServiceCommandFactory`. Разрез
kill-switch на прошлом заходе завёл слой `StrategyActionExecutor`, но
единственной его реализацией был сам kill-switch, а kill-switch как
**объявленное действие стратегии** (Scope A/B) породил класс проблем
(валидация подтипа↔actionType, роутинг, тихий залип — код-ревью заход 2).
Уточняем `action-orchestration-vs-command.md` (CMD-Q6) и фиксируем целевую
слоистость.

## Решение

### Слои

```
DealOrchestratorJob — петля по активным сделкам; на Deal.Status выбирает Handler.
Handler (на статус)  — «рулит ситуацией» по статусу; 3 метода (ниже).
  └─ Оркестратор действия — решает, какое объявленное StrategyAction применимо
       сейчас (условие сработало? risk ok?), и на этом проходе дёргает executor.
       └─ StrategyActionExecutor (на тип действия) — per-pass: следующая команда
            действия за проход (или «готово»), состояние в DealActionState.
            └─ CommandExecutor — 1 атомарная команда.

Kill-switch — СБОКУ: аварийный executor вне слоёв StrategyAction/Command.
```

Зависимости сверху вниз: handler знает про оркестратор действия и
исполнителей; `CommandExecutor` не знает про вызывающих (его могут дёргать
`StrategyActionExecutor`, `DealContextService`, kill-switch и др.).

### Handler — 3 метода

Каждый handler держит ровно три роли (точный интерфейс — за реализацией):

- **условия входа** — предусловия работы этого прохода (напр. `MANAGING`:
  позиция жива; свежесть рыночных данных). Не выполнены → переход/`stay`.
- **handle** — работа: через оркестратор действия исполняет применимые
  `StrategyAction`; может звать risk/price-калькуляторы и `CommandExecutor`'ы.
- **условия перехода** — по **transition-условиям стратегии** + подтверждённым
  фактам решает `nextStatus`. Сменил статус → на следующей итерации петля
  возьмёт другой handler.

Стратегия задаёт статусы-переходы и условия переходов; handler'ы ими
пользуются. Handler не хардкодит переходы, а читает их из стратегии.

### `StrategyActionExecutor` — per-pass эмиттер (не синхронный)

Исполнитель **одного типа действия** (`CREATE` ordinary/algo, …). За проход
смотрит стадию действия (`DealActionState`) и выдаёт **следующую** команду
(place → refresh-подтверждение по фактам → следующая) либо сигнал «готово».
Секвенс ведёт **петля по подтверждённым фактам** (не ACK) — сохраняем принцип
CMD-Q6. Это обобщение нынешних `DealActionPlanner` + `ServiceCommandFactory`,
разложенных по типам действий.

Многопроходность обязательна: ордер/algo — place-и-подтвердить за один
синхронный вызов нельзя (recovery — штатный, по фактам).

### Kill-switch — вне слоёв (аварийный executor)

Kill-switch (`KillSwitchExecutor`) — **не** `StrategyAction`, **не**
`CommandExecutor`, **не** `StrategyActionExecutor`. Синхронный fire-all
teardown + сверка реального состояния биржи; зовётся **программно**
(`SafetyHoldCoordinator`, будущий `AnomalyJob`), в стратегии не объявляется.

Почему вне: kill-switch — **аварийный выход**, а не плановое действие
стратегии. Объявлять его как действие с условием — смешивать
emergency-response со стратегической логикой; цена показана ревью (валидация
подтип↔actionType, роутинг по типу, тихий вечный залип при мис-объявлении).
И как аварийный тормоз он **не должен зависеть** от исправности петли —
поэтому синхронный self-contained, а не loop-driven (природа CMD-Q6-исключения
сохранена). Внутри держит инвариант «защиту снимать только после
подтверждённого закрытия позиции».

Стратегия может **триггерить** аварию как риск-политику (напр.
`MarketDataExpiredAction.KILL_SWITCH` на устаревание данных) — это вызов
executor'а по условию риска, а не объявление kill-switch как действия.

### Exit — условие-перехода, не действие

Выход сделки = стратегия объявляет переход `MANAGING → EXIT_PENDING` с
условием; handler переводит статус; teardown ведёт `ExitPendingHandler`.
Отдельного `DEAL_EXIT`-действия **не заводим** — выход это «market-close всё +
дочистка», а этим уже владеет `ExitPendingHandler`. Дедикейтед exit-действие
оправдано только при кастомном закрывающем ордере (тип/цена сверх
market-close) — пока не требуется.

Следствие: `CLOSE_FULL` (`StrategyPositionAction`) становится избыточным
(вырожденный подтип + пустая подтаблица) — снять отдельным заходом.

## Альтернативы (отвергнуты)

- **`StrategyActionExecutor` синхронный fire-all на каждое действие
  (вариант A).** Отвергнут: обычные действия (ордер/algo) многопроходны
  (place→refresh), синхронный вызов ломает факт-driven recovery и CMD-Q6.
  Fire-all оправдан только для аварийного kill-switch — а он вынесен из слоёв.
- **Kill-switch как объявленное `StrategyAction` (Scope A/B).** Отвергнут:
  смешение аварии со стратегией; породил валидацию/роутинг/залип; kill-switch —
  программный аварийный executor.
- **`DEAL_EXIT` как действие.** Отвергнут (пока): выход выражается
  условием-перехода на `EXIT_PENDING`; дедикейтед действие — только под кастомный
  выход.
- **Транзишены захардкожены в handler'ах.** Отвергнуто: переходы и их условия
  задаёт стратегия, handler ими пользуется (иначе стратегия не управляет FSM).

## Уточнение CMD-Q6

Принцип «loop-driven действие-оркестрация vs self-contained аварийный
teardown» (`action-orchestration-vs-command.md`) сохраняется, но выражается
через слои: действие-оркестрация = `StrategyActionExecutor` (per-pass, ведёт
петля); аварийный teardown = `KillSwitchExecutor` **сбоку** — не команда и не
действие. Тип `ServiceCommandType.EXECUTE_KILL_SWITCH` убран (kill-switch — не
команда); прежняя формулировка «`KILL_SWITCH` — команда» этим заменена.

## Связи

- Уточняет — `docs/decisions/action-orchestration-vs-command.md` (CMD-Q6).
- Handler'ы и петля — `docs/components/DealStateMachine.md`,
  `docs/components/DealOrchestratorJob.md`, `docs/components/ManagingHandler.md`.
- Аварийный kill-switch — `docs/components/KillSwitchExecutor.md`.
- Канон командного слоя — `docs/rules/command-lifecycle.md`,
  `docs/components/models/ServiceCommand.md`.
