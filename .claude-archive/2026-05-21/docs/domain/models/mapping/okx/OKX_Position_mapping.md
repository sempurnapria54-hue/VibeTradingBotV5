# OKX Position mapping

> Статус документа: exchange-specific mapping-дока для `Position` на OKX.
>
> Документ описывает, как OKX request/response DTO и normalized external snapshot превращаются в доменную `Position`.
>
> Документ не заменяет `Position.md`. Доменная модель, статусы и runtime-семантика `Position` описаны в `Position.md`.

---

# 1. Назначение

Эта дока отвечает на вопрос:

```text
как данные OKX по позиции попадают в доменную модель Position
и какие поля OKX request/response используются client-layer / mapper-layer.
```

Документ нужен для:

* `OkxClientService`;
* `OkxRestClient`;
* OKX request DTO;
* OKX response DTO;
* `PositionMapper`;
* `PositionExternalSnapshot`;
* `PositionStatusResolver`;
* `RefreshPositionExecutor`;
* `ClosePositionExecutor`.

---

# 2. Приоритет источников

Если источники противоречат друг другу, использовать такой приоритет:

```text
1. Текущие явно подтверждённые решения по Position.
2. OKX endpoint-доки.
3. Актуальная доменная модель Position.md.
4. Общие проектные документы:
   - Статусы торговых сущностей.md;
   - Сервисные команды.md;
   - FSM этапы сделки.md;
   - Жизненный цикл сделки.md;
   - Оценка рисков.md.
5. Java-классы текущей реализации.
6. Legacy Position-доки.
```

OKX endpoint-доки являются источником истины по:

```text
endpoints;
request fields;
response fields;
required parameters;
rate limits;
ACK policy;
особенностям OKX.
```

Доменная модель проекта определяет:

```text
что хранить в Position;
какие статусы использовать;
как работает FSM;
как трактуется live risk.
```

---

# 3. Границы ответственности

## 3.1. Что описывает эта дока

Эта дока описывает:

* какие OKX endpoints используются для position-flow;
* как `GET /api/v5/account/positions` превращается в `PositionExternalSnapshot` или `null`;
* какие OKX поля валидируются в `ClientService` / adapter-layer;
* какие OKX поля маппятся в `PositionExternalSnapshot`;
* как direction определяется из `pos`;
* как работает `PositionStatusResolver`;
* как `PositionExternalSnapshot` обновляет domain `Position`;
* как `Position + DealContext` превращается в OKX close-position request;
* почему close-position response является только ACK.

## 3.2. Что не описывает эта дока

Эта дока не описывает подробно:

* полную доменную модель `Position` — см. `Position.md`;
* lifecycle `Deal` — см. `Жизненный цикл сделки.md`;
* FSM handlers — см. `FSM этапы сделки.md`;
* command-layer — см. `Сервисные команды.md`;
* risk-layer — см. `Оценка рисков.md`;
* аудит и timeline — см. `Аудит и история исполнения.md`.

---

# 4. OKX endpoints

## 4.1. Получить позиции

```text
GET /api/v5/account/positions
```

Используется в `REFRESH_POSITION`.

Для проекта основной запрос:

```text
GET /api/v5/account/positions?instType=SWAP&instId={instrumentExternalId}
```

Назначение:

```text
получить актуальный snapshot позиции по инструменту
```

Особенности:

* endpoint возвращает текущие актуальные позиции;
* в net mode по инструменту ожидается одна запись `posSide=net`;
* `pos` в net mode может быть положительным или отрицательным;
* `posId` может жить ограниченное время после полного закрытия позиции;
* для доказательства наличия / отсутствия live position используем запрос по `instId`, а не поиск по `posId`.

## 4.2. Закрыть позицию

```text
POST /api/v5/trade/close-position
```

Используется в `CLOSE_POSITION`.

Назначение:

```text
попросить OKX закрыть текущую позицию по рынку
```

Важно:

```text
close-position response — это ACK, а не финальный статус позиции.
```

Факт закрытия подтверждается отдельным `REFRESH_POSITION`.

---

# 5. ClientService constants / policy

