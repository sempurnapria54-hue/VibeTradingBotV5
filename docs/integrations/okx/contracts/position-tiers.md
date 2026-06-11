# OKX contracts: позиционные тиры (margin tiers)

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции чтения позиционных тиров (лимиты размера
позиции, ставки маржи и максимальное плечо по тирам).

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Public Data → REST API», секция «Get position tiers»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`, канал
чтения — `.claude/skills/integration-okx.md`). Последняя сверка:
2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Уточнение пути (находка прогона 3)

Сторонний скелет указывал `GET /account/position-tiers`; по офдоку
endpoint живёт в **Public Data**: `GET /api/v5/public/position-tiers`
(публичный, без подписи). Манифестная пометка «путь к подтверждению»
снята в пользу публичного пути.

## Статус использования

Не используется. Релевантность — риск-движок (шаг 5): `maxLever` по
тиру — реальный потолок плеча от размера позиции (вход RISK-Q2 —
worst-case guard); `maxSz` — кандидат позиционного лимита.

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
