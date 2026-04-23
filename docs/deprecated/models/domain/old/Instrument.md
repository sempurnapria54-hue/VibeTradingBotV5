## Доменная модель `Instrument` (с вложенной `InstrumentDynamicSpec`) + маппинг

Ниже — доменная сущность `Instrument`, внутри которой лежит объект `InstrumentDynamicSpec`.

Идея:

* `Instrument` — справочник инструмента (почти неизменное: валюты, контрактная математика, тип)
* `InstrumentDynamicSpec` — динамическая часть (tick/lot/min/max/state), которую можно часто обновлять

⚠️ Примечание про хранение в БД:

* В домене это **композиция** (объект внутри объекта).
* В persistence-слое это можно хранить как 1 таблицу (плоско) или как 2 таблицы (instrument + instrument_dynamic_spec) — домен от этого не зависит.

---

## 1) Доменная сущность `Instrument`

```java
package com.example.tradingbot.domain.model.core.exchange;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Справочник инструмента.
 *
 * Простыми словами: "кто это" + вложенная динамическая спека "как торговать сейчас".
 */
@Getter
@Setter
public class Instrument {

  /** Внутренний идентификатор инструмента в нашей БД. */
  private Long id;

  /** Имя инструмента на бирже (OKX instId), например ETH-USDT-SWAP. */
  private String exchangeInstrumentName;

  /** Тип инструмента на бирже: SPOT/MARGIN/SWAP/FUTURES/OPTION. */
  private InstrumentType instrumentType;

  /** Семейство инструмента (instFamily). */
  private String instrumentFamily;

  /** Underlying (uly). Для деривативов: базовый символ (может быть пустым). */
  private String underlying;

  /** Базовая валюта (baseCcy), например ETH. */
  private String baseCurrency;

  /** Котируемая валюта (quoteCcy), например USDT. */
  private String quoteCurrency;

  /** Валюта расчётов/маржи по контракту (settleCcy), например USDT. */
  private String settleCurrency;

  // --------- Контрактная математика (обычно стабильна) ---------

  /** Тип контракта (ctType): LINEAR / INVERSE (может быть пустым для SPOT). */
  private ContractType contractType;

  /** Стоимость 1 контракта (ctVal). */
  private BigDecimal contractValue;

  /** Валюта contractValue (ctValCcy). */
  private String contractValueCurrency;

  /** Мультипликатор контракта (ctMult), если биржа его возвращает. */
  private BigDecimal contractMultiplier;

  // --------- Прочие «стабильные» поля ---------

  /** Время листинга на бирже (listTime, ms -> Instant). */
  private Instant listedAt;

  /** Время экспирации (expTime) — актуально для FUTURES/OPTION. */
  private Instant expiresAt;

  /** Алиас (alias) — чаще полезен для фьючей. */
  private String alias;

  // --------- Динамическая спека (может меняться) ---------

  /**
   * Динамическая спецификация инструмента.
   *
   * Здесь лежит то, что может меняться на бирже:
   * tickSz/lotSz/minSz/max* и state.
   */
  private InstrumentDynamicSpec dynamicSpec;

  // --------- Управление у нас ---------

  /** Активен ли инструмент в нашем боте (мы его торгуем/собираем данные). */
  private Boolean active;

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

  public enum ContractType {
    LINEAR,
    INVERSE
  }
}
```

---

## 2) Доменная сущность `InstrumentDynamicSpec`

```java
package com.example.tradingbot.domain.model.core.exchange;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Динамическая спецификация инструмента (может меняться со временем).
 *
 * Простыми словами: "как торговать прямо сейчас".
 */
@Getter
@Setter
public class InstrumentDynamicSpec {

  /** Минимальный шаг цены (tickSz) для поля px. */
  private BigDecimal tickSize;

  /** Шаг размера (lotSz). Для SWAP/FUTURES обычно шаг в контрактах. */
  private BigDecimal lotSize;

  /** Минимальный размер ордера (minSz). Для SWAP/FUTURES — в контрактах. */
  private BigDecimal minSize;

  // --------- Лимиты по размерам/суммам ---------

  /** Максимальный размер (sz) для limit ордера (maxLmtSz). */
  private BigDecimal maxLimitSize;

  /** Максимальный размер (sz) для market ордера (maxMktSz). */
  private BigDecimal maxMarketSize;

  /** Максимальный размер (sz) для TWAP (maxTwapSz). */
  private BigDecimal maxTwapSize;

  /** Максимальный размер (sz) для iceberg (maxIcebergSz). */
  private BigDecimal maxIcebergSize;

  /** Максимальный размер (sz) для trigger ордеров (maxTriggerSz). */
  private BigDecimal maxTriggerSize;

  /** Максимальный размер (sz) для stop market (maxStopSz). */
  private BigDecimal maxStopSize;

  /** Максимальная сумма (amount) для limit ордера (maxLmtAmt). Обычно актуально для SPOT. */
  private BigDecimal maxLimitAmount;

  /** Максимальная сумма (amount) для market ордера (maxMktAmt). Обычно актуально для SPOT. */
  private BigDecimal maxMarketAmount;

  // --------- Статус/прочее ---------

  /** Статус инструмента (state): live/suspend/preopen/expired/... */
  private ExchangeState exchangeState;

  /** Максимально доступное плечо (lever), если биржа возвращает. */
  private BigDecimal maxLeverage;

  /** Тип правил торговли (ruleType), например normal / pre_market. */
  private String ruleType;

  /** Тип открытия (openType), чаще актуально для SPOT. */
  private String openType;

  /** Категория/группа — системные поля OKX (если приходят и хочется хранить). */
  private String category;

  private String groupId;

  // --------- Source timestamps ---------

  /** uTime — время обновления от биржи (обычно через WS instruments). */
  private Instant sourceUpdatedAt;

  public enum ExchangeState {
    LIVE,
    SUSPEND,
    PREOPEN,
    EXPIRED,
    TEST,
    UNKNOWN
  }
}
```

