## Объектная модель + маппинг для `POST /api/v5/trade/order-algo`

Ниже — **клиентские модели** (request/response) + **доменная модель** для хранения в БД (без persistence‑аннотаций, но с audit‑полями) + **маппинг в YAML**.

> Примечание: enums `OrderSide`, `PositionSide`, `TradeMode`, `TriggerPriceType` можно **переиспользовать из твоей доменной модели `Order`**, чтобы не плодить дубли.

---

## 1) Клиентские модели (OKX)

### 1.1 DTO для `POST /api/v5/trade/order-algo`

### 1.1.1 `CreateAlgoOrderRequest`

```java
package com.example.tradingbot.client.okx.model.trade;

import lombok.Getter;
import lombok.Setter;

/**
 * OKX: создать алго-ордер (TP/SL/Trailing/Trigger).
 * Все числовые поля отправляем строками — как требует OKX.
 */
@Getter
@Setter
public class CreateAlgoOrderRequest {

    /** instId — инструмент, например ETH-USDT-SWAP. */
    private String instId;

    /** tdMode — isolated/cross/cash. */
    private String tdMode;

    /** side — buy/sell. */
    private String side;

    /** posSide — net/long/short (актуально для SWAP/FUTURES в long/short режиме). */
    private String posSide;

    /** ordType — conditional/oco/trigger/move_order_stop. */
    private String ordType;

    /** sz — размер. Для SWAP обычно в контрактах. */
    private String sz;

    /** closeFraction — доля позиции для закрытия (например "1" = 100%). */
    private String closeFraction;

    /** reduceOnly — true/false, чтобы ордер только уменьшал позицию. */
    private Boolean reduceOnly;

    /** ccy — валюта маржи (для USDT-SWAP обычно USDT). */
    private String ccy;

    /** tgtCcy — только для SPOT market: base_ccy/quote_ccy. */
    private String tgtCcy;

    /** algoClOrdId — твой client-id для алго-ордера. */
    private String algoClOrdId;

    /** tag — твой тег. */
    private String tag;

    // --- TP/SL (conditional / oco) ---

    /** tpTriggerPx — цена триггера тейк-профита. */
    private String tpTriggerPx;

    /** tpTriggerPxType — last/index/mark. */
    private String tpTriggerPxType;

    /** tpOrdPx — цена ордера TP; -1 = market. */
    private String tpOrdPx;

    /** slTriggerPx — цена триггера стоп-лосса. */
    private String slTriggerPx;

    /** slTriggerPxType — last/index/mark. */
    private String slTriggerPxType;

    /** slOrdPx — цена ордера SL; -1 = market. */
    private String slOrdPx;

    // --- Trigger (trigger) ---

    /** triggerPx — цена триггера. */
    private String triggerPx;

    /** triggerPxType — last/index/mark. */
    private String triggerPxType;

    /** orderPx — цена ордера; -1 = market. */
    private String orderPx;

    // --- Trailing (move_order_stop) ---

    /** callbackRatio — трейл в процентах (например 0.01 = 1%). */
    private String callbackRatio;

    /** callbackSpread — трейл в абсолютной цене. */
    private String callbackSpread;

    /** activePx — цена активации трейлинга (если не указать — активен сразу). */
    private String activePx;
}
```

### 1.1.2 `CreateAlgoOrderResponse`

```java
package com.example.tradingbot.client.okx.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAlgoOrderResponse {

    /** code — "0" если запрос принят. */
    private String code;

    /** msg — сообщение (обычно пусто при успехе). */
    private String msg;

    /** data — массив результатов (обычно 1 элемент). */
    private List<CreateAlgoOrderResult> data;

    @Getter
    @Setter
    public static class CreateAlgoOrderResult {

        /** algoId — ID алго-ордера на стороне OKX. */
        private String algoId;

        /** algoClOrdId — твой client-id (если передавал). */
        private String algoClOrdId;

        /** clOrdId — deprecated. */
        private String clOrdId;

        /** sCode — 0 если принято, иначе код отказа. */
        private String sCode;

        /** sMsg — текст отказа, если sCode != 0. */
        private String sMsg;

        /** tag — твой тег. */
        private String tag;
    }
}
```

### 1.2 DTO для `POST /api/v5/trade/cancel-algos`

### 1.2.1 `CancelAlgoOrderRequest`

```java
package com.example.tradingbot.client.okx.model.trade;

import lombok.Getter;
import lombok.Setter;

/**
 * OKX: отменить алго-ордер.
 */
@Getter
@Setter
public class CancelAlgoOrderRequest {

    /** instId — инструмент, например ETH-USDT-SWAP. */
    private String instId;

    /** algoId — ID алго-ордера на OKX. */
    private String algoId;

    /** algoClOrdId — наш client-id алго-ордера (опционально). */
    private String algoClOrdId;
}
```

