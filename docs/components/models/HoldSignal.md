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
| `scope` | `HoldScope` | Уровень холда: `INSTRUMENT` (L3) / `EXCHANGE` (L4). |
| `code` | `String` | Машинно-читаемый код причины; попадает в `AnomalyReport.code`. |

**Severity не несёт.** Реактивный контур по определению `CRITICAL` —
severity задаётся на границе захода в `AnomalyReportService`, а не
переносится сигналом (см. `docs/rules/error-handling-policy.md`, дизайн
холдов шага 6).

## Фабрики

- `instrument(code)` — холд инструмента (L3): `HoldScope.INSTRUMENT` + code.
- `exchange(code)` — холд биржи (L4): `HoldScope.EXCHANGE` + code.

Публичного конструктора для прямой сборки не используем — сигнал строится
фабрикой по уровню, чтобы scope и намерение читались на call-site.

## Енум `HoldScope`

Скоуп реактивного safety-холда: на каком уровне выставляется блокировка
торговли (`TRADE_BLOCKED`) и kill-switch. **Общий** для `HoldSignal`
(сигнал из прохода) и `AnomalyReport` (журнал инцидента) — своего дока не
имеет, описан здесь.

- `INSTRUMENT` — холд одного инструмента (**уровень 3**): локализованная
  риск-ошибка (см. `docs/rules/instrument-hold.md`).
- `EXCHANGE` — холд всей биржи (**уровень 4**): каскад на все инструменты
  биржи (см. `docs/rules/exchange-hold.md`).

Уровни соответствуют error-градации (`docs/rules/error-handling-policy.md`).

## Связи

- `docs/components/SafetyHoldCoordinator.md` — координатор, потребляющий
  сигнал.
- `docs/models/domain/other/AnomalyReport.md` — журнал инцидента (`code`
  сигнала → `code` отчёта; `scope` общий).
- `docs/rules/instrument-hold.md`, `docs/rules/exchange-hold.md`,
  `docs/rules/error-handling-policy.md` — уровни L3/L4 и error-политика.
- `docs/components/KillSwitchExecutor.md` — исполнитель teardown,
  запускаемый реакцией.
