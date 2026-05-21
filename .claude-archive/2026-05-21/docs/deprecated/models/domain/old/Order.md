## Доменная модель `Order` + клиентские DTO `CreateOrder*` + маппинг

Ниже — **единая доменная сущность `Order`**, которая подходит сразу для нескольких эндпоинтов:

* `GET /api/v5/trade/orders-pending` — открытые ордера (snapshot)
* `GET /api/v5/trade/order` — детали ордера (snapshot)
* `POST /api/v5/trade/order` — создать ордер (запрос/ответ)

Идея такая:

* **Параметры ордера** (что мы хотели поставить) храним в `Order`.
* **Состояние на бирже** храним в `externalStatus` (как строка «как пришло от OKX»).
* **Наш доменный статус** (для бизнес-логики, ретраев и восстановления) храним в `status` (enum `Status`).
* Для надежности добавляем поля **последней попытки** и **результата create**.

---

## 1) Доменная сущность `Order`

```java
package com.example.tradingbot.domain.model.core.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Универсальная доменная сущность ордера.
 *
 * Простыми словами:
 * - Это «наша копия» ордера.
 * - В ней есть и то, что мы хотели отправить (intent),
 *   и то, что реально есть/было на бирже (snapshot).
 *
 * Важно:
 * - У OKX многие числа приходят строками → у нас BigDecimal.
 * - Пустые строки "" считаем как null.
 */
@Getter
@Setter
public class Order {

  // --------- Идентификаторы (наши / биржи) ---------

  /** Внутренний идентификатор записи в БД (если используешь). */
  private Long id;

  /** ID инструмента в нашей БД (справочник инструментов). */
  private Long instrumentId;

  /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
  private String exchangeInstrumentName;

  /** Тип инструмента: SPOT/MARGIN/SWAP/FUTURES/OPTION. */
  private InstrumentType instrumentType;

  /** ordId — ID ордера на стороне OKX (появится после успешного create). */
  private String exchangeOrderId;

  /** clOrdId — наш client order id (задаём сами при создании). */
  private String clientOrderId;

  /** tag — метка/тэг (если передавали). */
  private String tag;

  /** category — категория ордера (часто "normal"). */
  private String category;

  // --------- Два статуса: внешний (OKX) и наш доменный ---------

  /**
   * Внешний статус ордера как пришёл от OKX (строка).
   * Примеры: live / partially_filled / filled / canceled / mmp_canceled / ...
   */
  private String externalStatus;

  /** Наш доменный статус для бизнес-логики (ретраи, восстановление, решения). */
  private Status status;

  // --------- Поля для надежности (последняя попытка + результат create) ---------

  /** Последний requestId/корреляция в нашем приложении (удобно для логов). */
  private String lastRequestId;

  /** Когда мы последний раз пытались отправить запрос (создать/изменить). */
  private Instant lastAttemptAt;

  /** Результат последнего CREATE на уровне OKX (data[0].sCode). */
  private String lastCreateResultCode;

  /** Сообщение последнего CREATE на уровне OKX (data[0].sMsg). */
  private String lastCreateResultMessage;

  /** Когда биржа обработала запрос (data[0].ts, ms -> Instant). */
  private Instant exchangeProcessedAt;

  // --------- Сторона / режим позиции / режим торговли ---------

  /** side — buy/sell. */
  private OrderSide side;

  /** posSide — net/long/short (зависит от режима позиций). */
  private PositionSide positionSide;

  /** tdMode — режим торговли: isolated/cross/cash. */
  private TradeMode tradeMode;

  /** ccy — валюта маржи/обеспечения (для USDT‑SWAP обычно USDT). */
  private String marginCurrency;

  /** lever — плечо. */
  private BigDecimal leverage;

  /** reduceOnly — true/false: ордер только уменьшает позицию. */
  private Boolean reduceOnly;

  // --------- Параметры ордера (intent / snapshot) ---------

  /** ordType — тип ордера (limit/market/post_only/ioc/fok и т.д.). */
  private String orderType;

  /** px — цена (для limit). Для market часто пусто. */
  private BigDecimal price;

  /** sz — размер ордера. Для SWAP это обычно контракты. */
  private BigDecimal size;

  /** tgtCcy — только для SPOT market: base_ccy или quote_ccy. */
  private String targetCurrencyMode;

  // --------- Исполнение (snapshot) ---------

  /** accFillSz — сколько уже исполнено (накопленно). */
  private BigDecimal accumulatedFillSize;

  /** fillPx — цена последнего исполнения (если было). */
  private BigDecimal lastFillPrice;

  /** fillSz — размер последнего исполнения. */
  private BigDecimal lastFillSize;

  /** fillTime — время последнего исполнения (ms -> Instant). */
  private Instant lastFillTime;

  /** avgPx — средняя цена исполнения. */
  private BigDecimal averageFillPrice;

  /** tradeId — ID последней сделки по этому ордеру. */
  private String lastTradeId;

  // --------- Комиссии / ребейты / PnL ---------

  /** fee — комиссия (часто отрицательная, если уже были сделки). */
  private BigDecimal fee;

  /** feeCcy — валюта комиссии. */
  private String feeCurrency;

  /** rebate — ребейт (возврат) для maker‑сделок (если применимо). */
  private BigDecimal rebate;

  /** rebateCcy — валюта ребейта. */
  private String rebateCurrency;

  /** pnl — PnL без учёта комиссии (часто 0/пусто). */
  private BigDecimal pnl;

  // --------- TP/SL (прикреплённые к ордеру) ---------

  /** attachAlgoClOrdId — наш client id для прикреплённых TP/SL (если задавали). */
  private String attachAlgoClientOrderId;

  /** tpTriggerPx — триггер TP (если TP прикреплён напрямую). */
  private BigDecimal tpTriggerPrice;

  /** tpTriggerPxType — тип цены триггера TP: last/index/mark. */
  private TriggerPriceType tpTriggerPriceType;

  /** tpOrdPx — цена исполнения TP (для limit‑TP). */
  private BigDecimal tpOrderPrice;

  /** slTriggerPx — триггер SL (если SL прикреплён напрямую). */
  private BigDecimal slTriggerPrice;

  /** slTriggerPxType — тип цены триггера SL: last/index/mark. */
  private TriggerPriceType slTriggerPriceType;

  /** slOrdPx — цена исполнения SL. */
  private BigDecimal slOrderPrice;

  /** attachAlgoOrds[] — список прикреплённых algo‑ордеров (детали TP/SL). */
  private List<AttachedAlgoOrder> attachedAlgoOrders;

  /** linkedAlgoOrd.algoId — связанный algoId (например в OCO), если есть. */
  private String linkedAlgoId;

  /** algoId — algo ID (может быть пустым, пока не triggered). */
  private String algoId;

  /** algoClOrdId — client algo id (если задавали). */
  private String algoClientOrderId;

  /** isTpLimit — true/false: это TP‑limit или нет. */
  private Boolean tpLimit;

  // --------- Self-trade prevention ---------

  /** stpMode — режим защиты от самоторговли (пример: cancel_maker). */
  private String selfTradePreventionMode;

  // --------- Источник / отмена ---------

  /** source — откуда появился ордер (код строкой). */
  private String source;

  /** cancelSource — кто/что отменило ордер (код строкой), если отменён. */
  private String cancelSource;

  /** cancelSourceReason — причина отмены (если биржа дала). */
  private String cancelSourceReason;

  // --------- Прочее + timestamps ---------

  /** tradeQuoteCcy — котируемая валюта торговли (например USDT). */
  private String tradeQuoteCurrency;

  /** cTime — время создания ордера на бирже (ms -> Instant). */
  private Instant sourceCreatedAt;

  /** uTime — время последнего обновления ордера на бирже (ms -> Instant). */
  private Instant sourceUpdatedAt;

  // --------- Auditing (DB) ---------

  /** createdAt — когда запись создана в нашей БД. */
  private Instant createdAt;

  /** updatedAt — когда запись обновлена в нашей БД. */
  private Instant updatedAt;

  /** createdBy — кем создана (опционально). */
  private String createdBy;

  /** updatedBy — кем обновлена (опционально). */
  private String updatedBy;

  // --------- Nested models / enums ---------

  /** Детализация одного элемента массива attachAlgoOrds[]. */
  @Getter
  @Setter
  public static class AttachedAlgoOrder {

    /** attachAlgoId — ID прикреплённого algo‑ордера на OKX. */
    private String attachAlgoId;

    /** attachAlgoClOrdId — наш client id прикреплённого algo‑ордера. */
    private String attachAlgoClientOrderId;

    /** tpOrdKind — вид TP: condition или limit. */
    private String tpOrderKind;

    /** tpTriggerPx — триггер TP. */
    private BigDecimal tpTriggerPrice;

    /** tpTriggerRatio — триггер TP в процентах (например 0.3 = 30%). */
    private BigDecimal tpTriggerRatio;

    /** tpTriggerPxType — тип цены триггера TP: last/index/mark. */
    private TriggerPriceType tpTriggerPriceType;

    /** tpOrdPx — цена TP. */
    private BigDecimal tpOrderPrice;

    /** slTriggerPx — триггер SL. */
    private BigDecimal slTriggerPrice;

    /** slTriggerRatio — триггер SL в процентах (например 0.3 = 30%). */
    private BigDecimal slTriggerRatio;

    /** slTriggerPxType — тип цены триггера SL: last/index/mark. */
    private TriggerPriceType slTriggerPriceType;

    /** slOrdPx — цена SL. */
    private BigDecimal slOrderPrice;

    /** sz — размер (актуально для split‑TP, когда тейки дробятся). */
    private BigDecimal size;

    /** amendPxOnTriggerType — Cost‑price SL (0/1). */
    private String amendPriceOnTriggerType;

    /** failCode — код ошибки, если не удалось выставить/изменить. */
    private String failCode;

    /** failReason — причина ошибки (если есть). */
    private String failReason;

    /** Результат последней CANCEL операции (data[0].sCode). */
    private String lastCancelResultCode;

    /** Сообщение последней CANCEL операции (data[0].sMsg). */
    private String lastCancelResultMessage;
  }

  public enum Status {
    /** Запись создана у нас, но на биржу ещё не отправляли. */
    NEW,

    /** Мы отправили create (или собираемся отправить) — "заявка ушла". */
    CREATE_REQUESTED,

    /** Биржа приняла create (есть ordId и sCode=0). */
    CREATE_ACCEPTED,

    /** Биржа отклонила create (sCode!=0). */
    CREATE_REJECTED,

    /** Биржа подтверждает, что ордер активен. */
    LIVE,

    /** Ордер частично исполнен. */
    PARTIALLY_FILLED,

    /** Ордер полностью исполнен. */
    FILLED,

    /** Ордер отменён (в т.ч. mmp_canceled). */
    CANCELED,

    /** Мы не смогли корректно обработать/обновить ордер. */
    FAILED,

    /** Неизвестно (например, пришёл новый статус, который мы ещё не учли). */
    UNKNOWN,

    CANCEL_REQUESTED,
    CANCEL_ACCEPTED,
    CANCEL_REJECTED
  }

  public enum InstrumentType {
    SPOT,
    MARGIN,
    SWAP,
    FUTURES,
    OPTION
  }

  public enum OrderSide {
    BUY,
    SELL
  }

  public enum PositionSide {
    NET,
    LONG,
    SHORT
  }

  public enum TradeMode {
    ISOLATED,
    CROSS,
    CASH
  }

  public enum TriggerPriceType {
    LAST,
    INDEX,
    MARK
  }
}
```

