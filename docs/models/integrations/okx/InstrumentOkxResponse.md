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

**Перечень — целевой, не свершившийся** (H13 `DOCS_CHECK_12`). Он
описывает состав DTO **после** дельты `CODE` шага 7; в коде сегодня часть
позиций отсутствует, и они помечены явно. Прежняя редакция была написана
свершившимся временем («coded DTO несёт…»), из-за чего единственная работа,
у которой нет колонки-якоря, выглядела уже сделанной и ни в один перечень
дельты не попадала. Правило формулировки — то же, по которому целевым
временем записан `REFRESH_FILLS` (H15 `GAPS_CLOSE_6`).

Coded DTO `InstrumentOkxResponse` несёт подмножество, релевантное
идентичности инструмента (шаг 1), sizing/rounding-правилам
(шаг 5) **и валютам (шаг 7)**. Один DTO питает оба снапшота:
identity-снапшот `InstrumentExternalSnapshot` (шаги 1 и 7) и rules-снапшот
`InstrumentExternalRules` (шаг 5).

Identity/spec-поля, маппящиеся в `InstrumentExternalSnapshot`:

| OKX field | Тип (raw) | Snapshot field | Состояние |
|---|---|---|---|
| `instId` | string | `externalInstrumentId` | есть |
| `instType` | string | `externalInstrumentType` | есть |
| `baseCcy` | string | `externalBaseCurrency` | есть; **имя целевое** — см. §ниже |
| `quoteCcy` | string | `externalQuoteCurrency` | есть; **имя целевое** |
| `settleCcy` | string | `externalSettlementCurrency` | есть; **имя целевое** |
| `lotSz` | string (decimal) | `lotSize` | есть |
| `minSz` | string (decimal) | `minimumOrderSize` | есть |
| `ctVal` | string (decimal) | `contractValue` | есть |
| `ctMult` | string (decimal) | `contractMultiplier` | есть |
| `tickSz` | string (decimal) | `priceTickSize` | есть |
| `state` | string | `externalStatus` | есть |
| `lever` | string | `externalLeverage` | есть |

**Имена валютных полей снапшота — целевые** (H14 `DOCS_CHECK_12`, решение
пользователя, вариант 2). В коде снапшот сегодня несёт
`externalSettleCurrency` / `externalBaseCurrency` / `externalQuoteCurrency`,
а доменные поля названы `externalSettlementCurrency` / `externalBaseCurrency`
/ `externalQuoteCurrency` (`docs/decisions/instrument-currencies-home.md` —
имена доменных полей окончательны). Расходилось **трояко**: инвентарь нёс
третий набор (`settleCurrency`/`baseCurrency`/`quoteCurrency`). Решение —
**переименовать поле снапшота в коде** под доменное имя: снапшот
транзиентный ⇒ ни миграции, ни бэкфилла, зато маппинг остаётся по имени
(без явного `@Mapping`) и один факт зовётся одинаково во всех слоях.
Переименование внесено в **не-схемную дельту `CODE`**
(`docs/decisions/pnl-finalization-mechanics.md` §Следствия).

Rules-поля (sizing/rounding/ограничители), питающие rules-снапшот
шага 5 (см. `docs/models/mapping/InstrumentExternalRules.md` §OKX):

| OKX field | Тип (raw) | Назначение | Состояние |
|---|---|---|---|
| `ctType` | string | тип контракта (linear/inverse) | есть |
| `ctValCcy` | string | валюта стоимости контракта | есть |
| `maxLmtSz` | string (decimal) | макс. размер limit-ордера | есть |
| `maxMktSz` | string (decimal) | макс. размер market-ордера | есть |
| `maxTriggerSz` | string (decimal) | макс. размер trigger-ордера | есть |
| `maxStopSz` | string (decimal) | макс. размер stop-ордера | есть |
| `groupId` | string | id комиссионной группы инструмента; **ключ резолва ставки** — пара (`instType`, `groupId`) | **целевое: в коде поля нет** — ни в DTO, ни в rules-снапшоте, ни в модели навеса |

**`groupId` — целевая дельта `CODE`, и она внесена в перечень** (H13
`DOCS_CHECK_12`). Поле живёт в JSONB-навесе, собственной колонки не имеет
⇒ в schema-дельту шага не попадает **по построению**, и ни один
самопроверяемый перечень пропуск не ловит. Поэтому добыча ключа записана
отдельной строкой **не-схемной дельты `CODE`**
(`docs/decisions/pnl-finalization-mechanics.md` §Следствия): поле DTO + поле
`InstrumentExternalRulesExternalSnapshot` + строка маппера. Цена: миграций
не требует, новых вызовов биржи не добавляет (`/public/instruments` уже
читается). **Без этого** `externalFeeGroupId` остаётся `null`, ставка не
резолвится и `FEE_RATE_UNAVAILABLE` блокирует **каждый** risk-creating вход
(`docs/models/domain/other/InstrumentExternalRules.md`).

**`groupId` — ключ, а не ставка.** Инструмент несёт только id своей
комиссионной группы; сама ставка приходит отдельным эндпоинтом
`GET /api/v5/account/trade-fee` и живёт в своей модели `TradeFeeRate` (одна
строка на группу), не копией на инструменте
(`docs/models/domain/other/TradeFeeRate.md` §«Масштаб модели»,
`docs/decisions/pnl-finalization-mechanics.md` реш.4). Офдок (Get instruments
→ Response Parameters, «Instrument trading fee group ID»): «instType and
groupId should be used together to determine a trading fee group. Users should
use this endpoint together with fee rates endpoint to get the trading fee of a
specific symbol». Native-поля ставки —
`docs/models/integrations/okx/OkxTradeFeeResponse.md`; контракт —
`docs/integrations/okx/contracts/trade-fee.md`.

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
§OKX). **`groupId` — входит целевым составом** (шаг 7, `GAPS_CLOSE_3`;
формулировка уточнена H13 `DOCS_CHECK_12`): прежде он числился среди
неиспользуемых — ошибочно, ключ fee-группы нужен для резолва ставки; в коде
поля пока **нет**, его добыча — строка не-схемной дельты `CODE`. Не входят
прочие поля (`instFamily`, `uly`,
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
