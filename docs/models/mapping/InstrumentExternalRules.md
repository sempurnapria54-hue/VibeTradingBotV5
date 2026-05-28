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
`externalLotSize`, `externalMinSize`, `externalContractValue`,
`externalMaxLeverage`, `externalState` и др.). Доменные проекции
резолвятся при материализации модели:

- `externalState` → `InstrumentExternalRules.Status` (`LIVE` /
  `SUSPEND` / `PREOPEN` / `EXPIRED` / `TEST` / `UNKNOWN`).
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

### `OkxInstrumentResponse` → snapshot

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
| `lever` | `externalMaxLeverage` |
| `state` | raw → `Status` (резолв) |

### OKX status resolver

| OKX raw `state` | Domain `Status` |
|---|---|
| `live` | `LIVE` |
| `suspend` | `SUSPEND` |
| `preopen` | `PREOPEN` |
| `expired` | `EXPIRED` |
| `test` | `TEST` |
| unknown | `UNKNOWN` |

### Не маппимые поля OKX

`instFamily`, `uly`, `baseCcy`/`quoteCcy`/`settleCcy` (хранятся в
domain `Instrument`, не здесь), `ctMult`, `maxLmtSz`/`maxMktSz`/
`maxTwapSz`/`maxIcebergSz`/`maxTriggerSz`/`maxStopSz`/`maxLmtAmt`/
`maxMktAmt` (per-order лимиты — пока не используем),
`listTime`/`expTime`/`openType`/`ruleType` (lifecycle биржи; для
SWAP `expTime` обычно пусто), `category`/`groupId`/`alias`/`stk`/
`optType`, `posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (лимиты позиций).