`OkxClientService` не берёт OKX-specific request constants из `Position`.

Он сам подставляет exchange-specific constants / policy.

Для текущего проекта:

```text
instType = SWAP
mgnMode = isolated
posSide = net
ccy = USDT / settle currency, если требуется
```

Для `close-position`:

```text
autoCxl = adapter technical policy, если используется
```

Доменная логика не должна зависеть от `autoCxl` как от штатного cleanup-механизма сделки.

Если `autoCxl=true` применяется в OKX adapter, это technical exchange-specific detail.

---

# 6. Response validation в ClientService / adapter-layer

До создания `PositionExternalSnapshot` adapter-layer валидирует response.

Проверки:

```text
instId == expected Instrument.externalId
posSide == net
mgnMode == isolated
lever <= expected max leverage
```

`lever` не хранится в `Position` и `PositionExternalSnapshot`.

Проверка leverage может выполняться в двух местах:

```text
1. На этапе создания сделки / расчёта action.
2. Дополнительно при REFRESH_POSITION, чтобы поймать рассинхрон или ручное изменение.
```

Если response нарушает ожидаемый exchange invariant:

```text
ExternalInvariantViolationException
```

Примеры:

```text
actual posSide != net
actual mgnMode != isolated
actual instId != expected instrument
actual leverage > allowed leverage
невозможно безопасно определить direction
```

Runtime-реакция:

```text
Position.status = ERROR
Position.closeReason = EXCHANGE_INVARIANT_VIOLATION
Deal -> ERROR / safety-flow
```

---

# 7. OKX response -> `PositionExternalSnapshot` / `null`

## 7.1. Общий контракт ClientService

```text
позиция найдена     -> PositionExternalSnapshot
позиция не найдена  -> null
ошибка API/parse/invariant -> exception
```

`null` означает:

```text
запрос успешно выполнен, но позиция по expected instrument не найдена
```

`null` не является технической ошибкой.

Для `Position` `null` после успешного запроса по инструменту — нормальный closed-on-exchange факт.

## 7.2. Mapping fields

OKX response field -> `PositionExternalSnapshot`:

```text
posId  -> externalId
abs(pos) -> externalSize
avgPx  -> externalAverageEntryPrice
markPx -> externalMarkPrice
liqPx  -> externalLiquidationPrice
margin -> externalMargin
upl    -> externalUnrealizedProfit
cTime  -> externalCreatedAt
uTime  -> externalModifiedAt
```

`externalCreatedAt` и `externalModifiedAt` наследуются от `Auditable`.

## 7.3. Empty position response

Если OKX response успешный, но позиция не найдена:

```text
ClientService returns null
```

Не создаём пустой snapshot.

Не маппим `data=[]` в:

```text
new PositionExternalSnapshot(... null fields ...)
```

---

# 8. Direction mapping

Для OKX net-mode `direction` выводится из знака `pos`.

Правило:

```text
pos > 0 -> Position.Direction.LONG
pos < 0 -> Position.Direction.SHORT
```

`Position.externalSize` хранится как абсолютное значение:

```text
externalSize = abs(pos)
```

Пример:

```text
OKX pos = "2"
  -> direction = LONG
  -> externalSize = 2

OKX pos = "-2"
  -> direction = SHORT
  -> externalSize = 2
```

`posSide=net` не хранится в `Position`.

`posSide=net` валидируется в `ClientService` / adapter-layer.

Если direction не совпадает с expected direction текущей сделки, это нарушение инварианта.

---

# 9. `PositionStatusResolver`

`PositionStatusResolver` возвращает result-object:

```java
public class PositionStatusResolveResult {

    /**
     * Доменный статус позиции.
     */
    private Position.Status status;

    /**
     * Candidate причины закрытия / problem reason.
     *
     * RefreshPositionExecutor применяет его только если
     * текущий Position.closeReason == null.
     */
    private Position.CloseReason closeReason;
}
```

Базовая логика:

```text
snapshot == null
  -> status = CLOSED
  -> closeReason = EXTERNAL_CLOSE

snapshot != null
  -> status = ACTIVE
  -> closeReason = null
```

