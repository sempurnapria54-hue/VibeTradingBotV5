# OKX position mapping

## На какой вопрос отвечает этот файл

Как данные OKX по позиции попадают в доменную `Position`, какие поля
валидируются и как формируется close-position request.

## Контекст

Exchange-specific mapping для OKX. Доменная модель и статусы — в
`docs/models/core/Position.md` и `docs/lifecycles/Position.md`, эта
дока их не заменяет. Поля raw response — в
`docs/client/okx/models/OkxPositionResponse.md`.

## Endpoints

- **Получить позиции** (`REFRESH_POSITION`):
  `GET /api/v5/account/positions?instType=SWAP&instId={instrumentExternalId}`.
  Один логический запрос по инструменту; дополнительно по `posId` не
  ищем — цель в наличии/отсутствии live position по инструменту, а не
  в доказательстве старого `posId` (который после закрытия живёт
  ограниченное время). Ретраи — только при технических/API проблемах
  (timeout, connection reset, 5xx, rate limit, temporary error).
- **Закрыть позицию** (`CLOSE_POSITION`):
  `POST /api/v5/trade/close-position`. Response — ACK, не финальный
  статус (см. `docs/rules/ack-not-runtime-truth.md`).

## ClientService constants / policy

OKX-specific request-константы подставляет `OkxClientService`, не
`Position`: `instType=SWAP`, `mgnMode=isolated`, `posSide=net`,
`ccy=USDT`/settle при необходимости. Для close-position: `autoCxl` —
техническая adapter-policy, если используется; доменная логика не
должна зависеть от `autoCxl` как от штатного cleanup-механизма.

## Response validation (adapter-layer)

Перед созданием `PositionExternalSnapshot`:

```text
instId == expected Instrument.externalId
posSide == net
mgnMode == isolated
lever <= expected max leverage
```

`lever` не хранится в `Position`/`PositionExternalSnapshot`. Проверка
leverage может выполняться при создании сделки/расчёте action и
дополнительно при `REFRESH_POSITION` (поймать рассинхрон / ручное
изменение). Нарушение invariant → `ExternalInvariantViolationException`
(`posSide != net`, `mgnMode != isolated`, `instId != expected`,
`lever > allowed`, direction нельзя определить) → `Position.status =
ERROR`, `closeReason = EXCHANGE_INVARIANT_VIOLATION`, `Deal -> ERROR /
safety-flow`.

## ClientService контракт (snapshot / null / exception)

```text
позиция найдена            -> PositionExternalSnapshot
позиция не найдена         -> null  (успешный запрос, позиции по
                              инструменту нет — нормальный
                              closed-on-exchange факт)
API / parse / invariant    -> exception
```

Пустой snapshot не создаём; `data=[]` не маппим в snapshot с
null-полями.

## Mapping fields

```text
posId  -> externalId
abs(pos) -> externalSize
avgPx  -> externalAverageEntryPrice
markPx -> externalMarkPrice
liqPx  -> externalLiquidationPrice
margin -> externalMargin
upl    -> externalUnrealizedProfit
cTime  -> externalCreatedAt   (Auditable)
uTime  -> externalModifiedAt  (Auditable)
```

## Direction mapping

```text
pos > 0 -> Direction.LONG
pos < 0 -> Direction.SHORT
externalSize = abs(pos)
```

`posSide=net` валидируется в adapter-layer, в `Position` не хранится.
Если direction ≠ expected direction текущей сделки — нарушение
инварианта.

## Close-position request

`POST /api/v5/trade/close-position`, поля: `instId`, `mgnMode`,
`posSide`, `ccy` (optional), `autoCxl` (optional). Берутся **не** из
`Position`, а из `DealContext` / `Instrument` / Exchange-Account
settings / `OkxClientService` policy:

```text
Instrument.externalId     -> instId
adapter constant isolated -> mgnMode
adapter constant net      -> posSide
settle currency / USDT    -> ccy
adapter technical policy  -> autoCxl
```

## Close reason при close-position

`CLOSE_POSITION` payload несёт `requestedCloseReason`. Допустимы:
`CLOSED_BY_STRATEGY`, `KILL_SWITCH`, `MANUAL_CLOSE`. Не используются
как requested reason: `EXTERNAL_CLOSE` (закрытие на стороне биржи без
команды), `EXCHANGE_INVARIANT_VIOLATION` (problem reason), `UNKNOWN`
(fallback). `RefreshPositionExecutor` не перетирает уже заполненный
`Position.closeReason` (write-once).

## Position not found vs Order/AlgoOrder

Для `Position` not found после успешного запроса по инструменту — не
ошибка (`null` → `CLOSED` + `EXTERNAL_CLOSE`). Отличается от
`Order`/`AlgoOrder`, где not found после evidence-cycle может быть
problem-flow, если финал нельзя объяснить.
