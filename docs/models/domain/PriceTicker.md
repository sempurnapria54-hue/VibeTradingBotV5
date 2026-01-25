## Доменная модель для `market/ticker` + маппинг (REST и WS)

Ниже — доменная модель `PriceTicker` (без persistence-аннотаций, но с audit-полями) + YAML-маппинг.

Эта модель одинаково подходит для:

* REST `GET /api/v5/market/ticker?instId=...`
* WS public channel `tickers`

---

## 1) Доменная сущность `PriceTicker`

```java
package com.example.tradingbot.domain.model.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Снимок цены (тикер) по инструменту.
 *
 * Простыми словами:
 * - last = последняя цена сделки
 * - bid/ask = лучшие цены покупки/продажи в стакане
 *
 * Можно хранить в БД, если хочешь аудит или бэктест исполнения,
 * но для live-бота чаще достаточно держать в памяти (кэш).
 */
@Getter
@Setter
public class PriceTicker {

    /** Внутренний идентификатор записи в БД (если используешь). */
    private Long id;

    /** ID инструмента в нашей БД (справочник инструментов). */
    private Long instrumentId;

    /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
    private String exchangeInstrumentName;

    /** Тип инструмента: SPOT/MARGIN/SWAP/FUTURES/OPTION. */
    private String exchangeInstrumentType;

    // --------- Основные цены ---------

    /** last — последняя цена сделки. */
    private BigDecimal lastPrice;

    /** lastSz — размер последней сделки (в базовой валюте или контрактах — зависит от инструмента). */
    private BigDecimal lastSize;

    /** bidPx — лучшая цена покупки (лучший bid). */
    private BigDecimal bestBidPrice;

    /** bidSz — объём на лучшем bid. */
    private BigDecimal bestBidSize;

    /** askPx — лучшая цена продажи (лучший ask). */
    private BigDecimal bestAskPrice;

    /** askSz — объём на лучшем ask. */
    private BigDecimal bestAskSize;

    // --------- 24h статистика ---------

    /** open24h — цена открытия 24 часа назад. */
    private BigDecimal open24h;

    /** high24h — максимум за 24 часа. */
    private BigDecimal high24h;

    /** low24h — минимум за 24 часа. */
    private BigDecimal low24h;

    /** vol24h — объём за 24 часа (обычно в базовой валюте). */
    private BigDecimal volume24h;

    /** volCcy24h — объём за 24 часа в котируемой валюте (например USDT). */
    private BigDecimal volumeCurrency24h;

    /** sodUtc0 — цена на начало дня по UTC+0. */
    private BigDecimal startOfDayUtc0;

    /** sodUtc8 — цена на начало дня по UTC+8. */
    private BigDecimal startOfDayUtc8;

    // --------- Timestamps ---------

    /** ts — время тикера от биржи (ms -> Instant). */
    private Instant sourceTimestamp;

    // --------- Auditing (DB) ---------

    /** createdAt — когда запись создана в нашей БД. */
    private Instant createdAt;

    /** updatedAt — когда запись обновлена в нашей БД. */
    private Instant updatedAt;

    /** createdBy — кем создана (опционально). */
    private String createdBy;

    /** updatedBy — кем обновлена (опционально). */
    private String updatedBy;
}
```

---

## 2) Маппинг (YAML): OKX `market/ticker` → `PriceTicker`

```yaml
# инструмент
instId: exchangeInstrumentName                      # имя инструмента на бирже
# instrumentId: instrumentId                        # ID инструмента в нашей БД — берём из справочника по exchangeInstrumentName
instType: exchangeInstrumentType                    # тип инструмента (строкой)

# цены
last: lastPrice                                     # строка -> BigDecimal
lastSz: lastSize                                    # строка -> BigDecimal
bidPx: bestBidPrice                                 # строка -> BigDecimal
bidSz: bestBidSize                                  # строка -> BigDecimal
askPx: bestAskPrice                                 # строка -> BigDecimal
askSz: bestAskSize                                  # строка -> BigDecimal

# 24h
open24h: open24h                                    # строка -> BigDecimal
high24h: high24h                                    # строка -> BigDecimal
low24h: low24h                                      # строка -> BigDecimal
vol24h: volume24h                                   # строка -> BigDecimal
volCcy24h: volumeCurrency24h                        # строка -> BigDecimal
sodUtc0: startOfDayUtc0                             # строка -> BigDecimal
sodUtc8: startOfDayUtc8                             # строка -> BigDecimal

# timestamp
ts: sourceTimestamp                               # ms-строка -> Instant
```

---

## 3) Правила конвертации

* Пустые строки `""` → `null`.
* Числа строкой → `BigDecimal`.
* `ts` (миллисекунды строкой) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* `instrumentId` берём **не из OKX**, а из нашего справочника инструментов по `exchangeInstrumentName`.

---

## 4) Как использовать в боте (коротко)

* Основной источник: WS `tickers`.
* Fallback: REST `market/ticker`.
* Для расчёта размера:

    * BUY: можно брать `bestAskPrice`
    * SELL: можно брать `bestBidPrice`
    * если нужно проще/быстрее — `lastPrice`.
