## Доменная сущность `Position` + маппинг

Ниже — **доменная модель для snapshot позиции + **маппинг в YAML**.

Особенность: `Position` хранит не только snapshot позиции с биржи (`GET /account/positions`), но и **состояние последней операции закрытия позиции** (`POST /trade/close-position`) — по аналогии с тем, как мы делали для `Order`.

---

## 1) Доменная сущность `Position`

```java
package com.example.tradingbot.domain.model.core.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Снимок состояния позиции по инструменту.
 *
 * Это доменная модель (без persistence-аннотаций).
 * Хранение в БД делается отдельным persistence-слоем.
 *
 * Важно:
 * - Все числа у OKX приходят строками → у нас BigDecimal.
 * - Пустые строки "" считаем как null.
 */
@Getter
@Setter
public class Position {

  // --------- Identity ---------

  /** Внутренний идентификатор записи в БД (если используешь). */
  private Long id;

  /** ID инструмента в нашей БД (справочник инструментов). */
  private Long instrumentId;

  /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
  private String exchangeInstrumentName;

  /** Тип инструмента: SWAP/FUTURES/OPTION/MARGIN. */
  private InstrumentType instrumentType;

  /** Режим маржи: ISOLATED/CROSS. */
  private MarginMode marginMode;

  /** Сторона позиции: NET/LONG/SHORT. */
  private PositionSide positionSide;

  /** Валюта маржи/обеспечения (обычно USDT). */
  private String marginCurrency;

  /** posId — id позиции на бирже (удобно для даунтайма/сверки). */
  private String exchangePosId;

  /** tradeId — id последней сделки по позиции (для корреляции и даунтайма). */
  private String lastTradeId;

  // --------- Size / Prices ---------

  /** pos — размер позиции в контрактах. В NET-режиме знак может означать направление. */
  private BigDecimal positionContracts;

  /** avgPx — средняя цена входа. */
  private BigDecimal averagePrice;

  /** markPx — mark price (главная цена для рисков). */
  private BigDecimal markPrice;

  /** last — last price (последняя цена сделки; чаще для отображения). */
  private BigDecimal lastPrice;

  /** bePx — цена безубытка. */
  private BigDecimal breakEvenPrice;

  // --------- Risk / Margin ---------

  /** lever — плечо (например 10). */
  private BigDecimal leverage;

  /** liqPx — расчётная цена ликвидации. */
  private BigDecimal liquidationPrice;

  /** margin — маржа в позиции (особенно важно для isolated). */
  private BigDecimal positionMargin;

  /** notionalUsd — номинал позиции в USD (размер позиции в деньгах). */
  private BigDecimal notionalUsd;

  /** mgnRatio — margin ratio (насколько близко к риску). */
  private BigDecimal marginRatio;

  /** mmr — maintenance margin requirement. */
  private BigDecimal maintenanceMargin;

  /** adl — индикатор ADL 0..5 (как строка в OKX). */
  private String adl;

  // --------- PnL / Fees ---------

  /** upl — плавающий PnL (unrealized). */
  private BigDecimal unrealizedPnl;

  /** uplRatio — upl в относительном виде. */
  private BigDecimal unrealizedPnlRatio;

  /** realizedPnl — реализованный PnL (если биржа его даёт в этом snapshot). */
  private BigDecimal realizedPnl;

  /** fundingFee — суммарный funding (важно для SWAP). */
  private BigDecimal fundingFee;

  /** fee — комиссии. */
  private BigDecimal fee;

  // --------- Attached TP/SL (если есть) ---------

  /**
   * closeOrderAlgo[] — прикреплённые к позиции алгоритмические ордера закрытия (TP/SL и т.п.).
   *
   * Важно: API отдаёт массив, поэтому храним список.
   */
  private List<CloseAlgoOrder> closeAlgoOrders;

  // --------- Close-position operation (по аналогии с Order) ---------

  /**
   * externalStatus — сырой статус/ярлык, который мы можем хранить "как есть".
   *
   * Пример:
   * - "open" (позиция есть)
   * - "close_requested" (мы отправили close-position)
   * - "close_accepted" (биржа приняла)
   * - "close_rejected" (биржа отказала)
   * - "closed" (подтверждено snapshot-ом, что позиции нет)
   */
  private String externalStatus;

  /** status — наш доменный статус позиции/операции закрытия. */
  private Status status;

  /** autoCancelOnClose — мы закрывали с autoCxl=true/false. */
  private Boolean autoCancelOnClose;

  /** lastRequestId — наш id последней попытки закрытия (для корреляции/ретраев). */
  private String lastRequestId;

  /** lastAttemptAt — когда последняя попытка close-position была отправлена. */
  private Instant lastAttemptAt;

  /** lastCloseResultCode — code из ответа close-position (верхний уровень). */
  private String lastCloseResultCode;

  /** lastCloseResultMessage — msg из ответа close-position (верхний уровень). */
  private String lastCloseResultMessage;

  /** exchangeProcessedAt — когда мы считаем, что биржа "реально" обработала закрытие (обычно ставим после reconcile). */
  private Instant exchangeProcessedAt;

  // --------- Exchange timestamps ---------

  /** cTime — время создания позиции на бирже (ms -> Instant). */
  private Instant sourceCreatedAt;

  /** uTime — время последнего обновления позиции на бирже (ms -> Instant). */
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

  /**
   * Описание одного элемента массива closeOrderAlgo[].
   * Обычно содержит TP/SL параметры (если они выставлены как algo/attached).
   */
  @Getter
  @Setter
  public static class CloseAlgoOrder {

    /** algoId — id algo-ордера. */
    private String algoId;

    /** tpTriggerPx — цена-триггер тейк-профита (если есть). */
    private BigDecimal tpTriggerPrice;

    /** tpTriggerPxType — тип цены для TP: LAST/INDEX/MARK. */
    private TriggerPriceType tpTriggerPriceType;

    /** slTriggerPx — цена-триггер стоп-лосса (если есть). */
    private BigDecimal slTriggerPrice;

    /** slTriggerPxType — тип цены для SL: LAST/INDEX/MARK. */
    private TriggerPriceType slTriggerPriceType;

    /** closeFraction — доля закрытия (1 = 100%). */
    private BigDecimal closeFraction;
  }

  public enum Status {
    /** Позиция есть (pos != 0). */
    OPEN,

    /** Мы отправили close-position. */
    CLOSE_REQUESTED,

    /** Биржа приняла запрос close-position (ответ code=0). */
    CLOSE_ACCEPTED,

    /** Биржа отказала в close-position (ответ code!=0). */
    CLOSE_REJECTED,

    /** Подтверждено snapshot-ом, что позиции нет / pos=0. */
    CLOSED
  }

  public enum InstrumentType {
    SWAP,
    FUTURES,
    OPTION,
    MARGIN
  }

  public enum MarginMode {
    ISOLATED,
    CROSS
  }

  public enum PositionSide {
    NET,
    LONG,
    SHORT
  }

  public enum TriggerPriceType {
    LAST,
    INDEX,
    MARK
  }
}
```