### 1.2.2 `CancelAlgoOrderResponse`

```java
package com.example.tradingbot.client.okx.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelAlgoOrderResponse {

    private String code;

    private String msg;

    private List<Result> data;

    @Getter
    @Setter
    public static class Result {

        private String algoId;

        private String algoClOrdId;

        private String sCode;

        private String sMsg;
    }
}
```

---

## 2) Доменная модель

> Цель доменной модели: хранить у себя **намерение** (что мы хотели поставить) + **результат биржи** (algoId, коды) + audit/диагностику.

```java
package com.example.tradingbot.domain.model.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Алго-ордер на бирже (TP/SL/Trailing/Trigger).
 *
 * Простыми словами:
 * - это "правило", которое биржа исполняет сама,
 * - а не обычный лимит/маркет ордер.
 */
@Getter
@Setter
public class AlgoOrder {

    /** Внутренний идентификатор записи (если используешь). */
    private Long id;

    /** ID инструмента в нашей БД (справочник инструментов). */
    private Long instrumentId;

    /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
    private String exchangeInstrumentName;

    /** instType — тип инструмента (SPOT/MARGIN/SWAP/FUTURES/OPTION). */
    private Order.InstrumentType instrumentType;

    /** algoId — ID алго-ордера на OKX (появляется после успешного создания). */
    private String exchangeAlgoId;

    /** algoClOrdId — наш client-id алго-ордера (удобно для идемпотентности). */
    private String clientAlgoOrderId;

    /** tag — метка/тэг. */
    private String tag;

    /** ordType — тип алго-ордера. */
    private AlgoOrderType algoOrderType;

    /** side — buy/sell. */
    private Order.OrderSide side;

    /** posSide — net/long/short (важно для SWAP в long/short режиме). */
    private Order.PositionSide positionSide;

    /** tdMode — isolated/cross/cash. */
    private Order.TradeMode tradeMode;

    /** ccy — валюта маржи (для USDT-SWAP обычно USDT). */
    private String marginCurrency;

    /** reduceOnly — если true, то алго-ордер только закрывает/уменьшает позицию. */
    private Boolean reduceOnly;

    /** sz — размер (для SWAP обычно контракты). */
    private BigDecimal size;

    /** closeFraction — доля позиции, которую нужно закрыть (например 1 = 100%). */
    private BigDecimal closeFraction;

    // --------- TP/SL (conditional/oco) ---------

    /** TP: цена триггера тейк-профита. */
    private BigDecimal tpTriggerPrice;

    /** TP: тип цены триггера (LAST/INDEX/MARK). */
    private Order.TriggerPriceType tpTriggerPriceType;

    /** TP: цена исполнения; -1 = market (в запросе), у себя храним как число. */
    private BigDecimal tpOrderPrice;

    /** SL: цена триггера стоп-лосса. */
    private BigDecimal slTriggerPrice;

    /** SL: тип цены триггера (LAST/INDEX/MARK). */
    private Order.TriggerPriceType slTriggerPriceType;

    /** SL: цена исполнения; -1 = market. */
    private BigDecimal slOrderPrice;

    // --------- Trigger ---------

    /** Trigger: цена триггера. */
    private BigDecimal triggerPrice;

    /** Trigger: тип цены триггера. */
    private Order.TriggerPriceType triggerPriceType;

    /** Trigger: цена ордера; -1 = market. */
    private BigDecimal orderPrice;

    // --------- Trailing ---------

    /** Trailing: трейл в процентах (0.01 = 1%). */
    private BigDecimal callbackRatio;

    /** Trailing: трейл в абсолютной цене. */
    private BigDecimal callbackSpread;

    /** Trailing: цена активации трейлинга (если null — активен сразу). */
    private BigDecimal activePrice;

    // --------- Расширение (на будущее) ---------

    /** attachAlgoOrds — вложенные attach TP/SL (если используешь trigger+attach). */
    private List<Order.AttachedAlgoOrder> attachedAlgoOrders;

    // --------- Результат создания / диагностика ---------

    /** lastCreateResultCode — sCode из ответа биржи (0 = success). */
    private String lastCreateResultCode;

    /** lastCreateResultMessage — sMsg из ответа биржи. */
    private String lastCreateResultMessage;

    /** lastRequestId — наш внутренний id попытки/запроса (для ретраев). */
    private String lastRequestId;

    /** lastAttemptAt — когда последний раз пытались создать/обновить на бирже. */
    private Instant lastAttemptAt;

    /** exchangeProcessedAt — когда биржа приняла/обработала (если есть время из других ответов). */
    private Instant exchangeProcessedAt;

    // --------- Auditing (DB) ---------

    /** createdAt — когда запись создана в нашей БД. */
    private Instant createdAt;

    /** updatedAt — когда запись обновлена в нашей БД. */
    private Instant updatedAt;

    /** createdBy — кем создана (опционально). */
    private String createdBy;

    /** updatedBy — кем обновлена (опционально). */
    private String updatedBy;

    /** Результат последней CANCEL операции (data[0].sCode). */
    private String lastCancelResultCode;

    /** Сообщение последней CANCEL операции (data[0].sMsg). */
    private String lastCancelResultMessage;

    public enum AlgoOrderType {
        CONDITIONAL,
        OCO,
        TRIGGER,
        MOVE_ORDER_STOP
    }
}
```

