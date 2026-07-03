# KillSwitchService

## На какой вопрос отвечает этот файл

Кто триггерит аварийный kill-switch для реактивной реакции холда
(компонент-триггер): scope по уровню, каскад L4, агрегация подтверждения,
границы.

## Назначение

`KillSwitchService` — тонкий триггер `KillSwitchExecutor` для реактивной
реакции холда. Зовётся `SafetyHoldCoordinator`'ом на шаге kill-switch
(см. `docs/components/SafetyHoldCoordinator.md`); сам teardown и сверку
реального состояния делает `KillSwitchExecutor`
(`killSwitchExecutor.execute(dealContext).getSuccess()`, см.
`docs/components/KillSwitchExecutor.md`). Это тот «тонкий триггер»,
который подключает hold-подсистема (раньше — орфан
`DealFsmSupport.killSwitchCommand()`, удалён на сверке `CODE`).

## Scope по уровню

- **L3 `fireInstrument(dealContext)`** — kill-switch по графу **триггерной
  сделки** (её runtime graph + instId). Возвращает подтверждение закрытия
  риска (отчёт kill-switch): `true` — закрытие подтверждено, гейтит
  терминал `AnomalyReport`.
- **L4 `fireExchange(exchangeId)`** — **каскадный sweep** по всем активным
  сделкам биржи (`DealDataService.findActiveByExchangeId`), по вызову
  `KillSwitchExecutor` на каждую сделку (контекст строится
  `DealContextService.build`). **Per-deal best-effort:** сбой/исключение по
  одной сделке не срывает каскад — логируется, помечает результат
  неподтверждённым, обход продолжается. Возвращает `true` **только если
  закрытие подтверждено по КАЖДОЙ сделке** каскада; иначе `false` (у
  координатора → отчёт остаётся открытым).

## Границы

Не решает «как технически» снять риск и не ретраит teardown (это
`KillSwitchExecutor`, bounded внутри него). Не выставляет `TRADE_BLOCKED`,
не ведёт журнал, не эскалирует L3→биржа — это оркестрация
`SafetyHoldCoordinator`. Возвращает наверх только `Boolean`-подтверждение,
которым координатор гейтит терминал отчёта.

## Связи

- `docs/components/SafetyHoldCoordinator.md` — координатор, вызывающий
  триггер и трактующий подтверждение.
- `docs/components/KillSwitchExecutor.md` — исполнитель teardown и сверки.
- `docs/components/models/HoldSignal.md` — сигнал холда (scope L3/L4).
- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md` — уровни
  L3/L4.
