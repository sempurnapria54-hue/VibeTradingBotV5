# Order

## На какой вопрос отвечает этот файл

Что это за торговая модель `Order` (ordinary order) и вложенная
`AttachedAlgoOrder` (attached protection): структура, атрибуты,
енумы, external snapshots.

Статусы и переходы — в `docs/lifecycles/Order.md`.

## Назначение

`Order` — ordinary exchange order, связанный с конкретной `Deal`.
Хранит: локальный intent (что бот хотел создать), идентификаторы
(`internalId` + биржевой `externalId`), актуальный доменный статус,
сырой внешний статус биржи, параметры цены/размера, факты исполнения,
attached protection (если создана вместе с parent order).

`Order` **не** является действием стратегии. Связь
`StrategyAction` ↔ `Order` хранится через
`DealActionState` → `RuntimeTarget(entityType = ORDER, entityId)`,
поэтому `Order` не хранит `strategyActionId`, `strategyActionKey`,
`role`, `level` стратегии (механизм связи —
`docs/models/domain/other/DealActionState.md`).

## Структура `Order`

Java-класс `com.example.tradingbot.domain.model.core.order.Order`,
расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `dealId` | `Long` | Сделка, к которой относится ордер. |
| `internalId` | `String` | Межсервисный id; stable client id (OKX `clOrdId`). |
| `externalId` | `String` | Биржевой id ordinary order (OKX `ordId`). |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина финализации / перевода в ERROR. |
| `type` | `Type` | Бизнес-тип ordinary order (не подменяет strategy role). |
| `side` | `String` | Сторона (OKX `buy` / `sell`). |
| `externalStatus` | `String` | Сырой статус биржи (OKX `state`) — **диагностический факт**, FSM напрямую не использует. |
| `price` | `BigDecimal` | Цена (для market-like может быть null). |
| `size` | `BigDecimal` | Размер (для SWAP/FUTURES — контракты). |
| `accumulatedFillSize` | `BigDecimal` | Накопленный исполненный объём. |
| `averagePrice` | `BigDecimal` | Средняя цена исполнения. |
| `fee` | `BigDecimal` | Накопленная комиссия. |
| `positionReducingOnly` | `Boolean` | Доменное намерение: ордер только уменьшает позицию. |
| `replacesInternalId` | `String` | `internalId` предшественника в цепочке REPLACE (nullable; append-only след — обратная ссылка не хранится, выводится запросом). См. `docs/decisions/replace-not-amend.md`. |
| `plannedEntryPrice` | `BigDecimal` | Reference-цена входа **этой ноги**, по которой считался её риск (для market-входа — `ORDER_MARKET_REFERENCE_PRICE` калькулятора, на биржу не отправляемая). Write-once. |
| `plannedSizeContracts` | `BigDecimal` | Заявленный размер **этой ноги** (контракты). Write-once. |
| `plannedRiskAmount` | `BigDecimal` | **Плановый риск этой ноги** — убыток на её стопе, посчитанный при постановке ноги. Слагаемое знаменателя `R` сделки (H6/H11 `DOCS_CHECK_15`). Write-once. |
| `plannedRiskCurrency` | `String` | Валюта планового риска ноги (расчётная валюта инструмента). Write-once. |
| `plannedContractValue` | `BigDecimal` | Размер контракта инструмента (`ctVal`) **на момент постановки этой ноги** — четвёртый операнд тождества планового риска. Write-once (H5 `DOCS_CHECK_16`). |
| `attachedAlgoOrders` | `List<AttachedAlgoOrder>` | Embedded attached protection. |

Доменные методы: `isLive()` (CREATED/PENDING/ACTIVE/PARTIALLY_COMPLETED),
`hasActiveAttachedProtection()` (есть ≥1 active-like (PENDING/ACTIVE)
attached-защита), `toCancel(reason)`, `toComplete()` (→ COMPLETED/FILLED),
`toError(reason)`.

