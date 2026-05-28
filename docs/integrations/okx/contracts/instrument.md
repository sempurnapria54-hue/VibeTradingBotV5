# OKX contracts: instrument

## На какой вопрос отвечает этот файл

Каков контракт OKX-операции получения спецификации инструмента.

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
