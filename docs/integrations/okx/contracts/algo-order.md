# OKX contracts: algo-order

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций по algo-ордеру: endpoint'ы, лимиты,
ACK-семантика, ordType-specific body, evidence-cycle, ветвление
cancel-пути по семье algo.

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
сверка: 2026-06-11 (прогон 3 — cancel/amend/query поле-уровнево,
симметрия advance-семейства).

**Рантайм-расхождение (2026-06-20, провенанс `рантайм`):**
`cancel-advance-algos` отсутствует в офдоке (delisted 2025-04-24), но
**жив на demo** — подтверждён живым прогоном контура source-api
(см. находку И-2 ниже). Офдок не подменяется: расхождение офдок↔биржа
фиксируется этим провенансом.

## Контекст

Mapping в `AlgoOrder` — `docs/models/mapping/AlgoOrder.md` (раздел
`## OKX`). Native response/request поля —
`docs/models/integrations/okx/OkxAlgoOrderResponse.md`. Правила OKX —
`docs/integrations/okx/rules/`. Доменные модель/lifecycle —
`docs/models/domain/core/AlgoOrder.md` / `docs/lifecycles/AlgoOrder.md`.

## Endpoints

- **Create** (`SUBMIT_ALGO_ORDER`): `POST /api/v5/trade/order-algo`.
  Permission `Trade`; rate limit 20 req / 2 s по User ID + Instrument
  ID. Body — общие поля (`instId`, `tdMode`, `side`, `ordType`, `sz`,
  `posSide`, `reduceOnly`, `algoClOrdId`) + ordType-specific.