---

## 2) Маппинг (YAML): OKX `GET /api/v5/account/positions` → `Position`

```yaml
# identity / modes
instId: exchangeInstrumentName                 # имя инструмента на бирже (OKX instId)
# instrumentId: instrumentId                   # ID инструмента в нашей БД — заполняем из справочника по exchangeInstrumentName
instType: instrumentType                       # SWAP/FUTURES/OPTION/MARGIN -> enum (UPPERCASE)
mgnMode: marginMode                            # isolated/cross -> enum (ISOLATED/CROSS)
posSide: positionSide                          # net/long/short -> enum (NET/LONG/SHORT)
ccy: marginCurrency                            # валюта маржи/обеспечения (обычно USDT)
posId: exchangePosId                           # id позиции на бирже (полезно для даунтайма)
tradeId: lastTradeId                           # последняя сделка по позиции

# size / prices
pos: positionContracts                         # размер позиции в контрактах; строка -> BigDecimal
avgPx: averagePrice                            # средняя цена входа; строка -> BigDecimal
markPx: markPrice                              # mark price; строка -> BigDecimal
last: lastPrice                                # last price; строка -> BigDecimal
bePx: breakEvenPrice                           # цена безубытка; строка -> BigDecimal

# risk / margin
lever: leverage                                # плечо; строка -> BigDecimal
liqPx: liquidationPrice                        # цена ликвидации; строка -> BigDecimal
margin: positionMargin                         # маржа в позиции (isolated); строка -> BigDecimal
notionalUsd: notionalUsd                       # номинал позиции в USD; строка -> BigDecimal
mgnRatio: marginRatio                          # насколько близко к риску; строка -> BigDecimal
mmr: maintenanceMargin                         # maintenance margin; строка -> BigDecimal
adl: adl                                       # ADL шкала 0..5 (приходит строкой)

# pnl / fees
upl: unrealizedPnl                             # плавающий PnL; строка -> BigDecimal
uplRatio: unrealizedPnlRatio                   # доля/процент; строка -> BigDecimal
realizedPnl: realizedPnl                       # реализованный PnL; строка -> BigDecimal
fundingFee: fundingFee                         # funding fee (SWAP); строка -> BigDecimal
fee: fee                                       # комиссии; строка -> BigDecimal

# attached close algo orders (массив -> список)
closeOrderAlgo: closeAlgoOrders                # массив объектов closeOrderAlgo[] -> List<CloseAlgoOrder>
closeOrderAlgo[*].algoId: closeAlgoOrders[*].algoId                       # id algo-ордера
closeOrderAlgo[*].tpTriggerPx: closeAlgoOrders[*].tpTriggerPrice          # TP trigger price; строка -> BigDecimal
closeOrderAlgo[*].tpTriggerPxType: closeAlgoOrders[*].tpTriggerPriceType  # last/index/mark -> enum (LAST/INDEX/MARK)
closeOrderAlgo[*].slTriggerPx: closeAlgoOrders[*].slTriggerPrice          # SL trigger price; строка -> BigDecimal
closeOrderAlgo[*].slTriggerPxType: closeAlgoOrders[*].slTriggerPriceType  # last/index/mark -> enum (LAST/INDEX/MARK)
closeOrderAlgo[*].closeFraction: closeAlgoOrders[*].closeFraction         # доля закрытия; строка -> BigDecimal

# exchange timestamps
cTime: sourceCreatedAt                         # ms -> Instant
uTime: sourceUpdatedAt                         # ms -> Instant
```

