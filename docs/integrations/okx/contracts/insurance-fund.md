# OKX contracts: страховой фонд (security fund)

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции чтения баланса страхового фонда
(`insurance-fund`; в офдоке — «security fund»).

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Public Data → REST API», секция «Get security fund»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора по источнику и по задаче
«актуализируй» (`.claude/processes/api-docs-completion.md`, канал
чтения — `.claude/skills/integration-okx.md`). Последняя сверка:
2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется (рыночный контекст ADL-риска; прямой потребности
фазы 1 нет).

## GET /api/v5/public/insurance-fund

Rate limit 10 req / 2 s по IP. Query: `instType` (обяз.:
MARGIN/SWAP/FUTURES/OPTION), `instFamily` (обяз. для
FUTURES/SWAP/OPTION), `ccy` (обяз. для MARGIN), `type` (опц.:
`regular_update` / `liquidation_balance_deposit` / `bankruptcy_loss`
/ `platform_revenue` / `adl`; default — все), `after`/`before` по
`ts`, `limit` ≤ 100.

### Response

| Поле | Семантика |
|---|---|
| `total` | Совокупный баланс фонда, USD. |
| `instFamily` / `instType` | Скоуп фонда. |
| `details[]` | Записи: `balance` (баланс), `ccy`, `type`, `amt` (изменение — для liquidation_balance_deposit / bankruptcy_loss / platform_revenue, генерится раз в сутки ~08:00 UTC; для regular_update — `""`), `maxBal` / `maxBalTs` (максимум за 8 ч — только type=adl), `adlType` (события ADL: rate_adl_start / bal_adl_start / pos_adl_start / adl_end), `ts`. `decRate` — deprecated. |
