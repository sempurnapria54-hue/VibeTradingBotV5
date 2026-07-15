# OKX contracts: instrument

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции получения спецификации инструмента.

## Внешний источник правды

Дистиллят официального дока OKX (`https://www.okx.com/docs-v5/en/`,
раздел «Public Data → REST API», секция «Get instruments»; появился
также приватный `GET /api/v5/account/instruments` — «Trading Account
→ Get instruments», вне периметра: используем публичный). При
расхождении с офдоком побеждает офдок; синхронизация — перевыкачка +
дифф при каждом заходе интегратора
(`.claude/processes/api-docs-completion.md` §4a, канал —
`.claude/skills/integration-okx.md`). Последняя сверка: **2026-07-14**
(прогон интегратора, `GAPS_CLOSE_3` шага 7 — сверка fee-полей
`groupId`; прежняя сверка 2026-06-11 — существование/путь + пример
офдока по манифесту).

## Контекст

Mapping в `InstrumentExternalRules` —
`docs/models/mapping/InstrumentExternalRules.md` (раздел `## OKX`).
Доменная модель — `docs/models/domain/other/InstrumentExternalRules.md`.
Обновляет правила — `docs/components/InstrumentExternalRulesSyncJob.md`.
Native-поля ответа — `docs/models/integrations/okx/InstrumentOkxResponse.md`.

## `groupId` — ключ комиссионной группы инструмента

`public/instruments` отдаёт `groupId` — «Instrument trading fee group ID»
(офдок: Get instruments → Response Parameters). Это **ключ**, по которому
резолвится ставка комиссии, а не сама ставка: ставку отдаёт отдельный
эндпоинт `GET /api/v5/account/trade-fee`
(`docs/integrations/okx/contracts/trade-fee.md`).

Офдок там же: «instType and groupId should be used together to determine a
trading fee group. Users should use this endpoint together with fee rates
endpoint to get the trading fee of a specific symbol». Отсюда **ось резолва —
пара (`instType`, `groupId`)**, не голый `groupId`: одно и то же число значит
разное при разном `instType`.

На инструменте оседает только ключ (`InstrumentExternalRules.externalFeeGroupId`);
дом ставки — `TradeFeeRate`, одна строка на группу
(`docs/models/domain/other/TradeFeeRate.md`,
`docs/decisions/pnl-finalization-mechanics.md` реш.4).

## Endpoint

`GET /api/v5/public/instruments`. Permission: Public (auth не нужен).
Rate limit: 20 req / 2 s по IP + Instrument Type. Query: `instType`
обязателен (`SPOT`/`MARGIN`/`SWAP`/`FUTURES`/`OPTION`), `instId` опц.
для точечного запроса.

**Рантайм (2026-06-19, demo, контур source-api / M1; провенанс
`рантайм`):** несуществующий `instId` (при валидном `instType`) →
**реджект** `51001` «Instrument ID … doesn't exist», а не `code=0` с
пустым `data`. Поведение совпадает с ticker/candles/mark-price.
Пропуск `instType` → `50014`; битый `instType` → `51000`.

WS-альтернатива (на первом этапе не используется): public канал
`instruments` — событие приходит по одному/нескольким инструментам с
теми же полями, что REST, плюс `uTime`. На обновление adapter
перезаписывает поля спецификации и обновляет `sourceUpdatedAt`.
Подробнее по WS-каналам — OKX-Q4 в open-questions.
