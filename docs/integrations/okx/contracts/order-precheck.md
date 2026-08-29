# OKX contracts: order precheck

## На какой вопрос отвечает этот файл

Каков контракт операции order precheck (серверная пре-оценка влияния
ордера на счёт до постановки).

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Order Book Trading → Trade», секция «POST / Order
precheck»). При расхождении с офдоком побеждает офдок; синхронизация
— перевыкачка + дифф при каждом заходе интегратора по источнику и по
задаче «актуализируй» (`.claude/processes/api-docs-completion.md`,
канал чтения — `.claude/skills/integration-okx.md`). Последняя
сверка: 2026-06-11 (прогон 3, поле-уровневая дистилляция).

## Статус использования

**Не используется (решено на шаге 5).** Шаг 5 делает **собственный**
преконтроль (`RiskValidator` читает persisted `InstrumentExternalRules`, в
биржу за ограничениями не ходит). Серверный `order-precheck` неприменим в
нашем режиме маржи (isolated/Futures, `acctLv=2` — ограничение ниже) и в
фазе 1 не используется; door-open при смене режима. Решение —
`docs/models/domain/other/InstrumentExternalRules.md`.

## Ограничение применимости (офдок)

«Only applicable to **Multi-currency margin mode** and **Portfolio
margin mode**» — precheck работает только при `acctLv = 3 | 4`
(`account-config.md`). Для счёта в Spot/Futures mode endpoint
неприменим. Следствие для шага 5: серверный precheck не замена
собственному преконтролю; применимость проверять по
`GET /account/config` на bootstrap (см. **В-9**).

## Endpoint

`POST /api/v5/trade/order-precheck`. Permission `Trade`; rate limit
5 req / 2 s по User ID.

### Request

Подмножество полей place order: `instId`, `tdMode`, `side`,
`posSide` (long/short-режим), `ordType`, `sz`, `px`, `reduceOnly`,
`tgtCcy`, `attachAlgoOrds` (TP/SL/trailing — вкл. `callbackRatio`/
`callbackSpread`/`activePx`). Состав и семантика полей — `order.md`,
`OkxOrderResponse.md`.

### Response (`data[0]`) — снапшот «до / после»

Пары «текущее значение / изменение после ордера»:

| Поле | Семантика |
|---|---|
| `adjEq` / `adjEqChg` | Скорректированный (эффективный) эквити, USD. |
| `imr` / `imrChg` | Initial margin requirement, USD. |
| `mmr` / `mmrChg` | Maintenance margin requirement, USD. |
| `mgnRatio` / `mgnRatioChg` | Maintenance margin ratio. |
| `availBal` / `availBalChg` | Доступный баланс (auto-borrow выкл.). |
| `liqPx` | Текущая оценка цены ликвидации. |
| `liqPxDiff` / `liqPxDiffRatio` | Дистанция (и доля) от оценки ликвидации до mark price после ордера. |
| `liab` / `liabChg` / `liabChgCcy` | Обязательства и их изменение (cross / isolated). |
| `posBal` / `posBalChg`, `type` | Положительный актив isolated-MARGIN-позиции и тип его валюты. |