---

## 2) Клиентские модели

> Это **клиентский слой** (DTO для REST), не доменный.

### 2.1 DTO для `POST /api/v5/trade/order`

#### 2.1.1 `CreateOrderRequest`

```java
package com.example.tradingbot.client.okx.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    private String instId;
    private String tdMode;
    private String ccy;

    private String clOrdId;
    private String tag;

    private String side;
    private String posSide;

    private String ordType;
    private String sz;
    private String px;

    private Boolean reduceOnly;

    private String tgtCcy;
    private Boolean banAmend;
    private String pxAmendType;

    private String tradeQuoteCcy;
    private String stpMode;

    private List<AttachAlgoOrder> attachAlgoOrds;

    @Getter
    @Setter
    public static class AttachAlgoOrder {

        private String attachAlgoClOrdId;

        private String tpTriggerPx;
        private String tpTriggerRatio;
        private String tpOrdPx;
        private String tpOrdKind;
        private String tpTriggerPxType;

        private String slTriggerPx;
        private String slTriggerRatio;
        private String slOrdPx;
        private String slTriggerPxType;

        private String sz;
        private String amendPxOnTriggerType;
    }
}
```

### 2.1.2 `CreateOrderResponse`

```java
package com.example.tradingbot.client.okx.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderResponse {

    private String code;
    private String msg;
    private List<DataItem> data;

    private String inTime;
    private String outTime;

    @Getter
    @Setter
    public static class DataItem {

        private String ordId;
        private String clOrdId;
        private String tag;

        private String ts;

        private String sCode;
        private String sMsg;
    }
}
```

