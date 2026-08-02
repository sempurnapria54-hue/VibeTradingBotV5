# PositionCloseResult — mapping между слоями

## На какой вопрос отвечает этот файл

Как положение закрытой позиции источника ложится на `Position`.

## Контекст

Mapping-слой **второй ноги `REFRESH_POSITION_COMMAND`** (live → positions-history,
`docs/components/RefreshPositionExecutor.md` §Evidence-cycle). Здесь **нет
отдельной persisted доменной сущности**: положение закрытия ложится
полями на **`Position`** (`docs/models/domain/core/Position.md`
§«Положение закрытия»), откуда его читает финализатор и пишет число в
`Deal.resultProfit`; категорийная разбивка — в `DealCashFlow`
(`docs/models/mapping/DealCashFlow.md`). Граничный объект —
`PositionCloseResultExternalSnapshot` (не persisted; как
`PositionExternalSnapshot`), единственное, что выходит за
`IntegrationService`/adapter (`docs/rules/raw-exchange-dto-boundary.md`).

Native-модель — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.
Контракт endpoint'а — `docs/integrations/okx/contracts/position.md`
§«История закрытых позиций». Источник числа (что за данные и почему
positions-history) — `docs/decisions/result-profit-source.md`; механика
финализации — `docs/decisions/pnl-finalization-mechanics.md`.
Доменное число и правила PnL — `docs/models/domain/aggregate/Deal.md`
§«Итоговый PnL». Сквозные правила —
`docs/rules/raw-exchange-dto-boundary.md`,
`docs/rules/business-logic-on-domain-model.md`.

Снапшот, как `PositionExternalSnapshot`, **не требует отдельного
`*ExternalSnapshot.md`** (нет самостоятельного persisted содержания;
`docs/models/externalSnapshot/README.md`) — его поля зафиксированы ниже.

Текущие источники: **OKX**.

## Source-agnostic ядро

### Mapping-flow

```text
positions-history REST response -> raw OkxPositionsHistoryResponse
  -> IntegrationService validation
  -> PositionCloseResultMapper -> PositionCloseResultExternalSnapshot
  -> RefreshPositionExecutor (нога 2 evidence-cycle)
  -> Position (поля положения закрытия, persisted)
  -> FinalizeDealExitExecutor | MarkDealEmergencyClosedExecutor
  -> Deal.resultProfit
```

Raw DTO не выходит за пределы `IntegrationService` / adapter-layer;
executor работает только с validated normalized snapshot.

**Где факт живёт между добычей и потреблением** (H1/H3, `GAPS_CLOSE_7`,
ревизует H13 `GAPS_CLOSE_6`): **на строке `Position`**. Прежняя редакция
объявляла снапшот транзитным без durable-дома и потому уводила добычу во
**вложенный шаг** финализирующего действия — конструкция, которой канон
командного слоя не знает, и которая оставляла окно линковки bills без
верхней границы (у `REFRESH_BILLS_COMMAND`, идущей отдельным проходом, доступа к
чужой памяти нет). Посылка снята: добытое **персистится на `Position`**,
границу прохода FSM пересекает штатно, вложенность не нужна вовсе.
Число на `Deal` по-прежнему пишет **финализатор**
(`docs/decisions/pnl-finalization-mechanics.md` реш.2), не refresh-executor:
`Position` несёт **биржевой факт**, `Deal` — **посчитанное число**.

### `PositionCloseResultExternalSnapshot`

| Snapshot field | Тип | Семантика |
|---|---|---|
| `externalRealizedPnl` | `BigDecimal` | готовый net realized P&L (net от всех издержек, посчитан биржей) |
| `externalResultCurrency` | `String` | валюта, в которой посчитан `realizedPnl` |
| `externalCloseType` | `String` | тип последнего закрытия (`1`–`6`; ликвидация/ADL = `3`–`6`) |
| `externalPosId` | `String` | биржевой id позиции (ключ адресации записи) |
| `externalModifiedAt` | `OffsetDateTime` | время обновления записи positions-history |

Числовые/временные поля нормализуются при построении снапшота (string →
`BigDecimal`/`OffsetDateTime`, empty → null), уже провалидированные как
parseable. Тип времени — `OffsetDateTime` по конвенции проекта; **имя** поля
времени источника — конвенционное `externalModifiedAt` (симметрично
`PositionExternalSnapshot`), собственного `externalUpdatedAt` снапшот больше
не заводит (H25, `GAPS_CLOSE_7`;
`docs/models/domain/other/Auditable.md` §«Единое имя времени источника»).

**Состав сужен до полей с названным потребителем** (H22, `GAPS_CLOSE_7`;
codestyle §«Неиспользуемый код»). Выведены из снапшота: `closeAvgPx`
(средняя цена выхода), `triggerPx` (цена триггера ликвидации/ADL) — и
**`openAvgPx`** (H23, `DOCS_CHECK_8`): потребителя в фазе 1 нет ни у
одного, а маппинг `openAvgPx → Position.externalAverageEntryPrice` делал
колонку двуписьменной (live `avgPx` — текущая средняя, `openAvgPx` —
средняя за жизнь позиции; при доборах они расходятся, провенанс поля
становился неоднозначным). `Position.externalAverageEntryPrice` пишет
**только live-нога**; понадобится средняя за жизнь — заводится отдельное
поле, не перегружается это. Выведенные поля остаются кандидатами в
носители измеримости искажений (`PNL-Q1`). Побочно это **обесточивает
H19**: расхождение доков о применимости `triggerPx` больше не нагружено
ничем — поле не маппится вовсе.