### `positionReducingOnly`

Доменное намерение (не внешний факт биржи, не заполняется из
`OrderExternalSnapshot`): ordinary order должен только уменьшать
позицию, не открывать/не увеличивать. Важно для partial exit
(частичное уменьшение — только через reduce-only `Order`/`AlgoOrder`,
см. `docs/rules/no-partial-close.md`). Маппинг в OKX `reduceOnly` и
invariant-проверка — в `docs/models/mapping/Order.md`.
Если биржа не поддерживает reduce-only/close-only — adapter может
проигнорировать; unsupported exchange на первом этапе не блокируем.

### Енумы `Order`

- **`Type`**: `ENTRY`, `ENTRY_ATTACHED_STOP_LOSS`, **`REDUCE_ONLY`**
  (третье значение введено решением держателя, `GAPS_CLOSE_16`). Бизнес-тип,
  не описывает strategy role (grid-entry / partial-exit / full-exit).
  - `ENTRY` — вход **без** встроенной защиты;
  - `ENTRY_ATTACHED_STOP_LOSS` — вход **со** встроенным attached SL;
  - **`REDUCE_ONLY`** — нога, **уменьшающая** позицию: частичный выход
    шага `PARTIAL_EXIT` (`docs/rules/no-partial-close.md`). Риска не
    создаёт, риск-преконтроль не проходит, полей планового риска не несёт.

  **Енум различает две оси, и это принято сознательно** (H1
  `DOCS_CHECK_16` + решение держателя): первые два значения — **наличие
  встроенной защиты**, третье — **направление риска**. Прежняя редакция
  («`Type` предикатом отбора ног входа быть не может») **снята**: с третьим
  значением `Type` разделяет, и предикаты на нём становятся верными.

  > **Инвариант, которым удерживаются две оси:**
  > `Type = REDUCE_ONLY` ⇔ `positionReducingOnly = true`.
  > Поля два намеренно и роли у них разные: `positionReducingOnly` —
  > **доменное намерение**, уходящее на биржу (`OKX reduceOnly`,
  > `docs/models/mapping/Order.md`); `Type.REDUCE_ONLY` — **бизнес-тип**,
  > по которому идёт отбор. Рассогласование пары — нарушение инварианта, а
  > не допустимое состояние: валидация состава шага стратегии обязана его
  > отвергать (дельта `CODE`, `.claude/work/backlog.md` §Шаг 7).
  > Названная цена решения: носителей у одной оси два, и согласованность
  > держится проверкой, а не построением.
  >
  > **Основание — решение держателя, а не исключение в правиле
  > именования.** Правка, вводившая такое исключение в
  > `.claude/rules/naming.md`, держателем **отклонена** (`GAPS_CLOSE_16`):
  > правило разведения уровней абстракции остаётся без исключений, а два
  > случая ошибочных предикатов — повод чинить **предикаты**
  > (`.claude/work/backlog.md` §Шаг 7), а не правило. Само третье
  > значение в силе; ссылок на несуществующее исключение в доках нет.

  Дельта `CODE`: javadoc всех трёх констант в `Order.java` приводится к
  этой редакции — прежний называл обе существующие «входным ордером»
  (`.claude/work/backlog.md` §Шаг 7).
- **`Status`**: `CREATED`, `PENDING`, `ACTIVE`, `PARTIALLY_COMPLETED`,
  `COMPLETED`, `CANCELED`, `ERROR` (значения/переходы — в lifecycle).
- **`CloseReason`**: `FILLED`, `CANCELED_BY_STRATEGY`,
  `REPLACED_BY_STRATEGY` (стратегия заменила другим ордером —
  REPLACE-ремодел, симметрично `AlgoOrder`), `KILL_SWITCH`,
  `MANUAL_CANCEL`, `CONDITION_EXPIRED` (условие создания/ожидания
  ордера больше неактуально — штатная причина, не ошибка),
  `MISSING_AFTER_REFRESH` (не найден после refresh/search/history
  цикла), `UNKNOWN_EXTERNAL_STATUS`, `EXCHANGE_INVARIANT_VIOLATION`,
  `UNKNOWN`.

