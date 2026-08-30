# AlgoOrderOkxResponse (OKX algo-order)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели условной заявки источника.

## Инвентарь полей

### Используемые

| OKX field | Тип | Семантика |
|---|---|---|
| `instType` | string | тип инструмента (для сверки expected) |
| `instId` | string | инструмент (для сверки) |
| `algoId` | string | биржевой algo id |
| `algoClOrdId` | string | client id (stable, основной матчинг) |
| `state` | string | сырой статус (`live`/`pause`/`effective`/`canceled`/`order_failed`/`partially_failed`/`partially_effective`) |
| `failCode` | string | код ошибки |
| `actualSz` | string-decimal | фактический размер срабатывания |
| `actualPx` | string-decimal | фактическая цена срабатывания |
| `triggerTime` | string-ms | время срабатывания |
| `ordId` | string | связанный обычный ордер (может быть пустым) |
| `ordIdList` | array<string> | список связанных `ordId` (split-сценарии) |
| `cTime` | string-ms | время создания |
| `uTime` | string-ms | время обновления (есть в history) |
| **TP/SL поля:** | | |
| `tpTriggerPx` | string-decimal | TP trigger |
| `tpTriggerPxType` | string | `last`/`index`/`mark` |
| `tpOrdPx` | string-decimal | TP order price (`-1` = market) |
| `slTriggerPx` | string-decimal | SL trigger |
| `slTriggerPxType` | string | `last`/`index`/`mark` |
| `slOrdPx` | string-decimal | SL order price (`-1` = market) |
| **Trailing (`move_order_stop`):** | | |
| `callbackRatio` | string-decimal | трейл в доле |
| `callbackSpread` | string-decimal | трейл в абсолютных единицах |
| `activePx` | string-decimal | цена активации trailing |
| `moveTriggerPx` | string-decimal | текущее значение trailing trigger |
| **Trigger (`ordType=trigger`):** | | |
| `triggerPx` | string-decimal | цена триггера |
| `triggerPxType` | string | `last`/`index`/`mark` |
| `ordPx` | string-decimal | цена выставляемого ордера (`-1` = market) |

### Не маппится в snapshot / используется adapter'ом для validation

`ordType` (резолвится по conditionType — adapter сверяет),
`side`, `actualSide`, `tdMode` (=`isolated` константа),
`posSide` (=`net` константа), `reduceOnly` (invariant validation),
`closeFraction`.

### Диагностика / специфические режимы

`ccy`, `lever`, `quickMgnType`, `tag`, `clOrdId` (опц. связь с
обычным ордером), `last` («последняя цена при размещении»;
служебное), `amendPxOnTriggerType` (`0`/`1` cost-price SL для
split-TP), `tgtCcy` (SPOT market: `base_ccy`/`quote_ccy`).

Iceberg / TWAP (не используется bot'ом, поля приходят пустыми):
`pxVar`, `pxSpread`, `szLimit`, `pxLimit`, `timeInterval`.

Вложенный `attachAlgoOrds[*]` (встречается не во всех режимах):
`attachAlgoClOrdId`, `tp*Px`/`tp*PxType`/`tpOrdPx`, `sl*Px`/
`sl*PxType`/`slOrdPx`; расширенный вариант — `attachAlgoId`,
`tpOrdKind`, `failReason`.

## ACK ответы create/cancel

Сокращённый набор — `algoId`, `algoClOrdId`, `clOrdId` (deprecated),
`sCode`, `sMsg`, `tag`. Маппинг ACK в domain и semantics —
`docs/integrations/okx/contracts/algo-order.md`,
`docs/rules/ack-not-runtime-truth.md`.

## Конвертация

`empty string → null`; numeric string → `BigDecimal`; timestamp
string → epoch millis / `Instant`; `state` остаётся raw string при
выходе из источника.
