# OkxBalanceResponse (OKX account balance)

## На какой вопрос отвечает этот файл

Какие поля у OKX account balance response — что приходит от биржи и
что из этого используется.

## Контекст

Нативная модель источника OKX. Возвращается `GET
/api/v5/account/balance`. Не выходит за `ClientService`/adapter —
`docs/rules/raw-exchange-dto-boundary.md`.

Mapping в `BalanceContainerExternalSnapshot` /
`BalanceExternalSnapshot` и далее в `BalanceContainer`/`Balance` —
`docs/models/mapping/Balance.md` (раздел `## OKX`). Доменная модель и
freshness/risk-семантика — `docs/models/domain/core/BalanceContainer.md`.
Контракт endpoint'а / rate limits / validation —
`docs/integrations/okx/contracts/balance.md`. Правила OKX —
`docs/integrations/okx/rules/`.

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

`data` содержит ровно один account snapshot. OKX может вернуть много
больше полей — не все попадают в домен или normalized snapshot.

## Поля, которые используются (account-level, `data[0]`)

| OKX field | Тип (raw) | Назначение |
|---|---|---|
| `uTime` | string (epoch millis) | Время обновления account snapshot. |
| `totalEq` | string (decimal) | Total equity аккаунта. |
| `adjEq` | string (decimal) | Adjusted / effective equity. |
| `availEq` | string (decimal) | Account-level available equity. |
| `details` | array | Currency-level записи (см. ниже). |

## Поля, которые используются (currency-level, `details[*]`)

| OKX field | Тип (raw) | Назначение |
|---|---|---|
| `ccy` | string | Валюта (например, `USDT`). |
| `uTime` | string (epoch millis) | Время обновления currency snapshot. |
| `eq` | string (decimal) | Equity по валюте. |
| `cashBal` | string (decimal) | Cash balance по валюте. |
| `availBal` | string (decimal) | Available balance по валюте. |
| `frozenBal` | string (decimal) | Frozen balance по валюте. |

Числа OKX приходят строками. Обязательные числовые строки должны
парситься в `BigDecimal`; пустая строка в обязательном поле
недопустима.

## Поля, которые НЕ маппятся в домен

Validation-only / не нужные v1 runtime (остаются внутри raw DTO и
adapter-layer): `isoEq`, `ordFroz`, `imr`, `mmr`, `borrowFroz`,
`mgnRatio`, `notionalUsd` и его breakdown, `upl`, `delta`,
`deltaLever`, `deltaNeutralStatus`, `liab`, `uplLiab`, `crossLiab`,
`isoLiab`, `interest`, `twap`, `frpType`, `maxLoan`, `eqUsd`,
`notionalLever`, `stgyEq`, `isoUpl`, `spotInUseAmt`, `clSpotInUseAmt`,
`maxSpotInUse`, `spotIsoBal`, `smtSyncEq`, `spotCopyTradingEq`,
`spotBal`, `openAvgPx`, `accAvgPx`, `spotUpl`, `spotUplRatio`,
`totalPnl`, `totalPnlRatio`, `colRes`, `colBorrAutoConversion`,
`collateralRestrict`, `collateralEnabled`, `autoLendStatus`,
`autoLendMtAmt`, `rewardBal`.
