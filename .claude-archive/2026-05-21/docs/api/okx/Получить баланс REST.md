### Получить баланс (REST)

**Endpoint:** `GET /api/v5/account/balanceExternalSnapshot`  
**Query (опционально):**

- `ccy` — одна валюта или список до 20 через запятую (USDT или BTC,ETH).

**Доступ / лимиты / аутентификация:**

- Permission: Read
- Rate limit: 10 requests / 2 seconds, правило — User ID
- Auth headers (REST private):
    - OK-ACCESS-KEY
    - OK-ACCESS-SIGN
    - OK-ACCESS-TIMESTAMP (ISO строка, например 2020-12-08T09:08:57.715Z (текущая в UTC)) Важно, чтобы точно такая же
      строка участвовала в подписи (prehash) и стояла в хидере.
    - OK-ACCESS-PASSPHRASE
    - Content-Type: application/json
- Demo trading: добавить x-simulated-trading: 1 (иначе будет несоответствие окружения/ключа).

***Как считается подпись (важно для “рабочего” реквеста):***

- prehash = timestamp + method + requestPath + body, затем HMAC_SHA256(secret, prehash) и Base64. Для GET body обычно
  пустой.

**Пример запроса:**

```bash
curl -X GET 'https://www.okx.com/api/v5/account/balanceExternalSnapshot?ccy=USDT' \
  -H 'Content-Type: application/json' \
  -H 'OK-ACCESS-KEY: <api_key>' \
  -H 'OK-ACCESS-SIGN: <base64_hmac_sha256_signature>' \
  -H 'OK-ACCESS-TIMESTAMP: 2026-01-24T12:34:56.789Z' \
  -H 'OK-ACCESS-PASSPHRASE: <passphrase>'
  # -H 'x-simulated-trading: 1'   # только для demo

```

**Пример ответа:**

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "uTime": "1769253296789",
      "totalEq": "1023.45",
      "isoEq": "0",
      "adjEq": "1023.45",
      "availEq": "1023.45",
      "ordFroz": "0",
      "imr": "0",
      "mmr": "0",
      "borrowFroz": "0",
      "mgnRatio": "",
      "notionalUsd": "0",
      "notionalUsdForBorrow": "0",
      "notionalUsdForSwap": "0",
      "notionalUsdForFutures": "0",
      "notionalUsdForOption": "0",
      "upl": "0",
      "delta": "0",
      "deltaLever": "0",
      "deltaNeutralStatus": "0",
      "details": [
        {
          "ccy": "USDT",
          "eq": "1023.45",
          "cashBal": "1023.45",
          "availBal": "900.00",
          "uTime": "1769253296789",

          "isoEq": "0",
          "availEq": "0",
          "disEq": "0",
          "fixedBal": "0",
          "frozenBal": "123.45",
          "ordFrozen": "0",

          "liab": "0",
          "upl": "0",
          "uplLiab": "0",
          "crossLiab": "0",
          "isoLiab": "0",

          "mgnRatio": "",
          "imr": "",
          "mmr": "",
          "interest": "0",
          "twap": "0",
          "frpType": "0",
          "maxLoan": "0",
          "eqUsd": "1023.45",
          "borrowFroz": "0",
          "notionalLever": "",
          "stgyEq": "0",
          "isoUpl": "0",

          "spotInUseAmt": "",
          "clSpotInUseAmt": "",
          "maxSpotInUse": "",
          "spotIsoBal": "0",
          "smtSyncEq": "0",
          "spotCopyTradingEq": "0",

          "spotBal": "",
          "openAvgPx": "",
          "accAvgPx": "",
          "spotUpl": "",
          "spotUplRatio": "",
          "totalPnl": "",
          "totalPnlRatio": "",

          "colRes": "0",
          "colBorrAutoConversion": "0",
          "collateralRestrict": false,
          "collateralEnabled": false,
          "autoLendStatus": "off",
          "autoLendMtAmt": "0",
          "rewardBal": "0"
        }
      ]
    }
  ]
}

