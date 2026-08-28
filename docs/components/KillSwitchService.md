# KillSwitchService

## На какой вопрос отвечает этот файл

Кто триггерит аварийный kill-switch для реактивной реакции холда
(компонент-триггер): scope-исполнители, каскад биржи, агрегация
подтверждения, границы.

## Назначение

`KillSwitchService` — тонкий триггер `KillSwitchExecutor` для реактивной
реакции холда. Kill-switch — состав **полных** реакций: L3-полной
инструмента (`fireInstrument`) и ступени 2 биржевой лестницы
(`fireExchange()` → `Exchange.TRADE_BLOCKED`). У биржевой ступени 1
(`Exchange.HOLD` — ручной гейт входов) kill-switch-шага нет — на ней
сервис не зовётся (`docs/rules/exchange-hold.md`). Зовётся
`SafetyHoldCoordinator`'ом на шаге kill-switch
(см. `docs/components/SafetyHoldCoordinator.md`); сам teardown и сверку
реального состояния делает `KillSwitchExecutor`
(`killSwitchExecutor.execute(dealContext).getSuccess`, см.
`docs/components/KillSwitchExecutor.md`). Это тот «тонкий триггер»,
который подключает hold-подсистема (раньше — орфан
`DealFsmSupport.killSwitchCommand`, удалён на сверке `CODE`).

## Scope-исполнители

Ярлыки уровня со scope-API сняты: scope описывает
радиус, уровень серьёзности — отдельная ось
(`docs/rules/error-handling-policy.md`).

- **Инструмент-scope `fireInstrument(dealContext)`** — kill-switch по графу
  **триггерной сделки** (её runtime graph + instId). Возвращает
  подтверждение закрытия риска (отчёт kill-switch): `true` — закрытие
  подтверждено, гейтит терминал `AnomalyReport`.
- **Биржа-scope `fireExchange(exchangeId)`** — **каскадный sweep** по всем
  активным сделкам биржи (`DealDataService.findActiveByExchangeId`), по
  вызову `KillSwitchExecutor` на каждую сделку (контекст строится
  `DealContextService.build`). **Per-deal best-effort:** сбой/исключение по
  одной сделке не срывает каскад — логируется, помечает результат
  неподтверждённым, обход продолжается. Возвращает `true` **только если
  закрытие подтверждено по КАЖДОЙ сделке** каскада; иначе `false` (у
  координатора → отчёт остаётся открытым).

## Границы

Не решает «как технически» снять риск и не ретраит teardown (это
`KillSwitchExecutor`, bounded внутри него). Не выставляет `TRADE_BLOCKED`,
не ведёт журнал, не эскалирует инструмент→биржа — это оркестрация
`SafetyHoldCoordinator`. Возвращает наверх только `Boolean`-подтверждение,
которым координатор гейтит терминал отчёта.

## Связи

- `docs/components/SafetyHoldCoordinator.md` — координатор, вызывающий
  триггер и трактующий подтверждение.
- `docs/components/KillSwitchExecutor.md` — исполнитель teardown и сверки.
- `docs/components/models/HoldSignal.md` — сигнал холда (scope).
- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md` — правила
  холдов по scope.
