# Instrument — mapping между слоями

## На какой вопрос отвечает этот файл

Как `Instrument` переходит между слоями.

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

Размерные поля спецификации (`lotSz`, `minSz`, `ctVal`, `ctMult`,
`tickSz`) приходят в `InstrumentExternalSnapshot` **транзиентно** и в
шаге 1 в домен **не персистятся**. Их персистентный дом —
`InstrumentExternalRules` (JSONB-навес на `Instrument`), материализуемый
**на шаге 5** (риск-преконтроль), вне оркестрации рыночных данных шага 1
(`docs/models/domain/other/InstrumentExternalRules.md`);
`ctMult` навес не хранит. **Валюты (settle/base/quote)
персистятся на самом `Instrument`** — с шага 7, см. таблицу шага 7 ниже.
Роль `externalLeverage`/биржевой потолок плеча — там же
(рабочее плечо пишется inline в
`SubmitOrderExecutor` перед постановкой открывающего ордера,
`docs/components/SubmitOrderExecutor.md`).

## OKX

### `InstrumentOkxResponse` → snapshot → domain (идентичность + биржевые поля)

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

### `InstrumentOkxResponse` → snapshot → domain (валюты)

| OKX field | Snapshot field | → domain `Instrument` |
|---|---|---|
| `settleCcy` | `externalSettlementCurrency` | `externalSettlementCurrency` |
| `baseCcy` | `externalBaseCurrency` | `externalBaseCurrency` |
| `quoteCcy` | `externalQuoteCurrency` | `externalQuoteCurrency` |

Валюты приходят **тем же снапшотом и той же тропой заведения инструмента**
(переход `CREATED → SYNC`, `docs/lifecycles/Instrument.md`), что
идентичность и биржевые поля; с шага 7 они **персистятся** на `Instrument`. Формулировка «тем же синком» **снята**: в проекте она
двузначна (онбординговый `SYNC` против периодического
`InstrumentSyncJob`), а писателем является первый — валюты
неизменны и периодического подтверждения не требуют.
Расчётная валюта — операнд трёх потребителей шага 7 и авторитет
`Deal.resultProfitCurrency`
(`docs/models/domain/core/Instrument.md`).

### Справочные поля OKX в шаге 1 (не персистятся)

`lotSz`, `minSz`, `ctVal`, `ctMult`, `tickSz` — приходят в снапшоте, в
домен идентичности не мапятся (их дом — `InstrumentExternalRules`).
Биржевые `state`/`lever` из этого перечня исключены — они персистятся
на `Instrument` (`externalStatus`/`externalLeverage`, см. таблицу выше);
валюты из него исключены с шага 7 (таблица выше).
Полный OKX-инвентарь —
`docs/models/integrations/okx/InstrumentOkxResponse.md`; персистентная
проекция sizing/rounding-полей —
`mapping/InstrumentExternalRules.md` (материализуется на шаге 5).

## Связи

- Доменная модель — `docs/models/domain/core/Instrument.md`.
- Lifecycle онбординга — `docs/lifecycles/Instrument.md`.
- Торговые правила инструмента (материализуются на шаге 5) —
  `docs/models/domain/other/InstrumentExternalRules.md`,
  `docs/models/mapping/InstrumentExternalRules.md`,
  `docs/models/domain/other/InstrumentExternalRules.md`.
