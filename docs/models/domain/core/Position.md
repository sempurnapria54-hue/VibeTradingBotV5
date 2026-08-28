# Position

## На какой вопрос отвечает этот файл

Что это за торговая модель `Position`: структура, атрибуты, енумы,
формула live risk, что хранит и что не хранит.

Статусы и переходы — в `docs/lifecycles/Position.md`.

## Назначение

`Position` — runtime-сущность позиции внутри `Deal`, **строка одного
эпизода** (одна биржевая позиция = один `posId`). Отражает состояние
сопровождаемой позиции и отвечает на вопрос: «есть ли live-risk позиция
по сделке прямо сейчас», а после закрытия — **несёт положение закрытия**
(realized-факты закрытой позиции, добытые второй ногой `REFRESH_POSITION_COMMAND`,
см. §«Положение закрытия»). Хранит **только** данные, нужные для
сопровождения live-risk позиции и для финализации сделки по ней.

**Сделка многоэпизодна** (решение держателя,
`docs/decisions/multi-episode-deal.md`): позиция может схлопнуться в ноль
и открыться заново новой ногой входа, получив **новый `posId`**. Каждый
эпизод — своя строка `Position`; живой из них может быть только одна
(§Инварианты).

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
| `externalCloseType` | `String` | **Положение закрытия:** сырой тип **последнего** закрытия источника (OKX `type`: `1`–`2` торговое, `3`–`6` ликвидация/ADL). Провенанс аварийного терминала (`docs/decisions/pnl-finalization-mechanics.md` реш.3) и операнд `Deal.closeOutcome` (H2 `GAPS_CLOSE_13`). Что поле **не** несёт: принудительный эпизод **внутри** сделки, за которым последовал наш выход, — см. `docs/models/domain/aggregate/Deal.md` §«Признаки отбора для отчёта» (H5 `GAPS_CLOSE_13`). |
| `externalRealizedProfitGross` | `BigDecimal` | **Положение закрытия:** реализованный P&L **до** издержек (`pnl` записи positions-history). Потребитель назван — первая пара раздельной сверки разбивки (H19 `DOCS_CHECK_12`, `docs/components/FinalizeDealExitExecutor.md`). `null`, пока позиция жива или запись закрытия не добыта. |
| `externalFee` | `BigDecimal` | **Положение закрытия:** знаковая комиссионная компонента записи (`fee`; минус — комиссия, плюс — ребейт — **сырой знак**, как у `DealCashFlow.externalFee`). Потребитель назван — вторая пара раздельной сверки (H19 `DOCS_CHECK_12`). `null`, пока позиция жива или запись закрытия не добыта. |
| `externalFundingCost` | `BigDecimal` | **Положение закрытия:** накопленный funding закрытой позиции (`fundingFee` записи positions-history), **знак нормализован при маппинге в снапшот**: это **издержка** — положительна, когда фондирование уплачено (H20 `DOCS_CHECK_12`; единственное место приведения — `docs/models/mapping/PositionCloseResult.md` §«Знак `fundingFee`»). Потребители названы — де-микширование R-мультипликатора (`docs/decisions/per-trade-risk-policy.md` §«Асимметрия числителя и знаменателя») и третья пара раздельной сверки (H19 `DOCS_CHECK_12`); `FUNDING`-строки `DealCashFlow` остаются **сверкой** этого числа, не источником (H20 `DOCS_CHECK_11`). `null`, пока позиция жива или запись закрытия не добыта. |
| `externalLiquidationPenalty` | `BigDecimal` | **Положение закрытия:** ликвидационный штраф записи (`liqPenalty`; **сырой знак** источника). Потребитель назван — четвёртая пара раздельной сверки против категории `LIQ_PENALTY` (H7 `DOCS_CHECK_13`, `docs/components/FinalizeDealExitExecutor.md`). `null`, пока позиция жива или запись закрытия не добыта. |

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
  допускается максимум одна **живая** (`status = ACTIVE`) `Position`;
  закрытые эпизоды остаются строками той же таблицы
  (`docs/decisions/multi-episode-deal.md`). Прежний инвариант «максимум
  одна `Position` на `Deal`» **снят** вместе с ключом `uk_position_deal`
  (§Персистентность). `relatedPositions` по-прежнему не нужны: эпизоды
  адресуются `dealId` и своим `externalId`.