### Плановый риск и его операнды — дом здесь (`RISK-Q4` закрыт; H6/H11 `DOCS_CHECK_15`)

`plannedEntryPrice` (reference-цена входа, по которой считался риск),
`plannedSizeContracts` (заявленный размер) и **`plannedRiskAmount` /
`plannedRiskCurrency`** (сам плановый риск) — **атрибуты ноги входа**, не
сделки: при многоногом входе их несколько.

**Риск переехал на ногу вместе со своими операндами** (H6/H11
`DOCS_CHECK_15`, решение пользователя). Прежде на `Deal` жило одно
write-once-число, а операнды сравнения — на ногах; тождество, на котором
стоит омиссионный член epsilon
(`plannedRiskAmount − |plannedEntryPrice − stop| × plannedSizeContracts ×
ctVal` = ожидаемая комиссия), верно **только в одной точке** — там, где
все три операнда принадлежат одной ноге. При многоногом входе обе
возможные ветки были односторонними: агрегат по сделке против ценовой
части одной ноги даёт **отрицательное** вычитаемое (допуск схлопывается до
шум-флора на самых крупных сделках), первая нога — вычитаемое ~1/N
(допуск систематически уже режима отказа). Держа риск на ноге, тождество
восстанавливается **поногово**, а сделке достаётся **сумма** — и та же
сумма закрывает второй дефект: знаменатель `R` перестаёт быть числом
первой ноги (`docs/models/domain/aggregate/Deal.md` §«Плановый риск»).

**Все шесть — write-once** (`updatable = false`; шестое —
`plannedStopPrice`, §ниже). REPLACE-нога не
переписывает ни reference-цену, ни риск: иначе разрыв «заявлено ↔ взято»
становится неизмеримым, а слагаемое знаменателя перестаёт быть тем
числом, под которое сайзились
(`docs/decisions/per-trade-risk-policy.md` §«Асимметрия»).

### `plannedContractValue` — пятое число, и почему оно persisted (H5 `DOCS_CHECK_16`)

Тождество планового риска ноги стоит на **четырёх** операндах:

```text
plannedRiskAmount_i = |plannedEntryPrice_i − stop_i|
                      × plannedSizeContracts_i × ctVal_i
                      + ожидаемая комиссия_i
```

Три из них были persisted write-once, а `ctVal` **дочитывался финализатором
из JSONB-навеса `InstrumentExternalRules`**, который синк переписывает
каждым тиком. Довод, которым проект отверг пере-чтение **ставки комиссии**
(«persisted write-once воспроизводимо, перечитанное значение — нет»,
`docs/decisions/pnl-finalization-mechanics.md` реш.4), дословно применим и
здесь — и применён не был: в одной секции стояли «ставку не перечитываем,
потому что persisted» и «`ctVal` читай из навеса».

**Что чинится.** Два следствия, оба измеримые:

- **тихое** — смена `ctVal` между входом и финализацией даёт неверный
  допуск на **уже закрытой** сделке: либо ложный `MISMATCHED`, либо
  погашенный контроль. Величина искажения кратна отношению старого и нового
  размера контракта, то есть не мала;
- **громкое** — нерезолвимый `ctVal` (навес не синхронизирован) уводил
  завершившуюся **штатно** сделку с корректным `resultProfit` в
  `EMERGENCY_CLOSED`: терминальная ось получала событие, к торговле
  отношения не имеющее.

**Канал и писатель — те же, что у четырёх соседей**: значение приходит
`CreateOrderCommandPayload`'ом входного действия (риск-преконтроль читает
навес **в момент постановки** — он и так его читает, чтобы посчитать риск),
пишется `CreateOrderExecutor`'ом той же транзакцией, что создание ноги.
Отдельного чтения навеса ход не добавляет.

