# OkxInstrumentResponse (OKX instrument spec)

## На какой вопрос отвечает этот файл

Какие поля у OKX instrument response — что приходит от биржи и что
из этого используется.

## Контекст

Нативная модель источника OKX (Java `InstrumentResponse`).
Возвращается `GET /api/v5/public/instruments`. Не выходит за
`ClientService`/adapter — `docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `InstrumentExternalSnapshot` и далее в `Instrument`
(идентичность + биржевые `externalStatus`/`externalLeverage` +
справочные поля) — см.
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
| `state` | string | `externalStatus` |
| `lever` | string | `externalLeverage` |

Числовые spec-поля OKX (`lotSz`/`minSz`/`ctVal`/`ctMult`/`tickSz`)
приходят строками; в snapshot — `BigDecimal`. Биржевые `state`/
`lever` остаются сырыми строками (`externalStatus`/
`externalLeverage`) и в шаге 1 персистятся на `Instrument` (см.
`docs/models/mapping/Instrument.md`).

## Поля, которые НЕ входят в этот DTO

OKX `public/instruments` отдаёт больше полей. Часть из них —
sizing/rounding-правила инструмента (`ctType`, `ctValCcy`,
`maxLmtSz`/`maxMktSz`/`maxTriggerSz`/`maxStopSz` и др.) —
потребляются отдельной моделью `InstrumentExternalRules` (см.
`docs/models/mapping/InstrumentExternalRules.md` §OKX); прочие
(`instFamily`, `uly`, `listTime`/`expTime`, `category`/`alias` и
т. п.) доменно не используются. Биржевые `state`/`lever` теперь
входят в этот DTO (→ `externalStatus`/`externalLeverage`, см.
таблицу выше) и в шаге 1 через `InstrumentExternalRules` не идут.
Coded `InstrumentResponse` несёт только snapshot-релевантное
подмножество.

> **Разграничение (шаг 1).** Этот DTO — источник для транзиентного
> `InstrumentExternalSnapshot` (граница). Идентичность плюс биржевые
> `state`/`lever` (→ `externalStatus`/`externalLeverage`)
> персистятся на `Instrument`; справочные sizing-поля в шаге 1
> персистентно не хранятся (`docs/models/mapping/Instrument.md`).
> Модель `InstrumentExternalRules` (sizing/rounding, `ctType`,
> sizes) отложена за пределы шага 1 (backlog п.9) и на
> base/quote/settle больше не претендует — дубль Н1 снят.
> Соотнесение снапшота с `InstrumentExternalRules` / возможный
> ренейм — INSTR-Q1; соотнесение биржевых `externalStatus`/
> `externalLeverage` с rules-полями и валидация плеча — INSTR-Q2.
