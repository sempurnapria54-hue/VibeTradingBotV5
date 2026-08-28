# OKX contracts: batch-операции по ордерам

## На какой вопрос отвечает этот файл

Каков контракт batch-операций OKX по ordinary order (place / cancel /
amend пакетом): endpoint'ы, лимиты, поэлементный ACK, атомарность.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Trade», секции «POST / Place multiple
orders», «POST / Cancel multiple orders», «POST / Amend multiple
orders»). При расхождении с офдоком побеждает офдок; синхронизация —
перевыкачка + дифф при каждом заходе интегратора по источнику и по
задаче «актуализируй» (`.claude/processes/api-docs-completion.md`,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется: command-layer держит гранулярность «одна команда —
одна сущность». Док — покрытие
продуктового периметра, не план внедрения.

## Endpoints

- **Place batch:** `POST /api/v5/trade/batch-orders`. Permission
  `Trade`; rate limit 300 **orders** / 2 s по User ID + Instrument ID
  (Options — User ID + Instrument Family). Лимит считается числом
  ордеров, не запросов; запрос с одним ордером расходует лимит
  одиночного place. До 20 ордеров за запрос, исполняются по очереди
  (офдок: «Orders will be placed in turn»). Тело — массив элементов с
  полями одиночного place order (`instId`, `tdMode`, `side`,
  `posSide`, `ordType`, `sz`, `px`, `clOrdId`, `reduceOnly`,
  `attachAlgoOrds` и т. д. — состав полей: `order.md`,
  `OkxOrderResponse.md`).
- **Cancel batch:** `POST /api/v5/trade/cancel-batch-orders`.
  Permission `Trade`; rate limit 300 orders / 2 s (правила как у
  place batch); до 20 за запрос. Элемент: `instId` + одно из `ordId`
  / `clOrdId` (оба → биржа берёт `ordId`).
- **Amend batch:** `POST /api/v5/trade/amend-batch-orders`.
  Permission `Trade`; rate limit 300 orders / 2 s; до 20 за запрос.
  Элемент — поля одиночного amend: `instId`, `ordId`/`clOrdId`,
  `reqId`, `newSz`/`newPx`, `cxlOnFail`, `pxAmendType`,
  `attachAlgoOrds` (вкл. `newCallbackRatio`/`newCallbackSpread`/
  `newActivePx` attached-trailing). `newSz` — новый **полный** размер
  с учётом исполненной части.

## Атомарность и ACK

- Офдок фиксирует атомарность только для **Portfolio Margin**: «либо
  все ордера приняты, либо все отклонены». Вне PM исходы поэлементные.
- ACK поэлементный: `sCode`/`sMsg` (+ `subCode` у place) per
  `data[i]`; top-level `code`/`msg` — статус запроса; `inTime`/
  `outTime` — времена REST-шлюза (микросекунды). ACK не runtime truth
  (`docs/rules/ack-not-runtime-truth.md`).