- **Amend** (доменом **не используется** — REPLACE-only,
  `docs/decisions/replace-not-amend.md`; контракт — поверхность
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
- **Cancel** (`CANCEL_ALGO_ORDER`): `POST /api/v5/trade/cancel-algos`.
  Permission `Trade`; rate limit 20 **orders** / 2 s по User ID +
  Instrument ID. Body — массив `{ instId, algoId | algoClOrdId }`
  (оба → биржа берёт `algoId`), до 10 за запрос. Ответ `data[i]`:
  `algoId`, `sCode`, `sMsg` (`clOrdId`/`algoClOrdId`/`tag` —
  deprecated). Отказ через `sCode != 0` (algo уже
  сработал/закрыт/отменён/не найден). Покрытие advance-семьи этим
  endpoint'ом — конфликт внутри офдока, см. находку **И-2** ниже;
  исторический парный endpoint `cancel-advance-algos` выведен из
  официальной документации (changelog 2025-04-24).
- **Details** (`REFRESH_ALGO_ORDER`): `GET /api/v5/trade/order-algo`.
  Permission `Read`. Query: одно из `algoId` (приоритет) /
  `algoClOrdId`; `instId` опц. Ответ — массив `data`, ожидается 0 или
  1 элемент.
- **Pending** (звено цикла `REFRESH_ALGO_ORDER`): `GET /api/v5/trade/orders-algo-pending`.
  Permission `Read`. Фильтры по `ordType`, `instType`, `instId`,
  `algoId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.
- **History** (звено цикла `REFRESH_ALGO_ORDER`):
  `GET /api/v5/trade/orders-algo-history`. Permission `Read`; rate
  limit 20 req / 2 s по User ID. История доступна за последние 3
  месяца. Query: **`ordType` обязателен** (вычисляется из
  `conditionType`); + одно из `state` (`effective`/`canceled`/
  `order_failed` — `partially_failed` из текущего офдока ушёл,
  дрейф зафиксирован прогоном 3) или `algoId`; опц. `instType`,
  `instId`, пагинация `after`/`before` по `algoId`, `limit` ≤ 100.

### Видимость advance-семьи в query (симметрия, офдок)

`ordType`-фильтры **pending** и **history** принимают обе семьи:
`conditional` / `oco` / `trigger` / `chase` (новый тип, FUTURES/SWAP)
/ `move_order_stop` / `iceberg` / `twap` / `smart_iceberg` —
advance-algo **виден** в тех же query-звеньях; details (`order-algo`)
работает по `algoId`/`algoClOrdId` без типового ограничения (ответ
несёт `szLimit`/`pxLimit` для iceberg/twap, `advanceOrdType`).
Evidence-cycle `REFRESH_ALGO_ORDER` для trailing не ломается.

## ACK-семантика

ACK любой create/cancel (`sCode=0`) не runtime truth
(`docs/rules/ack-not-runtime-truth.md`). `CANCEL_ALGO_ORDER` не
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

**Решение (пользователь, 2026-06-11):** `CANCEL_ALGO_ORDER` ветвит
cancel-путь по семье algo — **ordinary** (trigger / oco /
conditional) → `cancel-algos`; **advance** (trailing
`move_order_stop` / iceberg / twap) → `cancel-advance-algos`.
Исполнитель выбирает endpoint по семье отменяемого algo
(`conditionType → ordType → семья`, маппинг —
`docs/models/mapping/AlgoOrder.md`).

**Основание** — продуктовый факт: стратегия предусматривает
trailing-защиту (`TrailingSettings`, `ConditionType.TRAILING_*` —
`docs/models/domain/aggregate/Strategy.md`,
`docs/models/domain/core/AlgoOrder.md`); сужение скоупа до ordinary
(прежний крен (б)) с этим несовместимо.

### Находка И-2 (прогон 3): cancel-advance-algos выведен из офдока

Свежая сверка живого офдока (2026-06-11) меняет фактуру прогона 2:

- **Changelog 2025-04-24:** «Delisted endpoints from the document —
  Cancel Advance Algo Order». В текущем доке `cancel-advance-algos`
  отсутствует (0 упоминаний на странице); добавлен был ~2021-09/10
  вместе с WS-каналом advance algo.
- **Конфликт внутри страницы:** нормативный текст «POST / Cancel
  algo order» ограничения по семье **не несёт** («Cancel unfilled
  algo orders»), но Python-пример той же секции комментирует
  «not including Iceberg order, TWAP order, Trailing Stop order».
- Фактура прогона 2 («две семьи cancel», офдок-провенанс через
  okx.com-поиск) опиралась, по-видимому, на устаревший
  индексированный контент.

**Следствие для решения (а) — провалидировано (пользователь,
2026-06-11, принято без правки по существу):** ветвление (а) стоит
как есть; advance-ветка сохраняет пометку «endpoint вне текущего
офдока». Снятие И-2 — **runtime-проверка в demo trading**
(постановка + отмена `move_order_stop` через `cancel-algos`) на
`CODE` шага 4. Оговорка по исполнению: кредов demo trading пока нет
— проверка ждёт их появления (креды — за пользователем); до
проверки документальная фактура прогона 3 принимается как
достоверная. Если `cancel-algos` отменит обе семьи — ветвление
вырождается в один путь; если нет — advance-путь остаётся на
выведенном из дока (но исторически рабочем) endpoint'е.

**Снятие И-2 — рантайм-подтверждено (2026-06-20, demo, контур
source-api, кейсы M19tr/M19trs/M21; провенанс `рантайм` /
`подтверждён-прогоном`):** `cancel-advance-algos` **жив на demo вопреки
офдоку**. Постановка `move_order_stop` (`callbackRatio` и абсолютный
`callbackSpread`) + отмена через `cancel-advance-algos` вернули
`b.code="0"`, `data[0].sCode="0"` — endpoint рабочий. Фейк-`algoId` →
`b.code="1"`, `data[0].sCode="51293"` «The bot doesn't exist or has
already stopped». **Вывод:** advance-ветвь решения (а) (`cancel-advance-algos`
для trailing) — рабочая; ветвление по семье остаётся как есть, оговорка
«ждёт demo-кредов» снята. Endpoint остаётся вне офдока (delisted
2025-04-24), но подтверждён живым — расхождение офдок↔биржа держится на
провенансе `рантайм`, офдок не подменяется (канон `external-source-sync`;
тот же канон — `cancel-advance-algos` в манифесте покрытия). Вырождение
ветвления (отменяет ли `cancel-algos` и advance-семью) в этом прогоне не
проверялось — отдельный вопрос, корректность ветвления (а) не блокирует.

### Находка И-3 (прогон 3) — следствие закрыто решением REPLACE-only

Биржевой факт: `amend-algos` нормативно поддерживает только
Stop/Trigger; standalone `move_order_stop` / iceberg / twap **не
амендятся** (см. Endpoints → Amend). Следствие закрыто
(`GAPS_CLOSE_3`, 2026-06-11): домен не амендит **ничего** —
ремоделирование любой сущности идёт REPLACE-оркестрацией
(`docs/decisions/replace-not-amend.md`); амендная асимметрия биржи
перестала касаться доменного слоя (исторически И-3 — один из
триггеров выбора REPLACE-only).

## Evidence-cycle

Полный цикл: `GET /trade/order-algo` → `orders-algo-pending` →
`orders-algo-history`. Подробно — в `mapping/AlgoOrder.md` §OKX
evidence-cycle.
