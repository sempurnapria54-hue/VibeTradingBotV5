# SafetyHoldCoordinator

## На какой вопрос отвечает этот файл

Кто держит последовательность полной реакции холда (`FULL`): шаги,
исполнители, гейт терминала, эскалация, exception- и best-effort-политика,
границы.

## Назначение

`SafetyHoldCoordinator` — держатель **последовательности полной реакции**
(`FULL`: `TRADE_BLOCKED` + kill-switch). **Биржевая ступень 1
(`Exchange.HOLD`) через координатор не идёт и реактивным контуром не
ставится вовсе** — это ручной гейт входов
(`docs/rules/exchange-hold.md`). Сам ничего не
исполняет напрямую, оркеструет исполнителей:

- `InstrumentDataService` / `ExchangeDataService` — выставление
  `TRADE_BLOCKED` scope (`blockTrade()`);
- `AnomalyReportService` — журнал инцидента и слепки (см.
  `docs/models/domain/other/AnomalyReport.md`);
- `KillSwitchService` — аварийное снятие риска (см.
  `docs/components/KillSwitchExecutor.md`).

Точка входа — **`react(HoldSignal)`**, идемпотентная по статусу scope.
Идентичность объекта блокировки (`instrumentId`/`exchangeId`) координатор
берёт **из сигнала**: `DealContext` из подписи ушёл —
тем же ходом, каким идентичность внесена внутрь сигнала уровнем выше. Карточка сделки не
нужна нигде по цепочке: реактивная поверхность `AnomalyReport` берёт
идентичность из того же сигнала, а слепки собирает
`AnomalyReportService`, которому радиус приходит через координатора
( — клауза верна и правки не требовала). Ветвление по классу
реакции **сюда не входит**: `SOFT` исполняет
`HoldService` сам (статус инструмента, без teardown), а до координатора
доходит только `FULL`. Одно место согласования эскалации
`ENTRY_BLOCKED → TRADE_BLOCKED` при этом сохраняется — им становится
`HoldService`.

## Последовательность реакции (класс `FULL`)

Инструмент-scope и биржа-scope — **одной формы у `FULL`**, различаются
только scope-исполнителями (`InstrumentDataService`/`fireInstrument` vs
`ExchangeDataService`/`fireExchange()`); ярлыки уровня со scope сняты. Биржевая **ступень 1**
(`Exchange.HOLD`) — **другая форма**: гейт entry-скана без kill-switch и
каскада, координатором **не** исполняется. Ставится либо вручную, либо
автоматическим триггером `MISMATCHED` через `HoldService` в ветке
`FREEZE` (`docs/rules/exchange-hold.md`,
`docs/components/HoldService.md`); мимо координатора идут обе тропы. Дизайн холдов шага 6:

1. **`TRADE_BLOCKED` scope первым** (`blockTrade()`) — gate и анкер
   идемпотентности. Повторный сигнал того же scope, когда scope **уже в
   `TRADE_BLOCKED`**, → реакция **пропускается** (ранний `return`,
   kill-switch не гоняется повторно).
   - **Анкер — `TRADE_BLOCKED`, а не «scope не в `ACTIVE`»**. С появлением мягкого класса холда
     (`Instrument.Status.ENTRY_BLOCKED`, `docs/rules/instrument-hold.md`) буквальное «ставится только из `ACTIVE`» маскировало бы
     аварию: инструмент под **мягким** холдом не в `ACTIVE`, kill-switch по
     нему не гонялся — и последующий риск-триггер уровня 3 был бы молча
     пропущен. Переход `ENTRY_BLOCKED → TRADE_BLOCKED` **разрешён** и
     реакцию не пропускает (эскалация мягкого класса в полный); обратной
     эскалации нет.
   - **Биржевая пара анкеров симметрична** (`docs/rules/exchange-hold.md`): `Exchange.HOLD` (мягкий холд, ступень 1) —
     **не** анкер идемпотентности — биржа под мягким холдом обязана
     принять последующий триггер ступени 2, `HOLD → TRADE_BLOCKED`
     разрешён и реакцию не пропускает; анкером `FULL`-реакции служит
     только `Exchange.TRADE_BLOCKED`.
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

- **Инструмент-scope не подтверждён → ЭСКАЛАЦИЯ на ступень 2**
  (`Exchange.TRADE_BLOCKED` + общебиржевой kill-switch): тем же контуром
  `reactExchange` с сигналом
  `HoldSignal.exchangeTradeBlock(EXCHANGE_KILL_SWITCH_RESIDUAL)`.
  Ступень — по триггеру: неустранимый остаток teardown — **живой риск,
  снятие которого не подтверждается**, то есть триггер ступени 2
  (`docs/rules/exchange-hold.md`). Controlled-тропа в эту эскалацию не
  попадает по другой причине: она **сама с самого начала идёт ступенью
  2** через `HoldService`, и
  эскалировать ей уже некуда — повторный сигнал гасится анкером
  `Exchange.TRADE_BLOCKED`. Обоснование (HOLD-Q1): неустранимый
  остаток означает, что интеграции нельзя доверять и радиус неизвестен →
  консервативно тормозим биржу (см.
  `docs/rules/exchange-hold.md` — частично
  superseded лестницей, `docs/rules/exchange-hold.md`;
  `docs/rules/controlled-exchange-exceptions.md`).
- **Биржа-scope не подтверждён → эскалировать некуда**: отчёт **остаётся
  открытым** (`KILL_SWITCH_EXECUTED`, не `COMPLETED`). Досверка орфанов
  вне модели сделки — проактивный `AnomalyJob`.

## Exception- и best-effort-политика

- **Exception-total:** реакция наружу исключение **не пробрасывает**. Сбой
  kill-switch → `AnomalyReport` `ERROR` (`fail`), проход оркестратора
  живёт. Это execution boundary для реактивного контура.
- **Журнал best-effort и НЕ гейтит kill-switch:** сбой любой записи
  отчёта (включая **создание** — `open` вернул `null`) логируется, но не
  подавляет teardown риска и не выходит наружу; последующие записи журнала
  становятся no-op. Снятие риска приоритетнее журнала.

## Не делает

Не закрывает свою триггерную сделку (её в `ERROR` уводит FSM/handler). Не
принимает вызовы от детекторов напрямую — единственный вход к нему
`HoldService`. Не исполняет мягкую ветку. Не решает «как технически» снять
риск (это
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
  проход, в котором детектор зовёт `HoldService`.
- `docs/components/HoldService.md` — общий исполнитель блокировки и
  единственный вход сюда.
- `docs/components/models/HoldSignal.md` — параметр вызова (радиус + класс
  реакции + code).
- `docs/models/domain/other/AnomalyReport.md`,
  `docs/lifecycles/AnomalyReport.md` — журнал инцидента и его lifecycle.
- `docs/rules/exchange-hold.md` —
  обоснование эскалации L3→биржа (HOLD-Q1); лестница
  (`docs/rules/exchange-hold.md`) его существо сохраняет:
  controlled exception даёт **ступень 2** (`Exchange.TRADE_BLOCKED` +
  flatten).
