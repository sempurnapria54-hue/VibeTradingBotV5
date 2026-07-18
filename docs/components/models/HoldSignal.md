# HoldSignal

## На какой вопрос отвечает этот файл

Что это за runtime value object `HoldSignal`: структура, фабрики, енум
`HoldScope`, ключевой инвариант «сигнал, не закрытие сделки».

## Назначение

`HoldSignal` — сигнал «поднять реактивный safety-холд scope». RVO
(`@Value`, immutable; см. `.claude/decisions/runtime-value-object.md`),
который несёт `DealTransition` рядом с уводом своей сделки в ERROR
(handler → transition). **Сам сделку не закрывает** — её в ERROR уводит
handler; сигнал адресует **инструмент-/биржа-широкую** реакцию,
координируемую `SafetyHoldCoordinator` в проходе `DealOrchestratorJob`
(см. `docs/components/SafetyHoldCoordinator.md`).

## Структура

| Поле | Тип | Назначение |
|---|---|---|
| `scope` | `HoldScope` | Scope (радиус) холда: `INSTRUMENT` / `EXCHANGE`. |
| `code` | `String` | Машинно-читаемый код причины; попадает в `AnomalyReport.code`. |

**Severity не несёт.** Реактивный контур по определению `CRITICAL` —
severity задаётся на границе захода в `AnomalyReportService`, а не
переносится сигналом (см. `docs/rules/error-handling-policy.md`, дизайн
холдов шага 6).

## Фабрики

- `instrument(code)` — холд инструмента: `HoldScope.INSTRUMENT` + code.
- `exchange(code)` — холд биржи: `HoldScope.EXCHANGE` + code.

Публичного конструктора для прямой сборки не используем — сигнал строится
фабрикой по scope, чтобы радиус и намерение читались на call-site.

## Енум `HoldScope`

Скоуп реактивного safety-холда: на каком радиусе выставляется блокировка
торговли (`TRADE_BLOCKED`) и kill-switch. **Общий** для `HoldSignal`
(сигнал из прохода) и `AnomalyReport` (журнал инцидента) — своего дока не
имеет, описан здесь.

- `INSTRUMENT` — холд одного инструмента (см.
  `docs/rules/instrument-hold.md`).
- `EXCHANGE` — холд всей биржи: каскад на все инструменты биржи (см.
  `docs/rules/exchange-hold.md`).

Scope описывает **радиус**, не уровень серьёзности: уровень — отдельная
ось, живёт в error-политике (`docs/rules/error-handling-policy.md`
§«Радиус ущерба задаёт scope»), и одному уровню могут соответствовать
разные радиусы (ярлыки уровня сняты с енума — H6, `GAPS_CLOSE_5`).

## Связи

- `docs/components/SafetyHoldCoordinator.md` — координатор, потребляющий
  сигнал.
- `docs/models/domain/other/AnomalyReport.md` — журнал инцидента (`code`
  сигнала → `code` отчёта; `scope` общий).
- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md`,
  `docs/rules/error-handling-policy.md` — правила холдов по scope и
  error-политика (уровни — там, ось отдельная).
- `docs/components/KillSwitchExecutor.md` — исполнитель teardown,
  запускаемый реакцией.
