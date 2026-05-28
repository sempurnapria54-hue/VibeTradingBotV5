# OKX fills mapping

## На какой вопрос отвечает этот файл

Как OKX fills (`trade/fills`, `trade/fills-history`) подтягиваются и
матчатся с известными `Order` / `AlgoOrder` / `Position` фактами.

## Контекст

Exchange-specific mapping для OKX. Доменно fills использует
`docs/components/RefreshFillsExecutor.md`: загружает fills с биржи,
сопоставляет с известными `Order` / `AlgoOrder` / `Position` facts и
обновляет вложенные runtime-сущности. Для финализации сделки результат
fills питает подсчёт `Deal.resultProfit`.

`Fill` как persisted entity на первом этапе **не вводим** (см.
`docs/components/RefreshFillsExecutor.md`); материализация `TradeFill`
— OKX-Q1 в `.claude/work/questions/open-questions.md`. Сужение
маппинга до used полей и итоговый формат `TradeFill` (если будет
введён) оформятся после закрытия OKX-Q1. До тех пор — fields-only
контракт endpoint'а (см. `OkxFillResponse.md`).

## Endpoints

- **Fills 3 дня** (`REFRESH_FILLS`):
  `GET /api/v5/trade/fills`. Permission: Read. Rate limit: 60 req / 2 s
  по User ID.
- **Fills 3 месяца** (`REFRESH_FILLS_HISTORY` — гипотетический; в коде
  пока единый `REFRESH_FILLS`): `GET /api/v5/trade/fills-history`.
  Permission: Read. Rate limit: 10 req / 2 s по User ID.

Раздел «полные fills > 3 месяцев» — отдельный async-флоу:
`docs/client/okx/rules/okx-fills-archive-mapping.md`.

## Query (одинаковые для обоих)

- `instType` — `SPOT/MARGIN/SWAP/FUTURES/OPTION`.
- `instId` — конкретный инструмент.
- `ordId` — id ордера; чтобы увидеть только fills этого ордера.
- `after` / `before` — пагинация **по `billId`** (не времени, не
  `ordId`).
- `begin` / `end` — фильтр по времени (Unix ms).
- `limit` — ≤ 100 (default 100).

`after`/`before` × `begin`/`end`: биржа сначала фильтрует по
`begin`/`end`, затем применяет пагинацию по `after`/`before`.

## Пагинация назад

1. Запрос без `after`.
2. Из ответа берём `min(billId)` (`billId` — лексикографические/
   числовые id; считаем строкой при сравнении, если не уверены в
   формате).
3. Следующий запрос с `after = min(billId)`.
4. Стоп: пустой `data`.

## Контракт `ClientService`

Успешный запрос возвращает список fills (может быть пустым). На
controlled error (`code != "0"`, parse, invariant) — исключение в
adapter; runtime не получает «частичный» ответ.

## Связь с domain

- `ordId` ↔ известный `Order.externalId` / `AlgoOrder.linkedOrderExternalIds`.
- `clOrdId` ↔ `Order.internalId`.
- Совокупный `fillSz`, `fillPx`, `fee` по `ordId` агрегируется в
  `Order` (`accumulatedFillSize`, `averagePrice`, накопленная `fee`)
  при refresh-контуре; ack-not-runtime-truth применяется
  (`docs/rules/ack-not-runtime-truth.md`).
- Идемпотентность `RefreshFillsExecutor` гарантирует, что повторный
  вызов не задваивает агрегаты
  (`docs/components/RefreshFillsExecutor.md`).

## После даунтайма

После расхождения REST `orders-history` (как биржа видит ордер) и
`fills` (какие сделки реально прошли) — fills более надёжный источник
факта исполнения. См. также
`docs/client/okx/rules/okx-fills-archive-mapping.md` для глубины >3
месяцев.
