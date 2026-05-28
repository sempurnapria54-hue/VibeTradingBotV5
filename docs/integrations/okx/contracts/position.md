# OKX contracts: position

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по позиции: endpoint'ы, лимиты,
close-position ACK, подтверждение факта закрытия.

## Контекст

Mapping в `Position` — `docs/models/mapping/Position.md` (раздел
`## OKX`). Native response —
`docs/models/integrations/okx/OkxPositionResponse.md`. Правила OKX —
`docs/integrations/okx/rules/`. Доменные модель/lifecycle —
`docs/models/domain/core/Position.md` / `docs/lifecycles/Position.md`.

## Endpoints

- **Получить позиции** (`REFRESH_POSITION`):
  `GET /api/v5/account/positions?instType=SWAP&instId={...}`.
  Permission `Read`; rate limit 10 req / 2 s по User ID. Один
  логический запрос по инструменту; дополнительно по `posId` не
  ищем — цель в наличии/отсутствии live position по инструменту, не
  в доказательстве старого `posId` (биржа держит ~30 дней).
  Query (все опц.): `instType`, `instId` (до 10 через запятую),
  `posId` (до 20). В net-режиме на инструмент ожидается одна запись
  с `posSide=net`; в long/short — отдельные `posSide=long`/`short`.
- **Закрыть позицию** (`CLOSE_POSITION`):
  `POST /api/v5/trade/close-position`. Permission `Trade`; rate
  limit 20 req / 2 s по User ID + Instrument ID. Body: `instId`
  (обяз.), `mgnMode` (обяз.; `isolated`/`cross`), `posSide` (условно
  обяз. — для net: `net`; для long/short: `long`/`short`), `ccy`
  (опц., для USDT-SWAP — `USDT`), `autoCxl` (опц. boolean —
  автоматически отменить все активные ордера по инструменту перед
  закрытием; рекомендуется `true`).

Ретраи на refresh — только при технических/API проблемах (timeout,
connection reset, 5xx, rate limit, temporary error).

## ACK-семантика close-position

Response — ACK, не финальный статус (`docs/rules/ack-not-runtime-truth.md`).
`data[0]` содержит `instId`, `posSide`. **Нет `ordId`** и нет
финального статуса позиции — подтверждение через `REFRESH_POSITION`
(позиция исчезла или `pos=0`), опционально через fills и/или WS
`positions`/`orders`.
