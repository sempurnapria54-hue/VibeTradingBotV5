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

| OKX field | Snapshot field |
|---|---|
| `instId` | `externalInstrumentId` |
| `instType` | raw → `instrumentType` (резолв) |
| `tickSz` | `externalTickSize` |
| `lotSz` | `externalLotSize` |
| `minSz` | `externalMinSize` |
| `ctVal` | `externalContractValue` |
| `ctValCcy` | `externalContractValueCurrency` |
| `ctType` | raw → `contractType` (резолв) |
| `maxLmtSz` | `externalMaxLimitSize` |
| `maxMktSz` | `externalMaxMarketSize` |
| `maxTriggerSz` | `externalMaxTriggerSize` |
| `maxStopSz` | `externalMaxStopSize` |
| `lever` | `externalMaxLeverage` |
| `state` | `externalState` → `Status` (резолв) |

Per-order max sizes и `lever`/`state` маппятся на шаге 5 (риск-преконтроль —
потребитель ограничений: `SIZE_ABOVE_LIMIT`, `EXCHANGE_MAX_LEVERAGE_EXCEEDED`,
`INSTRUMENT_NOT_LIVE`). Решение —
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

`instFamily`, `uly`, `baseCcy`/`quoteCcy`/`settleCcy` (приходят в
`InstrumentExternalSnapshot` — граничный снапшот инструмента, не в
этой модели; разграничение —
`docs/models/domain/core/Instrument.md`), `ctMult`,
`maxTwapSz`/`maxIcebergSz`/`maxLmtAmt`/`maxMktAmt` (per-order лимиты
неиспользуемых типов ордеров — не используем),
`listTime`/`expTime`/`openType`/`ruleType` (lifecycle биржи; для
SWAP `expTime` обычно пусто), `category`/`groupId`/`alias`/`stk`/
`optType`, `posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (позиционные лимиты —
форвард к риску на биржу/портфель, фаза 3,
`docs/decisions/per-trade-risk-policy.md`).