- **Эпизоды различаются `externalId` (`posId`).** Пара
  (`dealId`, `externalId`) уникальна: наблюдение того же `posId`
  обновляет свою строку, наблюдение нового — заводит новую и закрывает
  предыдущую (`docs/lifecycles/Position.md` §«Смена эпизода»).
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
    переиспользован биржей, запись адресуется инструментом и **временным
    окном от `Order.externalCreatedAt` первой отправленной ноги входа**
    (H12 `DOCS_CHECK_12`, решение пользователя); однозначность держит
    инвариант «одна активная сделка на инструмент»
    (H9 `DOCS_CHECK_10` — прежняя ветка «ноги 2 нет без локального `posId`»
    снята, см. `docs/components/RefreshPositionExecutor.md`).
    **«Окном сделки» операнд назывался ошибочно, и довод переписан** (H9 /
    Р7 `GAPS_CLOSE_16`): нижнюю границу окна пишет **`SubmitOrderExecutor`**
    — `Order.externalCreatedAt` первой отправленной ноги, всегда при
    постановке, — то есть к моменту запроса она **стоит**; верхнюю
    (`Deal.billsWindowEnd`) пишет эта же нога. Операнд всё равно берётся
    **напрямую с ноги**, чтобы тропа не зависела от порядка двух
    собственных записей (`docs/models/mapping/PositionCloseResult.md`
    §Validation). Прежняя редакция («обе границы пишет эта же нога, то есть
    на момент запроса они пусты») выражала снятую топологию писателей.
    Ограничение подтропы (вход не дошёл до биржи ⇒ операнда нет) —
    `docs/components/RefreshPositionExecutor.md` §Evidence-cycle.
  - **Несколько записей в окне — это несколько эпизодов одной сделки**
    (`docs/decisions/multi-episode-deal.md`), а не неразобранная
    развилка адресации: однозначность держит тот же инвариант слота
    («одна активная сделка на инструмент»), и каждая запись окна
    материализуется своей строкой `Position` по своему `posId`.
    **Остаток хвоста `integrator`** — какие оси запроса принимает
    история позиций источника (сверка по контракту,
    `docs/integrations/okx/contracts/position.md`); рантайм-подтверждение
    многозаписного окна — §AG1 контура тестов источника.

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

Поля: `externalId`, `externalSize`, **`direction`** (доменный
`Position.Direction` — нормализован в слое интеграции из знака `pos`,
`docs/models/mapping/Position.md` §«Direction mapping»),
`externalAverageEntryPrice`, `externalMarkPrice`,
`externalLiquidationPrice`, `externalMargin`,
`externalUnrealizedProfit` (+ `externalCreatedAt` /
`externalModifiedAt` от `Auditable`). Если поле не обновляет
`Position` — в snapshot не попадает. OKX mapping — в
`docs/models/mapping/Position.md`.

> `direction` в перечень **дозаведён** (H12 `DOCS_CHECK_15`, курационный
> хвост): поле есть и в коде снапшота, и в mapping-таблице, а перечень
> здесь его не называл. Симметричное поле второго снапшота —
> `PositionCloseResultExternalSnapshot.direction` (H7 `DOCS_CHECK_15`:
> тоже доменное значение, нормализуется на границе).

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
`docs/rules/command-lifecycle.md` §Правило); отдельной команды
`REFRESH_POSITIONS_HISTORY` **не вводится** — сущность одна (`Position`),
а refresh-набор держит по одной команде на сущность.

Добытое **приземляется на `Position`** (persisted), а не живёт транзитно:
**все поля §Структура с пометкой «Положение закрытия»** (восемь) плюс
наследуемый `externalModifiedAt` (`uTime` записи закрытия; на `Deal` та же
транзакция пишет `billsWindowEnd`). Перечень здесь не дублируется —
дубль-перечень из четырёх полей отстал от состава и утверждал второй
состав в том же файле (H1 `DOCS_CHECK_14`); место истины состава —
§Структура и таблица snapshot → `Position`
(`docs/models/mapping/PositionCloseResult.md`). Следствия:

- у факта закрытия есть **durable-дом** ⇒ он пересекает границу прохода
  FSM штатно, и потребители (финализатор штатной тропы, аварийный
  терминал, окно линковки bills) читают его со строки, а не из памяти
  чужого действия;
- **идемпотентность — командная**, как у любого `REFRESH_*`: повторное
  чтение приводит поля к состоянию биржи; вложенности «команда внутри
  команды» не возникает вовсе;
