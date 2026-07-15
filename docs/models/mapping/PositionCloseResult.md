# PositionCloseResult — mapping между слоями

## На какой вопрос отвечает этот файл

Как положение закрытой позиции (positions-history OKX) нормализуется
через транзитный `PositionCloseResultExternalSnapshot` и как его число
ложится на `Deal.resultProfit`.

## Контекст

Mapping-слой носителя числа `Deal.resultProfit`. Здесь **нет отдельной
persisted доменной сущности**: net realized P&L уходит в поле
`Deal.resultProfit`, категорийная разбивка — в `DealCashFlow`
(`docs/models/mapping/DealCashFlow.md`). Граничный объект —
**транзитный `PositionCloseResultExternalSnapshot`** (не persisted; как
`PositionExternalSnapshot`), единственное, что выходит за
`IntegrationService`/adapter (`docs/rules/raw-exchange-dto-boundary.md`).

Native-модель — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.
Контракт endpoint'а — `docs/integrations/okx/contracts/position.md`
§«История закрытых позиций». Источник числа (что за данные и почему
positions-history) — `docs/decisions/result-profit-source.md`; механика
финализации и добыча факта — `docs/decisions/pnl-finalization-mechanics.md`.
Доменное число и правила PnL — `docs/models/domain/aggregate/Deal.md`
§«Итоговый PnL». Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`.

Транзитный снапшот, как `PositionExternalSnapshot`, **не требует
отдельного `*ExternalSnapshot.md`** (нет самостоятельного persisted
содержания; `docs/models/externalSnapshot/README.md`) — его поля
зафиксированы ниже.

Текущие источники: **OKX**.

## Source-agnostic ядро

### Mapping-flow

```text
positions-history REST response -> raw OkxPositionsHistoryResponse
  -> IntegrationService validation
  -> PositionCloseResultMapper -> PositionCloseResultExternalSnapshot
  -> RefreshPositionsHistoryExecutor -> Deal.resultProfit
```

Raw DTO не выходит за пределы `IntegrationService` / adapter-layer;
`RefreshPositionsHistoryExecutor` работает только с validated normalized
snapshot. Snapshot **транзитный** — не персистится; число из него
`RefreshPositionsHistoryExecutor` доводит до `Deal` (финализация пишет
`resultProfit` на `Deal`, `docs/decisions/pnl-finalization-mechanics.md`
реш.2).

### `PositionCloseResultExternalSnapshot` (транзитный)

| Snapshot field | Тип | Семантика |
|---|---|---|
| `externalRealizedPnl` | `BigDecimal` | готовый net realized P&L (net от всех издержек, посчитан биржей) |
| `externalResultCurrency` | `String` | валюта результата (`USDT` для `ETH-USDT-SWAP`) |
| `externalCloseAvgPx` | `BigDecimal` | средняя цена выхода (закрытия) |
| `externalOpenAvgPx` | `BigDecimal` | средняя цена входа |
| `externalTriggerPx` | `BigDecimal` | цена триггера ликвидации/ADL (только при `type` 3–6; иначе null) |
| `externalCloseType` | `String` | тип последнего закрытия (`1`–`6`; ликвидация/ADL = `3`–`6`) |
| `externalPosId` | `String` | биржевой id позиции (ключ агрегации записи) |
| `externalUpdatedAt` | `OffsetDateTime` | время обновления записи positions-history |

Числовые/временные поля нормализуются при построении снапшота (string →
`BigDecimal`/`OffsetDateTime`, empty → null), уже провалидированные как
parseable. Тип времени — `OffsetDateTime` по конвенции проекта (как
`docs/models/mapping/Balance.md`, `DealCashFlow.externalTs`, `Auditable`).

### snapshot → `Deal`

`RefreshPositionsHistoryExecutor` доводит снапшот до финализации;
на `Deal` пишутся:

| Snapshot field | Domain | Семантика |
|---|---|---|
| `externalRealizedPnl` | `Deal.resultProfit` | заголовочное число net realized P&L |
| `externalResultCurrency` | `Deal.resultProfitCurrency` | валюта результата |

Остальные поля снапшота (`externalCloseAvgPx`, `externalOpenAvgPx`,
`externalTriggerPx`, `externalCloseType`, `externalPosId`,
`externalUpdatedAt`) — **вход финализации / аудита** (сверка bills↔net,
провенанс аварийного терминала по `type`/`triggerPx`, сопоставление по
`posId`), в `Deal` напрямую **не пишутся**.

### Validation (структурная, до маппинга)

В `IntegrationService` источника:

- **Structural:** `response != null`; `code == 0`; на `posId` резолвится
  **ровно одна финализированная** запись positions-history (инвариант
  агрегации, `docs/integrations/okx/contracts/position.md` §«Инвариант
  агрегации»; **N11, требует рантайм-верификации**). Множественная /
  нефинализированная запись на `posId` — controlled external error, не
  молчаливое взятие слайса.
- **Numeric:** числа приходят строками; обязательные (`realizedPnl`, `ccy`)
  заполнены и парсятся; для **чистого** закрытия `realizedPnl` присутствует
  (пустое `realizedPnl` при чистом закрытии недопустимо). `triggerPx` —
  только для `type` 3–6.
- **Аварийный контур:** для `EMERGENCY_CLOSED` при genuinely недоступном
  net снапшот числа не даёт → `resultProfit = null` с семантикой
  «неисчислимо» (не ноль), терминал всё равно проходит
  (`docs/decisions/pnl-finalization-mechanics.md` реш.3).

### Error policy

- **Temporary API problem** (timeout, connection reset, 5xx): команда
  `REFRESH_POSITIONS_HISTORY` retryable через командную машинерию;
  финализация ждёт факта.
- **Invalid response / инвариант агрегации нарушен** (`code != 0`,
  множественная/нефинализированная запись на `posId`, обязательные не
  парсятся): controlled external error; число не стейджится; финализация
  не завершает чистый `CLOSED` без числа (инвариант непустоты
  `resultProfit`, `docs/models/domain/aggregate/Deal.md`).

## OKX

### `OkxPositionsHistoryResponse` → `PositionCloseResultExternalSnapshot`

См. инвентарь — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `realizedPnl` | `externalRealizedPnl` |
| `ccy` | `externalResultCurrency` |
| `closeAvgPx` | `externalCloseAvgPx` |
| `openAvgPx` | `externalOpenAvgPx` |
| `triggerPx` | `externalTriggerPx` |
| `type` | `externalCloseType` |
| `posId` | `externalPosId` |
| `uTime` | `externalUpdatedAt` (epoch millis → `OffsetDateTime`) |

Числовые поля парсятся в `BigDecimal`, `uTime` — в `OffsetDateTime`; `empty
string → null`. Список не маппимых полей (`pnl`, `fee`, `fundingFee`,
`liqPenalty`, `settledPnl`, `pnlRatio`, `mgnMode`, `posSide` и др.) — в
`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

### OKX validation notes

- **Structural:** `code == 0`.
- **Query:** запись добывается по `posId` (плюс `instType`/`instId`);
  пагинация positions-history — по `uTime` (`limit` ≤ 100),
  `docs/integrations/okx/contracts/position.md` §«История закрытых позиций».
- **Инвариант агрегации (N11):** `realizedPnl` кумулятивен по всем
  partial-закрытиям и доборам за жизнь `posId`; читается финализированной
  записью (позиция flat по `REFRESH_POSITION`). До рантайм-верификации —
  **предположение** (контур source-api, `.claude/tests/source-api/okx/plan.md`
  §AG1); гейтит корректность числа до `CODE`.
