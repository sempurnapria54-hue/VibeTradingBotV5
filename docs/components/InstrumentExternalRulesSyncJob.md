# InstrumentExternalRulesSyncJob

## На какой вопрос отвечает этот файл

Кто обновляет внешние правила инструмента (компонент-job): что делает,
источник, частота.

## Назначение

`InstrumentExternalRulesSyncJob` обновляет `InstrumentExternalRules` (см.
`docs/models/domain/other/InstrumentExternalRules.md`) из REST биржи. Источник:
`GET /api/v5/public/instruments`.

> **Отложено за пределы шага 1.** Джоба готовит
> `InstrumentExternalRules` — модель, отложенную за шаг 1
> (округление/sizing/риск — поздние шаги; backlog п.9 / отложенная
> rules-подсистема). В активную оркестрацию шага 1
> (`docs/processes/candle-loading.md`,
> `docs/processes/market-data-calculation.md`) **не входит**;
> материализуется вместе с правилами на поздних шагах. Идентичность
> инструмента в шаге 1 синхронизируется отдельно (онбординг —
> `docs/lifecycles/Instrument.md`, граница —
> `docs/models/mapping/Instrument.md`), через тот же endpoint
> спецификации. Соотнесение rules со снапшот-концепцией / возможный
> ренейм — INSTR-Q1.

На первом этапе обновление только через REST; WebSocket для instruments
можно добавить позже. `InstrumentExternalRules` меняется редко, поэтому
хранится в БД как актуальный snapshot правил инструмента.

## Делает

- читает правила инструмента из REST;
- маппит client-модель в `InstrumentExternalRulesExternalSnapshot`,
  затем в `InstrumentExternalRules` (OKX-маппинг —
  `docs/models/mapping/InstrumentExternalRules.md`);
- сохраняет/обновляет актуальный snapshot правил.

## Связи

Результат используется для округления цены/размера, расчёта размера в
контрактах, проверки min/max limits, биржевого max leverage и
торгуемости инструмента (`status`).
