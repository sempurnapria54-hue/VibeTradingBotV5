# InstrumentExternalRulesSyncJob

## На какой вопрос отвечает этот файл

Кто обновляет внешние правила инструмента (компонент-job): что делает,
источник, частота.

## Назначение

`InstrumentExternalRulesSyncJob` обновляет `InstrumentExternalRules` (см.
`docs/models/other/InstrumentExternalRules.md`) из REST биржи. Источник:
`GET /api/v5/public/instruments`.

На первом этапе обновление только через REST; WebSocket для instruments
можно добавить позже. `InstrumentExternalRules` меняется редко, поэтому
хранится в БД как актуальный snapshot правил инструмента.

## Делает

- читает правила инструмента из REST;
- маппит client-модель в `InstrumentExternalRulesExternalSnapshot`,
  затем в `InstrumentExternalRules` (OKX-маппинг —
  `docs/client/okx/rules/okx-instrument-mapping.md`);
- сохраняет/обновляет актуальный snapshot правил.

## Связи

Результат используется для округления цены/размера, расчёта размера в
контрактах, проверки min/max limits, биржевого max leverage и
торгуемости инструмента (`status`).
