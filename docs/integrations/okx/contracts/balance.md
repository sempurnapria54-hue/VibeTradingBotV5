# OKX contracts: balance

## На какой вопрос отвечает этот файл

Каков контракт операции получения баланса.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Trading Account → REST API», секция «Get balance»). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора
(`.claude/processes/api-docs-completion.md`, канал —
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(прогон 1 — соответствие спеке подтверждено).

## Endpoint

`GET /api/v5/account/balance?ccy={settleCurrency}`. Для текущего
`ETH-USDT-SWAP`: `?ccy=USDT`. Назначение — account-level snapshot
баланса + currency-level details по settle currency.

- **Permission:** `Read`.
- **Rate limit:** 10 req / 2 s по User ID.
- **Query:** `ccy` — опционально, одна валюта или список до 20 через
  запятую. Для runtime бота передаётся settle currency инструмента
  (SWAP/USDT risk и sizing требуют обязательную `USDT`-запись).
- **Auth headers (private REST):** `OK-ACCESS-KEY`, `OK-ACCESS-SIGN`,
  `OK-ACCESS-TIMESTAMP`, `OK-ACCESS-PASSPHRASE`,
  `Content-Type: application/json`. Demo trading:
  `x-simulated-trading: 1`.

## Структура response (упрощённо)

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "uTime": "1769253296789",
      "totalEq": "1023.45",
      "adjEq": "1023.45",
      "availEq": "1023.45",
      "details": [
        {
          "ccy": "USDT",
          "uTime": "1769253296789",
          "eq": "1023.45",
          "cashBal": "1023.45",
          "availBal": "900.00",
          "frozenBal": "123.45"
        }
      ]
    }
  ]
}
```

`data` содержит ровно один account snapshot. Поля и список не
маппимых — в `docs/models/integrations/okx/OkxBalanceResponse.md`.
Validation — в `docs/models/mapping/Balance.md`.