### 2.2 DTO для `POST /api/v5/trade/amend-order`

#### 2.2.1 `AmendOrderRequest`

```java
package com.example.tradingbot.client.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AmendOrderRequest {

    private String instId;
    private Boolean cxlOnFail;

    private String ordId;
    private String clOrdId;

    private String reqId;

    private String newSz;

    private String newPx;

    private String newPxUsd;

    private String newPxVol;

    private String pxAmendType;

    private List<AmendAttachedAlgoOrder> attachAlgoOrds;

    @Getter
    @Setter
    public static class AmendAttachedAlgoOrder {

        private String attachAlgoId;

        private String attachAlgoClOrdId;

        private String newTpTriggerPx;

        private String newTpTriggerRatio;

        private String newTpOrdPx;

        private String newTpOrdKind;

        private String newSlTriggerPx;

        private String newSlTriggerRatio;

        private String newSlOrdPx;

        private String newTpTriggerPxType;

        private String newSlTriggerPxType;

        private String sz;

        private String amendPxOnTriggerType;
    }
}
```

#### 2.2.2 `AmendOrderResponse`

```java
package com.example.tradingbot.client.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AmendOrderResponse {

    private String code;

    private String msg;

    private List<Result> data;

    private String inTime;

    private String outTime;

    @Getter
    @Setter
    public static class Result {

        private String ordId;

        private String clOrdId;

        private String ts;

        private String reqId;

        private String sCode;

        private String sMsg;
    }
}
```

