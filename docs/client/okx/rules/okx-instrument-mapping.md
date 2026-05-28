# OKX instrument mapping

## На какой вопрос отвечает этот файл

Как OKX-инструмент (REST `public/instruments`) маппится в доменный
`InstrumentExternalRules` и как нормализуется его статус/типы.

## Контекст

Exchange-specific mapping для OKX. Доменная модель — в
`docs/models/other/InstrumentExternalRules.md`, эта дока её не заменяет.
Обновляет правила `docs/components/InstrumentExternalRulesSyncJob.md`.

## Endpoint

- **Sync правил инструмента:** `GET /api/v5/public/instruments`.

На первом этапе обновление только через REST; WebSocket для instruments
можно добавить позже.

## Маппинг

OKX-ответ → `InstrumentExternalRulesExternalSnapshot` (сырые `external*`
строки) → `InstrumentExternalRules`. Сырой OKX DTO за `ClientService` не
выходит (`docs/rules/raw-exchange-dto-boundary.md`).

`external*`-поля snapshot сохраняются как есть (`externalTickSize`,
`externalLotSize`, `externalMinSize`, `externalContractValue`,
`externalMaxLeverage`, `externalState` и др.). Доменные проекции
резолвятся при материализации модели:

- `externalState` → `InstrumentExternalRules.Status` (`LIVE` / `SUSPEND`
  / `PREOPEN` / `EXPIRED` / `TEST` / `UNKNOWN`);
- сырой тип инструмента → `InstrumentType` (`SWAP` / `FUTURES` / `SPOT`
  / `MARGIN` / `OPTION` / `UNKNOWN`);
- сырой тип контракта → `ContractType` (`LINEAR` / `INVERSE` /
  `UNKNOWN`).

Неизвестное значение нормализуется в `UNKNOWN` соответствующего enum.
