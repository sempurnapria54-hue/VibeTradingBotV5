## Доменные модели и маппинг

> Ниже — пример доменной сущности **без persistence-аннотаций**, но с audit-полями, чтобы её можно было хранить в БД.

---

## 1) Доменная сущность `Candle`

```java
package com.example.tradingbot.domain.model.market;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Candle — одна свеча рынка (OHLCV) по конкретному инструменту и таймфрейму.
 *
 * Простыми словами:
 * - Одна строка = одна свеча.
 * - У свечи есть время открытия (openTime) и цены/объёмы.
 * - confirmStatus говорит, закрыта свеча или ещё формируется.
 */
@Getter
@Setter
public class Candle {

    /** Внутренний идентификатор записи в БД (если используешь). */
    private Long id;

    /** ID инструмента в нашей БД (справочник инструментов). */
    private Long instrumentId;

    /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
    private String exchangeInstrumentName;

    /**
     * Таймфрейм свечи (OKX bar). Регистр важен.
     * Рекомендуется хранить строго как в OKX (и/или как константу из OkxTimeframes), например: 1m, 5m, 1H, 4H, 1Dutc.
     */
    private String timeframe;

    /** Время открытия свечи (это же ts в ответе OKX), Unix ms -> Instant. */
    private Instant openTime;

    /** Цена открытия. */
    private BigDecimal open;

    /** Максимум за свечу. */
    private BigDecimal high;

    /** Минимум за свечу. */
    private BigDecimal low;

    /** Цена закрытия (для незакрытой свечи может меняться). */
    private BigDecimal close;

    /** Объём. Для SWAP/FUTURES обычно в контрактах; для SPOT — в базовой валюте. */
    private BigDecimal volume;

    /** Объём в валюте (поле volCcy). */
    private BigDecimal volumeCurrency;

    /** Объём в котируемой валюте (поле volCcyQuote), например в USDT. */
    private BigDecimal volumeQuoteCurrency;

    /** Закрыта свеча или ещё формируется. */
    private CandleConfirmStatus confirmStatus;

    /**
     * Когда мы последний раз обновили эту свечу из внешнего источника.
     * Полезно, если ты регулярно опрашиваешь "последние" и хочешь понимать свежесть данных.
     */
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

    public enum CandleConfirmStatus {
        /** Свеча ещё не закрыта, значения могут меняться. */
        UNCONFIRMED,

        /** Свеча закрыта, значения финальные. */
        CONFIRMED
    }
}
```

**Рекомендуемая уникальность в БД (чтобы не было дублей):**

* `(exchangeInstrumentName, timeframe, openTime)` или `(instrumentId, timeframe, openTime)`.

---

## 2) Маппинг (YAML): OKX `GET /market/candles` → `Candle`

```yaml
# data[i] = [ts,o,h,l,c,vol,volCcy,volCcyQuote,confirm]

instId: exchangeInstrumentName                      # берём из query instId (в ответе свечей его нет)
bar: timeframe                                      # берём из query bar (в ответе свечей его нет)

# data[*][0..8]
data[*][0]: openTime                                # ts (ms строкой) -> Instant

data[*][1]: open                                    # строка -> BigDecimal
data[*][2]: high

data[*][3]: low

data[*][4]: close

data[*][5]: volume                                  # vol

data[*][6]: volumeCurrency                          # volCcy

data[*][7]: volumeQuoteCurrency                     # volCcyQuote

data[*][8]: confirmStatus                           # "0"/"1" -> enum (UNCONFIRMED/CONFIRMED)
```

**Правила конвертации (чтобы маппинг был “рабочим”):**

* Пустые строки `""` → `null`.
* Числа строками → `BigDecimal`.
* `openTime`: `Instant.ofEpochMilli(Long.parseLong(ts))`.
* `confirmStatus`:

    * `"0"` → `UNCONFIRMED`
    * `"1"` → `CONFIRMED`
* `exchangeInstrumentName` и `timeframe` лучше проставлять из **контекста запроса** (query `instId` и `bar`), потому что в `data[]` их нет.

---

### Маппинг (YAML): OKX `GET /market/candles` → `client.model.Candle`

```yaml
# data[i] = [ts,o,h,l,c,vol,volCcy,volCcyQuote,confirm]

data[*][0]: candle.timestamp                         # ts (ms строкой) -> Instant/Long (как у тебя в client Candle)
data[*][1]: candle.open                              # open price (строка) -> BigDecimal
data[*][2]: candle.high                              # high price
data[*][3]: candle.low                               # low price
data[*][4]: candle.close                             # close price

data[*][5]: candle.volumeCoin                        # vol (обычно base/контракты)
data[*][6]: candle.volumeCurrency                    # volCcy

data[*][7]: candle.confirmedVolumeCurrency           # volCcyQuote (в котируемой валюте, напр. USDT)

data[*][8]: candle.status                            # confirm: "0"/"1" (лучше хранить как enum/строку)
```

### Правила конвертации (чтобы маппинг был «рабочим»)

* Пустые строки `""` → `null`.
* Числа приходят строками → `BigDecimal`.
* `ts` (ms строкой) → `Instant.ofEpochMilli(Long.parseLong(ts))` (или Long millis — как у тебя сейчас).
* `confirm`:

    * `"0"` → свеча не закрыта
    * `"1"` → свеча закрыта

---