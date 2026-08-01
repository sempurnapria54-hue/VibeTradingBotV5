# OkxPositionsHistoryResponse (OKX positions-history)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели OKX positions-history response и какие из
них использует bot.

## Контекст

Нативная модель источника OKX. Возвращается `GET
/api/v5/account/positions-history` (элемент `data[]`). Не выходит за
`IntegrationService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Добывается **второй ногой команды `REFRESH_POSITION`** (evidence-cycle
live → positions-history внутри одной команды; наполняет
`PositionCloseResultExternalSnapshot`, который приземляется полями
положения закрытия на `Position` — H1/H3 `GAPS_CLOSE_7`,
`docs/decisions/pnl-finalization-mechanics.md` реш.1). Отдельной команды
`REFRESH_POSITIONS_HISTORY` нет.

Mapping в `PositionCloseResultExternalSnapshot` и далее в `Position` →
`Deal.resultProfit`
— `docs/models/mapping/PositionCloseResult.md`. Контракт endpoint'а / rate
limits / история закрытых позиций — `docs/integrations/okx/contracts/position.md`
§«История закрытых позиций». Источник числа `resultProfit` (что за данные) —
`docs/decisions/result-profit-source.md`.

Отличие от live `/positions` (native `OkxPositionResponse`): positions-history
несёт **realized**-факты закрытой позиции (`realizedPnl`, `closeAvgPx` и т. д.),
которых нет у live-DTO. Средняя цена входа/выхода покрывается
`openAvgPx`/`closeAvgPx` — fills для этого не нужны; в used-набор из них
входит только `openAvgPx` (см. §«Не используется»).

## Инвентарь полей

### Используемые

Used-минимум для числа `resultProfit`: готовый net берётся одним полем
`realizedPnl`; своих слагаемых не складываем.

| OKX field | Тип | Семантика |
|---|---|---|
| `realizedPnl` | string-decimal | готовый net realized P&L = `pnl` + `fee` + `fundingFee` + `liqPenalty` (посчитан биржей) → `Position.externalRealizedProfit` → `Deal.resultProfit` |
| `ccy` | string | валюта, в которой посчитан `realizedPnl` → `Position.externalResultCurrency` → `Deal.resultProfitCurrency` (для `ETH-USDT-SWAP` — `USDT`) |
| `openAvgPx` | string-decimal | средняя цена входа → `Position.externalAverageEntryPrice` |
| `type` | string | тип последнего закрытия (`1` частичное / `2` полное / `3` ликвидация / `4` частичная ликвидация / `5` ADL не полностью / `6` ADL полностью) → `Position.externalCloseType` (провенанс аварийного терминала) |
| `posId` | string | биржевой id позиции (ключ адресации записи; истекает ~30 дней после полного закрытия) — сверяется с `Position.externalId` |
| `uTime` | string-ms | время обновления записи → `Position.externalModifiedAt` (сортировка/пагинация positions-history — тоже по `uTime`) |

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
- **Выведены из used на `GAPS_CLOSE_7` (H22)** — потребителя в фазе 1 нет,
  поля без потребителя не заводим (codestyle §«Неиспользуемый код»):
  `closeAvgPx` (средняя цена выхода) и `triggerPx` (цена триггера
  ликвидации/ADL). Оба — кандидаты в носители провенанса ликвидации/ADL,
  вопрос открыт (`PNL-Q1`). Побочно снят остаток H19: расхождение доков о
  применимости `triggerPx` больше ничего не нагружает — поле не маппится.
- **Идентификация / атрибуты позиции** (не нужны числу; USDT-SWAP net /
  isolated фиксированы адаптером): `mgnMode`, `posSide`, `direction`,
  `lever`, `uly`, `cTime` (время создания).

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; timestamp string →
epoch millis → `OffsetDateTime` (конвенция типов времени проекта;
`docs/models/mapping/PositionCloseResult.md`).
