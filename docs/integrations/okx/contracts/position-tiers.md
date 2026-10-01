# OKX contracts: позиционные тиры (margin tiers)

## На какой вопрос отвечает этот файл

Каков контракт операции чтения позиционных тиров (лимиты размера
позиции, ставки маржи и максимальное плечо по тирам).

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Public
Data → REST API», секция «Get position tiers»). Процедура сверки и
приоритет офдока — `.claude/rules/external-source-sync.md`. Последняя
сверка: 2026-06-11 (поле-уровневая дистилляция).

## Путь эндпоинта

Сторонний скелет указывал `GET /account/position-tiers`; по офдоку
endpoint живёт в **Public Data**: `GET /api/v5/public/position-tiers`
(публичный, без подписи). Манифестная пометка «путь к подтверждению»
снята в пользу публичного пути.

## Статус использования

**Используется частью** (решение 2026-09-30,
`.claude/decisions/entry-liquidation-estimate.md`): коннектор читает тиры
семьи инструмента вторым запросом при каждом чтении правил инструмента, по
`instFamily` из ответа спецификации — по одной семье на запрос
(`docs/models/mapping/InstrumentExternalRules.md`). Тиры изолированной
маржи (`tdMode=isolated`) становятся строками справочных правил
инструмента: `minSz`, `maxSz` и `mmr` каждого тира — операнды оценки цены
ликвидации позиции после акта, создающего риск
(`docs/rules/risk-policy.md`; форма — `docs/spec/risk-limits.json`,
величина `postActMaintenanceMarginRate`). Эндпоинт публичный, и преконтроль
live-вызовов не делает: тиры читаются из персистированных правил, как
прочие ограничения инструмента.

Не используются: `imr`, `maxLever` тира — потолок плеча для преконтроля
остаётся инструмент-уровневым (`InstrumentExternalRules.externalMaxLeverage`
из `lever`); как площадка отвечает на позицию, чьё плечо выше тирового
максимума, корпусом не описано и остаётся предметом сверки, — и поля
займа MARGIN.

## GET /api/v5/public/position-tiers

Rate limit 10 req / 2 s по IP. Query: `instType` (обяз.:
MARGIN/SWAP/FUTURES/OPTION), `tdMode` (обяз.: `cross`/`isolated`),
`instFamily` (обяз. для SWAP/FUTURES/OPTION; до 5 через запятую),
`instId` (MARGIN), `ccy` (cross MARGIN — возвращает лимиты займа),
`tier` (опц., конкретный тир).

### Response (элементы `data[]`)

| Поле | Семантика |
|---|---|
| `tier` | Номер тира. |
| `minSz` / `maxSz` | Границы размера позиции в тире (деривативы — контракты; для `ccy` — границы займа). |
| `imr` / `mmr` | Ставки initial / maintenance margin тира. |
| `maxLever` | Максимальное плечо тира. |
| `uly` / `instFamily` / `instId` | Идентификация инструмента/семейства. |
| `baseMaxLoan` / `quoteMaxLoan` | Лимиты займа (MARGIN). |
| `optMgnFactor` | Маржинальный коэффициент опционов. |
