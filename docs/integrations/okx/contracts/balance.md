# OKX contracts: balance

## На какой вопрос отвечает этот файл

Каков контракт операции получения баланса.

## Источник правды вне репозитория

Официальный док OKX (`https://www.okx.com/docs-v5/en/`, раздел «Trading
Account → REST API», секция «Get balance»; семантика `uTime` счёта —
также «Trading Account → WebSocket», секция «Account channel»). Процедура
сверки и приоритет офдока — `.claude/rules/external-source-sync.md`.
Последняя сверка: 2026-09-30 (семантика двух `uTime` ответа; наблюдением на
demo не подтверждена).

## Endpoint

`GET /api/v5/account/balance?ccy={settleCurrency}`. Для текущего
`ETH-USDT-SWAP`: `?ccy=USDT`. Назначение — account-level snapshot
баланса + currency-level details по settle currency.

- **Permission:** `Read`.
- **Rate limit:** 10 req / 2 s по User ID.
- **Query:** `ccy` — опционально, одна валюта или список до 20 через
  запятую. Для runtime бота передаётся settle currency инструмента
  (SWAP/USDT risk и sizing требуют обязательную `USDT`-запись).
- **Auth (private REST):** подпись и её заголовки —
  `docs/integrations/okx/rules/request-signing.md`; контур demo —
  `docs/integrations/okx/contracts/service-urls.md`;
  `Content-Type: application/json`.

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
          "uTime": "1769228737644",
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
маппимых — в `docs/models/integrations/okx/BalanceOkxResponse.md`.
Validation — в `docs/models/mapping/Balance.md`.
