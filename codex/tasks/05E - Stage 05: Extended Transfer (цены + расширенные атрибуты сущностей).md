# Task 050E — Stage 05: Extended Transfer (цены + расширенные атрибуты сущностей)

Контекст: в сценарии reconcile явно есть шаг A4 «Перенос данных с биржи в БД (после counts-only)», но **без списка полей**. fileciteturn9file4

Эта таска расширяет Transfer (п.10 сценария) до «полезного минимума»: цены, размеры, статусы и ключевые атрибуты позиций/ордеров/algo-ордеров.

> Важно: это **не торговая логика**. Только обновление атрибутов в БД по snapshot.

---

## Цель

После выполнения counts-only (SYNC) обновлять в БД:

1. `Instrument` — актуальные цены (last/mark/index) + timestamp обновления
2. `PositionEntity` — размер/цены/плечо/маржинальные поля
3. `OrderEntity` — параметры заявки и прогресс исполнения
4. `AlgoOrderEntity` — параметры SL/TP/trigger/trailing

Все обновления:

* идемпотентны
* без дублей
* без секретов в логах

---

## 1) Расширение snapshot

### 1.1 Новая часть snapshot: tickers

Добавь в `ExchangeSnapshot`:

* `Map<String, ExternalTicker> tickersByInstId`

Создай модель `ExternalTicker` (минимум):

* `String instId`
* `String last`
* `String markPx` (если доступно)
* `String idxPx` (если доступно)
* `String ts` (если есть)

### 1.2 Snapshot provider

В `OkxExchangeSnapshotProvider.captureSnapshot()`:

* собрать `positions`, `ordersPending`, `algoOrdersPending`
* **дополнительно** собрать ticker для каждого `managedInstrument.instId`

> Если какой-то ticker не удалось получить — логируем WARN и продолжаем reconcile.

---

## 2) Миграции БД

### 2.1 V3 migration

Добавь `src/main/resources/db/migration/V3__reconcile_extended_transfer_fields.sql`.

#### 2.1.1 instrument

Добавить поля:

* `last_price VARCHAR(64) NULL`
* `mark_price VARCHAR(64) NULL`
* `index_price VARCHAR(64) NULL`
* `price_updated_at TIMESTAMPTZ NULL`

Индекс (опционально):

* `(price_updated_at)`

#### 2.1.2 position

Добавить поля:

* `pos VARCHAR(64) NULL`          -- размер позиции в контрактах (OKX positions.pos)
* `avg_px VARCHAR(64) NULL`
* `mark_px VARCHAR(64) NULL`
* `liq_px VARCHAR(64) NULL`
* `lever VARCHAR(32) NULL`
* `mgn_mode VARCHAR(16) NULL`     -- isolated/cross
* `upl VARCHAR(64) NULL`          -- unrealized PnL
* `u_time BIGINT NULL`            -- OKX uTime/cTime как ms, если есть

#### 2.1.3 order

Добавить поля:

* `state VARCHAR(32) NULL`        -- state из OKX
* `ord_type VARCHAR(32) NULL`
* `px VARCHAR(64) NULL`
* `sz VARCHAR(64) NULL`
* `fill_sz VARCHAR(64) NULL`
* `avg_px VARCHAR(64) NULL`
* `fee VARCHAR(64) NULL`
* `c_time BIGINT NULL`
* `u_time BIGINT NULL`

#### 2.1.4 algo_order

Добавить поля:

* `state VARCHAR(32) NULL`
* `algo_type VARCHAR(32) NULL`    -- уже есть, если нет — добавить
* `sz VARCHAR(64) NULL`
* `trigger_px VARCHAR(64) NULL`
* `ord_px VARCHAR(64) NULL`
* `tp_trigger_px VARCHAR(64) NULL`
* `tp_ord_px VARCHAR(64) NULL`
* `sl_trigger_px VARCHAR(64) NULL`
* `sl_ord_px VARCHAR(64) NULL`
* `callback_ratio VARCHAR(64) NULL`
* `callback_spread VARCHAR(64) NULL`
* `c_time BIGINT NULL`
* `u_time BIGINT NULL`

---

## 3) Обновление @Entity

Обнови persistence сущности:

* `InstrumentEntity`
* `PositionEntity`
* `OrderEntity`
* `AlgoOrderEntity`

Требования:

* Lombok без `@Data`.
* Nullable поля отмечаем явно.

---

## 4) Transfer service (расширение)

### 4.1 Новый сервис

Создай `ExchangeToDbExtendedTransferService` в `com.example.tradingbot.domain.service.reconcile`:

* `void transfer(Long exchangeId, Long instrumentId, ExchangeSnapshot snapshot, InstrumentBucket bucket)`

Вызывать **после** counts-only sync.

### 4.2 Правила обновления

#### Instrument

* если в snapshot есть ticker по instId → обновить:

    * `Instrument.lastPrice`, `markPrice`, `indexPrice`
    * `Instrument.priceUpdatedAt = nowUtc`

#### Position

Если на бирже `Positions.count == 1`:

* найти активную `PositionEntity` в БД по `(exchangeId,instrumentId)`
* обновить поля `pos/avgPx/markPx/liqPx/lever/mgnMode/upl/uTime` по snapshot

Если `Positions.count == 0`:

* ничего не «воскрешать»; закрытие делается в counts-only sync (SYNC-3/4).

#### Orders

Для каждого `ExternalOrder` из snapshot:

* найти `OrderEntity` по:

    * сначала `(exchangeId,instrumentId, clientOrderId=clOrdId)`
    * если clOrdId пустой → fallback по `exchangeOrderId=ordId` (только если уникально)
* обновить `exchangeOrderId/state/ordType/px/sz/fillSz/avgPx/fee/cTime/uTime`

#### AlgoOrders

Аналогично Orders:

* матчинг по `clientAlgoOrderId=algoClOrdId`, иначе fallback по `exchangeAlgoOrderId=algoId`
* обновить `state/algoType/...` поля

### 4.3 Идемпотентность

* Обновлять entity только если значение реально изменилось.
* Не создавать новые сущности кроме тех, что создаёт counts-only sync.

---

## 5) DataService API (минимальные методы)

В `persistence.service.*` добавь patch-методы:

* `InstrumentDataService.updatePricesIfChanged(exchangeId, instrumentId, last, mark, index, priceUpdatedAt)`
* `PositionDataService.applyExchangeSnapshot(...)`
* `OrderDataService.applyExchangeSnapshot(...)`
* `AlgoOrderDataService.applyExchangeSnapshot(...)`

> Внутри DataService допускается работать напрямую с Entity (т.к. это persistence слой).

---

## Ограничения

* Не добавлять новые REST endpoints.
* Не выполнять CancelFlow.
* Не тянуть trading-логику.

---

## DoD

* При запуске reconcile после SYNC обновляются цены инструмента и расширенные атрибуты сущностей.
* Повторный запуск не создаёт дублей и не меняет строки без реальных изменений.
* Миграция V3 применяется на чистой БД.
