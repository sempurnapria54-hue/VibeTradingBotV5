# OKX contracts: algo-order

## На какой вопрос отвечает этот файл

Каков контракт операций по algo-ордеру.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Algo Trading», секции «POST / Place
algo order», «POST / Cancel algo order», «POST / Amend algo order»,
«GET / Algo order details», «GET / Algo order list», «GET / Algo
order history»; changelog — `https://www.okx.com/docs-v5/log_en/`).
При расхождении с офдоком побеждает офдок; синхронизация —
перевыкачка + дифф при каждом заходе интегратора по источнику и по
задаче «актуализируй» (`.claude/processes/api-docs-completion.md`,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (cancel/amend/query поле-уровнево,
симметрия advance-семейства).

**Рантайм-расхождение (2026-06-20, провенанс `рантайм`):**
`cancel-advance-algos` отсутствует в офдоке (delisted 2025-04-24), но
**жив на demo** — подтверждён живым прогоном контура source-api
(см. находку И-2 ниже). Офдок не подменяется: расхождение офдок↔биржа
фиксируется этим провенансом.

## Endpoints

- **Create** (`SUBMIT_ALGO_ORDER_COMMAND`): `POST /api/v5/trade/order-algo`.
  Permission `Trade`; rate limit 20 req / 2 s по User ID + Instrument
  ID. Body — общие поля (`instId`, `tdMode`, `side`, `ordType`, `sz`,
  `posSide`, `reduceOnly`, `algoClOrdId`) + ordType-specific.
- **Amend** (доменом **не используется** — REPLACE-only,
  `docs/rules/replace-not-amend.md`; контракт — поверхность
  биржи): `POST /api/v5/trade/amend-algos`. Permission `Trade`; rate
  limit 20 req / 2 s по User ID + Instrument ID.
  **Только Stop/Trigger-ордера** — офдок («POST / Amend algo
  order»): «Support Stop order and Trigger order only, not including
  Move_order_stop order, Iceberg order, TWAP order, Trailing Stop
  order» — advance-семья (вкл. standalone trailing) **не амендится**
  (находка И-3 ниже). Body: `instId` (обяз.), `algoId` /
  `algoClOrdId` (одно обяз.), `cxlOnFail`, `reqId`, `newSz`;
  TP/SL-ветка: `newTpTriggerPx`/`newTpOrdPx`/`newSlTriggerPx`/
  `newSlOrdPx`/`new*TriggerPxType` (`0` = удалить ногу); trigger:
  `newTriggerPx`/`newOrdPx`/`newTriggerPxType` + `attachAlgoOrds`.
- **Cancel** (`CANCEL_ALGO_ORDER_COMMAND`): `POST /api/v5/trade/cancel-algos`.
  Permission `Trade`; rate limit 20 **orders** / 2 s по User ID +
  Instrument ID. Body — массив `{ instId, algoId | algoClOrdId }`
  (оба → биржа берёт `algoId`), до 10 за запрос. Ответ `data[i]`:
  `algoId`, `sCode`, `sMsg` (`clOrdId`/`algoClOrdId`/`tag` —
  deprecated). Отказ через `sCode != 0` (algo уже
  сработал/закрыт/отменён/не найден). Покрытие advance-семьи этим
  endpoint'ом — конфликт внутри офдока, см. находку **И-2** ниже;
  исторический парный endpoint `cancel-advance-algos` выведен из
  официальной документации (changelog 2025-04-24).
- **Details** (`REFRESH_ALGO_ORDER_COMMAND`): `GET /api/v5/trade/order-algo`.
  Permission `Read`. Query: одно из `algoId` (приоритет) /
  `algoClOrdId`; `instId` опц. Ответ — массив `data`, ожидается 0 или
  1 элемент.
- **Pending** (звено цикла `REFRESH_ALGO_ORDER_COMMAND`): `GET /api/v5/trade/orders-algo-pending`.
  Permission `Read`. Фильтры по `ordType`, `instType`, `instId`,
  `algoId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.
  **Применяется и счёт-широко** (`instType=SWAP`, `instId` не задаётся):
  срез живых algo счёта читает проактивная детекция
  (`docs/components/AnomalyJob.md`), и складывается он из **вызова на
  семью** — контур запрашивает `conditional`, `oco` и `move_order_stop`
  по отдельности, а `limit` задаёт явно потолком страницы, чтобы усечение
  было наблюдаемым.

  **Две величины этого пункта не сверены с источником и помечены.**
  (а) **Обязательность `ordType`.** Контур обращается к эндпоинту так,
  будто параметр обязателен (отсюда вызов на семью), и на этом стои́т
  клейм дома детекции; здесь он числится **фильтром**. Носители
  расходятся, и ни один из них рантайм-фактом не подтверждён.
  (б) **Лимит частоты.** У соседних счёт-широких эндпоинтов он назван
  (`orders-pending` — 60 req / 2 s, `account/positions` — 10 req / 2 s,
  оба по User ID); здесь не назван вовсе, а клейм дома детекции говорит
  «у всех трёх по User ID». Обе величины — предмет сверки контура;
  задача — `.claude/tests/source-api/okx/code-preconditions.md`.
- **History** (звено цикла `REFRESH_ALGO_ORDER_COMMAND`):
  `GET /api/v5/trade/orders-algo-history`. Permission `Read`; rate
  limit 20 req / 2 s по User ID. История доступна за последние 3
  месяца. Query: **`ordType` обязателен** (вычисляется из
  `conditionType`); + одно из `state` (`effective`/`canceled`/
  `order_failed` — `partially_failed` из текущего офдока ушёл) или `algoId`; опц. `instType`,
  `instId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.

### Видимость advance-семьи в query (симметрия, офдок)

`ordType`-фильтры **pending** и **history** принимают обе семьи:
`conditional` / `oco` / `trigger` / `chase` (новый тип, FUTURES/SWAP)
/ `move_order_stop` / `iceberg` / `twap` / `smart_iceberg` —
advance-algo **виден** в тех же query-звеньях; details (`order-algo`)
работает по `algoId`/`algoClOrdId` без типового ограничения (ответ
несёт `szLimit`/`pxLimit` для iceberg/twap, `advanceOrdType`).
Evidence-cycle `REFRESH_ALGO_ORDER_COMMAND` для trailing не ломается.

## ACK-семантика

ACK любой create/cancel (`sCode=0`) не runtime truth
(`docs/rules/ack-not-runtime-truth.md`). `CANCEL_ALGO_ORDER_COMMAND` не
ставит `CANCELED`. Submit использует stable client id
(`internalId → algoClOrdId`); перед retry — refresh/search по
`algoClOrdId`.

### Create response (ACK)

`POST /trade/order-algo` → `data[0]` с `algoId`, `algoClOrdId`,
`clOrdId` (deprecated), `sCode`, `sMsg`, `tag`.

### Cancel response (ACK)

`POST /trade/cancel-algos` → `data[0]` с `algoId`, `sCode`, `sMsg`
(`algoClOrdId` / `clOrdId` / `tag` в cancel-ответе помечены офдоком
deprecated). Для advance-ветки (`cancel-advance-algos`, И-1(а))
форма ответа исторически та же; текущим офдоком не специфицирована
(И-2).

## ordType-specific create body

- `conditional` — TP **или** SL (если оба в `conditional` в
  net-режиме, биржа может проигнорировать TP — для одновременных
  TP+SL использовать `oco`).
- `oco` — TP и SL вместе; срабатывание одного отменяет другой.
- `trigger` — `triggerPx` + `orderPx` (`-1` = market) + опц.
  `triggerPxType` (default `last`). Активы при постановке обычно
  **не морозятся** (проверка баланса в момент срабатывания).
- `move_order_stop` — trailing: ровно одно из `callbackRatio`
  (`0.01` = 1%) / `callbackSpread` (абсолют); опц. `activePx` — без
  него трейлинг включается сразу. Расчёт триггера: long → min +
  spread/ratio; short → max − spread/ratio.

`closeFraction` (доля позиции при срабатывании, `1` = 100%) на
первом этапе не используем. Для protective обычно `reduceOnly=true`;
для SWAP рекомендуется `tpTriggerPxType=mark`.

## Ветвление cancel-пути по семье (И-1 закрыт — исход (а))

**Решение (пользователь, 2026-06-11):** `CANCEL_ALGO_ORDER_COMMAND` ветвит
cancel-путь по семье algo — **ordinary** (trigger / oco /
conditional) → `cancel-algos`; **advance** (trailing
`move_order_stop` / iceberg / twap) → `cancel-advance-algos`.
Исполнитель выбирает endpoint по семье отменяемого algo
(`conditionType → ordType → семья`, маппинг —
`docs/models/mapping/AlgoOrder.md`).

### Отмена advance-семьи: endpoint вне офдока, но живой

**Факт офдока.** `cancel-advance-algos` исключён из документации
2025-04-24 и в текущем доке отсутствует. Нормативный текст «Cancel algo
order» ограничения по семье не несёт, но пример той же секции
называет iceberg / twap / trailing исключениями — страница противоречит
себе.

**Рантайм (2026-06-20, demo, контур source-api; провенанс `рантайм`).**
Endpoint жив: постановка `move_order_stop` (и долевой, и абсолютный
откат) с последующей отменой через `cancel-advance-algos` вернула успех;
несуществующий идентификатор — реджект `51293`.

**Следствие.** Ветвление по семье остаётся; расхождение
офдок ↔ биржа держится на провенансе `рантайм`, офдок не
подменяется (`.claude/rules/external-source-sync.md`). Отменяет ли
`cancel-algos` также и advance-семью — не проверено; корректность
ветвления это не блокирует.

### Amend advance-семьи биржей не поддерживается

`amend-algos` нормативно поддерживает только Stop/Trigger; standalone
`move_order_stop` / iceberg / twap не амендятся. Доменного слоя это не
касается: ремоделирование идёт только через замещение
(`docs/rules/replace-not-amend.md`).

## Evidence-cycle

Полный цикл: `GET /trade/order-algo` → `orders-algo-pending` →
`orders-algo-history`. Подробно — в `mapping/AlgoOrder.md`
evidence-cycle.