Если `snapshot.externalSize == 0`:

```text
status = ACTIVE
closeReason = null
live risk = false
```

Почему не `CLOSED`:

```text
snapshot найден, значит OKX всё ещё возвращает позиционный record.
Такой кейс считается cleanup / anomaly / retry case,
но не normal CLOSED, пока биржа продолжает возвращать snapshot.
```

---

# 10. `PositionExternalSnapshot` -> `Position`

`RefreshPositionExecutor` применяет результат к локальной `Position`.

## 10.1. Snapshot найден, локальной Position нет

Если snapshot найден в рамках active Deal flow:

```text
создать Position
Position.dealId = Deal.id
Position.status = ACTIVE
Position.direction = resolved direction
заполнить external* поля из snapshot
```

Это штатный сценарий после исполнения entry order.

## 10.2. Snapshot найден, локальная Position есть

```text
Position.status = ACTIVE
обновить external* поля из snapshot
проверить, что direction не изменилась
```

Live risk:

```text
Position.status == ACTIVE && externalSize > 0
```

Если `externalSize == 0`:

```text
Position.status = ACTIVE
live risk = false
cleanup / anomaly / retry case
```

## 10.3. Snapshot не найден, локальная Position есть

```text
Position.status = CLOSED
```

`closeReason` заполняется только если текущий `Position.closeReason == null`.

Для обычного refresh candidate из resolver:

```text
EXTERNAL_CLOSE
```

## 10.4. Snapshot не найден, локальной Position нет

```text
новую CLOSED Position не создаём
```

FSM / handler дальше анализирует текущий `DealContext` и статус сделки.

---

# 11. REFRESH_POSITION request policy для OKX

Для OKX `REFRESH_POSITION` делает один логический exchange-запрос:

```text
GET /api/v5/account/positions?instType=SWAP&instId={instrumentExternalId}
```

Дополнительно по `posId` не ищем.

Причина:

```text
цель REFRESH_POSITION — понять наличие или отсутствие live position по инструменту,
а не доказать существование старого posId.
```

`posId` после закрытия может жить ограниченное время и не является лучшим proof отсутствия live position.

Ретраи выполняются только при технических проблемах:

```text
timeout
connection reset
5xx
rate limit
temporary exchange error
```

Если запрос успешный и позиция не найдена:

```text
ClientService returns null
```

---

# 12. Position + DealContext -> close-position request

OKX endpoint:

```text
POST /api/v5/trade/close-position
```

Request fields:

```text
instId
mgnMode
posSide
ccy optional
autoCxl optional
```

Mapping:

```text
Instrument.externalId       -> instId
adapter constant isolated   -> mgnMode
adapter constant net        -> posSide
settle currency / USDT      -> ccy
adapter technical policy    -> autoCxl
```

`instId`, `mgnMode`, `posSide`, `ccy`, `autoCxl` не берутся из `Position`.

Они берутся из:

```text
DealContext
Instrument
Exchange / Account settings
OkxClientService policy
```

---

# 13. close-position response policy

Пример OKX response:

```json
{
  "code": "0",
  "msg": "",
  "data": [
    {
      "instId": "ETH-USDT-SWAP",
      "posSide": "net"
    }
  ]
}
```

Политика:

```text
code = 0
  -> ACK success

code != 0
  -> command failed / exchange error
```

Важно:

```text
ACK не закрывает Position.
ACK не меняет Position.status на CLOSED.
ACK не является runtime truth.
```

После успешного ACK требуется `REFRESH_POSITION`.

Только `REFRESH_POSITION` подтверждает, что позиции больше нет на бирже.

---

# 14. Close reason при close-position

`CLOSE_POSITION` command payload несёт `requestedCloseReason`.

Допустимые значения для close-flow:

```text
CLOSED_BY_STRATEGY
KILL_SWITCH
MANUAL_CLOSE
```

Не используем как requested reason:

```text
EXTERNAL_CLOSE
EXCHANGE_INVARIANT_VIOLATION
UNKNOWN
```

Причина:

