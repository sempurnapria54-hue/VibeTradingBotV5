# CreateOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ORDER_COMMAND`. Создаёт локальный `Order` со статусом `CREATED`,
генерирует `internalId`, сохраняет рассчитанные параметры, создаёт
attached protection внутри order (если есть), обновляет target-колонки
`DealActionState` (`targetEntityType = ORDER`, `targetEntityId = orderId`
— объект `RuntimeTarget` расплющен в колонки,
`docs/rules/command-lifecycle.md`) и
`DealActionState.status = CREATED` — всё одной транзакцией. На биржу не
ходит, цену не пересчитывает, условия не проверяет.

## Плановый риск сделки (`R`)

Для **входного** действия executor той же транзакцией пишет
`Deal.plannedRiskEquityBase` (write-once, база первого сайзинга —
`docs/models/domain/aggregate/Deal.md`) и
`Order.plannedRiskAmount` / `plannedRiskCurrency` **создаваемой ноги** —
величину `risk amount`, посчитанную `RiskValidator` при преконтроле
**этого же** действия (`|entry − stop| × contracts × ctVal + commissions`)
— и **пересчитывает `Deal.plannedRiskAmount` как сумму** по ногам входа
сделки. Валидация и создание
идут одним проходом (`docs/rules/risk-validator-scope.md`: валидатор
вызывается после расчёта цены/размера и **до** создания команды), поэтому
**durable-слота между проходами не нужно**.

### Канал доставки — поля `CreateOrderCommandPayload` (H5 `DOCS_CHECK_10`)

«Durable-слот не нужен» отвечает на вопрос «где число живёт **между
проходами**», а не «**чем** оно доезжает **внутри** прохода» — это разные
оси, и вторая до сих пор оставалась незакрытой: ни один существующий
носитель метрику не несёт. `CalculatedStrategyAction` явно её не содержит
(«не содержит `RiskValidationResult` и `CalculatedRiskMetrics`»),
`RiskValidationResult` несёт decision/checks/comment, `CalculatedSize` —
размер, а единственный числовой слот `RiskCheckResult.actualValue` на
happy-path **не существует**: в фазе 1 `RiskValidator` строит **только**
`BLOCKED`-результаты, то есть на пути `ALLOWED` список `checks` пуст.

- **поле на `RiskValidationResult`** (`riskAmount`/`riskCurrency`,
  заполняемое и на `ALLOWED`) — работоспособно, но требует, чтобы
  валидатор начал строить не-`BLOCKED` результаты ради переноса числа;
  RVO решения превращается в транспорт метрики. Оставлено вторым
  вариантом на случай, если метрика понадобится ещё одному потребителю;
- **пересчёт в executor'е** — дублирует формулу сайзинга и заводит второй
  экземпляр того же класса отказа, ради устранения которого знак ставки
  снимается **одним местом** (`docs/rules/pnl-reconciliation.md`
  реш.4).

**Вместе с риском пишутся его операнды**: той же транзакцией —
`plannedEntryPrice` (reference-цена входа, по которой считался риск) и
`plannedSizeContracts` (заявленный размер) — **на создаваемую сущность ноги
входа, то есть на `Order`, а не на `Deal`**. Редакция
«`Deal.plannedEntryPrice` / `Deal.plannedSizeContracts`» **снята**: на
`Deal` write-once-поле при многоногом входе сохраняло бы число **первой**
ноги и молча выдавало его за сделку; в `deals` этих
колонок нет.

**Писатель — этот executor, и только он**: `CreateOrderExecutor` пишет
операнды на `Order` той же транзакцией, что
создаёт сущность. Это прямое следствие «дом — нога входа», и оно
**не ломает** канон «CREATE-команда создаёт сущность и
пишет её поля одной транзакцией» (альтернатива «один писатель на границе
риск-преконтроля» его ломала бы). Второго per-leg-писателя нет: входной
тропы алго-ордером не существует, `CreateAlgoOrderExecutor` к плановому
риску не причастен (`docs/components/CreateAlgoOrderExecutor.md`).

Обе величины уже лежат в payload (`price`, `sizeContracts`), но
`plannedEntryPrice` **нельзя брать с `Order.price`**: при market-входе
executor заполняет его только когда `sendPriceToExchange` истинно, то есть
reference-цена в сущности ордера не остаётся. Без этих двух чисел разрыв
«заявленный риск ↔ взятый» неизмерим постфактум
(`docs/models/domain/aggregate/Deal.md`;
`docs/models/domain/core/Order.md`).

**Куда пишутся шесть чисел**: все шесть — **на `Order`** создаваемой ноги
(`planned_risk_amount`, `planned_risk_currency`, `planned_entry_price`,
`planned_size_contracts`, **`planned_contract_value`**,
**`planned_stop_price`**), и **той же транзакцией** executor пересчитывает
суммы на `Deal`.

**Числа риска на `Deal` пересчитываются здесь целиком — все четыре**. Создание
ноги входа меняет сразу два операнда: состав слагаемых и **действующую
защиту** — risk-creating вход ставится с attached SL
(`docs/rules/live-risk-protection.md`), и до первой
standalone-защиты действующей является именно она; её уровень садится
write-once шестым числом (`Order.plannedStopPrice`) той же транзакцией.
По общему правилу «кто меняет любой операнд, пересчитывает всю четвёрку»
executor делает это одной транзакцией с созданием ноги. Состав, счёт и
формулы — место истины `docs/models/domain/aggregate/Deal.md`; здесь не пересказываются. На **открывающем** входе
`protectionRelievedRiskAmount` равен нулю точно — защита ещё ничего не
сняла, и это верное значение, а не пустота.

