# OkxPositionsHistoryResponse (OKX positions-history)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели OKX positions-history response и какие из
них использует bot.

## Контекст

Нативная модель источника OKX. Возвращается `GET
/api/v5/account/positions-history` (элемент `data[]`). Не выходит за
`IntegrationService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Добывается командой **`REFRESH_POSITIONS_HISTORY`** (наполняет транзитный
`PositionCloseResultExternalSnapshot` — число `Deal.resultProfit`;
`docs/decisions/pnl-finalization-mechanics.md` реш.1).

Mapping в `PositionCloseResultExternalSnapshot` и далее в `Deal.resultProfit`
— `docs/models/mapping/PositionCloseResult.md`. Контракт endpoint'а / rate
limits / история закрытых позиций — `docs/integrations/okx/contracts/position.md`
§«История закрытых позиций». Источник числа `resultProfit` (что за данные) —
`docs/decisions/result-profit-source.md`.

Отличие от live `/positions` (native `OkxPositionResponse`): positions-history
несёт **realized**-факты закрытой позиции (`realizedPnl`, `closeAvgPx` и т. д.),
которых нет у live-DTO. За среднюю цену выхода/входа отвечает
`closeAvgPx`/`openAvgPx` — fills для этого не нужны.

## Инвентарь полей

### Используемые

Used-минимум для числа `resultProfit`: готовый net берётся одним полем
`realizedPnl`; своих слагаемых не складываем.

| OKX field | Тип | Семантика |
|---|---|---|
| `realizedPnl` | string-decimal | готовый net realized P&L = `pnl` + `fee` + `fundingFee` + `liqPenalty` (посчитан биржей) → `Deal.resultProfit` |
| `ccy` | string | валюта результата → `Deal.resultProfitCurrency` (для `ETH-USDT-SWAP` — `USDT`) |
| `closeAvgPx` | string-decimal | средняя цена выхода (закрытия позиции) |
| `openAvgPx` | string-decimal | средняя цена входа |
| `triggerPx` | string-decimal | цена триггера ликвидации/ADL (только `type` 3–6) |
| `type` | string | тип последнего закрытия (`1` частичное / `2` полное / `3` ликвидация / `4` частичная ликвидация / `5` ADL не полностью / `6` ADL полностью) |
| `posId` | string | биржевой id позиции (ключ агрегации записи; истекает ~30 дней после полного закрытия) |
| `uTime` | string-ms | время обновления записи (сортировка/пагинация positions-history — по `uTime`) |

### Не используется bot'ом (отбрасывается на маппинге)

Числом не потребляются: net берётся готовым `realizedPnl`; категорийная
разбивка (комиссия / funding / rebate / штраф) — из bills → `DealCashFlow`
(`docs/models/mapping/DealCashFlow.md`), не из этих полей.

- **Слагаемые net и производные PnL** (net берётся готовым `realizedPnl`,
  разбивка — из bills): `pnl` (без комиссий), `fee` (минус — комиссия,
  плюс — ребейт), `fundingFee` (накопленный funding), `liqPenalty`
  (ликвидационный штраф), `settledPnl` (cross-FUTURES), `pnlRatio`.
- **Объёмы / прочие цены:** `openMaxPos` (максимум позиции), `closeTotalPos`
  (накопленный закрытый объём), `nonSettleAvgPx` (cross-FUTURES).
- **Идентификация / атрибуты позиции** (не нужны числу; USDT-SWAP net /
  isolated фиксированы адаптером): `mgnMode`, `posSide`, `direction`,
  `lever`, `uly`, `cTime` (время создания).

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; timestamp string →
epoch millis → `OffsetDateTime` (конвенция типов времени проекта;
`docs/models/mapping/PositionCloseResult.md`).