```

**Описание полей (что они значат):**
***Верхний уровень***

- code — "0" если успех.
- msg — текст ошибки/сообщение.
- data[0] — агрегат по аккаунту (USD-оценки):
    - uTime — время обновления (ms Unix).
    - totalEq — суммарная equity в USD
    - isoEq — equity изолированной маржи в USD (актуально для некоторых режимов).
    - adjEq — “effective/adjusted equity” в USD Это equity, но с поправками OKX для риск-расчётов:
      например, если часть активов считается залогом “не 1 к 1” (есть скидка/дисконт), или есть долги, или режим
      аккаунта требует особых расчётов..
    - availEq — доступная equity на уровне аккаунта (в некоторых режимах).
    - ordFroz — заморожено под ордера (USD).
    - imr / mmr — initial/maintenance margin requirement (USD) для cross-логики.
    - borrowFroz — “потенциальный IMR под заём” (USD), часто "" вне нужных режимов.
    - mgnRatio — margin ratio (может быть "").
    - notionalUsd — Это номинальная стоимость позиций/экспозиции в USD по типам (swap/futures/option/borrow). Для SWAP
      грубо: размер позиции × цена (в USD эквиваленте). Это нужно бирже для риска/лимитов.
    - upl — unrealized PnL на уровне аккаунта (USD) (для multi-ccy/PM). Плавающая прибыль/убыток по открытой позиции, по
      текущей цене. Пока позиция не закрыта — это “на бумаге”. Закрыл позицию → станет realized PnL (зафиксировано).
    - **Поля дельта-нейтрал режима: Delta-neutral стратегия — когда трейдер старается держать суммарную delta около
      нуля, чтобы зарабатывать на арбитраже/фандинге/базисе, а не на направлении цены. OKX отдельно описывает такой
      режим как strategy type: delta neutral.**
    - delta — это показатель, насколько твой портфель/позиции “чувствительны” к движению базового актива (в первую
      очередь актуально для опционов и сложных портфелей).
    - deltaNeutralStatus — по сути включён/активен ли этот режим/стратегия (если ты вообще VIP и используешь).
    - deltaLever — упрощённо “delta с учётом плеча/риска” (биржевой риск-индикатор). Для твоего SWAP-бота обычно это
      пусто и можно игнорировать.
    - details[] — детализация по валютам (data[0].details[i] — по конкретной валюте (например USDT)):
        - **Основные:**
        - ccy — код валюты (например USDT).
        - eq — “сколько у тебя всего по этой валюте” с учётом маржи/оценок (equity).
        - cashBal — фактический баланс валюты (“лежит на счету”).
        - availBal — сколько можно прямо сейчас использовать (не заморожено).
        - uTime — время последнего обновления по этой валюте (в миллисекундах).
        - **Заморозки и “что занято”:**
        - frozenBal — сколько валюты сейчас заморожено (в целом).
        - ordFrozen — сколько заморожено под открытые ордера (чтобы их обеспечить).
        - fixedBal — заморозка под спец-стратегии OKX (Dip/Peak Sniper) — обычно тебе не нужно.
        - **Оценки в USD и “дисконт”:**
        - eqUsd — equity по этой валюте, пересчитанная в USD.
        - disEq — “дисконтированная” оценка в USD (если валюта считается залогом не 1:1).
        - **Изолированная/кросс-маржа и доступная equity:**
        - isoEq — equity в изолированной марже (если применимо).
        - availEq — доступная equity (если применимо).
        - **Долги/заём (маржинальные режимы):**
        - liab — общий долг по этой валюте (если занимал).
        - crossLiab — долг в режиме cross (общий по аккаунту).
        - isoLiab — долг в режиме isolated (по отдельным позициям/изол.марже).
        - interest — начисленные проценты по долгу.
        - maxLoan — сколько максимум можно занять по этой валюте.
        - borrowFroz — “заморозка/резерв” под потенциальный заём (часто пусто).
        - uplLiab — “долг из-за плавающего убытка” в некоторых режимах (часто пусто).
        - **PnL (плавающая прибыль/убыток):**
        - upl — общий плавающий PnL по позициям, связанным с этой валютой (если применимо).
        - isoUpl — плавающий PnL именно в isolated (если применимо).
        - **Требования к марже (чаще для cross/portfolio режимов):**
        - mgnRatio — маржин-рейтинг/соотношение (насколько близко к риску) — часто пусто.
        - imr — требование initial margin (сколько нужно на открытие) — часто пусто.
        - mmr — требование maintenance margin (сколько нужно, чтобы не ликвиднуло) — часто пусто.
        - notionalLever — “эффективное плечо/левередж” по валюте (если применимо).
        - **Forced repayment / risk-метрики OKX (Это штуки из риск-менеджмента биржи: если у тебя маржинальный долг/риск
          становится опасным, биржа может принудительно сокращать риск (например, погашение/конвертация/ограничения).)
          **:
        - twap — индикатор риска принудительного погашения (шкала 0..5).
        - frpType — тип принудительного погашения: 0 нет, 1/2 — разные режимы.
        - stgyEq — “equity стратегии”.
        - **Коллатерал / ограничения залога (Collateral (залог) — активы, которые биржа разрешает использовать как
          обеспечение маржи.Не все активы равны: некоторые считаются с дисконтом или могут быть ограничены (
          нельзя/ограниченно использовать как залог)):**
        - colRes — статус ограничений по залогу со стороны платформы (0/1/2).
        - colBorrAutoConversion — индикатор риска авто-конвертации (0..5).
        - collateralRestrict — устаревшее поле (deprecated), вместо него colRes.
        - collateralEnabled — включён ли режим collateral (для некоторых режимов аккаунта).
        - **Auto-lend (Функция “автоматически сдавать свободные средства в займ”, чтобы получать доход (проценты)):**
        - autoLendStatus — статус автолендинга: unsupported/off/pending/active.
        - autoLendMtAmt — сколько реально “размещено/смэтчено” в автолендинге.
        - **Portfolio / Smart-sync / Copy-trading (Portfolio margin — режим, где риск может считаться “портфелем”, с
          взаимозачётами между активами/позициями (сложнее, чем обычная isolated/cross). Copy-trading / Smart-sync —
          механика копитрейдинга: биржа ведёт отдельные расчёты equity/лимитов для синхронизации между мастером и
          подписчиками. Поэтому там куча полей “для копитрейдинга”):**
        - spotInUseAmt — сколько spot-средств сейчас “используется” как риск-оффсет (portfolio margin).
        - clSpotInUseAmt — пользовательский лимит spot risk offset (portfolio margin).
        - maxSpotInUse — максимально возможный spot risk offset (portfolio margin).
        - spotIsoBal — spot-баланс в isolated контексте (copy-trading/особые режимы).
        - smtSyncEq — smart-sync equity (для copy trader).
        - spotCopyTradingEq — smart-sync equity для spot copy-trading.
        - **Spot PnL/стоимость (Это учёт себестоимости и PnL именно для spot-активов. Eсли торгуешь spot/ведёшь cost
          basis):**
        - spotBal — spot-баланс этой валюты (в единицах валюты).
        - openAvgPx — средняя цена покупки (стоимость) для spot (в USD).
        - accAvgPx — накопленная средняя цена (в USD).
        - spotUpl — spot плавающий PnL (в USD).
        - spotUplRatio — spot плавающий PnL в процентах/доле.
        - totalPnl — суммарный PnL по spot (в USD) за всё время/накопленный.
        - totalPnlRatio — суммарный PnL по spot в процентах/доле.
        - **Прочее:**
        - rewardBal — баланс “trial funds / reward” (бонусные средства).

---