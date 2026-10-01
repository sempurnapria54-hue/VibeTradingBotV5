# AccountConfigOkxResponse (OKX account configuration)

## На какой вопрос отвечает этот файл

Какие поля у OKX account-config response — ответа с конфигурацией счёта.

## Инвентарь полей

### Используемые (под `BalanceContainer`)

Ответ — `data[0]` операции `GET /api/v5/account/config`
(`docs/integrations/okx/contracts/account-config.md`). Читается вместе со
снимком средств, и оба поля едут в снапшот снимка **сырыми**; в доменный
словарь их переводит граница коннектора при сборке контейнера
(`docs/models/mapping/Balance.md`).

| OKX field | Тип (raw) | Семантика |
|---|---|---|
| `acctLv` | string | режим счёта: `1` Spot / `2` Futures / `3` Multi-currency margin / `4` Portfolio margin. Снапшот — `externalAccountLevel`, домен — `accountMode` |
| `posMode` | string | режим позиций: `net_mode` / `long_short_mode`. Снапшот — `externalPositionMode`, домен — `positionMode` |

### Не используется (отбрасывается на маппинге)

- **`perm`, `ip`, `label`** — права, IP-привязки и метка текущего API-ключа:
  свойства ключа, а не счёта; контур их не читает.
- **`uid`, `mainUid`, `type`, `kycLv`** — идентичность и тип аккаунта.
- **`acctStpMode`** — self-trade prevention: сознательно не используем,
  действует умолчание площадки.
- **`autoLoan`, `enableSpotBorrow`, `spotBorrowAutoRepay`** — заём; контур
  займа не допускает, и его исключает сам режим счёта, а не эти флаги.
- **`ctIsoMode`, `mgnIsoMode`** — режимы переводов изолированной маржи.
- **`feeType`** — валюта списания комиссии, только Spot; для SWAP-контура
  неприменим и рычагом оплаты комиссии сторонним токеном не является
  (`docs/integrations/okx/contracts/account-config.md`).
- **`level`, `levelTmp`** — комиссионный уровень; ось тира ставок читается
  своим эндпоинтом (`docs/models/integrations/okx/TradeFeeOkxResponse.md`).
- **`liquidationGear`, `greeksType`, `opAuth`, `roleType`, `traderInsts`,
  `spotRoleType`, `spotTraderInsts`, `settleCcy`, `settleCcyList`,
  `stgyType`** — вне контура.

## Конвертация

Оба поля приходят строками и в снапшоте остаются ими. Перевод в доменный
словарь — при сборке контейнера; значение вне словаря и пустое значение
дают **пустоту режима**, а не угаданный режим и не отказ чтения
(`docs/models/mapping/Balance.md`).
