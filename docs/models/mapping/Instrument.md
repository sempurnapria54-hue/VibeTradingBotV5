# Instrument — mapping между слоями

## На какой вопрос отвечает этот файл

Как `Instrument` переходит между слоями (источник ↔
`InstrumentExternalSnapshot` ↔ domain) и что из снапшота
персистится в шаге 1.

## Контекст

Mapping-слой для `Instrument`. Доменная модель —
`docs/models/domain/core/Instrument.md`. Граничный снапшот —
`InstrumentExternalSnapshot` (транзиентный, единственное, что
выходит за `ClientService`/adapter; см.
`docs/rules/raw-exchange-dto-boundary.md`). Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`. Контракт
endpoint'а — `docs/integrations/okx/contracts/instrument.md`.
Инвентарь полей источника —
`docs/models/integrations/okx/OkxInstrumentResponse.md`.

Текущие источники: **OKX**.

## Source-agnostic ядро

### Mapping-flow

source ответ → `InstrumentExternalSnapshot` (транзиентный:
идентичность + справочные поля спецификации) → domain `Instrument`.

### Что персистится в шаге 1 = только идентичность

В шаге 1 (поток рыночных данных) снапшот→domain резолвит **только
идентичность** инструмента: `externalId`, `externalType` (+
конфигурируемые `marginMode`/`leverage`/`externalMarginMode`).
Статус домена (`Instrument.Status`) — онбординг-статус системы
(`docs/lifecycles/Instrument.md`), он **не** проекция биржевого
`state` (биржевой `state` резолвится в `InstrumentExternalRules`,
которая за пределами шага 1).

Справочные поля спецификации (base/quote/settle currency, `lotSz`,
`minSz`, `ctVal`, `ctMult`, `tickSz`, `lever`, `state`) приходят в
`InstrumentExternalSnapshot` **транзиентно** и в шаге 1 в домен
**не персистятся** — персистентного дома у них нет (модель
`InstrumentExternalRules` отложена; backlog п.9). Куда они лягут
персистентно и как снапшот-концепция соотнесётся с
`InstrumentExternalRules` — открытый вопрос INSTR-Q1.

## OKX

### `OkxInstrumentResponse` → snapshot → domain (идентичность, шаг 1)

| OKX field | Snapshot field | → domain `Instrument` |
|---|---|---|
| `instId` | `externalInstrumentId` | `externalId` |
| `instType` | `externalInstrumentType` | `externalType` |

`marginMode` / `externalMarginMode` / `leverage` — торговая
конфигурация инструмента, не из спецификации спота биржи (см.
`docs/models/domain/core/Instrument.md`). `internalId` / `exchangeId`
присваиваются системой при онбординге.

### Справочные поля OKX в шаге 1 (не персистятся)

`baseCcy`/`quoteCcy`/`settleCcy`, `lotSz`, `minSz`, `ctVal`,
`ctMult`, `tickSz`, `lever`, `state` — приходят в снапшоте, в домен
шага 1 не мапятся (INSTR-Q1). Полный OKX-инвентарь —
`docs/models/integrations/okx/OkxInstrumentResponse.md`; их будущая
персистентная проекция — `mapping/InstrumentExternalRules.md`
(модель отложена).

## Связи

- Доменная модель — `docs/models/domain/core/Instrument.md`.
- Lifecycle онбординга — `docs/lifecycles/Instrument.md`.
- Отложенные торговые правила —
  `docs/models/domain/other/InstrumentExternalRules.md`,
  `docs/models/mapping/InstrumentExternalRules.md`.
- Открытый вопрос — INSTR-Q1
  (`.claude/work/questions/open-questions.md`).
