# OKX contracts: fills

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по fills (3d, 3m): endpoint'ы, query,
лимиты, пагинация.

## Контекст

Mapping (стаб, до материализации `TradeFill`) —
`docs/models/mapping/TradeFill.md`. Native response —
`docs/models/integrations/okx/OkxFillResponse.md`. Использует
`RefreshFillsExecutor` (`docs/components/RefreshFillsExecutor.md`).
Глубже 3 месяцев — `docs/integrations/okx/contracts/fills-archive.md`.

## Endpoints

- **Fills 3 дня** (`REFRESH_FILLS`):
  `GET /api/v5/trade/fills`. Permission: Read. Rate limit: 60 req / 2 s
  по User ID.
- **Fills 3 месяца** (`REFRESH_FILLS_HISTORY` — гипотетический; в коде
  пока единый `REFRESH_FILLS`): `GET /api/v5/trade/fills-history`.
  Permission: Read. Rate limit: 10 req / 2 s по User ID.

## Query (одинаковые для обоих)

- `instType` — `SPOT/MARGIN/SWAP/FUTURES/OPTION`.
- `instId` — конкретный инструмент.
- `ordId` — id ордера; чтобы увидеть только fills этого ордера.
- `after` / `before` — пагинация **по `billId`** (не времени, не
  `ordId`).
- `begin` / `end` — фильтр по времени (Unix ms).
- `limit` — ≤ 100 (default 100).

`after`/`before` × `begin`/`end`: биржа сначала фильтрует по
`begin`/`end`, затем применяет пагинацию.

## Пагинация назад

1. Запрос без `after`.
2. Из ответа берём `min(billId)`.
3. Следующий запрос с `after = min(billId)`.
4. Стоп: пустой `data`.

## Контракт `ClientService`

Успешный запрос возвращает список fills (может быть пустым). На
controlled error (`code != "0"`, parse, invariant) — exception в
adapter; runtime не получает «частичный» ответ.

## После даунтайма

Совмещаются `orders-history` (как биржа видит ордер) и `fills`
(какие сделки реально прошли); fills — более надёжный источник
факта исполнения.
