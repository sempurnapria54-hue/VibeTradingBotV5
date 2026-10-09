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
| `marginMode` | `Position.marginMode` | режим маржи записи — доменное значение `Instrument.MarginMode`, переносится по имени. **Атрибут границы, а не поле строки**, как биржевое имя инструмента: колонки под него нет — дом правила `docs/models/domain/core/Position.md`. Различает в **счёт-широком срезе** запись режима контура и запись иного режима (`docs/components/AnomalyJob.md`) |

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

**Запись контура выбирается режимом маржи, а не порядком в ответе.**
Площадка держит изолированную и кросс-позицию одного инструмента рядом
отдельными записями, а отбора по режиму маржи у запроса живых позиций нет
(дом факта — контракт позиции площадки,
`docs/integrations/okx/contracts/position.md`). Ограничение контура «не
более одной позиции на пару счёт-инструмент»
(`docs/rules/trading-constraints.md`) держит только **наши** позиции —
режим маржи контура один; кросс-позицию того же инструмента заводит чужая
активность на счёте, и ей контур не препятствует. Поэтому чтение по
инструменту берёт запись режима маржи контура (`isolated`), а запись иного
режима нашу позицию не описывает и в снапшот не попадает. Это **отбор, а не
сверка**: запись иного режима — не нарушение контракта, а чужая позиция.
Чтение истории закрытых позиций сужается тем же режимом параметром запроса
(`mgnMode`). У **счёт-широкого среза** отбора нет: срез отдаёт записи обоих
режимов, и каждый снапшот среза **несёт режим маржи своей записи** —
доменное значение, в которое коннектор переводит сырой `mgnMode`
(константа источника границу не переходит, `.claude/rules/codestyle.md`).
Чужая кросс-позиция на инструменте контура поэтому приезжает отдельной
записью, **различимой** с нашей: счёт записей на инструмент идёт по режиму
контура, а запись иного режима видит второй исход того же детектора — как
сущность, которую система не создавала (`docs/components/AnomalyJob.md`).
Закрывает такую запись снятие риска вне графа сделок — её собственным
режимом (`docs/components/KillSwitchExecutor.md`). Принадлежность
инструменту сверяет только чтение закрытых позиций
(`docs/models/integrations/okx/PositionsHistoryOkxResponse.md`).

### Close-position request

`Domain → request` (поля **не** из `Position`, а из
`DealContext`/`Instrument`/Exchange-Account settings/adapter policy):

```text
Instrument.externalId    → instId
MarginMode аргумента     → mgnMode (пусто — adapter const isolated)
adapter const net        → posSide (если применимо)
settle currency / USDT   → ccy (необязательна: пусто — поле не уходит)
adapter technical policy → autoCxl
```

Валюта расчёта необязательна и на входе коннектора: снятие риска по позиции
на инструменте вне контура её не знает
(риск вне графа сделок — `docs/components/KillSwitchExecutor.md`).
**Режим маржи необязателен тем же образом, но пустота у него значит
другое:** пусто — закрывается запись режима контура, и это тропа всякой
нашей позиции; непустой режим приносит только снятие риска вне графа
сделок, закрывая запись её собственным режимом (там же).

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
| `mgnMode` | `marginMode`: `isolated` → `ISOLATED`, `cross` → `CROSS`; иное — значение вне формы контракта (единственный отказ чтения, раздел проверок чтения выше) |

`instType`, `posSide`, `lever` — в `Position` /
`PositionExternalSnapshot` не хранятся и не сверяются (почему — раздел
проверок чтения в source-agnostic ядре выше). `mgnMode` записи
**переносится, но не сверяется**: он операнд **отбора** записи контура
при живом чтении и различитель записей в счёт-широком срезе (там же), а
вместе с `posSide` у коннектора ещё и поле **запроса** закрытия. **`instId` маппится** — в
снапшот и дальше в `Position.externalInstrumentId`, атрибут границы без
колонки: снапшот приходит и СРЕЗОМ по множеству инструментов (чтение
всех живых позиций одним запросом), где адресат каждого не задан
запросом.

### OKX close-position request body

`POST /api/v5/trade/close-position`: `instId`, `mgnMode`, `posSide`,
`ccy` (опц.), `autoCxl` (опц.). Берутся **не** из `Position`, а из
аргументов операции «закрыть позицию» (инструмент, расчётная валюта,
режим маржи — `docs/components/IntegrationService.md`) и политики коннектора — тело
собирает читатель источника `OkxSourceReader`:

```text
Instrument.externalId     → instId
MarginMode аргумента      → mgnMode (пусто — adapter constant isolated)
adapter constant net      → posSide
settle currency / USDT    → ccy (опц.; пусто — поле не уходит)
adapter technical policy  → autoCxl
```

`autoCxl=true` снимает стоящие **заявки на закрытие** (reduce-only),
которые иначе отвергли бы закрытие площадки; входные заявки он не
снимает, и закрытие они переживают (семантика флага и провенанс —
`docs/integrations/okx/contracts/position.md`). Снятие входных ног —
забота порядка снятия риска, а не флага (`docs/rules/exit-teardown-order.md`).
