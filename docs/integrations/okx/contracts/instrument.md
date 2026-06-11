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
`.claude/skills/integration-okx.md`). Последняя сверка: 2026-06-11
(существование/путь + пример офдока по манифесту; поле-уровневая
перевычитка — при заходе по теме).

## Контекст

Mapping в `InstrumentExternalRules` —
`docs/models/mapping/InstrumentExternalRules.md` (раздел `## OKX`).
Доменная модель — `docs/models/domain/other/InstrumentExternalRules.md`.
Обновляет правила — `docs/components/InstrumentExternalRulesSyncJob.md`.

## Endpoint

`GET /api/v5/public/instruments`. Permission: Public (auth не нужен).
Rate limit: 20 req / 2 s по IP + Instrument Type. Query: `instType`
обязателен (`SPOT`/`MARGIN`/`SWAP`/`FUTURES`/`OPTION`), `instId` опц.
для точечного запроса.

WS-альтернатива (на первом этапе не используется): public канал
`instruments` — событие приходит по одному/нескольким инструментам с
теми же полями, что REST, плюс `uTime`. На обновление adapter
перезаписывает поля спецификации и обновляет `sourceUpdatedAt`.
Подробнее по WS-каналам — OKX-Q4 в open-questions.
