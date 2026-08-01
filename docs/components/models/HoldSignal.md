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

**Словарь радиусов проекта.** На каком радиусе действует реакция.
**Общий** для `HoldSignal` (сигнал из прохода) и `AnomalyReport` (журнал
инцидента) — своего дока не имеет, описан здесь.

- `INSTRUMENT` — один инструмент (см. `docs/rules/instrument-hold.md`).
- `INSTRUMENT_GROUP` — инструменты одной комиссионной группы: обесценен
  факт, общий для группы (несвежесть ставки / ключа группы,
  `docs/rules/instrument-hold.md` §«Несвежесть ставки комиссии»).
- `EXCHANGE` — вся биржа/аккаунт: каскад на все инструменты биржи (см.
  `docs/rules/exchange-hold.md`).

Перечень совпадает с перечнем радиусов error-политики
(`docs/rules/error-handling-policy.md` §«Перечень scope и реакций»):
инструмент / группа инструментов / биржа-аккаунт.

**`HoldSignal` использует подмножество.** Фабрик по-прежнему две
(`instrument` / `exchange`) — реактивный kill-switch-контур групповым
радиусом не оперирует. `INSTRUMENT_GROUP` производит **мягкая** тропа, и
живёт он на `AnomalyReport.scope` (H4, `GAPS_CLOSE_6`). Значение не
«неиспользуемое»: у него другой производитель, не другой енум.

Отсюда следствие для поверхности создания отчёта (H23, `GAPS_CLOSE_7`):
журнальная тропа заводит `AnomalyReport` **без `HoldSignal`** — она не
рождена проходом над сделкой, `DealContext` у неё может не быть вовсе
(`InstrumentExternalRulesSyncJob`), и severity она задаёт сама
(`NON_CRITICAL`). Фабрика `instrumentGroup(code)` в `HoldSignal` **не
заводится**: это был бы сигнал без адресата — реактивный координатор
групповым радиусом не оперирует
(`docs/models/domain/other/AnomalyReport.md` §«Производящая поверхность»).

Scope описывает **радиус**, не уровень серьёзности: уровень — отдельная
ось, живёт в error-политике (`docs/rules/error-handling-policy.md`
§«Радиус ущерба задаёт scope»), и одному уровню могут соответствовать
разные радиусы (ярлыки уровня сняты с енума — H6, `GAPS_CLOSE_5`).
Радиус не задаёт и **статус** enforcement: мягкая реакция ставит
`Instrument.Status.ENTRY_BLOCKED`, kill-switch-класс — `TRADE_BLOCKED`
(`docs/rules/instrument-hold.md` §Enforcement).

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