---

## 3) Маппинг (YAML)

Формат: **поле в exchange модели : поле в доменной модели**  `# комментарий`

### 3.1 Создание: `AlgoOrder` → `CreateAlgoOrderRequest`

```yaml
# обязательные
instId: exchangeInstrumentName                # instId берем из доменной модели

# tdMode
# (в request это строка: isolated/cross/cash)
# в домене это enum TradeMode
#
# tradeMode: tradeMode                        # (в коде это маппинг enum -> lowercase)

tdMode: tradeMode                             # enum -> строка (isolated/cross/cash)
side: side                                     # enum BUY/SELL -> buy/sell
posSide: positionSide                          # enum NET/LONG/SHORT -> net/long/short
ordType: algoOrderType                         # enum -> conditional/oco/trigger/move_order_stop

# размеры
sz: size                                       # BigDecimal -> String
closeFraction: closeFraction                   # BigDecimal -> String

# риск/маржа
reduceOnly: reduceOnly                         # Boolean
ccy: marginCurrency                            # USDT и т.п.

# идентификация/тег
algoClOrdId: clientAlgoOrderId                 # client id
tag: tag                                       # tag

# TP

tpTriggerPx: tpTriggerPrice                    # BigDecimal -> String

tpTriggerPxType: tpTriggerPriceType            # enum -> last/index/mark

tpOrdPx: tpOrderPrice                          # BigDecimal -> String (-1 = market)

# SL

slTriggerPx: slTriggerPrice                    # BigDecimal -> String

slTriggerPxType: slTriggerPriceType            # enum -> last/index/mark

slOrdPx: slOrderPrice                          # BigDecimal -> String

# Trigger

triggerPx: triggerPrice                        # BigDecimal -> String

triggerPxType: triggerPriceType                # enum -> last/index/mark

orderPx: orderPrice                            # BigDecimal -> String (-1 = market)

# Trailing

callbackRatio: callbackRatio                   # BigDecimal -> String
callbackSpread: callbackSpread                 # BigDecimal -> String
activePx: activePrice                          # BigDecimal -> String

# Только для SPOT market (если вдруг нужно)
# tgtCcy: ...
```

### 3.2 Применение ответа: `CreateAlgoOrderResponse.data[0]` → `AlgoOrder`

```yaml
algoId: exchangeAlgoId                         # OKX algoId
algoClOrdId: clientAlgoOrderId                 # client id (если был)
tag: tag                                       # tag
sCode: lastCreateResultCode                    # 0 = success
sMsg: lastCreateResultMessage                  # текст ошибки
# lastAttemptAt: lastAttemptAt                 # ставим "сейчас" в момент обработки ответа
```

### 3.3 `AlgoOrder` → `CancelAlgoOrderRequest`

```yaml
exchangeInstrumentName: instId                      # instId
exchangeAlgoId: algoId                              # algoId
clientAlgoOrderId: algoClOrdId                      # опционально
```

### 3.4 `CancelAlgoOrderResponse.data[0]` → `AlgoOrder`

```yaml
algoId: exchangeAlgoId                              # сверка/обновление
algoClOrdId: clientAlgoOrderId                      # сверка
sCode: lastCancelResultCode                         # код результата отмены
sMsg: lastCancelResultMessage                       # сообщение
now(): lastAttemptAt                                # когда пытались отменить

```

---

## 4) Правила конвертации (чтобы было “рабочее”)

* Пустые строки `""` от биржи → `null`.
* Все числа в request отправляем строками; в домене храним `BigDecimal`.
* Enum → строка (в request):

    * `BUY -> buy`, `SELL -> sell`
    * `ISOLATED -> isolated`, `CROSS -> cross`, `CASH -> cash`
    * `NET -> net`, `LONG -> long`, `SHORT -> short`
    * `LAST -> last`, `INDEX -> index`, `MARK -> mark`
    * `CONDITIONAL -> conditional`, `OCO -> oco`, `TRIGGER -> trigger`, `MOVE_ORDER_STOP -> move_order_stop`
* `instrumentId` заполняем **не из OKX**, а из нашего справочника по `exchangeInstrumentName`.

---

## 5) Мини-заметка про переиспользование уже описанных моделей

* Enums можно переиспользовать из `Order`.
* Для `attachedAlgoOrders` можно переиспользовать `Order.AttachedAlgoOrder` (у тебя он уже описан).
