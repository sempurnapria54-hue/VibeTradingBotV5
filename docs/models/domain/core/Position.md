# Position

## На какой вопрос отвечает этот файл

Что это за торговая модель `Position`: структура, атрибуты, енумы,
формула live risk, что хранит и что не хранит.

Статусы и переходы — в `docs/lifecycles/Position.md`.

## Назначение

`Position` — runtime-сущность позиции внутри `Deal`. Отражает состояние
сопровождаемой позиции и отвечает на вопрос: «есть ли live-risk позиция
по сделке прямо сейчас», а после закрытия — **несёт положение закрытия**
(realized-факты закрытой позиции, добытые второй ногой `REFRESH_POSITION_COMMAND`,
см. §«Положение закрытия»). Хранит **только** данные, нужные для
сопровождения live-risk позиции и для финализации сделки по ней.

`Position` **не** отвечает за: итоговый profit/loss сделки (число живёт
полем `Deal`, здесь — **биржевой факт**, из которого его считает
финализатор), историю команд, историю strategy actions, сырые exchange
responses, полную копию response биржи.

## Структура

Java-класс `com.example.tradingbot.domain.model.core.position.Position`,
расширяет `Auditable` (`externalCreatedAt` / `externalModifiedAt`
наследуются).

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор позиции в БД. |
| `dealId` | `Long` | Сделка, в рамках которой сопровождается позиция. |
| `externalId` | `String` | Биржевой ID позиции, если биржа его отдаёт (OKX: `posId`). Не stable client id. |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина закрытия / problem reason. Не дублирует `Deal.CloseReason`. |
| `direction` | `Direction` | Доменное направление (`LONG` / `SHORT`). |
| `externalSize` | `BigDecimal` | Размер по данным биржи, нормализованный абсолют (`abs(pos)`). |
| `externalAverageEntryPrice` | `BigDecimal` | Средняя цена входа. |
| `externalMarkPrice` | `BigDecimal` | Mark price. |
| `externalLiquidationPrice` | `BigDecimal` | Расчётная цена ликвидации. |
| `externalMargin` | `BigDecimal` | Маржа позиции. |
| `externalUnrealizedProfit` | `BigDecimal` | Нереализованный PnL. |
| `externalRealizedProfit` | `BigDecimal` | **Положение закрытия:** готовый net realized P&L закрытой позиции, посчитанный биржей (`realizedPnl` positions-history). `null`, пока позиция жива или запись закрытия не добыта. |
| `externalResultCurrency` | `String` | **Положение закрытия:** валюта, в которой посчитан `externalRealizedProfit` (`ccy` записи positions-history). **Проверяемый признак, не источник** `Deal.resultProfitCurrency` — авторитет валюты результата — расчётная валюта инструмента (H10 `DOCS_CHECK_10`, `docs/models/domain/aggregate/Deal.md` §«Валюта результата: один авторитет»). |
| `externalCloseAveragePrice` | `BigDecimal` | **Положение закрытия:** средняя цена **фактического выхода** (`closeAvgPx` записи positions-history). Потребитель назван — калибровка запаса на проскок **на тропе attached-SL** (основной операнд калибровки — `AlgoOrder.externalPrice`; §«Цена фактического выхода» ниже). `null`, пока позиция жива, запись закрытия не добыта либо источник цены не отдал. |
| `externalCloseType` | `String` | **Положение закрытия:** сырой тип последнего закрытия источника (OKX `type`: `1`–`2` торговое, `3`–`6` ликвидация/ADL). Провенанс аварийного терминала (`docs/decisions/pnl-finalization-mechanics.md` реш.3). |
| `externalFundingCost` | `BigDecimal` | **Положение закрытия:** накопленный funding закрытой позиции, посчитанный биржей (`fundingFee` записи positions-history). Потребитель назван — де-микширование R-мультипликатора (`docs/decisions/per-trade-risk-policy.md` §H25); `FUNDING`-строки `DealCashFlow` остаются **сверкой** этого числа, не источником (H20 `DOCS_CHECK_11`). `null`, пока позиция жива или запись закрытия не добыта. |

Поля §«Положение закрытия» пишет **вторая нога `REFRESH_POSITION_COMMAND`**
(positions-history), не финализатор; наследуемый `externalModifiedAt`
принимает `uTime` записи закрытия (той же транзакцией нога пишет
`Deal.billsWindowEnd` — верхнюю границу окна линковки bills; из
`externalModifiedAt` окно **не реконструируется**, узел 1
`DOCS_CHECK_8`). `externalAverageEntryPrice` пишет **только live-нога**
(`avgPx`); `openAvgPx` записи закрытия не маппится (H23 —
`docs/models/mapping/PositionCloseResult.md`). Состав ограничен полями с
**названным потребителем** (codestyle §«Неиспользуемый код»); что
осталось за бортом и почему — §«Что `Position` не хранит».