### 2.3 DTO для `POST /api/v5/trade/cancel-order`

#### 2.3.1 `CancelOrderRequest`

```java
package com.example.tradingbot.client.model.trade;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderRequest {

    /** instId — инструмент, например ETH-USDT-SWAP. */
    private String instId;

    /** ordId — ID ордера OKX. */
    private String ordId;

    /** clOrdId — наш client order id. */
    private String clOrdId;
}
```

#### 2.3.2 `CancelOrderResponse`

```java
package com.example.tradingbot.client.model.trade;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderResponse {

    private String code;

    private String msg;

    private List<Result> data;

    private String inTime;

    private String outTime;

    @Getter
    @Setter
    public static class Result {

        private String ordId;

        private String clOrdId;

        private String sCode;

        private String sMsg;
    }
}
```

---

## 3) Маппинг (YAML)

### 3.1 OKX `GET /api/v5/trade/orders-pending` → доменная `Order`

```yaml
# инструмент
instId: exchangeInstrumentName                       # имя инструмента на бирже (OKX instId)
# instrumentId: instrumentId                         # ID инструмента в нашей БД — заполняем из справочника по exchangeInstrumentName
instType: instrumentType                             # SPOT/MARGIN/SWAP/FUTURES/OPTION -> enum (UPPERCASE)

# идентификаторы
ordId: exchangeOrderId                               # ID ордера OKX
clOrdId: clientOrderId                               # наш client order id

tag: tag                                             # тэг/метка
category: category                                   # строка, обычно "normal"

# внешний статус
state: externalStatus                                # как пришло: live/partially_filled/...

# сторона / режим
side: side                                           # buy/sell -> enum (BUY/SELL)
posSide: positionSide                                # net/long/short -> enum (NET/LONG/SHORT)
tdMode: tradeMode                                    # isolated/cross/cash -> enum (ISOLATED/CROSS/CASH)

ccy: marginCurrency                                  # валюта маржи
lever: leverage                                      # плечо; строка -> BigDecimal
reduceOnly: reduceOnly                               # "true"/"false" -> Boolean

# параметры ордера
ordType: orderType                                   # тип ордера (оставляем строкой)
px: price                                            # цена; строка -> BigDecimal
sz: size                                             # размер; строка -> BigDecimal
tgtCcy: targetCurrencyMode                           # только для spot market

# исполнение
accFillSz: accumulatedFillSize                       # сколько уже исполнено
fillPx: lastFillPrice                                # цена последнего исполнения
fillSz: lastFillSize                                 # размер последнего исполнения
fillTime: lastFillTime                               # ms -> Instant
avgPx: averageFillPrice                              # средняя цена исполнения
tradeId: lastTradeId                                 # id последней сделки по ордеру

# комиссии / ребейты / pnl
fee: fee                                             # комиссия
feeCcy: feeCurrency                                  # валюта комиссии
rebate: rebate                                       # ребейт
rebateCcy: rebateCurrency                            # валюта ребейта
pnl: pnl                                             # pnl (без fee)

# TP/SL (attach)
attachAlgoClOrdId: attachAlgoClientOrderId           # client id для attach TP/SL

tpTriggerPx: tpTriggerPrice                          # TP trigger
tpTriggerPxType: tpTriggerPriceType                  # last/index/mark -> enum (LAST/INDEX/MARK)
tpOrdPx: tpOrderPrice                                # TP order price

slTriggerPx: slTriggerPrice                          # SL trigger
slTriggerPxType: slTriggerPriceType                  # last/index/mark -> enum
slOrdPx: slOrderPrice                                # SL order price

attachAlgoOrds: attachedAlgoOrders                   # массив attachAlgoOrds[] -> List<AttachedAlgoOrder>
attachAlgoOrds[*].attachAlgoId: attachedAlgoOrders[*].attachAlgoId
attachAlgoOrds[*].attachAlgoClOrdId: attachedAlgoOrders[*].attachAlgoClientOrderId
attachAlgoOrds[*].tpOrdKind: attachedAlgoOrders[*].tpOrderKind
attachAlgoOrds[*].tpTriggerPx: attachedAlgoOrders[*].tpTriggerPrice
attachAlgoOrds[*].tpTriggerRatio: attachedAlgoOrders[*].tpTriggerRatio
attachAlgoOrds[*].tpTriggerPxType: attachedAlgoOrders[*].tpTriggerPriceType
attachAlgoOrds[*].tpOrdPx: attachedAlgoOrders[*].tpOrderPrice
attachAlgoOrds[*].slTriggerPx: attachedAlgoOrders[*].slTriggerPrice
attachAlgoOrds[*].slTriggerRatio: attachedAlgoOrders[*].slTriggerRatio
attachAlgoOrds[*].slTriggerPxType: attachedAlgoOrders[*].slTriggerPriceType
attachAlgoOrds[*].slOrdPx: attachedAlgoOrders[*].slOrderPrice
attachAlgoOrds[*].sz: attachedAlgoOrders[*].size
attachAlgoOrds[*].amendPxOnTriggerType: attachedAlgoOrders[*].amendPriceOnTriggerType
attachAlgoOrds[*].failCode: attachedAlgoOrders[*].failCode
attachAlgoOrds[*].failReason: attachedAlgoOrders[*].failReason

linkedAlgoOrd.algoId: linkedAlgoId                   # связанный algo id (если есть)

algoId: algoId                                       # algo id (может быть пусто)
algoClOrdId: algoClientOrderId                       # client algo id
isTpLimit: tpLimit                                   # "true"/"false" -> Boolean

# stp
stpMode: selfTradePreventionMode                     # режим STP

# источник/отмена
source: source                                       # откуда ордер появился
cancelSource: cancelSource                           # кто отменил
cancelSourceReason: cancelSourceReason               # причина отмены

# прочее + timestamps
tradeQuoteCcy: tradeQuoteCurrency                    # котируемая валюта
cTime: sourceCreatedAt                               # ms -> Instant
uTime: sourceUpdatedAt                               # ms -> Instant
```

