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
идентичность + биржевые `externalStatus`/`externalLeverage` +
справочные поля спецификации) → domain `Instrument`.

### Что персистится в шаге 1

В шаге 1 (поток рыночных данных) снапшот→domain резолвит
идентичность инструмента (`externalId`, `externalType`) и биржевые
поля `externalStatus` (OKX `state`) и `externalLeverage` (OKX
`lever`); плюс конфигурируемые `marginMode`/`externalMarginMode` и
рабочее `leverage` (задаётся при создании инструмента, не из
снапшота). Онбординг-статус домена (`Instrument.Status`) — статус
системы (`docs/lifecycles/Instrument.md`), он **не** проекция
биржевого `state`: биржевой статус живёт сырым в `externalStatus`
(`String`), а `Status` нормализует онбординг.

Справочные поля спецификации (base/quote/settle currency, `lotSz`,
`minSz`, `ctVal`, `ctMult`, `tickSz`) приходят в
`InstrumentExternalSnapshot` **транзиентно** и в шаге 1 в домен
**не персистятся** — персистентного дома у них нет (модель
`InstrumentExternalRules` отложена; backlog п.9). Куда они лягут
персистентно и как снапшот-концепция соотнесётся с
`InstrumentExternalRules` — открытый вопрос INSTR-Q1. Роль
`externalLeverage` как биржевого потолка плеча и валидация рабочего
`leverage` — открытый вопрос INSTR-Q2.

## OKX

### `OkxInstrumentResponse` → snapshot → domain (идентичность + биржевые поля, шаг 1)

| OKX field | Snapshot field | → domain `Instrument` |
|---|---|---|
| `instId` | `externalInstrumentId` | `externalId` |
| `instType` | `externalInstrumentType` | `externalType` |
| `state` | `externalStatus` | `externalStatus` (сырой) |
| `lever` | `externalLeverage` | `externalLeverage` (сырой) |

`marginMode` / `externalMarginMode` — торговая конфигурация
инструмента, не из спецификации спота биржи (см.
`docs/models/domain/core/Instrument.md`). Рабочее `leverage`
(`Integer`) задаётся при создании инструмента и из снапшота не
приходит (не путать с биржевым `externalLeverage`). `internalId` /
`exchangeId` присваиваются системой при онбординге.

### Справочные поля OKX в шаге 1 (не персистятся)

`baseCcy`/`quoteCcy`/`settleCcy`, `lotSz`, `minSz`, `ctVal`,
`ctMult`, `tickSz` — приходят в снапшоте, в домен шага 1 не мапятся
(INSTR-Q1). Биржевые `state`/`lever` из этого перечня исключены —
они персистятся на `Instrument` (`externalStatus`/`externalLeverage`,
см. таблицу выше). Полный OKX-инвентарь —
`docs/models/integrations/okx/OkxInstrumentResponse.md`; будущая
персистентная проекция sizing/rounding-полей —
`mapping/InstrumentExternalRules.md` (модель отложена).

## Связи

- Доменная модель — `docs/models/domain/core/Instrument.md`.
- Lifecycle онбординга — `docs/lifecycles/Instrument.md`.
- Отложенные торговые правила —
  `docs/models/domain/other/InstrumentExternalRules.md`,
  `docs/models/mapping/InstrumentExternalRules.md`.
- Открытые вопросы — INSTR-Q1, INSTR-Q2
  (`.claude/work/questions/open-questions.md`).