---

## 3) Маппинг (YAML)

Формат: **поле в exchange модели : поле в доменной модели** `# комментарий`

### 3.1 OKX `GET /api/v5/public/instruments` → `Instrument` (включая nested `dynamicSpec`)

```yaml
# идентификация
instId: exchangeInstrumentName                       # имя инструмента на бирже
# id: id                                              # задаётся у нас
instType: instrumentType                             # SPOT/MARGIN/SWAP/FUTURES/OPTION -> enum (UPPERCASE)
instFamily: instrumentFamily                         # семейство
uly: underlying                                      # underlying

# валюты
baseCcy: baseCurrency                                # базовая валюта
quoteCcy: quoteCurrency                              # котируемая валюта
settleCcy: settleCurrency                            # валюта расчётов

# контрактная математика
ctType: contractType                                 # linear/inverse -> enum (LINEAR/INVERSE)
ctVal: contractValue                                 # строка -> BigDecimal
ctValCcy: contractValueCurrency                      # валюта contractValue
ctMult: contractMultiplier                           # строка -> BigDecimal

# "стабильные" времена
listTime: listedAt                                   # ms-строка -> Instant
expTime: expiresAt                                   # ms-строка -> Instant

alias: alias                                         # алиас

# dynamicSpec (вложенный объект)
tickSz: dynamicSpec.tickSize                         # строка -> BigDecimal
lotSz: dynamicSpec.lotSize                           # строка -> BigDecimal
minSz: dynamicSpec.minSize                           # строка -> BigDecimal

maxLmtSz: dynamicSpec.maxLimitSize                   # строка -> BigDecimal
maxMktSz: dynamicSpec.maxMarketSize                  # строка -> BigDecimal
maxTwapSz: dynamicSpec.maxTwapSize                   # строка -> BigDecimal
maxIcebergSz: dynamicSpec.maxIcebergSize             # строка -> BigDecimal
maxTriggerSz: dynamicSpec.maxTriggerSize             # строка -> BigDecimal
maxStopSz: dynamicSpec.maxStopSize                   # строка -> BigDecimal

maxLmtAmt: dynamicSpec.maxLimitAmount                # строка -> BigDecimal
maxMktAmt: dynamicSpec.maxMarketAmount               # строка -> BigDecimal

state: dynamicSpec.exchangeState                     # live/suspend/... -> enum
lever: dynamicSpec.maxLeverage                       # строка -> BigDecimal
ruleType: dynamicSpec.ruleType                       # строка
openType: dynamicSpec.openType                       # строка
category: dynamicSpec.category                       # строка
groupId: dynamicSpec.groupId                         # строка

uTime: dynamicSpec.sourceUpdatedAt                   # ms-строка -> Instant (может приходить через WS)

# active: active                                     # управляем у себя
```

### 3.2 WS `instruments` push → `Instrument.dynamicSpec`

WS событие по сути повторяет поля REST.
Правило обновления:

* по `instId` находим `Instrument`
* обновляем **только** `dynamicSpec.*` поля (tick/lot/min/max/state/uTime)

---

## 4) Правила конвертации (коротко)

* Пустые строки `""` → `null`.
* Числа строкой → `BigDecimal`.
* Время `listTime/expTime/uTime` (ms строка) → `Instant.ofEpochMilli(Long.parseLong(value))`.
* Enum:

    * `instType` → uppercase enum
    * `ctType`: `linear -> LINEAR`, `inverse -> INVERSE`
    * `state`: `live -> LIVE`, `suspend -> SUSPEND`, `preopen -> PREOPEN`, иначе `UNKNOWN`

---

## 5) Практика хранения

Даже если в БД это будет 2 таблицы (instrument + instrument_dynamic_spec), доменная модель остаётся такой:

* `Instrument` содержит `dynamicSpec`.

Это удобно:

* в коде всегда доступно `instrument.getDynamicSpec().getTickSize()`
* обновления от WS/REST затрагивают только `dynamicSpec`.