## Инварианты

- `Position` принадлежит `Deal` через `dealId`. В рамках одной `Deal`
  допускается максимум одна `Position` (`relatedPositions` не нужны).
- `Position` **не** хранит `instrumentId`, `exchangeId`, `internalId`,
  `strategyActionId`, `strategyActionKey`. Эти данные приходят через
  `DealContext` (Exchange / Instrument), см. lifecycle.
- `Position` создаётся и обновляется только через `REFRESH_POSITION_COMMAND`
  executor (**обе ноги** — live и positions-history); FSM напрямую
  `Position` не создаёт и поля не заполняет.
- `Position` не client-created entity, не имеет stable client id.
  `externalId` (OKX `posId`) не вечен: биржа может очистить id после
  закрытия — поэтому не единственный источник идемпотентности.
  - **Адресация записи positions-history не ключуется `posId`.** Когда
    `posId` не наблюдался (позиция открылась и закрылась между тиками) либо
    переиспользован биржей, запись адресуется инструментом и окном сделки;
    однозначность держит инвариант «одна активная сделка на инструмент»
    (H9 `DOCS_CHECK_10` — прежняя ветка «ноги 2 нет без локального `posId`»
    снята, см. `docs/components/RefreshPositionExecutor.md`).
  - **Хвост `integrator`:** какие оси запроса принимает история позиций
    источника и как она ведёт себя, если в окне по инструменту оказалось
    несколько записей — сверка по контракту
    (`docs/integrations/okx/contracts/position.md`).

## Енумы

### `Direction`

`LONG` / `SHORT`. `NET` **не** используется как direction: net — это
режим/сторона позиции на бирже, а не направление рыночного риска.
Для OKX net-mode направление выводится из знака `pos` (см.
`docs/models/mapping/Position.md`). `posSide=net`
валидируется в adapter-layer и в `Position` не хранится.

### `Status`

`ACTIVE` / `CLOSED` / `ERROR`. Минимальный набор; промежуточные
`CREATED` / `PENDING` / `OPENING` / `CLOSING` / `PARTIALLY_CLOSED`
не вводятся. Значения и переходы — в `docs/lifecycles/Position.md`.

### `CloseReason`

- `CLOSED_BY_STRATEGY` — штатное закрытие как действие стратегии.
- `KILL_SWITCH` — аварийный safety-flow / kill-switch.
- `MANUAL_CLOSE` — ручное закрытие пользователем.
- `EXTERNAL_CLOSE` — позиция закрылась на стороне биржи без текущей
  команды close (SL/TP/trailing/liquidation/ADL/иной exchange-event).
- `EXCHANGE_INVARIANT_VIOLATION` — problem reason для `ERROR` (adapter
  обнаружил нарушение exchange-specific invariant).
- `UNKNOWN` — fallback, если причину безопасно определить не удалось.

`Position.CloseReason` (каким механизмом закрыта позиция) не дублирует
`Deal.CloseReason` (почему завершилась сделка с точки зрения торговой
логики). Примеры: SL → `EXTERNAL_CLOSE` / `Deal=STOP_LOSS`; явный
close → `CLOSED_BY_STRATEGY` / `Deal=STRATEGY_EXIT`; kill-switch →
`KILL_SWITCH` / `Deal=EMERGENCY_CLOSE`. Правило записи (write-once) —
в lifecycle.

## Live risk

Формула (первоисточник — здесь, `.claude/decisions/rule-source-of-truth.md`):

```java
public boolean hasLiveRisk() {
    return status == Status.ACTIVE
        && externalSize != null
        && externalSize.compareTo(BigDecimal.ZERO) > 0;
}
```

`ACTIVE` сам по себе ещё не означает live market risk — нужен
`externalSize > 0`. Различение `status` vs live risk и случай
`ACTIVE && externalSize == 0` (cleanup/anomaly/retry, live risk = false)
— в `docs/lifecycles/Position.md`.

## `PositionExternalSnapshot`

Нормализованный объект для обновления `Position` (не raw/diagnostic
exchange response; раздел модели по `.claude/decisions/model-granularity.md`).
Создаётся только если позиция реально найдена на бирже; если не
найдена — `IntegrationService` возвращает `null`, а не пустой snapshot.