**Инвариант «шесть или ни одного».** Все шесть чисел производит один
преконтроль и пишет одна транзакция: непустой `plannedRiskAmount_i` при
пустом `plannedContractValue_i` (или `plannedStopPrice_i`) недостижим. Это и есть основание, по
которому финализатор перестаёт быть читателем навеса
(`docs/components/InstrumentExternalRulesDataService.md` §Использование).

**Чего ход не делает.** Он не персистит **готовое** число ожидаемой
комиссии (`plannedCommission_i`) — этот вариант рассмотрен и отвергнут
(решение пользователя): он заводил бы число, выводимое из уже
персистенных. Сам `stop_i` при этом персистится — но **отдельным
операндом**, а не свёрнутым в комиссию (§«`plannedStopPrice` — шестое
число»). Прежняя редакция резолвила его через `Order.attachedAlgoOrders`;
это перестало работать, когда встроенная защита доборной ноги стала
временной (`docs/rules/risk-creating-entry-protection.md` §«Защита доборной
ноги снимается после пересчёта основной»).

**Дом — только `orders`** (`RISK-Q4` закрыт 2026-08-20): входной тропы
алго-ордером не существует (вход — ordinary `Order`; у
`AlgoOrder.ConditionType` входного значения нет — проверено по коду),
поэтому вторая четвёрка колонок в `algo_orders` была бы мёртвой схемой с
живым именем. Пишет их исполнитель `CREATE_ORDER_COMMAND` входного
действия той же транзакцией, что создание ноги; переиспользовать
`orders.size`/`price` нельзя — это колонки биржевой стороны,
перезаписываемые эхом ответа при каждом рефреше. Условие возврата вопроса
— `docs/models/domain/core/AlgoOrder.md` §Назначение.

**Пусты у reduce-only-ног.** У ноги `Type.REDUCE_ONLY` (частичный выход,
шаг `PARTIAL_EXIT`) планового риска нет по построению — она риска не
создаёт и риск-преконтроля не проходит
(`docs/rules/risk-validator-scope.md` §«Не вызывается»). Пустота здесь —
«признак неприменим», а не «операнд не добыт»
(`docs/rules/absent-value-semantics.md`).

> **Популяции «защитных ordinary-ордеров» не существует** — клауза снята
> (H1 `DOCS_CHECK_16`, inspection). Защита — это `AlgoOrder` (шаги
> `MAIN_PROTECTION` / `PROTECTION_ADJUSTMENT`) либо attached-элемент внутри
> самой входной ноги; ordinary-ордером защита не ставится ни на одной
> тропе. Прежняя редакция называла её наравне с reduce-only и тем
> подсказывала читателю несуществующий класс строк.

### `plannedStopPrice` — шестое число (Р3 `GAPS_CLOSE_16`, решение держателя)

`Order.plannedStopPrice` (`planned_stop_price`, `numeric(36,18)`, nullable,
`updatable = false`) — **уровень стопа, под который считался
`plannedRiskAmount_i`**, на момент постановки ноги. Приходит тем же
`CreateOrderCommandPayload`'ом (преконтроль его и так резолвит — из
`attachedProtection.stopLossSettings` действия), пишется той же транзакцией.

**Почему persisted, а не резолвится из защиты.** Решение Р3 сделало
встроенную защиту доборной ноги **временной**: она снимается после
пересчёта основной под увеличенную позицию
(`docs/rules/risk-creating-entry-protection.md` §«Защита доборной ноги
снимается…»). Снятая защита операндом тождества быть не может — уровень
надо было бы восстанавливать по терминальным элементам коллекции, то есть
по состоянию, которое стратегия вправе менять. Persisted-число этого не
требует: тождество ноги воспроизводимо из фактов её постановки, тем же
доводом, каким персистятся `ctVal` и reference-цена.