### 3.2 Доменная `Order` → `CreateOrderRequest`

```yaml
# доменная Order -> client CreateOrderRequest

exchangeInstrumentName: instId                       # OKX instId
tradeMode: tdMode                                    # isolated/cross/cash
marginCurrency: ccy                                  # обычно null/"" для USDT-SWAP

clientOrderId: clOrdId                               # твой client id
tag: tag                                             # метка

side: side                                           # BUY/SELL -> buy/sell
positionSide: posSide                                # NET/LONG/SHORT -> net/long/short

orderType: ordType                                   # limit/market/...
size: sz                                             # BigDecimal -> string
price: px                                            # BigDecimal -> string (только если нужно для ordType)

reduceOnly: reduceOnly                               # Boolean

selfTradePreventionMode: stpMode                     # строка, если используешь

a ttachAlgoClientOrderId: attachAlgoOrds[*].attachAlgoClOrdId # см. ниже (attach)

# attach TP/SL
attachedAlgoOrders: attachAlgoOrds
attachedAlgoOrders[*].attachAlgoClientOrderId: attachAlgoOrds[*].attachAlgoClOrdId
attachedAlgoOrders[*].tpTriggerPrice: attachAlgoOrds[*].tpTriggerPx
attachedAlgoOrders[*].tpTriggerRatio: attachAlgoOrds[*].tpTriggerRatio
attachedAlgoOrders[*].tpOrderPrice: attachAlgoOrds[*].tpOrdPx
attachedAlgoOrders[*].tpOrderKind: attachAlgoOrds[*].tpOrdKind
attachedAlgoOrders[*].tpTriggerPriceType: attachAlgoOrds[*].tpTriggerPxType
attachedAlgoOrders[*].slTriggerPrice: attachAlgoOrds[*].slTriggerPx
attachedAlgoOrders[*].slTriggerRatio: attachAlgoOrds[*].slTriggerRatio
attachedAlgoOrders[*].slOrderPrice: attachAlgoOrds[*].slOrdPx
attachedAlgoOrders[*].slTriggerPriceType: attachAlgoOrds[*].slTriggerPxType
attachedAlgoOrders[*].size: attachAlgoOrds[*].sz
attachedAlgoOrders[*].amendPriceOnTriggerType: attachAlgoOrds[*].amendPxOnTriggerType
```

