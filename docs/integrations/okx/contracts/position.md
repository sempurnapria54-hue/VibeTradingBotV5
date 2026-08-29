# OKX contracts: position

## На какой вопрос отвечает этот файл

Каков контракт операций по позиции.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
разделы «Trading Account → REST API» — «Get positions», «Get
positions history»; «Order Book Trading → Trade» — «POST / Close
positions»). При расхождении с офдоком побеждает офдок;
синхронизация — перевыкачка + дифф при каждом заходе интегратора по
источнику и по задаче «актуализируй»
(`.claude/processes/api-docs-completion.md`, канал чтения —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(соответствие positions/close-position; positions-history
поле-уровнево).

## Endpoints

- **Получить позиции** (`REFRESH_POSITION_COMMAND`):
  `GET /api/v5/account/positions?instType=SWAP&instId={...}`.
  Permission `Read`; rate limit 10 req / 2 s по User ID. Один
  логический запрос по инструменту; дополнительно по `posId` в **live**-ноге
  не ищем — её цель в наличии/отсутствии live position по инструменту, не
  в доказательстве старого `posId` (биржа держит ~30 дней). При not-found
  команда переходит на **вторую ногу** — positions-history по `posId`
.
  Query (все опц.): `instType`, `instId` (до 10 через запятую),
  `posId` (до 20). В net-режиме на инструмент ожидается одна запись
  с `posSide=net`; в long/short — отдельные `posSide=long`/`short`.
- **Закрыть позицию** (`CLOSE_POSITION_COMMAND`):
  `POST /api/v5/trade/close-position`. Permission `Trade`; rate
  limit 20 req / 2 s по User ID + Instrument ID. Body: `instId`
  (обяз.), `mgnMode` (обяз.; `isolated`/`cross`), `posSide` (условно
  обяз. — для net: `net`; для long/short: `long`/`short`), `ccy`
  (опц., для USDT-SWAP — `USDT`), `autoCxl` (опц. boolean —
  автоматически отменить все активные ордера по инструменту перед
  закрытием; рекомендуется `true`).

Ретраи на refresh — только при технических/API проблемах (timeout,
connection reset, 5xx, rate limit, temporary error).

## История закрытых позиций (источник числа `resultProfit`)

`GET /api/v5/account/positions-history`. Permission `Read`; rate
limit 10 req / 2 s по User ID. Глубина — 3 месяца, сортировка по
`uTime` (новые первыми). Офдок: «Get positions history». Статус:
**источник заголовочного числа** `Deal.resultProfit` (готовый net
`realizedPnl`) — выбран на шага 7 (2026-07-03; **В-3
закрыт**, `docs/models/domain/aggregate/Deal.md`). `closeAvgPx`/
`openAvgPx` покрывают среднюю цену выхода/входа (fills для этого не
нужны).

**Добыча:** эндпоинт — **вторая нога evidence-cycle команды
`REFRESH_POSITION_COMMAND`**. Наполняет
`PositionCloseResultExternalSnapshot`, который приземляется **полями
положения закрытия на `Position`** (`docs/models/domain/core/Position.md`),
откуда число читает финализатор. Отдельной команды
`REFRESH_POSITIONS_HISTORY` нет.
Native-модель — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`;
mapping native→snapshot→`Position`→`Deal` —
`docs/models/mapping/PositionCloseResult.md`.

- **Query (все опц.):** `instType`, `instId`, `mgnMode`
  (`cross`/`isolated`), `type` (тип последнего закрытия: `1`
  частичное / `2` полное / `3` ликвидация / `4` частичная ликвидация
  / `5` ADL не полностью / `6` ADL полностью), `posId`,
  `after`/`before` — пагинация **по `uTime`** (не по id; записи с
  одинаковым `uTime` приходят одной страницей), `limit` ≤ 100.
- **P&L-поля элемента:** `realizedPnl` = `pnl` + `fee` +
  `fundingFee` + `liqPenalty` (+ `settledPnl` cross-FUTURES);
  `pnl` (без комиссий), `fee` (минус — комиссия, плюс — ребейт),
  `fundingFee` (накопленный), `liqPenalty`, `pnlRatio`.
- **Цены/объёмы:** `openAvgPx`, `closeAvgPx`, `openMaxPos`
  (максимум позиции), `closeTotalPos` (накопленный закрытый объём),
  `triggerPx` (цена триггера ликвидации/ADL — **опционально**, см. ниже),
  `nonSettleAvgPx`/`settledPnl` (cross FUTURES).

### Инвариант агрегации (N11, требует рантайм-верификации)

**Инвариант:** **один эпизод** ↔ один `posId` ↔ **одна финализированная**
запись positions-history, чей `realizedPnl` **кумулятивен по ВСЕМ**
partial-закрытиям и доборам за жизнь **этой позиции**; читается
**финализированной** (позиция полностью закрыта / flat по
`REFRESH_POSITION_COMMAND`).

**Помечено как предположение** до рантайм-верификации (контур source-api,
demo, `.claude/tests/source-api/okx/plan.md`). Верифицировать:
агрегирует ли OKX partial-выходы (partial TP `type` 1 → SL `type` 2) в
**одну** запись на `posId`, в какой момент запись **финализирована**
(.5) и отдаёт ли окно с несколькими эпизодами **отдельную** запись на
каждый `posId` (.9). Риск чтения нефинализированной / послайсовой
записи → **систематический недосчёт realized** (левый хвост
R-распределения усечён молча). **Гейтит корректность числа**
`Deal.resultProfit` → верификация до `CODE`
(`docs/rules/pnl-reconciliation.md` реш.6).

## ACK-семантика close-position

Response — ACK, не финальный статус (`docs/rules/ack-not-runtime-truth.md`).
`data[0]` содержит `instId`, `posSide`. **Нет `ordId`** и нет
финального статуса позиции — подтверждение через `REFRESH_POSITION_COMMAND`
(позиция исчезла или `pos=0`), опционально через fills и/или WS
`positions`/`orders`.
