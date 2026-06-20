# InstrumentOkxResponse (OKX instrument spec)

## На какой вопрос отвечает этот файл

Какие поля у OKX instrument response — что приходит от биржи и что
из этого используется.

## Контекст

Нативная модель источника OKX (Java `InstrumentOkxResponse`).
Возвращается `GET /api/v5/public/instruments`. Не выходит за
`IntegrationService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `InstrumentExternalSnapshot` и далее в `Instrument`
(идентичность + биржевые `externalStatus`/`externalLeverage` +
справочные поля) — см.
`docs/models/domain/core/Instrument.md` и класс
`domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot`.
Контракт endpoint'а / rate limit —
`docs/integrations/okx/contracts/instrument.md`.

## Поля DTO

Coded DTO `InstrumentOkxResponse` несёт подмножество, релевантное
идентичности инструмента (шаг 1) **и** sizing/rounding-правилам
(шаг 5). Один DTO питает оба снапшота: identity-снапшот
`InstrumentExternalSnapshot` (шаг 1) и rules-снапшот
`InstrumentExternalRules` (шаг 5).

Identity/spec-поля, маппящиеся в `InstrumentExternalSnapshot`
(шаг 1):

| OKX field | Тип (raw) | Snapshot field |
|---|---|---|
| `instId` | string | `externalInstrumentId` |
| `instType` | string | `externalInstrumentType` |
| `baseCcy` | string | `baseCurrency` |
| `quoteCcy` | string | `quoteCurrency` |
| `settleCcy` | string | `settleCurrency` |
| `lotSz` | string (decimal) | `lotSize` |
| `minSz` | string (decimal) | `minimumOrderSize` |
| `ctVal` | string (decimal) | `contractValue` |
| `ctMult` | string (decimal) | `contractMultiplier` |
| `tickSz` | string (decimal) | `priceTickSize` |
| `state` | string | `externalStatus` |
| `lever` | string | `externalLeverage` |

Rules-поля (sizing/rounding/ограничители), питающие rules-снапшот
шага 5 (см. `docs/models/mapping/InstrumentExternalRules.md` §OKX):

| OKX field | Тип (raw) | Назначение |
|---|---|---|
| `ctType` | string | тип контракта (linear/inverse) |
| `ctValCcy` | string | валюта стоимости контракта |
| `maxLmtSz` | string (decimal) | макс. размер limit-ордера |
| `maxMktSz` | string (decimal) | макс. размер market-ордера |
| `maxTriggerSz` | string (decimal) | макс. размер trigger-ордера |
| `maxStopSz` | string (decimal) | макс. размер stop-ордера |

Числовые spec-поля OKX (`lotSz`/`minSz`/`ctVal`/`ctMult`/`tickSz`)
приходят строками; в snapshot — `BigDecimal`. Биржевые `state`/
`lever` остаются сырыми строками (`externalStatus`/
`externalLeverage`) и в шаге 1 персистятся на `Instrument` (см.
`docs/models/mapping/Instrument.md`).

## Поля, которые НЕ входят в этот DTO

OKX `public/instruments` отдаёт больше полей. Sizing/rounding-правила
инструмента (`ctType`, `ctValCcy`,
`maxLmtSz`/`maxMktSz`/`maxTriggerSz`/`maxStopSz`) **входят** в этот
DTO (см. таблицу rules-полей выше) и питают rules-снапшот
`InstrumentExternalRules` (`docs/models/mapping/InstrumentExternalRules.md`
§OKX). Не входят прочие поля (`instFamily`, `uly`,
`listTime`/`expTime`, `category`/`alias` и т. п.) — доменно не
используются. Coded `InstrumentOkxResponse` несёт только подмножество,
релевантное идентичности (шаг 1) и правилам (шаг 5).

> **Разграничение.** Один DTO `InstrumentOkxResponse` питает два
> снапшота. Шаг 1: identity-снапшот `InstrumentExternalSnapshot`
> (граница) — идентичность плюс биржевые `state`/`lever`
> (→ `externalStatus`/`externalLeverage`) персистятся на
> `Instrument`; справочные sizing-поля в шаге 1 персистентно не
> хранятся (`docs/models/mapping/Instrument.md`). Шаг 5: rules-снапшот
> модели `InstrumentExternalRules` (sizing/rounding, `ctType`,
> sizes, per-order max sizes, `lever`/`state`) — потребляет
> sizing/rounding-поля и ограничители из этого DTO; на
> base/quote/settle не претендует (дубль Н1 снят). Снапшот-концепция/
> ренейм (INSTR-Q1) и роль leverage/HOLD (часть INSTR-Q2) закрыты
> решением
> `docs/decisions/instrument-external-rules-materialization.md`.
