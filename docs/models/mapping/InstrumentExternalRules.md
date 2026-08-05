# InstrumentExternalRules — mapping между слоями

## На какой вопрос отвечает этот файл

Как доменный `InstrumentExternalRules` ложится на нативные модели
источников, нормализуется через
`InstrumentExternalRulesExternalSnapshot` и как резолвятся типы и
статус инструмента.

## Контекст

Mapping-слой для `InstrumentExternalRules`. Доменная модель —
`docs/models/domain/other/InstrumentExternalRules.md`. Обновляет
правила — `docs/components/InstrumentExternalRulesSyncJob.md`. Сквозные
правила — `docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт endpoint'а —
`docs/integrations/<name>/contracts/instrument.md`.

**Ставка комиссии здесь не маппится — только ключ её группы.** Значение ставки
приходит **другим эндпоинтом** (`trade-fee`) и едет своим снапшотом в
`TradeFeeRate` (`docs/models/mapping/TradeFeeRate.md`,
`docs/models/domain/other/TradeFeeRate.md`); в этой модели живёт
`externalFeeGroupId` — половина ключа резолва. Один mapping-док — один
источник; синк у них общий (`InstrumentExternalRulesSyncJob`), но модели и
такт разные.

**Ось резолва — пара, и обе её половины сырые** (H7, `GAPS_CLOSE_4`):
(`externalInstrumentType`, `externalFeeGroupId`), а **не** доменная проекция
`instrumentType`, которая резолвится ниже по этому же файлу
(§«Резолв enum'ов при материализации»). Довод — коллизия `UNKNOWN`:
`docs/models/domain/other/TradeFeeRate.md` §«Масштаб модели».

Текущие источники: **OKX**.

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
дом `Instrument` (H6 `DOCS_CHECK_11`,
`docs/decisions/instrument-currencies-home.md`,
`docs/models/mapping/Instrument.md`). Промежуточная редакция
(`DOCS_CHECK_9`) маппила их сюда; строки сняты вместе с полями модели.

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
`docs/decisions/instrument-external-rules-materialization.md`.

### Разграничение со снапшотом инструмента (шаг 1)

Биржевые `state`/`lever` приходят и на шаге 1 в доменный `Instrument`
(`externalStatus`/`externalLeverage`, через `InstrumentExternalSnapshot`,
`docs/models/mapping/Instrument.md`), и здесь — в rules при материализации
на шаге 5. **Авторитетный для преконтроля источник** торгуемости и потолка
плеча — rules (`Status`/`externalState`, `externalMaxLeverage`); одноимённые
сырые поля на `Instrument` несут то же значение, но для преконтроля не
авторитетны (дубль; устранение — мелкая чистка). Решение —
`docs/decisions/instrument-external-rules-materialization.md` (закрыт INSTR-Q1,
снят leverage/HOLD-под-вопрос INSTR-Q2).

### Не маппимые поля OKX

`instFamily`, `uly`, `ctMult`,
`maxTwapSz`/`maxIcebergSz`/`maxLmtAmt`/`maxMktAmt` (per-order лимиты
неиспользуемых типов ордеров — не используем),
`listTime`/`expTime`/`openType`/`ruleType` (lifecycle биржи; для
SWAP `expTime` обычно пусто), `category`/`alias`/`stk`/
`optType`, `posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (позиционные лимиты —
форвард к риску на биржу/портфель, фаза 3,
`docs/decisions/per-trade-risk-policy.md`).

**`groupId` из этого списка снят** (шаг 7, `GAPS_CLOSE_3`, H1). Он не «прочее
поле биржи», а **ключ резолва ставки комиссии**: офдок OKX прямо предписывает
брать его отсюда — «instType and groupId should be used together to determine a
trading fee group. Users should use this endpoint together with fee rates
endpoint to get the trading fee of a specific symbol» (Get instruments →
Response Parameters). Отброс был сделан до changelog OKX **2025-11-21**,
который ввёл `groupId` в Get instruments и `feeGroup` в Get fee rates,
задепрекейтив флэт `maker`/`taker` для SWAP/FUTURES; с этого момента резолв
ставки SWAP **завязан именно на `groupId`**, и его отброс оставлял `CODE` без
ключа группы (прогноз комиссии молча выпадал в null).
