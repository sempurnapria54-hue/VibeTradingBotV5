# OKX contracts: конфигурация счёта и плеча

## На какой вопрос отвечает этот файл

Каков контракт OKX-операций конфигурации счёта: чтение конфигурации
(`account/config`), режим позиций (`set-position-mode`), плечо
(`set-leverage`, `leverage-info`).

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секции «Get account
configuration», «Set position mode», «Set leverage», «Get leverage»).
При расхождении с офдоком побеждает офдок; синхронизация —
перевыкачка + дифф при каждом заходе интегратора по источнику и по
задаче «актуализируй» (`.claude/processes/api-docs-completion.md`,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется. Форвард-кандидат **В-9** (шаг 5 / bootstrap):
старт-валидация посылок адаптера — адаптер шлёт константы
`tdMode=isolated`, `posSide=net` (`rules/adapter-constants.md`) как
данность; `GET /account/config` позволяет подтвердить `posMode` и
режим счёта на старте, `set-*` — привести к ожидаемым. Смежно:
INSTR-Q2 (кто и когда выставляет плечо), В-2 (применимость
precheck по `acctLv`).

## GET /api/v5/account/config

Permission `Read`; rate limit 5 req / 2 s по User ID. Без параметров.
Ответ `data[0]` — полная конфигурация:

| Поле | Семантика |
|---|---|
| `acctLv` | Режим счёта: `1` Spot / `2` Futures / `3` Multi-currency margin / `4` Portfolio margin. |
| `posMode` | Режим позиций: `long_short_mode` / `net_mode` (FUTURES/SWAP). Посылка адаптера — `net`. |
| `perm` | Права текущего API-ключа: `read_only` / `trade` / `withdraw` (через запятую). |
| `ip` | IP-привязки текущего ключа (`""` — без привязки). |
| `uid` / `mainUid` | ID аккаунта / главного аккаунта (равны — мы на главном). |
| `acctStpMode` | Аккаунт-уровневый self-trade prevention: `cancel_maker` (default) / `cancel_taker` / `cancel_both` (см. В-5: STP сознательно не используем — действует биржевой default). |
| `autoLoan` | Авто-заём в мульти-валютной марже. |
| `ctIsoMode` / `mgnIsoMode` | Режим переводов маржи isolated-деривативов / isolated-маржи (automatic / autonomy / quick_margin...). |
| `greeksType` | Формат греков (PA/BS) — OPTION. |
| `feeType` | Валюта списания комиссии: `0` валюта получения / `1` котируемая. |
| `level` / `levelTmp` | Fee tier (и временный). |
| `kycLv` | KYC-уровень главного аккаунта. |
| `label` | Метка текущего API-ключа. |
| `liquidationGear` | Порог алертов margin ratio. |
| `roleType` / `traderInsts`, `spotRoleType` / `spotTraderInsts` | Копитрейдинг-роли (не используем). |
| `opAuth` | Активирована ли торговля опционами. |
| `enableSpotBorrow` / `spotBorrowAutoRepay` | Спот-заём (Spot mode). |
| `type` | Тип аккаунта (main / sub-варианты). |
| `settleCcy` / `settleCcyList` | Валюта (и список) расчёта USD-маржинальных контрактов. |
| `stgyType` | Тип стратегии счёта: general / delta neutral. |

## POST /api/v5/account/set-position-mode

Permission `Trade`; rate limit 5 req / 2 s по User ID. Body:
`posMode` = `long_short_mode` | `net_mode` (FUTURES/SWAP). Ответ —
эхо `posMode`. Portfolio margin поддерживает только net.

## POST /api/v5/account/set-leverage

Permission `Trade`; rate limit 20 req / 2 s по User ID. Body:
`lever` (обяз.), `mgnMode` (обяз., `isolated`/`cross`; только
`cross`, если передан `ccy`), `instId` / `ccy` (условно — уровень
применения), `posSide` (обяз. только для isolated long/short
FUTURES/SWAP). Офдок перечисляет 11 сценариев уровня применения
(pair / currency / underlying / contract level); SWAP-relevant:

- isolated SWAP, net: `instId` + `mgnMode=isolated` (contract
  level) — наш случай при ручном выставлении плеча;
- isolated SWAP, long/short: + `posSide`;
- cross SWAP: `instId` + `mgnMode=cross` (contract level).

Ответ — эхо `lever`/`mgnMode`/`instId`/`posSide`.

## GET /api/v5/account/leverage-info

Permission `Read`; rate limit 20 req / 2 s по User ID. Query:
`mgnMode` (обяз.), `instId` (до 20 через запятую) / `ccy` (cross
MARGIN валютного уровня). Ответ: `instId`, `ccy`, `mgnMode`,
`posSide` (в long/short-режиме — обе стороны отдельными записями),
`lever`. Для cross-позиций Expiry/Perpetual Futures под PM плечо не
запрашивается (офдок).
