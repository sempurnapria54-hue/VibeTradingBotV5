# InstrumentOkxResponse (OKX instrument spec)

## На какой вопрос отвечает этот файл

Какие поля у нативной модели инструмента источника.

## Поля DTO

Coded DTO `InstrumentOkxResponse` несёт подмножество, релевантное
идентичности инструмента, sizing/rounding-правилам
**и валютам**. Один DTO питает оба снапшота:
identity-снапшот `InstrumentExternalSnapshot` (шаги 1 и 7) и rules-снапшот
`InstrumentExternalRules`.

Identity/spec-поля, маппящиеся в `InstrumentExternalSnapshot`:

| OKX field | Тип (raw) | Snapshot field | Состояние |
|---|---|---|---|
| `instId` | string | `externalInstrumentId` | есть |
| `instType` | string | `externalInstrumentType` | есть |
| `baseCcy` | string | `externalBaseCurrency` | есть; **имя целевое** — см. |
| `quoteCcy` | string | `externalQuoteCurrency` | есть; **имя целевое** |
| `settleCcy` | string | `externalSettlementCurrency` | есть; **имя целевое** |
| `lotSz` | string (decimal) | `lotSize` | есть |
| `minSz` | string (decimal) | `minimumOrderSize` | есть |
| `ctVal` | string (decimal) | `contractValue` | есть |
| `ctMult` | string (decimal) | `contractMultiplier` | есть |
| `tickSz` | string (decimal) | `priceTickSize` | есть |
| `state` | string | `externalStatus` | есть |
| `lever` | string | `externalLeverage` | есть |

**Имена валютных полей снапшота — целевые**. В коде снапшот сегодня несёт
`externalSettleCurrency` / `externalBaseCurrency` / `externalQuoteCurrency`,
а доменные поля названы `externalSettlementCurrency` / `externalBaseCurrency`
/ `externalQuoteCurrency` (`docs/models/domain/core/Instrument.md` —
имена доменных полей окончательны). Расходилось **трояко**: инвентарь нёс
третий набор (`settleCurrency`/`baseCurrency`/`quoteCurrency`). Решение —
**переименовать поле снапшота в коде** под доменное имя: снапшот
транзиентный ⇒ ни миграции, ни бэкфилла, зато маппинг остаётся по имени
(без явного `@Mapping`) и один факт зовётся одинаково во всех слоях.
Переименование внесено в **не-схемную дельту `CODE`**
(`docs/rules/pnl-reconciliation.md`).

Rules-поля (sizing/rounding/ограничители), питающие rules-снапшот
шага 5 (см. `docs/models/mapping/InstrumentExternalRules.md`):

| OKX field | Тип (raw) | Назначение | Состояние |
|---|---|---|---|
| `ctType` | string | тип контракта (linear/inverse) | есть |
| `ctValCcy` | string | валюта стоимости контракта | есть |
| `maxLmtSz` | string (decimal) | макс. размер limit-ордера | есть |
| `maxMktSz` | string (decimal) | макс. размер market-ордера | есть |
| `maxTriggerSz` | string (decimal) | макс. размер trigger-ордера | есть |
| `maxStopSz` | string (decimal) | макс. размер stop-ордера | есть |
| `groupId` | string | id комиссионной группы инструмента; **ключ резолва ставки** — пара (`instType`, `groupId`) | **целевое: в коде поля нет** — ни в DTO, ни в rules-снапшоте, ни в модели навеса |

**`groupId` — целевая дельта `CODE`, и она внесена в перечень**. Поле живёт в JSONB-навесе, собственной колонки не имеет
⇒ в schema-дельту шага не попадает **по построению**, и ни один
самопроверяемый перечень пропуск не ловит. Поэтому добыча ключа записана
отдельной строкой **не-схемной дельты `CODE`**
(`docs/rules/pnl-reconciliation.md`): поле DTO + поле
`InstrumentExternalRulesExternalSnapshot` + строка маппера. Цена: миграций
не требует, новых вызовов биржи не добавляет (`/public/instruments` уже
читается). **Без этого** `externalFeeGroupId` остаётся `null`, ставка не
резолвится и `FEE_RATE_UNAVAILABLE` блокирует **всякое** валидируемое
действие: не только вход, но и перенос уровня, и ослабление защиты —
ставка стои́т операндом живого слагаемого одновременного потолка
(`docs/models/domain/other/InstrumentExternalRules.md`).

**`groupId` — ключ, а не ставка.** Инструмент несёт только id своей
комиссионной группы; сама ставка приходит отдельным эндпоинтом
`GET /api/v5/account/trade-fee` и живёт в своей модели `TradeFeeRate` (одна
строка на группу), не копией на инструменте
(`docs/models/domain/other/TradeFeeRate.md`,
`docs/rules/pnl-reconciliation.md` реш.4). Офдок (Get instruments
→ Response Parameters, «Instrument trading fee group ID»): «instType and
groupId should be used together to determine a trading fee group. Users should
use this endpoint together with fee rates endpoint to get the trading fee of a
specific symbol». Native-поля ставки —
`docs/models/integrations/okx/TradeFeeOkxResponse.md`; контракт —
`docs/integrations/okx/contracts/trade-fee.md`.

Числовые spec-поля OKX (`lotSz`/`minSz`/`ctVal`/`ctMult`/`tickSz`)
приходят строками; в snapshot — `BigDecimal`. Биржевые `state`/
`lever` остаются сырыми строками (`externalStatus`/
`externalLeverage`) и в шаге 1 персистятся на `Instrument` (см.
`docs/models/mapping/Instrument.md`).

## Поля, которые НЕ входят в этот DTO