- поля пишутся **write-once по факту своего эпизода**: повторный проход
  перезаписывает их тем же значением записи (адресация по `posId`), а не
  накапливает. Накопление по сделке живёт **уровнем выше** — числа
  `Deal` суть суммы по строкам эпизодов
  (`docs/models/domain/aggregate/Deal.md` §«Итоговый PnL»), поэтому
  пересчёт остаётся идемпотентным: строка приводится к состоянию биржи,
  сумма выводится из строк.

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
`Deal.md`/`DealActionState.md`, H21 `DOCS_CHECK_8`). Их **восемь**:
`external_realized_profit`, `external_result_currency`,
`external_close_average_price` (H26 `DOCS_CHECK_10`),
`external_close_type`, **`external_funding_cost`** (H20 `DOCS_CHECK_11` —
операнд де-микширования R-мультипликатора; пропуск в этом перечне закрыт
H15 `DOCS_CHECK_12`), **`external_realized_profit_gross`** и
**`external_fee`** (H19 `DOCS_CHECK_12` — правые операнды раздельной сверки
по категориям), **`external_liquidation_penalty`** (H7 `DOCS_CHECK_13` —
правый операнд четвёртой пары) — все `numeric(36,18)` кроме
`external_result_currency` и `external_close_type` — обе
**`varchar(64)`** (единая норма длин строковых колонок, H18
`DOCS_CHECK_15`; прежние `varchar(16)`/`varchar(32)` по снятой
категоризации «валюта / сырой код источника»; типы
дописаны H13 `DOCS_CHECK_14` по общему правилу
`docs/rules/persistence-representation.md` §«Строковые колонки»), все nullable
(пусты, пока позиция жива или запись закрытия не добыта), добавляются
миграцией шага 7; полная schema-дельта шага —
`docs/decisions/pnl-finalization-mechanics.md` §«Schema-дельта шага 7».

**Ключи и индексы — многоэпизодная форма**
(`docs/decisions/multi-episode-deal.md`), миграцией шага 7:

- **`uk_position_deal` дропается** — «одна позиция на сделку» перестало
  быть инвариантом;
- **`uk_position_deal_live`** — частичный уникальный индекс
  `on positions (deal_id) where status = 'ACTIVE'`: живой эпизод у
  сделки один. Предикат стоит на `status`, не на `external_size`:
  `ACTIVE && externalSize = 0` — легитимное состояние живой строки
  (§Live risk);
- **`uk_position_deal_external`** — **частичный** уникальный индекс
  `unique (deal_id, external_id) where external_id is not null`:
  идемпотентность материализации эпизода по `posId`. Форма — частичная,
  а не сплошная (B5 `DOCS_CHECK_21`): собственное правило проекта прямо
  называет сплошной `UNIQUE` поверх nullable-операнда **дефектом ключа,
  а не страховкой** и требует выражать такой ключ частичным индексом
  либо `UNIQUE NULLS NOT DISTINCT`
  (`docs/rules/idempotency-via-unique.md`). Предикат делает намерение
  явным: ключ охраняет строки с **наблюдённым** `posId`.
  - **Строка без наблюдённого `posId` ключом не охраняется** — и это
    записано, а не выводится читателем. Довод «такая строка бывает не
    более чем одна» держится на первом ключе (`where status =
    'ACTIVE'`) и действует, **пока строка жива**: после перевода в
    `CLOSED` первый ключ её не держит, и второй такой же строки схема не
    запрещает. Тропа появления второй — повторная материализация
    закрытого эпизода без `posId`; исполнитель её не порождает
    (`docs/components/RefreshPositionExecutor.md`), но схемного запрета
    нет. Цена ложности: Σ `resultProfit` считается **по строкам
    эпизодов**, то есть дубль задваивает число.
  - **`UNIQUE NULLS NOT DISTINCT` отвергнут** — он охраняет и
    null-строку, но требует зафиксировать минимальную версию PostgreSQL
    (PG 15+), которая в доках не зафиксирована, и расходится с формой
    сестринских ключей того же шага (`deal_*_action_states` — частичные
    с явным отсечением `NULL`);
- **`ix_position_deal`** `on positions (deal_id)` — покрытие горячей
  выборки эпизодов сделки. Прежде его давал префикс `uk_position_deal`
  (комментарий блока индексов `V6` говорит это дословно); со снятием
  ключа покрытие пропадает, а читается оно на **каждом** проходе
  (сборка `DealContext`, суммы финализации).

Бэкфилл не нужен — таблицы пусты
(`.claude/rules/pre-launch-schema-changes.md`).

**Перечень здесь и schema-дельта шага обязаны совпадать по составу**
(`docs/rules/persistence-representation.md` §«Место истины схемы»): этот
раздел — место истины, дельта — сборка-указатель.

## Что Position не хранит

`Position` не хранит fills, **категорийную разбивку** движений средств
(она живёт в `DealCashFlow`), strategy/action/audit history, raw exchange
response. Слагаемые net записи закрытия (`pnl`, `fee`, `fundingFee`,
`liqPenalty`) полями **есть** — как правые операнды четырёх пар сверки
(§«Положение закрытия» выше); разбивкой они не являются: это четыре
числа биржи, против которых сверяются четыре суммы по категориям.

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
