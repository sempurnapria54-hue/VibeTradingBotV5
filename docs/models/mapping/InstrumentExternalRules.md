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

### Разграничение со снапшотом инструмента (шаг 1)

Биржевые `state`/`lever` в шаге 1 потребляются доменным
`Instrument` (`externalStatus`/`externalLeverage`) через граничный
`InstrumentExternalSnapshot` — `docs/models/mapping/Instrument.md`.
Здесь (rules-маппинг) они больше **не** маппятся: rules отложена за
пределы шага 1 и описывает sizing/rounding-правила. Поля
`externalState`/`externalMaxLeverage` и проекция `Status` в модели
`InstrumentExternalRules` сохранены, но их сорсинг при
материализации rules и соотнесение с биржевыми полями `Instrument`
(в т.ч. возможный дубль/удаление) — открытый вопрос INSTR-Q2 (роль
`externalLeverage` как биржевого потолка плеча, валидация рабочего
плеча) и INSTR-Q1 (снапшот-концепция rules).

### Не маппимые поля OKX

`state`/`lever` (в шаге 1 → доменный `Instrument`:
`externalStatus`/`externalLeverage`, через
`InstrumentExternalSnapshot`; разграничение выше), `instFamily`,
`uly`, `baseCcy`/`quoteCcy`/`settleCcy` (приходят в
`InstrumentExternalSnapshot` — граничный снапшот инструмента, не в
этой модели; разграничение —
`docs/models/domain/core/Instrument.md`), `ctMult`,
`maxLmtSz`/`maxMktSz`/
`maxTwapSz`/`maxIcebergSz`/`maxTriggerSz`/`maxStopSz`/`maxLmtAmt`/
`maxMktAmt` (per-order лимиты — пока не используем),
`listTime`/`expTime`/`openType`/`ruleType` (lifecycle биржи; для
SWAP `expTime` обычно пусто), `category`/`groupId`/`alias`/`stk`/
`optType`, `posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (лимиты позиций).