---

## 3) Маппинг (YAML): `Position` → OKX `POST /api/v5/trade/close-position` (request)

> Для реквеста мы берём поля из `Position`.

```yaml
instId: exchangeInstrumentName                 # инструмент
mgnMode: marginMode                            # ISOLATED/CROSS -> isolated/cross
posSide: positionSide                          # NET/LONG/SHORT -> net/long/short
ccy: marginCurrency                            # обычно USDT
autoCxl: autoCancelOnClose                     # true/false
```

---

## 4) Маппинг (YAML): OKX `close-position` (response) → `Position` (обновляем поля операции)

Ответ `close-position` — это **ack** (приняли/отказали). Финальное состояние позиции подтверждаем отдельным snapshot-ом.

```yaml
# фиксируем, что мы делали попытку
now(): lastAttemptAt

# результат ответа
response.code: lastCloseResultCode
response.msg: lastCloseResultMessage

# доменный статус по ack
# if response.code == "0" -> status = CLOSE_ACCEPTED
# else -> status = CLOSE_REJECTED

# внешний статус (для дебага/читаемости)
# if response.code == "0" -> externalStatus = "close_accepted"
# else -> externalStatus = "close_rejected"
```

---

## 5) Подтверждение факта закрытия (reconcile)

**Источник истины:** `GET /api/v5/account/positions?instId=...`

Правила:

* если позиция **отсутствует** в списке **или** `pos == 0` →

    * `status = CLOSED`
    * `externalStatus = "closed"`
    * `exchangeProcessedAt = now()` (или время с биржи/WS, если есть)

Примечание:

* Даже если `close-position` вернул ошибку, позиция могла быть уже закрыта (TP/SL/ликвидация/вручную). Поэтому reconcile — обязателен.

---

## 6) Правила конвертации (коротко, чтобы маппинг был “рабочим”)

* Пустые строки `""` → `null` (для BigDecimal/Instant/Enum).
* Время `cTime/uTime` (миллисекунды строкой) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* Enum поля приводить к верхнему регистру: `isolated -> ISOLATED`, `mark -> MARK`, и т.д.
* `instrumentId` берём **не из OKX**, а из нашего справочника инструментов по `exchangeInstrumentName`.