### snapshot → `Position`

Применяет `RefreshPositionExecutor` (нога 2) на **той же** `Position`,
статус которой нога 1 уже перевела в `CLOSED`:

| Snapshot field | Domain | Семантика |
|---|---|---|
| `externalRealizedPnl` | `Position.externalRealizedProfit` | биржевой net realized P&L закрытой позиции |
| `externalResultCurrency` | `Position.externalResultCurrency` | валюта, в которой он посчитан |
| `externalCloseType` | `Position.externalCloseType` | провенанс закрытия (`3`–`6` = закрыла биржа) |
| `externalModifiedAt` | `Position.externalModifiedAt` + **`Deal.billsWindowEnd`** | `uTime` записи закрытия (конвенция `Auditable`, H25). На `Deal` — верхняя граница окна линковки bills, пишется той же транзакцией (узел 1 `DOCS_CHECK_8`; окно из `Position.externalModifiedAt` больше не реконструируется) |
| `externalPosId` | сверка с `Position.externalId` | не перезаписывает: адресация, а не данные |

### `Position` → `Deal` (финализатор)

| `Position` | `Deal` | Кто пишет |
|---|---|---|
| `externalRealizedProfit` | `resultProfit` (слагаемое net) | `FinalizeDealExitExecutor` / `MarkDealEmergencyClosedExecutor` |
| `externalResultCurrency` | `resultProfitCurrency` | они же |

`externalCloseType` в `Deal` **не пишется** — он вход провенанса
аварийного терминала (`docs/decisions/pnl-finalization-mechanics.md`
реш.3), читается со строки `Position`. Запрашиваемость провенанса
ликвидации/ADL на уровне `Deal` — открытый вопрос `PNL-Q1`.

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
  (пустое `realizedPnl` при чистом закрытии недопустимо). `triggerPx`
  валидацией не рассматривается вовсе — поле из снапшота выведено (H22,
  `GAPS_CLOSE_7`), поэтому расхождение доков о его применимости больше
  ничего не нагружает (закрывает остаток H19; единственный носитель
  формулировки — `docs/integrations/okx/contracts/position.md` §История,
  сверка с офдоком остаётся открытой у `integrator`).
- **Аварийный контур:** для `EMERGENCY_CLOSED` при genuinely недоступном
  net запись закрытия не найдена / числа не даёт → поля положения закрытия
  на `Position` остаются `null` → `Deal.resultProfit = null` с семантикой
  «неисчислимо» (не ноль), терминал всё равно проходит
  (`docs/decisions/pnl-finalization-mechanics.md` реш.3).

### Error policy

- **Temporary API problem** (timeout, connection reset, 5xx): нога 2
  наследует retry **своей команды** `REFRESH_POSITION_COMMAND` (командная
  машинерия, анкер — `DealActionState`); финализация ждёт факта.
- **Invalid response / инвариант агрегации нарушен** (`code != 0`,
  множественная/нефинализированная запись на `posId`, обязательные не
  парсятся): controlled external error; поля положения закрытия не
  пишутся; финализация не завершает чистый `CLOSED` без числа (инвариант
  непустоты `resultProfit`, `docs/models/domain/aggregate/Deal.md`).
- **Запись не найдена** — не ошибка команды: статус `CLOSED` уже поставлен
  ногой 1, поля остаются `null`, сделка уходит тропой «неисчислимо».
  Различение «жёсткий отказ чтения» vs «пусто» и реакция на каждой тропе —
  `docs/decisions/pnl-finalization-mechanics.md` §«Асимметрия троп отказа
  добычи».

## OKX

### `OkxPositionsHistoryResponse` → `PositionCloseResultExternalSnapshot`

См. инвентарь — `docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

| OKX field | Snapshot field |
|---|---|
| `realizedPnl` | `externalRealizedPnl` |
| `ccy` | `externalResultCurrency` |
| `type` | `externalCloseType` |
| `posId` | `externalPosId` |
| `uTime` | `externalModifiedAt` (epoch millis → `OffsetDateTime`) |

Числовые поля парсятся в `BigDecimal`, `uTime` — в `OffsetDateTime`; `empty
string → null`. Список не маппимых полей (`pnl`, `fee`, `fundingFee`,
`liqPenalty`, `settledPnl`, `pnlRatio`, `mgnMode`, `posSide`, а также
`closeAvgPx`/`triggerPx` — выведены H22; `openAvgPx` — выведен H23
`DOCS_CHECK_8`) — в
`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`.

### OKX validation notes

- **Structural:** `code == 0`.
- **Query:** запись добывается по `posId` (плюс `instType`/`instId`);
  пагинация positions-history — по `uTime` (`limit` ≤ 100),
  `docs/integrations/okx/contracts/position.md` §«История закрытых позиций».
- **Инвариант агрегации (N11):** `realizedPnl` кумулятивен по всем
  partial-закрытиям и доборам за жизнь `posId`; читается финализированной
  записью (позиция flat по `REFRESH_POSITION_COMMAND`). До рантайм-верификации —
  **предположение** (контур source-api, `.claude/tests/source-api/okx/plan.md`
  §AG1); гейтит корректность числа до `CODE`.
