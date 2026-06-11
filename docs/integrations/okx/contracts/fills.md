# OKX contracts: fills

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по fills (3d, 3m): endpoint'ы, query,
лимиты, пагинация.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Trade», секции «GET / Transaction
details (last 3 days / last 3 months)»). При расхождении с офдоком
побеждает офдок; синхронизация — перевыкачка + дифф при каждом
заходе интегратора (`.claude/processes/api-docs-completion.md` §4a,
канал — `.claude/skills/integration-okx.md`). Последняя сверка:
2026-06-11 (прогон 1 — соответствие спеке подтверждено).

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
- **Fills 3 месяца** (звено `REFRESH_FILLS`, эскалация 3d→3m **внутри
  команды**): `GET /api/v5/trade/fills-history`. Permission: Read. Rate
  limit: 10 req / 2 s по User ID. `REFRESH_FILLS` обходит `/trade/fills`
  (3d) → `/trade/fills-history` (3m) внутри одной команды (см.
  `docs/decisions/refresh-evidence-cycle-ownership.md`); отдельной
  `REFRESH_FILLS_HISTORY`-команды нет. Архив 3m+ (`fills-archive`,
  async-флоу) — `OKX-Q2` (шаг 7).

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

## Контракт `IntegrationService`

Успешный запрос возвращает список fills (может быть пустым). На
controlled error (`code != "0"`, parse, invariant) — exception в
adapter; runtime не получает «частичный» ответ.

## После даунтайма

Совмещаются `orders-history` (как биржа видит ордер) и `fills`
(какие сделки реально прошли); fills — более надёжный источник
факта исполнения.
