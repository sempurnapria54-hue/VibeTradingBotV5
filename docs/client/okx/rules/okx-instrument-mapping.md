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
  Permission: Public (auth не нужен). Rate limit: 20 req / 2 s по IP
  + Instrument Type. Query: `instType` обязателен (`SPOT`/`MARGIN`/
  `SWAP`/`FUTURES`/`OPTION`), `instId` опц. для точечного запроса.

На первом этапе обновление только через REST; WebSocket для instruments
(public канал `instruments`) можно добавить позже — событие приходит
по одному/нескольким инструментам с теми же полями, что REST, плюс
`uTime`. На обновление adapter перезаписывает поля спецификации и
обновляет `sourceUpdatedAt`.

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

## Поля response (по архивному источнику)

Используемые поля → snapshot:

| OKX field | Назначение | Snapshot field |
|---|---|---|
| `instId` | имя инструмента | `externalInstrumentId` |
| `instType` | тип инструмента | сырой → `instrumentType` (резолв) |
| `tickSz` | минимальный шаг цены `px` | `externalTickSize` |
| `lotSz` | шаг размера `sz` (контракты для SWAP) | `externalLotSize` |
| `minSz` | минимальный размер ордера | `externalMinSize` |
| `ctVal` | стоимость 1 контракта | `externalContractValue` |
| `ctValCcy` | валюта `ctVal` | `externalContractValueCurrency` |
| `ctType` | тип контракта (`linear`/`inverse`) | сырой → `contractType` (резолв) |
| `lever` | максимальное плечо | `externalMaxLeverage` |
| `state` | статус инструмента | сырой → `Status` (резолв) |

Sizing-формула (для линейного `ctValCcy = baseCcy`): `baseQty = usdtNotional /
price`; `contracts = baseQty / ctVal`; округлить по `lotSz`; проверить
`minSz`. См. `docs/components/SizeCalculator.md`.

Поля, не маппимые (диагностика / неиспользуемые режимы): `instFamily`,
`uly`, `baseCcy`/`quoteCcy`/`settleCcy` (хранятся в domain
`Instrument`, не здесь), `ctMult`, `maxLmtSz`/`maxMktSz`/`maxTwapSz`/
`maxIcebergSz`/`maxTriggerSz`/`maxStopSz`/`maxLmtAmt`/`maxMktAmt`
(per-order лимиты — пока не используем), `listTime`/`expTime`/
`openType`/`ruleType` (lifecycle биржи; для SWAP `expTime` обычно
пусто), `category`/`groupId`/`alias`/`stk`/`optType`,
`posLmtAmt`/`posLmtPct`/`maxPlatOILmt` (лимиты позиций).
