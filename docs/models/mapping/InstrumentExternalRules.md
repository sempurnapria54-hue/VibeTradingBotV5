# InstrumentExternalRules — mapping между слоями

## На какой вопрос отвечает этот файл

Как справочные правила инструмента переходят между слоями.

## Source-agnostic ядро

### Mapping-flow

source ответ → `InstrumentExternalRulesExternalSnapshot` (сырые
`external*` строки) → `InstrumentExternalRules`.

**Ответов источника два:** спецификация инструмента и позиционные тиры его
семьи. Тиры едут в снапшот сырыми строками и разбираются в числа при
материализации — в доменные `PositionTier`
(`docs/models/domain/other/InstrumentExternalRules.md`).

`external*`-поля snapshot сохраняются как есть (`externalTickSize`,
`externalLotSize`, `externalMinSize`, `externalContractValue` и
др.). Доменные проекции резолвятся при материализации модели:

- сырой тип инструмента → `InstrumentType` (`SWAP` / `FUTURES` /
  `SPOT` / `MARGIN` / `OPTION` / `UNKNOWN`).
- сырой тип контракта → `ContractType` (`LINEAR` / `INVERSE` /
  `UNKNOWN`).

Неизвестное значение нормализуется в `UNKNOWN` соответствующего enum.

### Sizing-формула (линейный контракт, `ctValCcy = baseCcy`)

Форма здесь не переписывается: дом формы — `docs/spec/order-sizing.json`
(`contractsFromAllocation`, `desiredContracts`, `entryContracts`), смысл —
`docs/components/SizeCalculator.md`. Из этого дока в форму заходят
`ctVal`, `lotSz` и `minSz`.

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

### `PositionTierOkxResponse` → snapshot

Инвентарь — `docs/models/integrations/okx/PositionTierOkxResponse.md`;
контракт — `docs/integrations/okx/contracts/position-tiers.md`.

**Чтение идёт по семье инструмента, взятой из ответа спецификации:** запрос
`instType` (тип правил), `tdMode=isolated`, `instFamily` = `instFamily`
ответа спецификации. Одно чтение правил инструмента стоит поэтому двух
запросов к площадке, и второй идёт по своему лимиту публичного эндпоинта
(10 запросов за 2 секунды по IP). Семьи в ответе спецификации нет — тиры не
читаются, и правила уходят с пустыми тирами.

| OKX field | Snapshot field (`externalPositionTiers[]`) | Домен (`positionTiers[]`) |
|---|---|---|
| `minSz` | `externalMinSize` | `minSize` |
| `maxSz` | `externalMaxSize` | `maxSize` |
| `mmr` | `externalMaintenanceMarginRate` | `maintenanceMarginRate` |

**Структурная валидация тиров — до маппинга:** каждая запись несёт
запрошенную семью, а `minSz`, `maxSz`, `mmr` непусты; иначе — нарушение
инварианта контракта (`docs/rules/controlled-exchange-exceptions.md`).
Число, не разобравшееся при материализации, — то же нарушение. Пустой
ответ — пустые тиры, а не пустой перечень: «не прочли» и «нет» оценке
ликвидации неразличимы и обе значат «не измерено».

**Отказ чтения тиров роняет всё чтение правил инструмента**, а не
отдаёт правила без тиров: частичные правила переписали бы навес читателя
вместе с прежними тирами, а прежний навес честнее — его несвежесть
измерима по строке-владельцу.

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

`uly`, `ctMult`,
`maxTwapSz`/`maxIcebergSz`/`maxLmtAmt`/`maxMktAmt` (per-order лимиты
неиспользуемых типов ордеров — не используем),
`listTime`/`expTime`/`openType`/`ruleType` (lifecycle биржи; для
SWAP `expTime` обычно пусто), `category`/`alias`/`stk`/
`optType`, `posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (позиционные лимиты —
форвард к риску на биржу/портфель — потолок одновременного риска тенанта (`docs/architecture/signal-strategy-allocator.md`),
`docs/rules/risk-policy.md`).

**`instFamily` из этого списка снят:** он операнд запроса позиционных
тиров, хотя в снапшот и навес по-прежнему не переносится.

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
