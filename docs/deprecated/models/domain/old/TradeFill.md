## Доменная модель + маппинг

### 1) Доменная сущность `TradeFill`

```java
package com.example.tradingbot.domain.model.core.exchange;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * TradeFill — одна сделка (одно исполнение ордера).
 *
 * Простыми словами:
 * - Ордер мог исполниться кусками → будет несколько TradeFill.
 * - По ним мы понимаем реальный факт торговли: цена, объём, комиссия, время.
 */
@Getter
@Setter
public class TradeFill {

  /** Внутренний идентификатор записи в БД (если используешь). */
  private Long id;

  /** ID инструмента в нашей БД (справочник инструментов). */
  private Long instrumentId;

  /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
  private String exchangeInstrumentName;

  /** Тип инструмента: SPOT/MARGIN/SWAP/FUTURES/OPTION. */
  private InstrumentType instrumentType;

  /** ordId — ID ордера на стороне OKX, который породил сделку. */
  private String exchangeOrderId;

  /** clOrdId — наш client order id (если задавали). */
  private String clientOrderId;

  /** tradeId — ID сделки на стороне OKX. */
  private String exchangeTradeId;

  /** billId — внутренний ID записи (удобен для пагинации after/before). */
  private String billId;

  /** tag — метка/тэг (если передавали). */
  private String tag;

  /** fillPx — цена сделки. */
  private BigDecimal price;

  /** fillSz — объём сделки. Для SWAP обычно контракты. */
  private BigDecimal size;

  /** side — buy/sell. */
  private OrderSide side;

  /** posSide — net/long/short (если биржа вернула). */
  private PositionSide positionSide;

  /** execType — maker/taker по ликвидности. */
  private ExecutionType executionType;

  /** feeCcy — валюта комиссии. */
  private String feeCurrency;

  /** fee — комиссия за сделку (часто отрицательная). */
  private BigDecimal fee;

  /** ts — время сделки на бирже. */
  private Instant sourceTradeTime;

  // --------- Auditing (DB) ---------

  /** createdAt — когда запись создана в нашей БД. */
  private Instant createdAt;

  /** updatedAt — когда запись обновлена в нашей БД. */
  private Instant updatedAt;

  /** createdBy — кем создана (опционально). */
  private String createdBy;

  /** updatedBy — кем обновлена (опционально). */
  private String updatedBy;

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

  /** Maker/Taker. */
  public enum ExecutionType {
    MAKER,
    TAKER
  }
}
```

---

### 2) Маппинг (YAML): OKX `GET /trade/fills` → `TradeFill`

```yaml
# инструмент
instId: exchangeInstrumentName                       # имя инструмента на бирже (OKX instId)
# instrumentId: instrumentId                         # ID инструмента в нашей БД — берём из справочника по exchangeInstrumentName
instType: instrumentType                             # SPOT/MARGIN/SWAP/FUTURES/OPTION -> enum (UPPERCASE)

# идентификаторы
ordId: exchangeOrderId                               # ID ордера OKX
clOrdId: clientOrderId                               # наш client order id
tradeId: exchangeTradeId                             # ID сделки OKX
billId: billId                                       # ID записи для пагинации

tag: tag                                             # тэг/метка

# цена/объём
fillPx: price                                        # строка -> BigDecimal
fillSz: size                                         # строка -> BigDecimal

# сторона
side: side                                           # buy/sell -> enum (BUY/SELL)
posSide: positionSide                                # net/long/short -> enum (NET/LONG/SHORT)

# maker/taker
execType: executionType                              # T/M -> enum (TAKER/MAKER)

# комиссия
feeCcy: feeCurrency                                  # валюта комиссии
fee: fee                                             # строка -> BigDecimal

# время
ts: sourceTradeTime                                  # ms -> Instant
```

**Правила конвертации (коротко):**

* Пустые строки `""` → `null`.
* Числа приходят строками → `BigDecimal`.
* Время `ts` (миллисекунды строкой) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* Enum:

    * `buy -> BUY`, `sell -> SELL`
    * `SWAP -> SWAP` (и т.д. в UPPERCASE)
    * `net/long/short -> NET/LONG/SHORT`
    * `execType: T -> TAKER`, `M -> MAKER`

---