**Следствие — ветка «операнд допуска не резолвится» теряет производителя.**
После H5 её единственным производителем было вырождение предиката селекции
attached-защиты (`docs/components/FinalizeDealExitExecutor.md` §«Предикат
селекции защиты»). С персистом `stop_i` предикат перестаёт быть операндом
епсилона вовсе, и производителя у ветки не остаётся. Что с этим делать —
оставить ветку защитной или снять её вместе с кодом аномалии — **открыто**
(`.claude/work/progress/phase-1-step-7-gaps-close-16.md` §«Развилки,
возвращаемые на валидацию»).

### Предикат «нога входа» — три носителя, и они согласованы инвариантом

H1 `DOCS_CHECK_16`; редакция пересмотрена решением держателя по итогам
inspection частичного выхода.

**Что было не так.** Место истины называло предикатом отбора
`(Type.ENTRY)`. При двузначном енуме это читалось одновременно как
**исключение** штатного защищённого входа (`ENTRY_ATTACHED_STOP_LOSS`) и
как **невыключение** reduce-only-ноги: последняя обязана была носить одно
из двух входных имён, потому что третьего значения не было, а
`type NOT NULL`.

**Что решено.** Введено третье значение `Type.REDUCE_ONLY` (§Енумы), и
после этого различают **все три** носителя:

| Форма | Выражение | Носитель |
|---|---|---|
| **Бизнес-тип** | `Type ∈ {ENTRY, ENTRY_ATTACHED_STOP_LOSS}` | §Енумы; `StrategyOrderAction.orderType` |
| **Намерение (граница биржи)** | `positionReducingOnly = false` — риск-преконтроль вызывается только для risk-creating | `docs/rules/risk-validator-scope.md` §Вызывается; `docs/components/CreateOrderActionExecutor.md` |
| **Факт (производная)** | нога с **непустым `plannedRiskAmount`** — следствие первых двух: у reduce-only преконтроль не гоняется, шесть чисел не производятся | `docs/components/FinalizeDealExitExecutor.md` §epsilon |

**Состояние ноги в этих формах не участвует.** «Нога входа» и «нога, чей
заявленный риск считается» — разные вопросы: первый решает `Type`, второй
добавляет к нему конъюнкт по `Status` (живая либо исполнившаяся,
`docs/models/domain/aggregate/Deal.md` §«Предикат отбора слагаемых»).

**Три носителя не расходятся — их связывает инвариант**
`Type = REDUCE_ONLY ⇔ positionReducingOnly = true` (§Енумы), а непустота
`plannedRiskAmount` следует из второго: преконтроль, который пишет шесть
чисел, для reduce-only не вызывается. Поэтому все три предиката отбирают
**одно и то же множество**, и выбор между ними — вопрос доступности
операнда в точке чтения, а не смысла.

**Чем пользоваться где — решение держателя:**

- **отбор строк `orders`** (сумма `R`, epsilon, отчёт) — **бизнес-тип**
  `Type ∈ {ENTRY, ENTRY_ATTACHED_STOP_LOSS}`. Он называет роль ноги прямо,
  читается человеком и не требует знать, какой преконтроль гонялся;
- **там, где отбираются слагаемые числовой суммы** (`Deal.plannedRiskAmount`,
  омиссионный член epsilon), к типу **добавляется страховочный конъюнкт** —
  непустота `plannedRiskAmount`. Он производен от первых двух форм и, пока
  инвариант держится, исхода не меняет; он стоит на случай, когда инвариант
  **не** держится, чтобы в сумму не попало слагаемое-пустота;
- **решение «звать ли риск-преконтроль»** — **намерение**
  (`positionReducingOnly`): оно же уходит на биржу, и удваивать его
  `Type`-проверкой незачем.

