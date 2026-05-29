# OkxInstrumentResponse (OKX instrument spec)

## На какой вопрос отвечает этот файл

Какие поля у OKX instrument response — что приходит от биржи и что
из этого используется.

## Контекст

Нативная модель источника OKX (Java `InstrumentResponse`).
Возвращается `GET /api/v5/public/instruments`. Не выходит за
`ClientService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `InstrumentExternalSnapshot` и далее в `Instrument`
(идентичность + справочные поля) — см.
`docs/models/domain/core/Instrument.md` и класс
`domain.model.core.instrument.external_snapshot.InstrumentExternalSnapshot`.
Контракт endpoint'а / rate limit —
`docs/integrations/okx/contracts/instrument.md`.

## Поля DTO

Coded DTO `InstrumentResponse` несёт подмножество, релевантное
снапшоту инструмента; все поля маппятся в
`InstrumentExternalSnapshot`:

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

Числа OKX приходят строками; в snapshot — `BigDecimal`.

## Поля, которые НЕ входят в этот DTO

OKX `public/instruments` отдаёт больше полей. Часть из них —
торговые правила инструмента (`state`, `lever`, `ctType`,
`ctValCcy`, `maxLmtSz`/`maxMktSz`/`maxTriggerSz`/`maxStopSz` и др.)
— потребляются отдельной моделью `InstrumentExternalRules` (см.
`docs/models/mapping/InstrumentExternalRules.md` §OKX); прочие
(`instFamily`, `uly`, `listTime`/`expTime`, `category`/`alias` и
т. п.) доменно не используются. Coded `InstrumentResponse` несёт
только snapshot-релевантное подмножество.

> Разграничение `InstrumentExternalSnapshot` (этот DTO) ↔
> `InstrumentExternalRules` (богаче полями, отдельный DTO/маппинг),
> устранение дублирования справочных полей — на доработке
> (`DOCS_CHECK_2`, backlog п.9).
