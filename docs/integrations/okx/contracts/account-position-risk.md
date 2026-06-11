# OKX contracts: риск-снапшот аккаунта и позиций

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции `account-position-risk` — одновременный
снапшот балансов и позиций аккаунта.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секция «Get account and
position risk»). При расхождении с офдоком побеждает офдок;
синхронизация — перевыкачка + дифф при каждом заходе интегратора по
источнику и по задаче «актуализируй»
(`.claude/processes/api-docs-completion.md`, канал чтения —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(прогон 3, поле-уровневая дистилляция).

## Статус использования

Не используется (низкий приоритет). Отличие от пары
`balance.md` + `position.md`: балансы и позиции возвращаются **одним
временным срезом** («on the same time snapshot») — полезно для
консистентных сверок (anomaly/санити), когда раздельные запросы дают
рассинхрон.

## GET /api/v5/account/account-position-risk

Permission `Read`; rate limit 10 req / 2 s по User ID. Query:
`instType` (опц.).

### Response (`data[0]`)

| Поле | Семантика |
|---|---|
| `ts` | Время среза. |
| `adjEq` | Скорректированный/эффективный эквити в USD (только Multi-currency / Portfolio margin). |
| `balData[]` | Балансы: `ccy`, `eq` (эквити валюты), `disEq` (discount equity, USD). |
| `posData[]` | Позиции: `instType`, `instId`, `mgnMode`, `posId`, `posSide` (long/short/net), `pos` (контракты; isolated-перевод может дать запись с `pos=0`), `posCcy` (MARGIN), `ccy` (валюта маржи), `notionalCcy` / `notionalUsd` (нотинал в монете / USD). `baseBal`/`quoteBal` — deprecated (Quick Margin). |