```text
EXTERNAL_CLOSE — позиция закрылась на стороне биржи без текущей команды close-position.
EXCHANGE_INVARIANT_VIOLATION — problem reason.
UNKNOWN — fallback, а не намерение закрыть позицию.
```

`RefreshPositionExecutor` не перетирает уже заполненный `Position.closeReason`.

---

# 15. OKX fields, которые не переносим в Position

Не переносим в `Position` и `PositionExternalSnapshot`:

```text
instId
instType
mgnMode
posSide
lever
availPos
hedgedPos
last
idxPx
usdPx
bePx
uplRatio
uplLastPx
uplRatioLastPx
imr
mmr
mgnRatio
notionalUsd
realizedPnl
settledPnl
pnl
fee
fundingFee
liqPenalty
ccy
interest
liab
liabCcy
pendingCloseOrdLiabVal
adl
tradeId
closeOrderAlgo[]
spot / option / margin-specific fields
bizRefId
bizRefType
raw response
```

Почему:

* `instId`, `posSide`, `mgnMode`, `lever` используются для request/validation в `ClientService` / adapter-layer.
* `availPos` не нужен, потому что `CLOSE_POSITION` закрывает всю позицию, а partial exit выполняется через reduce-only `Order` / `AlgoOrder`.
* `bePx` не используется для сопровождения live-risk позиции.
* `tradeId` — id последней сделки/fill, а не id позиции в истории.
* `realizedPnl`, `fee`, `fundingFee`, `pnl` используются для итоговой аналитики через fills/history/finalization flow, а не через `Position`.
* `closeOrderAlgo[]` не моделируется внутри `Position`, потому что защита живёт через `Order` / `AlgoOrder`.
* raw response не является частью domain-модели.

---

# 16. Recovery-сценарий после рестарта

Сценарий:

```text
1. Бот создал entry order с attached protection.
2. Приложение упало или потеряло связь с биржей.
3. Entry order исполнился.
4. OKX создала позицию.
5. Позиция закрылась по SL / TP / trailing.
6. После рестарта локальной Position может ещё не быть.
```

Это не anomaly, если есть active `Deal` и известный entry order, который объясняет появление позиции.

Recovery:

```text
REFRESH_ORDER / REFRESH_ORDER_HISTORY
  -> подтверждает исполнение entry order

REFRESH_POSITION
  -> возвращает null, потому что позиции уже нет

REFRESH_ALGO_ORDER_HISTORY / attached protection facts
  -> подтверждает, что сработал SL / TP / trailing

REFRESH_FILLS
  -> собирает факты исполнений для итогового PnL
```

Локальную `CLOSED Position` можно не создавать, если локальной Position ещё не было.

`Deal` финализируется по собранным facts.

---

# 17. Ошибки и invariant violations

## 17.1. Unknown / invalid response

Если OKX response невозможно безопасно распарсить или интерпретировать:

```text
controlled exception
Deal -> ERROR / safety-flow
```

## 17.2. Exchange invariant violation

Если response нарушает expected invariant:

```text
ExternalInvariantViolationException
```

Примеры:

```text
posSide != net
mgnMode != isolated
instId != expected instrument
lever > expected max leverage
direction != expected direction
```

Runtime reaction:

```text
Position.status = ERROR
Position.closeReason = EXCHANGE_INVARIANT_VIOLATION
Deal -> ERROR / safety-flow
```

## 17.3. Position not found

Для `Position` not found после успешного `GET /account/positions` по instrument — не ошибка.

```text
ClientService returns null
PositionStatusResolver -> CLOSED + EXTERNAL_CLOSE
```

Это отличается от `Order` / `AlgoOrder`, где not found после evidence-cycle может быть problem-flow, если финал нельзя объяснить.

---

# 18. Связанные документы

* `Position.md`
* `Статусы торговых сущностей.md`
* `Сервисные команды.md`
* `FSM этапы сделки.md`
* `Жизненный цикл сделки.md`
* `Order.md`
* `AlgoOrder.md`
* `OKX_Order_mapping.md`
* `OKX_AlgoOrder_mapping.md`
* `Оценка рисков.md`
* `Аудит и история исполнения.md`