**Write-once — на ноге; сумма на сделке не write-once.** Уже заполненный
плановый риск ноги не перетирается — ни REPLACE-ремоделом стопа, ни
добором; то же для его операндов: `R` ноги — риск **на её входе**,
бенчмарк измерения результата. Поле `Deal` при этом двигается при
появлении **новой** ноги входа — растёт состав слагаемых, а не
переписывается уже принятое. Для **reduce-only** `CREATE_ORDER_COMMAND`
(частичный выход, `Type.REDUCE_ONLY`) поля не пишутся и суммы не меняются.
Популяции «защитных ordinary-ордеров» не существует — защита ставится
`AlgoOrder`'ом либо attached-элементом входной ноги
(`docs/models/domain/core/Order.md`).

**Предикат «входное действие» — риск-преконтроль**. Executor различает ноги **не по `orderType` payload'а**:
`Order.Type` с третьим значением `REDUCE_ONLY` разделять умеет, но у
писателя под рукой более прямой операнд — сам факт преконтроля, и удваивать
его типом незачем (три эквивалентных носителя и выбор между ними —
`docs/models/domain/core/Order.md`).
Различитель — **факт, что действие прошло риск-преконтроль**
(risk-creating / risk-increasing, не reduce-only —
`docs/rules/risk-validator-scope.md`), и его материальный след
в payload'е: шесть чисел планового риска **присутствуют** ровно у входного
действия, потому что производит их преконтроль. Пустой набор чисел ⇒ нога
не входная ⇒ ни поля, ни суммы не трогаются. Правило
агрегации **лимита** при многоногом входе (`GRID_ENTRY`/пирамидинг)
**закрыто** сделочными лимитами
— знаменатель им не затронут
(`docs/models/domain/aggregate/Deal.md`).

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` / target-колонки — `docs/models/domain/other/DealActionState.md`.

## CreateOrderCommandPayload

`orderType` (`Order.Type`), `strategyDirection` (`StrategyTradeDirection`),
`side` (buy/sell), `positionSide`, `instrumentExternalId`, `marginMode`,
`executionType`, `sizeContracts`, `price`, `sendPriceToExchange`,
`positionReducingOnly` (доменное намерение → OKX `reduceOnly` в adapter),
`attachedProtection` (`AttachedProtectionPayload`, если order создаётся со
стартовым SL/TP).

**Восьмое поле — `plannedRiskEquityBase`**. Едет тем же payload'ом, в шестёрку **не входит**: это
**база процента**, по которой `SizeCalculator` считал бюджет, а не
операнд тождества риска ноги. Приземляется **на `Deal`**, а не на ногу,
и **write-once** — базу задаёт первый сайзинг сделки
(`docs/models/domain/aggregate/Deal.md`);
вторая и последующие ноги везут его тем же полем, executor записи не
делает (guard `where planned_risk_equity_base is null`).

**Седьмое поле — `liquidationDistanceRatio`**. Едет тем же payload'ом, но **в шестёрку не входит**:
это измеритель запаса до ликвидации, а не операнд тождества риска, и его
пустота законна при заполненной шестёрке. Операнд `liqPx` берётся из
`DealContext` — живого эпизода (`deal.livePosition`) при **доборе**; на открывающем
входе позиции ещё нет ⇒ поле пусто
(`docs/models/domain/core/Order.md`).

**Второй измеритель — `bookDepthAtPlacement`**. Едет тем же payload'ом, в шестёрку **не входит** по
той же причине. Операнд — `CalculationContext.marketPriceData`
(`externalAskSize` для `BUY`, `externalBidSize` для `SELL`); свежих цен в
контексте нет ⇒ поле пусто
(`docs/models/domain/core/Order.md`).

Итого payload несёт **девять** чисел: шесть тождества + **два**
измерителя + база процента; плюс `triggerPriceType` в
`attachedProtection`-подобъекте — он не число и в
счёт не входит.
У не-входных `CREATE_ORDER_COMMAND` (защита,
reduce-only) они пусты — там нет преконтроля, который их производит. Это единственное исключение из «payload
хранит минимум»: минимум означает «не тащить то, что executor возьмёт из
сущности», а эти шесть чисел **в сущности не остаются** —
`plannedEntryPrice` при market-входе не садится даже на `Order.price`
.

Хранит минимум для создания; client id (`internalId`), external id берутся
из создаваемой сущности. `positionSide`/`marginMode` — generic command-level
intent; OKX adapter всё равно ставит `tdMode=isolated`, `posSide=net` и
валидирует response (см. `docs/models/mapping/Order.md`).

### AttachedProtectionPayload

Параметры attached protection при создании order со стартовым SL/TP
(вложен в `CreateOrderCommandPayload.attachedProtection`). Структура
attached protection — `docs/models/domain/core/Order.md`
(`AttachedAlgoOrder`).

**Payload несёт и `triggerPriceType`** —
объявленную стратегией ценовую базу триггера
(`StopLossSettings.triggerPriceType`). Без неё
`attachAlgoOrds[*].slTriggerPxType` уходил бы пустым и биржа применяла
бы свой default `last`, тогда как запас до ликвидации считается от
`mark` (дом принципа —
`docs/rules/live-risk-protection.md`).