> Примечание: строка `a ttachAlgoClientOrderId...` выше — просто напоминание, что `attachAlgoClOrdId` берём из `attachAlgoClientOrderId`. Если не нужно — удали.

### 3.3 `CreateOrderResponse` → доменная `Order` (обновление полей после create)

```yaml
# CreateOrderResponse -> доменная Order (обновляем существующую запись)

data[0].ordId: exchangeOrderId                       # ID ордера OKX

data[0].clOrdId: clientOrderId                       # сверка

data[0].tag: tag                                     # сверка/обновление

data[0].ts: exchangeProcessedAt                      # ms -> Instant

data[0].sCode: lastCreateResultCode                  # результат create

data[0].sMsg: lastCreateResultMessage                # текст результата create

# статус (правило)
# if sCode != "0" -> status = CREATE_REJECTED
# else -> status = CREATE_ACCEPTED
```

### 3.4 Domain `Order` + "что меняем" → `AmendOrderRequest`

```yaml
# идентификация
order.exchangeInstrumentName: request.instId                 # instId
order.exchangeOrderId: request.ordId                         # ordId (предпочтительнее)
# order.clientOrderId: request.clOrdId                       # clOrdId (если ordId неизвестен)

# служебное
amend.reqId: request.reqId                                   # reqId (корреляция)
amend.cancelOnFail: request.cxlOnFail                         # cxlOnFail
amend.priceAmendType: request.pxAmendType                     # pxAmendType

# что меняем
amend.newPrice: request.newPx                                 # newPx (строкой)
amend.newSize: request.newSz                                  # newSz (строкой)

# TP/SL (если меняем)
amend.attachedAlgoAmends: request.attachAlgoOrds              # attachAlgoOrds[]
```