Поля: `externalId`, `externalSize`, `externalAverageEntryPrice`,
`externalMarkPrice`, `externalLiquidationPrice`, `externalMargin`,
`externalUnrealizedProfit` (+ `externalCreatedAt` /
`externalModifiedAt` от `Auditable`). Если поле не обновляет
`Position` — в snapshot не попадает. OKX mapping — в
`docs/models/mapping/Position.md`.

Вторая нога `REFRESH_POSITION_COMMAND` (positions-history) нормализуется
**своим** граничным объектом `PositionCloseResultExternalSnapshot` и
обновляет ту же `Position` полями §«Положение закрытия»
(`docs/models/mapping/PositionCloseResult.md`). Два снапшота — потому
что это два разных ответа источника об одной сущности, а не потому что
сущностей две.

## Положение закрытия

**Добывается второй ногой `REFRESH_POSITION_COMMAND`** (H1/H3, `GAPS_CLOSE_7`).
`REFRESH_POSITION_COMMAND` проходит evidence-cycle **внутри одной команды**: live
`/account/positions` → при not-found (позиция закрыта)
`/account/positions-history` по `posId`. Это тот же within-command-обход,
которым `REFRESH_ORDER_COMMAND` эскалирует live → pending → history
(`docs/decisions/refresh-evidence-cycle-ownership.md`,
`docs/rules/command-lifecycle.md` §«Команды атомарны»); отдельной команды
`REFRESH_POSITIONS_HISTORY` **не вводится** — сущность одна (`Position`),
а refresh-набор держит по одной команде на сущность.

Добытое **приземляется на `Position`** (persisted), а не живёт транзитно:
`externalRealizedProfit`, `externalResultCurrency`, `externalCloseType`,
`externalModifiedAt` (`uTime` записи закрытия; на `Deal` та же транзакция
пишет `billsWindowEnd`). Следствия:

- у факта закрытия есть **durable-дом** ⇒ он пересекает границу прохода
  FSM штатно, и потребители (финализатор штатной тропы, аварийный
  терминал, окно линковки bills) читают его со строки, а не из памяти
  чужого действия;
- **идемпотентность — командная**, как у любого `REFRESH_*`: повторное
  чтение приводит поля к состоянию биржи; вложенности «команда внутри
  команды» не возникает вовсе;
- поля пишутся **write-once по факту**: повторный проход перезаписывает их
  тем же значением записи (адресация по `posId`), а не накапливает.

Нормализация ответа и per-source-маппинг —
`docs/models/mapping/PositionCloseResult.md`; контракт эндпоинта —
`docs/integrations/okx/contracts/position.md` §«История закрытых позиций».

### Цена фактического выхода (H26 `DOCS_CHECK_10`; операнд пересмотрен H21 `DOCS_CHECK_11`)

Разница «цена стопа ↔ цена фактического выхода» — операнд калибровки
запаса на проскок за стоп
(`docs/decisions/per-trade-risk-policy.md` §«Без поправки на проскок»).
Бэктест-проскок систематически оптимистичен именно на тех барах, где стоп
и срабатывает [Kaufman гл.21 «Price Shocks», PDF с.1895-1899], поэтому
калибровка обязана опираться на живые исполнения.

**Основной операнд — `AlgoOrder.externalPrice`, не `externalCloseAveragePrice`**
(решение пользователя H21 `DOCS_CHECK_11`). Выборка ограничена
**стоповыми типами условия** — `AlgoOrder.conditionType ∈ {STOP_LOSS,
OCO_FULL, PARTIAL_STOP_LOSS}` при `closeReason = TRIGGERED`.

- **Почему операнд сменился.** `externalCloseAveragePrice` — средняя цена
  выхода **за всю жизнь позиции**: она смешивает частичные TP с
  исполнением стопа (для LONG частичный TP выше входа, стоп ниже ⇒ средняя
  смещена в благоприятную сторону, измеренный проскок систематически
  **занижается**) и не отличает стоп-аут от strategy-exit / TIME_STOP /
  TAKE_PROFIT / ликвидации. `AlgoOrder.externalPrice` — фактическая цена
  срабатывания **той самой стоп-ноги**, смешения нет по построению.
- **Обе половины сравнения — на одной строке.** Уровень стопа живёт в
  `AlgoOrder.condition.trigger.stopLoss.value` **того же** `AlgoOrder`,
  который сработал, поэтому вопрос «какая из двух цен стопа берётся при
  трейлинге» не возникает: сработавший `AlgoOrder` и есть последний живой
  (`docs/models/domain/core/AlgoOrder.md` §Структура,
  §Condition-модель).
