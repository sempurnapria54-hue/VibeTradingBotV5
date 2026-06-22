# Instrument HOLD: финансовый холд по инструменту

## На какой вопрос отвечает этот файл

Какое правило системы определяет инструмент-scope холд (уровень 3
error-градации): чем триггерится, что блокирует, как снимается.

## Правило

`Instrument.HOLD` — safety-пауза **по одному инструменту** (в отличие от
`docs/rules/exchange-hold.md`, останавливающего всю биржу). Вводится
**уровнем 3** error-градации (`docs/rules/error-handling-policy.md`):
**финансовая ошибка по инструменту**.

### Триггеры (риск-условия, не код-исключения)

- **серия неудач подряд** по инструменту (порог — провизорное число,
  бэктест/пользователь, `docs/rules/error-handling-policy.md`);
- **нарушение риск-политики** на инструменте (например, обнаруженная
  бесстоповая risk-creating позиция —
  `docs/rules/risk-creating-entry-protection.md`; иные нарушения риска на
  сделку/инструмент).

Это **риск-условия**, а не таксономия runtime-исключений (уровни 1-2,
`docs/rules/runtime-error-classification.md`) и не нарушение контракта
интеграции/инвариантов системы (уровень 4 → холд **биржи**,
`docs/rules/exchange-hold.md`). Поэтому холд **инструмент-scope**, а не
exchange-scope.

### Реакция

При срабатывании триггера: **холд инструмента** + **kill-switch** (снять
live risk по инструменту, `docs/components/KillSwitchExecutor.md`) +
**`AnomalyReport`** (журнал инцидента,
`docs/models/domain/other/AnomalyReport.md`).

### Что блокирует

Пока инструмент в `HOLD` — **новые сделки и наращивание риска по этому
инструменту запрещены** (создание `ENTRY`/`GRID_ENTRY`, scaling/pyramiding,
normal-flow torговые actions). Read/refresh и risk-reducing
(cancel/close/kill-switch) — разрешены (как в `exchange-hold`, но скоуп —
один инструмент). Сделки по **другим** инструментам биржи не затронуты.

### Снятие — вручную

Холд инструмента снимается **вручную** после разбора человеком — как и
exchange-холд уровня 4. Автоматического снятия нет: финансовая ошибка
требует ручного решения, что риск понят и устранён. Согласуется с
`AnomalyReport.Severity` (`CRITICAL` → торговля по инструменту остаётся
запрещённой до ручного разбора;
`docs/models/domain/other/AnomalyReport.md`).

## Enforcement

Фактическая блокировка торговли по инструменту живёт в **статусе
инструмента** (`Instrument.Status = HOLD`, енум уже есть —
`docs/models/domain/core/Instrument.md`; полный lifecycle периферийных
статусов — backlog п.9). `AnomalyReport.severity` задаёт политику; точка
enforcement — статус инструмента (то же место, что для post-anomaly
блокировки, `docs/rules/exchange-hold.md` §DISABLED). Полная модель
координации статусов инструмента — backlog п.9.

## Почему

Уровень 3 error-градации (`docs/rules/error-handling-policy.md`):
финансовая ошибка по инструменту локализуется на инструменте, не валит всю
биржу (в отличие от нарушения контракта/инвариантов — уровень 4). Снятие
вручную — чтобы живой риск не возобновлялся без человеческого разбора.

## Связанное

- `docs/rules/error-handling-policy.md` (источник градации, уровень 3).
- `docs/rules/exchange-hold.md` (уровень 4, exchange-scope).
- `docs/models/domain/other/AnomalyReport.md` (журнал + severity-политика).
- `docs/rules/risk-creating-entry-protection.md` (один из триггеров).
- `docs/models/domain/core/Instrument.md` (`Status.HOLD`),
  `docs/lifecycles/Instrument.md`.
