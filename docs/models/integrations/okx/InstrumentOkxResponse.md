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
| `baseCcy` | string | `externalBaseCurrency` | есть |
| `quoteCcy` | string | `externalQuoteCurrency` | есть |
| `settleCcy` | string | `externalSettleCurrency` | есть; в домене — `externalSettlementCurrency` (ниже) |
| `lotSz` | string (decimal) | `externalLotSize` | есть |
| `minSz` | string (decimal) | `externalMinSize` | есть |
| `ctVal` | string (decimal) | `externalContractValue` | есть |
| `ctMult` | string (decimal) | `externalContractMultiplier` | есть |
| `tickSz` | string (decimal) | `externalTickSize` | есть |
| `state` | string | `externalStatus` | есть |
| `lever` | string | `externalLeverage` | есть |

**Валюта расчёта зовётся в снапшоте и в домене по-разному**: снапшот несёт
`externalSettleCurrency`, доменная модель — `externalSettlementCurrency`
(`docs/models/domain/core/Instrument.md` — имена доменных полей
окончательны), и маппер коннектора связывает их явным сопоставлением, а не
по имени. Базовая и котируемая валюты зовутся одинаково во всех слоях.

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
| `groupId` | string | id комиссионной группы инструмента; **ключ резолва ставки** — пара (`instType`, `groupId`) | есть — в DTO, в rules-снапшоте и в модели навеса поле `externalFeeGroupId` (`docs/models/mapping/InstrumentExternalRules.md`) |
| `instFamily` | string | семья инструмента — **операнд запроса позиционных тиров**: у SWAP площадка отдаёт тиры по семье, а не по инструменту (`docs/integrations/okx/contracts/position-tiers.md`). В снапшот и навес не переносится; поля нет в ответе — тиры не читаются (`docs/models/mapping/InstrumentExternalRules.md`) | есть |

**`groupId` доезжает до навеса, и путь у него один.** Коннектор переносит
поле DTO в rules-снапшот `InstrumentExternalRulesExternalSnapshot` полем
`externalFeeGroupId` строкой маппера `InstrumentExternalRulesMapper`;
`market-data` синком справочника кладёт правила в JSONB-навес
`instruments.external_rules`, и примесь конвертера навеса изымает из строки
ставку и идентификатор владельца, но не ключ группы
(`docs/rules/persistence-representation.md` §«Состав ключей строки навеса»).
Ядро по паре (`instType`, `groupId`) и счёту достраивает ставку из
`TradeFeeRate` (`docs/models/domain/core/Instrument.md` §«Проекция у
торгового ядра»). Собственной колонки у поля нет, поэтому в schema-дельту оно
не попадает **по построению**; добыча миграций не требует и новых вызовов
биржи не добавляет (`/public/instruments` и так читается). **Пустым
`externalFeeGroupId` остаётся только у ответа площадки без `groupId`** — тогда
ставка не резолвится, и `FEE_RATE_UNAVAILABLE` блокирует **всякое**
валидируемое действие: не только вход, но и перенос уровня, и ослабление
защиты — ставка стои́т операндом живого слагаемого одновременного потолка
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