- **Посылка «полей фактического исполнения у алго-сущности нет» неверна.**
  Прежняя редакция этого раздела опиралась на неё как на довод. Фактически
  `AlgoOrder` несёт `externalSize` (`actualSz`), `externalPrice`
  (`actualPx`) и `externalTriggerTime` (`triggerTime`) — и в модели
  (`docs/models/domain/core/AlgoOrder.md` §Структура), и в маппинге
  (`docs/models/mapping/AlgoOrder.md`), и в коде
  (`AlgoOrder.java`). Верно она **только для `AttachedAlgoOrder`**: у
  attached-защиты есть `stopLossTriggerPrice` (заявленный уровень), но
  цены фактического срабатывания нет.

**Остаточный потребитель `externalCloseAveragePrice` — тропа attached-SL.**
Выход через `AttachedAlgoOrder` — тоже стоп-аут, но собственной цены
исполнения у него нет. Для такой сделки единственный наблюдаемый факт
выхода — `externalCloseAveragePrice`, и он равен цене стоп-ноги **только**
при полном стоп-ауте без частичных выходов. Поэтому подвыборка
ограничивается предикатом: выход через attached-SL, `externalCloseType = 2`
и отсутствие строк partial-exit действий по сделке. Подвыборка **счётна**
(на контуре с активными partial-TP может оказаться пустой — это
наблюдаемо счётчиком, а не молча).

- **Расчётного потребителя в фазе 1 ни у одного из операндов нет** — они
  накапливают наблюдения; сам запас назначается на своём шаге.
  `triggerPx` остаётся выведенным: он кандидат `PNL-Q1` (провенанс
  ликвидации/ADL), другой вопрос.
- **Хвост `integrator`:** означает ли `actualPx` цену **исполнения**
  сработавшего ордера или цену его **выставления** после триггера. От
  ответа зависит, измеряет ли разность проскок или ноль
  (`.claude/tests/source-api/okx/plan.md`).

## Персистентность

Хранится в БД (entity `PositionEntity`, таблица `positions`, создана
`V6__create_deal_runtime_tables.sql`), наследует audit-поля
(`AuditableEntity`). Enum-поля (`status`, `close_reason`, `direction`)
хранятся строкой (имя enum; codestyle §Слои моделей и enum'ы).

**Колонки положения закрытия — `ALTER`, в `V6` их нет** (симметрично
`Deal.md`/`DealActionState.md`, H21 `DOCS_CHECK_8`):
`external_realized_profit`, `external_result_currency`,
`external_close_average_price` (H26 `DOCS_CHECK_10`),
`external_close_type` — nullable (пусты, пока позиция жива или запись
закрытия не добыта), добавляются миграцией шага 7; полная schema-дельта
шага — `docs/decisions/pnl-finalization-mechanics.md` §Следствия.

## Что Position не хранит

`Position` не хранит fills, слагаемые net (`pnl`, `fee`, `fundingFee`,
`liqPenalty` — категорийная разбивка живёт в `DealCashFlow`),
strategy/action/audit history, raw exchange response.

**Из положения закрытия не заводится полем** (нет потребителя в фазе 1 —
codestyle §«Неиспользуемый код»; H22, `GAPS_CLOSE_7`): цена триггера
ликвидации/ADL (`triggerPx`) — кандидат в носители **измеримости
искажений** (провенанс ликвидации/ADL); вопрос открыт (`PNL-Q1`,
`.claude/work/questions/open-questions.md`) и заводит поле вместе с
потребителем, а не раньше. В инвентаре источника поле числится
неиспользуемым
(`docs/models/integrations/okx/OkxPositionsHistoryResponse.md`).

**Средняя цена выхода (`closeAvgPx`) из этого перечня выведена** (H26
`DOCS_CHECK_10`): у неё потребитель назван — калибровка запаса на проскок,
— и она заведена полем `externalCloseAveragePrice` (§«Цена фактического
выхода»).

`Position` (live `/positions`) **сам по себе не считает** итоговый PnL:
заголовочное `Deal.resultProfit` = net `realizedPnl` из **positions-history**
(поле `externalRealizedProfit` этой модели), разбивка — из bills; правило
принадлежит `Deal` (`.claude/decisions/rule-source-of-truth.md`,
`docs/models/domain/aggregate/Deal.md` §Итоговый PnL,
`docs/decisions/result-profit-source.md`). Полное закрытие
подтверждается через `REFRESH_POSITION_COMMAND`, не через ACK (см.
`docs/rules/ack-not-runtime-truth.md`).