> **Цена страховочного конъюнкта названа.** Рассогласование пары
> `Type` ↔ `positionReducingOnly` этот предикат **гасит молча**, причём с
> обеих сторон: пустое число выбрасывает страховка, а лишнее число —
> типовой конъюнкт. Тревоги предикат отбора не поднимает по построению,
> поэтому обнаружение рассогласования вынесено в отдельный механизм
> (`docs/models/domain/aggregate/Deal.md` §«Обнаружение рассогласования
> пары носителей»): превентивная валидация состава шага стратегии плюс
> сверка пары на пересчёте сумм.

**Четвёртого носителя не заводится.** Отдельное поле-признак «нога входа»
на `orders` не вводится — третьего места, знающего ту же ось, достаточно и
так (`.claude/rules/codestyle.md`; связность и без того выросла — цена
названа в §Енумы).

## Персистентность

Хранится в БД (entity `OrderEntity`, таблица `orders`, создана
`V6__create_deal_runtime_tables.sql`), наследует audit-поля
(`AuditableEntity`). Раздел заведён H16 `DOCS_CHECK_14` — **место истины
схемы сущности** (`docs/rules/persistence-representation.md` §«Место
истины схемы»); schema-дельта шага — сборка-указатель.

- Состав `V6`: `id` (identity, PK), `deal_id` (`NOT NULL`, FK →
  `deals`), `internal_id` (`varchar(64)` `NOT NULL`,
  `uk_order_internal_id`), `external_id` (`varchar(64)`), `status`
  (`varchar(32)` `NOT NULL`), `close_reason` (`varchar(32)`), `type`
  (`varchar(32)` `NOT NULL`), `side` (`varchar(16)`), `external_status`
  (`varchar(32)`), `price`, `size`, `accumulated_fill_size`,
  `average_price`, `fee` (все `numeric(36,18)`, nullable),
  `position_reducing_only` (`boolean`), `replaces_internal_id`
  (`varchar(64)`), шесть audit-колонок (`AuditableEntity`, nullable).
- **Колонки шага 7 — `ALTER`**: `planned_entry_price`,
  `planned_size_contracts`, **`planned_risk_amount`**,
  **`planned_contract_value`** (все четыре `numeric(36,18)`) и
  **`planned_risk_currency`** (`varchar(64)` — строковая колонка по правилу
  длин, `docs/rules/persistence-representation.md` §«Строковые колонки:
  длины»); все nullable (пусты у не-входных ног и у ордеров, заведённых вне
  нашего входа), write-once на уровне entity (`updatable = false`). Пара
  `planned_risk_*` добавлена H6/H11 `DOCS_CHECK_15` — дом планового риска
  переехал с `Deal` на ногу, на сделке остаётся сумма;
  `planned_contract_value` — H5 `DOCS_CHECK_16` (четвёртый операнд
  тождества перестаёт дочитываться из изменчивого навеса, §«`plannedContractValue`
  — пятое число»). Бэкфилл не нужен
  (`.claude/rules/pre-launch-schema-changes.md`).
- **Инвариант заполнения — «шесть или ни одного»**: все шесть колонок
  производит один риск-преконтроль и пишет одна транзакция, поэтому
  смешанного состояния (часть заполнена, часть нет) не бывает. На нём стоят
  вывод финализатора из читателей навеса **и** резолвимость `stop_i` после
  снятия встроенной защиты доборной ноги.
