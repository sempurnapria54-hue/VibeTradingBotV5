# AlgoOrderOkxResponse (OKX algo-order)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели условной заявки источника.

## Инвентарь полей

### Используемые

| OKX field | Тип | Семантика |
|---|---|---|
| `instId` | string | инструмент — адрес записи в счёт-широком срезе (`externalInstrumentId`) |
| `algoId` | string | биржевой algo id |
| `algoClOrdId` | string | client id (stable, основной матчинг) |
| `state` | string | сырой статус (`live`/`pause`/`effective`/`canceled`/`order_failed`/`partially_failed`/`partially_effective`) |
| `failCode` | string | код ошибки; «отказа нет» площадка отдаёт **кодом успеха `"0"`** (рантайм, `docs/integrations/okx/contracts/algo-order.md`) — граница переводит его в пусто (`docs/models/mapping/Order.md`) |
| `failReason` | string | диагностика отказа — операнд разбора у записи `state=order_failed`, объявленный формой цикла добычи (`docs/models/mapping/Order.md`); в колонку не садится, идёт в лог. Одноимённое поле **вложенного** `attachAlgoOrds[*]` — другое место и в перечень используемых не входит |
| `sz` | string-decimal | **объявленный** размер записи — операнд покрытия у материализованной встроенной защиты, найденной циклом добычи материализованной защиты (`docs/models/mapping/Order.md`; форма — `docs/spec/protection-coverage.json`) |
| `actualSz` | string-decimal | фактический размер срабатывания |
| `actualPx` | string-decimal | фактическая цена срабатывания |
| `triggerTime` | string-ms | время срабатывания |
| `ordId` | string | связанный обычный ордер (может быть пустым) |
| `ordIdList` | array<string> | список связанных `ordId` (split-сценарии) |
| `side` | string | `buy`/`sell` — эхо стороны; операнд сверки с нашим направлением у отдельной условной заявки (`docs/models/mapping/AlgoOrder.md`) |
| `reduceOnly` | string-bool | `true`/`false` — эхо признака «только уменьшать»; операнд сверки с намерением у отдельной условной заявки (`docs/integrations/okx/rules/reduce-only-invariant.md`). У материализованной встроенной защиты не используется — намерения у неё не объявлено |
| `cTime` | string-ms | время создания |
| `uTime` | string-ms | время обновления (есть в history) |
| **TP/SL поля:** | | |
| `tpTriggerPx` | string-decimal | TP trigger |
| `tpTriggerPxType` | string | `last`/`index`/`mark` — эхо базы; операнд сверки объявленной базы у отдельной условной заявки |
| `tpOrdPx` | string-decimal | TP order price (`-1` = market) |
| `slTriggerPx` | string-decimal | SL trigger |
| `slTriggerPxType` | string | `last`/`index`/`mark` — эхо базы; операнд сверки объявленной базы у обеих форм защиты |
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

### Не используется

`instType` и `ordType` (параметры запроса ног цикла, а не операнды
ответа), `actualSide`, `tdMode` (константа нашего запроса), `posSide`
(константа нашего запроса), `closeFraction` (механизм доли на первом этапе
не используется). Почему не сверяются тип заявки, режим маржи и сторона
позиции — `docs/models/mapping/AlgoOrder.md`, подраздел сверки эха.

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
