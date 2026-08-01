# SafetyHoldCoordinator

## На какой вопрос отвечает этот файл

Кто координирует реактивную реакцию CRITICAL-холда над сделкой
(компонент-координатор): последовательность шагов, исполнители, гейт
терминала, эскалация, exception- и best-effort-политика, границы.

## Назначение

`SafetyHoldCoordinator` — координатор реактивной реакции CRITICAL-холда
над сделкой. Держатель **решения о последовательности**; сам ничего не
исполняет напрямую, оркеструет исполнителей:

- `InstrumentDataService` / `ExchangeDataService` — выставление
  `TRADE_BLOCKED` scope (`blockTrade`);
- `AnomalyReportService` — журнал инцидента и слепки (см.
  `docs/models/domain/other/AnomalyReport.md`);
- `KillSwitchService` — аварийное снятие риска (см.
  `docs/components/KillSwitchExecutor.md`).

Вызывается в проходе `DealOrchestratorJob` по `DealTransition.holdSignal`
(handler увёл свою сделку в ERROR и приложил `HoldSignal` — см.
`docs/components/models/HoldSignal.md`), под concurrency-гардом прохода
**D-M1** (в фазе 1 — in-process `JobExecutionGuard`, см.
`docs/components/DealOrchestratorJob.md`). Точка входа —
`react(signal, dealContext)`, идемпотентная по статусу scope.

## Последовательность реакции

Инструмент-scope и биржа-scope — **одной формы**, различаются только
scope-исполнителями (`InstrumentDataService`/`fireInstrument` vs
`ExchangeDataService`/`fireExchange`); ярлыки уровня со scope сняты (H6,
`GAPS_CLOSE_5`; уровень — ось error-политики). Дизайн холдов шага 6:

1. **`TRADE_BLOCKED` scope первым** (`blockTrade`) — gate и анкер
   идемпотентности. Повторный сигнал того же scope, когда scope **уже в
   `TRADE_BLOCKED`**, → реакция **пропускается** (ранний `return`,
   kill-switch не гоняется повторно).
   - **Анкер — `TRADE_BLOCKED`, а не «scope не в `ACTIVE`»** (H3,
     `GAPS_CLOSE_6`). С появлением мягкого класса холда
     (`Instrument.Status.ENTRY_BLOCKED`, `docs/rules/instrument-hold.md`
     §Enforcement) буквальное «ставится только из `ACTIVE`» маскировало бы
     аварию: инструмент под **мягким** холдом не в `ACTIVE`, kill-switch по
     нему не гонялся — и последующий риск-триггер уровня 3 был бы молча
     пропущен. Переход `ENTRY_BLOCKED → TRADE_BLOCKED` **разрешён** и
     реакцию не пропускает (эскалация мягкого класса в полный); обратной
     эскалации нет.
2. `AnomalyReport` `CREATED` + **before-слепок** (локальный БД-граф +
   внешний биржевой).
3. `IN_PROGRESS`.
4. **kill-switch(scope)** через `KillSwitchService` — возвращает
   подтверждение закрытия (`closeConfirmed`).
5. `KILL_SWITCH_EXECUTED`.
6. `completeOrEscalate` — терминал по подтверждению.

## Гейт терминала и эскалация

**Терминал `COMPLETED` — только при подтверждённом закрытии** (сверка
реального состояния биржи в отчёте самого kill-switch, bounded ретраем
teardown внутри `KillSwitchExecutor`). После `complete` пишется
after-слепок.

Не подтверждено:

- **Инструмент-scope не подтверждён → ЭСКАЛАЦИЯ** на биржевой холд +
  общебиржевой kill-switch: тем же контуром `reactExchange` с сигналом
  `HoldSignal.exchange(EXCHANGE_KILL_SWITCH_RESIDUAL)` (code
  `EXCHANGE_KILL_SWITCH_RESIDUAL`). Обоснование (HOLD-Q1): неустранимый
  остаток означает, что интеграции нельзя доверять и радиус неизвестен →
  консервативно тормозим биржу (см.
  `docs/decisions/controlled-violation-exchange-wide-hold.md`,
  `docs/rules/controlled-exchange-exceptions.md`).
- **Биржа-scope не подтверждён → эскалировать некуда**: отчёт **остаётся
  открытым** (`KILL_SWITCH_EXECUTED`, не `COMPLETED`). Досверка орфанов
  вне модели сделки — проактивный `AnomalyJob` (ANOM-Q2, шаг 8; см.
  `docs/components/AnomalyJob.md`).

## Exception- и best-effort-политика

- **Exception-total:** реакция наружу исключение **не пробрасывает**. Сбой
  kill-switch → `AnomalyReport` `ERROR` (`fail`), проход оркестратора
  живёт. Это execution boundary для реактивного контура.
- **Журнал best-effort и НЕ гейтит kill-switch:** сбой любой записи
  отчёта (включая **создание** — `open` вернул `null`) логируется, но не
  подавляет teardown риска и не выходит наружу; последующие записи журнала
  становятся no-op. Снятие риска приоритетнее журнала.

## Не делает

Не закрывает свою триггерную сделку (её в ERROR уводит handler, приложив
`HoldSignal`). Не решает «как технически» снять риск (это
`KillSwitchExecutor`) и не собирает слепки сам (это
`AnomalyReportService`). Не ищет глобальные нарушения инвариантов и не
досверяет орфанов (это `AnomalyJob`).

## Связи

- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md` —
  правила холдов по scope (уровни — ось error-политики).
- `docs/rules/controlled-exchange-exceptions.md`,
  `docs/rules/error-handling-policy.md` — контур контролируемых
  исключений и общая error-политика.
- `docs/components/KillSwitchExecutor.md`,
  `docs/components/DealOrchestratorJob.md` — исполнитель teardown и
  проход, из которого поднимается реакция.
- `docs/components/models/HoldSignal.md` — сигнал холда (scope + code).
- `docs/models/domain/other/AnomalyReport.md`,
  `docs/lifecycles/AnomalyReport.md` — журнал инцидента и его lifecycle.
- `docs/decisions/controlled-violation-exchange-wide-hold.md` —
  обоснование эскалации L3→биржа (HOLD-Q1).
