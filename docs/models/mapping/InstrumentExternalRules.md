# InstrumentExternalRules — mapping между слоями

## На какой вопрос отвечает этот файл

Как справочные правила инструмента переходят между слоями.

## Source-agnostic ядро

### Mapping-flow

source ответ → `InstrumentExternalRulesExternalSnapshot` (сырые
`external*` строки) → `InstrumentExternalRules`.

`external*`-поля snapshot сохраняются как есть (`externalTickSize`,
`externalLotSize`, `externalMinSize`, `externalContractValue` и
др.). Доменные проекции резолвятся при материализации модели:

- сырой тип инструмента → `InstrumentType` (`SWAP` / `FUTURES` /
  `SPOT` / `MARGIN` / `OPTION` / `UNKNOWN`).
- сырой тип контракта → `ContractType` (`LINEAR` / `INVERSE` /
  `UNKNOWN`).

Неизвестное значение нормализуется в `UNKNOWN` соответствующего enum.

### Sizing-формула (линейный контракт, `ctValCcy = baseCcy`)

```text
baseQty   = usdtNotional / price
contracts = baseQty / ctVal
→ округлить по lotSz; проверить minSz
```

См. `docs/components/SizeCalculator.md`.

## OKX

### `InstrumentOkxResponse` → snapshot

`integrationToSnapshot` переносит сырые строки 1:1 — **enum'ы на этом
этапе не резолвятся**, snapshot держит только `external*`-строки:

| OKX field | Snapshot field |
|---|---|
| `instId` | `externalInstrumentId` |
| `instType` | `externalInstrumentType` |
| `tickSz` | `externalTickSize` |
| `lotSz` | `externalLotSize` |
| `minSz` | `externalMinSize` |
| `ctVal` | `externalContractValue` |
| `ctValCcy` | `externalContractValueCurrency` |
| `ctType` | `externalContractType` |
| `maxLmtSz` | `externalMaxLimitSize` |
| `maxMktSz` | `externalMaxMarketSize` |
| `maxTriggerSz` | `externalMaxTriggerSize` |
| `maxStopSz` | `externalMaxStopSize` |
| `lever` | `externalMaxLeverage` |
| `groupId` | `externalFeeGroupId` |
| `state` | `externalState` |

**Валюты (`settleCcy`/`baseCcy`/`quoteCcy`) навесом не маппятся** — их
дом `Instrument`. Промежуточная редакция маппила их сюда; строки сняты вместе с полями модели.

### Резолв enum'ов при материализации (`snapshotToDomain`)

Доменные проекции резолвятся **при материализации** модели
(`snapshotToDomain(snapshot, instrumentId)`), не на этапе snapshot:

| Сырое поле snapshot | Доменная проекция |
|---|---|
| `externalInstrumentType` | `instrumentType` (`InstrumentType`) |
| `externalContractType` | `contractType` (`ContractType`) |
| `externalState` | `status` (`Status`) |

Неизвестное сырое значение нормализуется в `UNKNOWN`. Per-order max sizes
и `lever`/`state` потребляет риск-преконтроль шага 5 (`SIZE_ABOVE_LIMIT`,
`EXCHANGE_MAX_LEVERAGE_EXCEEDED`, `INSTRUMENT_NOT_LIVE`). Решение —
`docs/models/domain/other/InstrumentExternalRules.md`.

### Разграничение со снапшотом инструмента

Биржевые `state`/`lever` приходят и на шаге 1 в доменный `Instrument`
(`externalStatus`/`externalLeverage`, через `InstrumentExternalSnapshot`,
`docs/models/mapping/Instrument.md`), и здесь — в rules при материализации
на шаге 5. **Авторитетный для преконтроля источник** торгуемости и потолка
плеча — rules (`Status`/`externalState`, `externalMaxLeverage`); одноимённые
сырые поля на `Instrument` несут то же значение, но для преконтроля не
авторитетны (дубль; устранение — мелкая чистка). Авторитетный носитель —
`docs/models/domain/other/InstrumentExternalRules.md`.

### Не маппимые поля OKX

`instFamily`, `uly`, `ctMult`,
`maxTwapSz`/`maxIcebergSz`/`maxLmtAmt`/`maxMktAmt` (per-order лимиты
неиспользуемых типов ордеров — не используем),
`listTime`/`expTime`/`openType`/`ruleType` (lifecycle биржи; для
SWAP `expTime` обычно пусто), `category`/`alias`/`stk`/
`optType`, `posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (позиционные лимиты —
форвард к риску на биржу/портфель, фаза 3,
`docs/rules/risk-policy.md`).

**`groupId` из этого списка снят**. Он не «прочее
поле биржи», а **ключ резолва ставки комиссии**: офдок OKX прямо предписывает
брать его отсюда — «instType and groupId should be used together to determine a
trading fee group. Users should use this endpoint together with fee rates
endpoint to get the trading fee of a specific symbol» (Get instruments →
Response Parameters). Отброс был сделан до changelog OKX **2025-11-21**,
который ввёл `groupId` в Get instruments и `feeGroup` в Get fee rates,
задепрекейтив флэт `maker`/`taker` для SWAP/FUTURES; с этого момента резолв
ставки SWAP **завязан именно на `groupId`**, и его отброс оставлял `CODE` без
ключа группы (прогноз комиссии молча выпадал в null).