- Enum-поля хранятся строкой (имя enum; codestyle §Слои моделей и
  enum'ы).

## Структура `AttachedAlgoOrder` (раздел `Order`)

Embedded защитный algo-order, созданный вместе с parent `Order` (OKX
`attachAlgoOrds`). На первом этапе — embedded-часть `Order`, **не**
standalone `AlgoOrder` (раздел модели по
`.claude/decisions/model-granularity.md`). Не материализуется
автоматически в standalone `AlgoOrder`, даже если в snapshot есть
attached/algo identifiers; standalone `AlgoOrder` создаётся только
отдельным `StrategyAlgoOrderAction` через `CREATE_ALGO_ORDER_COMMAND`.

Java-класс `...core.order.AttachedAlgoOrder`, расширяет `Auditable`.

| Поле | Тип | Назначение |
|---|---|---|
| `id` | `Long` | Внутренний идентификатор в БД. |
| `orderId` | `Long` | Parent `Order`. |
| `internalId` | `String` | Межсервисный id (OKX `attachAlgoClOrdId`). Ключ матчинга. |
| `externalAttachedId` | `String` | Id attached algo на бирже, пока он attached (OKX `attachAlgoId`). |
| `externalId` | `String` | Внешний id algo-order, если биржа возвращает (не материализует в standalone). |
| `status` | `Status` | Доменный статус (см. lifecycle). |
| `closeReason` | `CloseReason` | Причина финализации. |
| `type` | `Type` | Внутренний тип (`ATTACHED_STOP_LOSS`). |
| `externalStatus` | `String` | Сырой внешний статус (у OKX attachAlgoOrds полноценного state нет). |
| `externalType` | `String` | Биржевой тип attached protection. |
| `size` | `BigDecimal` | Размер. |
| `stopLossTriggerPrice` | `BigDecimal` | Триггерная цена SL (текущий проект — attached SL). |

Доменные методы: `isActiveLike()` (PENDING/ACTIVE),
`canTransitionTo(target)` (явная матрица — см. lifecycle),
`toPending/toActive/toComplete/toCancel/toError`.

### Енумы `AttachedAlgoOrder`

- **`Type`**: `ATTACHED_STOP_LOSS`.
- **`Status`**: `CREATED`, `PENDING`, `ACTIVE`, `COMPLETED`,
  `CANCELED`, `ERROR` (переходы — в lifecycle).
- **`CloseReason`**: `TRIGGERED`, `SWITCHED_BY_STRATEGY` (снята после
  подтверждения standalone main protection), `PARENT_ORDER_CANCELED`,
  `KILL_SWITCH`, `MANUAL_CANCEL`, `MISSING_AFTER_REFRESH`,
  `PROTECTION_LOST` (была активной, больше не подтверждается, и
  standalone protection отсутствует), `UNKNOWN_EXTERNAL_STATUS`,
  `UNKNOWN`.

## External snapshots

Нормализованные snapshots для refresh/search/history flow, не
persisted runtime-сущности (разделы модели по `model-granularity.md`).
Raw DTO не выходит за adapter-layer (`docs/rules/raw-exchange-dto-boundary.md`).
OKX mapping — в `docs/models/mapping/Order.md`.

- **`OrderExternalSnapshot`**: `internalId`, `externalId`, `type`,
  `side`, `externalStatus`, `price`, `size`, `accumulatedFillSize`,
  `averagePrice`, `fee`, `attachedAlgoOrders:
  List<AttachedAlgoOrderExternalSnapshot>`, `attachedAlgoInternalId`
  (top-level attached client id), `takeProfitTriggerPrice`,
  `stopLossTriggerPrice` (top-level triggers).
- **`AttachedAlgoOrderExternalSnapshot`**: `externalAttachedId`,
  `internalId` (ключ матчинга), `externalId`, `externalType`, `size`,
  `stopLossTriggerPrice`, `failCode`, `failReason` (заполненный
  failCode/failReason → attached ERROR).

## Что Order не хранит

На первом этапе не хранит: `strategyActionId`/`strategyActionKey`
(в `DealActionState`), `marginMode`/`tradeMode`/`positionSide` (OKX
`tdMode=isolated`/`posSide=net` — константы `OkxIntegrationService`),
external rules инструмента и fresh market price (собираются в
`CalculationContext` перед расчётом action), raw command result
history (проектируется отдельно, не runtime state), `reduceOnly` как
отдельный external snapshot-факт (проверяется adapter-layer прямо из
`OrderResponse.reduceOnly`).
