# Exchange HOLD: что блокируется

## На какой вопрос отвечает этот файл

Какое правило системы определяет, какие команды блокирует статус
`Exchange.HOLD`.

## Правило

`Exchange.HOLD` — safety-состояние биржи. Что блокируется — одинаково для
любого триггера (триггеры — см. §«Что переводит в HOLD»). В состоянии `HOLD`:

**Блокируются** normal trading commands:

```text
SUBMIT_ORDER
SUBMIT_ALGO_ORDER
```

(Амендных команд нет — REPLACE-ремодел блокируется этой же парой:
его place-нога — `SUBMIT_*`; `docs/decisions/replace-not-amend.md`.)

**Не блокируются** safety / read commands:

```text
REFRESH_*
SEARCH / HISTORY
CANCEL_ORDER
CANCEL_ALGO_ORDER
CLOSE_POSITION
```

(Kill-switch teardown командой **не** является — тип `EXECUTE_KILL_SWITCH`
убран; аварийное снятие риска идёт вне реестра команд через
`docs/components/KillSwitchExecutor.md` теми же `CANCEL_*` / `CLOSE_POSITION`
/ `REFRESH_*`, которые здесь и так не блокируются.)

Также `HOLD` блокирует создание новых `ENTRY`/`GRID_ENTRY`, normal-flow
TP/SL/trailing actions, pyramid/scaling и любые действия, увеличивающие
торговое намерение вне safety-flow.

## Что переводит в HOLD

- **Safety-каскад** по внешнему статусу биржи — первичный триггер
  (`docs/rules/external-status-resolution.md`).
- **Несвежесть ставки комиссии** — возраст `modifiedAt` актуальной строки
  `TradeFeeRate` больше **порога свежести** (конфиг, стартовое значение
  **24 ч**). Выставляет `InstrumentExternalRulesSyncJob`
  (`docs/components/InstrumentExternalRulesSyncJob.md`); дом факта —
  `docs/models/domain/other/TradeFeeRate.md` §«Свежесть → холд биржи».

  **Почему это холд, а не тихая торговля по старой ставке.** `modifiedAt`
  двигается при **каждом успешном** чтении, поэтому при часовом такте синка
  возраст 24 ч = **24 неудачи подряд**. Это не сетевая икота, а поломка
  интеграции (права / эндпоинт / подпись). Торговать по ставке неизвестной
  давности нельзя — прогноз комиссии входит в риск-сайзинг
  (`docs/decisions/per-trade-risk-policy.md` §«Учёт комиссий»); рвать живые
  сделки при этом незачем — холд блокирует **вход** (`SUBMIT_*`), не мешая
  `REFRESH_*` / `CANCEL_*` / `CLOSE_POSITION`, и сделки доживают. Отсутствие
  ставки **вовсе** — другой случай: это реджект `FEE_RATE_UNAVAILABLE` на
  `RiskValidator`, не холд (`docs/components/models/RiskCheckResult.md`).

## DISABLED (Exchange / Instrument)

`HOLD` — safety-пауза; `DISABLED` — конфигурационное отключение
`Exchange`/`Instrument`. На первом этапе `DISABLED` трактуется как
запрет новых сделок; разрешение safety/read операций зависит от причины
отключения и задаётся отдельно (в отличие от `HOLD`, где safety/read
всегда разрешены). Статус инструмента — также точка enforcement
блокировки торговли после `AnomalyReport` (severity `CRITICAL` →
торговля остаётся запрещённой; см. `docs/models/domain/other/AnomalyReport.md`).
Полная модель/lifecycle `Exchange`/`Instrument` — backlog п.9.

## Почему

Сквозное правило про gating команд на уровне биржи
(`.claude/decisions/rule-source-of-truth.md`). `HOLD` останавливает
создание нового риска, но оставляет возможность наблюдать состояние
(read/refresh) и снижать риск (cancel/close/kill-switch) до разбора
аномалии.

> Модель/lifecycle самого `Exchange` в текущей серии миграции не
> создаётся (не входит в backlog-порядок из 6 сущностей). Здесь
> зафиксировано только правило gating команд по `HOLD`; полная модель
> `Exchange` — отдельная задача.

## Связанное

- `docs/rules/external-status-resolution.md` (safety-каскад — первичный
  триггер перехода в HOLD).
- `docs/models/domain/other/TradeFeeRate.md` (несвежесть ставки — второй
  триггер); выставляет — `docs/components/InstrumentExternalRulesSyncJob.md`.
- `docs/rules/error-handling-policy.md` — exchange-HOLD = **уровень 4**
  error-градации (нарушение контракта интеграции / инвариантов системы);
  инструмент-scope холд (уровень 3) — `docs/rules/instrument-hold.md`.
- `docs/lifecycles/Order.md`.