### 3.5 `AmendOrderResponse` → обновление доменного `Order` (служебные поля)

```yaml
response.data[0].reqId: order.lastRequestId                   # последний request id
response.data[0].ts: order.exchangeProcessedAt                # когда биржа обработала запрос (ms -> Instant)
response.data[0].sCode: order.lastOperationResultCode         # код результата (рекомендация: обобщить)
response.data[0].sMsg: order.lastOperationResultMessage       # сообщение
now(): order.lastAttemptAt                                    # когда мы делали попытку

# Важно: price/size/state НЕ обновляем только по этому ответу.
# Их обновляем по WS order channel или GET /trade/order.
```

### 3.6 Доменная `Order` → `CancelOrderRequest`

```yaml
exchangeInstrumentName: instId                       # instId
exchangeOrderId: ordId                               # отменяем по ordId (если есть)
# clientOrderId: clOrdId                             # если ordId неизвестен, отменяем по clOrdId
```

### 3.7 `CancelOrderResponse` → доменная `Order` (обновление служебных полей)

```yaml
# попытка отмены (это делаем до запроса)
requestId: order.lastRequestId                       # например UUID
now(): order.lastAttemptAt                           # когда попытались отменить
order.status: CANCEL_REQUESTED                       # доменный статус

# результат (после ответа)
response.data[0].sCode: order.lastCancelResultCode
response.data[0].sMsg: order.lastCancelResultMessage

# правило статуса
# if sCode == "0" -> status = CANCEL_ACCEPTED
# else -> status = CANCEL_REJECTED

# Важно: externalStatus не меняем только по cancel-ответу.
# Реальную отмену подтверждаем через WS orders или GET /trade/order:
# - если externalStatus стало "canceled" -> status = CANCELED
```

---

## 4) Правила конвертации и статуса

### 4.1 Общие правила конвертации

* Пустые строки `""` → `null` (для BigDecimal/Instant/Boolean/Enum).
* Числа приходят строками → `BigDecimal`.
* Время `cTime/uTime/fillTime/ts` (миллисекунды строкой) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* Enum приводим к верхнему регистру:

  * `buy -> BUY`, `sell -> SELL`
  * `isolated -> ISOLATED`, `cross -> CROSS`, `cash -> CASH`
  * `net/long/short -> NET/LONG/SHORT`
  * `last/index/mark -> LAST/INDEX/MARK`
* `instrumentId` берём **не из OKX**, а из нашего справочника инструментов по `exchangeInstrumentName`.

### 4.2 Как обновлять `status` на основе `externalStatus`

* Если `externalStatus == "live"` → `status = LIVE`
* Если `externalStatus == "partially_filled"` → `status = PARTIALLY_FILLED`
* Если `externalStatus == "filled"` → `status = FILLED`
* Если `externalStatus == "canceled"` или `"mmp_canceled"` → `status = CANCELED`
* Любое другое значение → `status = UNKNOWN` (и сохраняем как есть в `externalStatus`)

### 4.3 Как обновлять `status` после `POST /trade/order`

* Перед отправкой: `status = CREATE_REQUESTED`, `lastAttemptAt = now`, `lastRequestId = <uuid>`
* После ответа:

  * если `data[0].sCode == "0"` → `status = CREATE_ACCEPTED` и сохраняем `exchangeOrderId`
  * если `data[0].sCode != "0"` → `status = CREATE_REJECTED`

> После `CREATE_ACCEPTED` не считается, что ордер «точно live». Реальный жизненный цикл подтверждаем snapshot-запросами (`orders-pending`, `trade/order`) или WS `orders`.

