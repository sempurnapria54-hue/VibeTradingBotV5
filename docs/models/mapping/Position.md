# Position — mapping между слоями

## На какой вопрос отвечает этот файл

Как `Position` переходит между слоями.

## Source-agnostic ядро

### `PositionExternalSnapshot` → `Position`

| Snapshot field | Domain | Семантика |
|---|---|---|
| `externalId` | `Position.externalId` | биржевой id позиции |
| `externalInstrumentId` | `Position.externalInstrumentId` | биржевое имя инструмента, переносится по имени. **Атрибут границы, а не поле строки:** колонки под него нет (у строки эпизода инструмент идёт через сделку), в наш числовой идентификатор на границе не резолвится — дом правила `docs/models/domain/core/Position.md`. Адресует позицию внутри **счёт-широкого среза** (`docs/components/AnomalyJob.md`) |
| `externalSize` | `Position.externalSize` | размер по модулю |
| `direction` | `Position.direction` | `LONG`/`SHORT` (из знака) |
| `externalAverageEntryPrice` | `Position.externalAverageEntryPrice` | средняя цена входа |
| `externalMarkPrice` | `Position.externalMarkPrice` | mark price |
| `externalLiquidationPrice` | `Position.externalLiquidationPrice` | цена ликвидации |
| `externalMargin` | `Position.externalMargin` | маржа позиции |
| `externalUnrealizedProfit` | `Position.externalUnrealizedProfit` | нереализованный PnL |
| `externalCreatedAt` | `Position.externalCreatedAt` | |
| `externalModifiedAt` | `Position.externalModifiedAt` | |

### Direction mapping

```text
pos > 0  → Direction.LONG
pos < 0  → Direction.SHORT
externalSize = abs(pos)
```

Направление выводится из знака и с направлением сделки не сверяется:
какой эпизод перед нами, определяет пара идентичности записи, а не
направление (`docs/components/RefreshPositionExecutor.md`).

### `IntegrationService` контракт (snapshot / null / exception)

```text
позиция найдена         -> PositionExternalSnapshot
позиция не найдена      -> null (успешный запрос; позиции нет — нормальный
                           closed-on-exchange факт)
API / parse / invariant -> exception
```

Пустой snapshot не создаём; `data=[]` не маппим в snapshot с
null-полями.

### Position not found vs Order/AlgoOrder

Для `Position` not found после успешного запроса по инструменту — не
ошибка (`null` → `CLOSED` + `EXTERNAL_CLOSE`). Отличается от
`Order`/`AlgoOrder`, где not found после evidence-cycle может быть
problem-flow.

### Invariant checks (общая идея)

**Ответ чтения живой позиции с ожиданием не сверяется**: ни
принадлежность записи запрошенному инструменту, ни сторона позиции, ни
режим маржи, ни плечо. Посылки, которые эти поля выражают, меряются не на
ответе, а преконтролем перед действием: режим позиций — на снимке средств
(`docs/integrations/okx/contracts/account-config.md`), режим маржи и плечо
пары против биржевого максимума — на строке состояния пары
(`docs/components/RiskValidator.md`). `lever` не хранится ни в `Position`,
ни в `PositionExternalSnapshot`.

**Отказ чтения здесь один — значение вне формы контракта** (число, время,
длина позиционной строки — сеть разбора коннектора):
`ExternalInvariantViolationException`, сделка уходит в `ERROR` и
safety-flow, статус позиции остаётся последним применённым фактом
(`docs/rules/controlled-exchange-exceptions.md`). **Нулевая нога
(`pos = 0`) направления не имеет:** направление пусто, а не подставлено —
подставленное стало бы наблюдением, которого не было.

**Названное ограничение: режим маржи записи чтение не проверяет.** Чтение
по инструменту берёт запись ответа без отбора по режиму маржи, и
принадлежность записи изолированному контуру держится ограничением контура
«не более одной позиции на пару счёт-инструмент»
(`docs/rules/trading-constraints.md`), а не наблюдением. Принадлежность инструменту
сверяет только чтение закрытых позиций
(`docs/models/integrations/okx/PositionsHistoryOkxResponse.md`).

### Close-position request

`Domain → request` (поля **не** из `Position`, а из
`DealContext`/`Instrument`/Exchange-Account settings/adapter policy):

```text
Instrument.externalId    → instId
adapter const isolated   → mgnMode (если поддерживается источником)
adapter const net        → posSide (если применимо)
settle currency / USDT   → ccy (необязательна: пусто — поле не уходит)
adapter technical policy → autoCxl
```

Валюта расчёта необязательна и на входе коннектора: снятие риска по позиции
на инструменте вне контура её не знает
(риск вне графа сделок — `docs/components/KillSwitchExecutor.md`).

Response — ACK, не финальный статус (`ack-not-runtime-truth.md`).

### Close reason при close-position

`CLOSE_POSITION_COMMAND` payload несёт `requestedCloseReason`. Допустимы:
`CLOSED_BY_STRATEGY`, `KILL_SWITCH`. Не используются
как requested reason: `EXTERNAL_CLOSE` (закрытие на стороне источника
без команды).
`RefreshPositionExecutor` не перетирает уже заполненный
`Position.closeReason` (write-once). Перечень значений — три, каждое с
названным производителем (`docs/models/domain/core/Position.md`).

## OKX

### `PositionOkxResponse` → `PositionExternalSnapshot`

См. инвентарь — `docs/models/integrations/okx/PositionOkxResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `posId` | `externalId` |
| `instId` | `externalInstrumentId` |
| `pos` | `abs(pos)` → `externalSize`; знак → `direction` |
| `avgPx` | `externalAverageEntryPrice` |
| `markPx` | `externalMarkPrice` |
| `liqPx` | `externalLiquidationPrice` |
| `margin` | `externalMargin` |
| `upl` | `externalUnrealizedProfit` |
| `cTime` | `externalCreatedAt` |
| `uTime` | `externalModifiedAt` |

`instType`, `mgnMode`, `posSide`, `lever` — в `Position` /
`PositionExternalSnapshot` не хранятся и не сверяются (почему — раздел
проверок чтения в source-agnostic ядре выше); `mgnMode` и `posSide`
у коннектора есть только константами **запроса** закрытия. **`instId` маппится** — в
снапшот и дальше в `Position.externalInstrumentId`, атрибут границы без
колонки: снапшот приходит и СРЕЗОМ по множеству инструментов (чтение
всех живых позиций одним запросом), где адресат каждого не задан
запросом.

### OKX close-position request body

`POST /api/v5/trade/close-position`: `instId`, `mgnMode`, `posSide`,
`ccy` (опц.), `autoCxl` (опц.). Берутся **не** из `Position`, а из
аргументов операции «закрыть позицию» (инструмент, расчётная валюта —
`docs/components/IntegrationService.md`) и политики коннектора — тело
собирает читатель источника `OkxSourceReader`:

```text
Instrument.externalId     → instId
adapter constant isolated → mgnMode
adapter constant net      → posSide
settle currency / USDT    → ccy (опц.; пусто — поле не уходит)
adapter technical policy  → autoCxl
```

`autoCxl=true` снимает стоящие **заявки на закрытие** (reduce-only),
которые иначе отвергли бы закрытие площадки; входные заявки он не
снимает, и закрытие они переживают (семантика флага и провенанс —
`docs/integrations/okx/contracts/position.md`). Снятие входных ног —
забота порядка снятия риска, а не флага (`docs/rules/exit-teardown-order.md`).
