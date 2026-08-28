# CreateOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ORDER_COMMAND`. Создаёт локальный `Order` со статусом `CREATED`,
генерирует `internalId`, сохраняет рассчитанные параметры, создаёт
attached protection внутри order (если есть), обновляет target-колонки
`DealActionState` (`targetEntityType = ORDER`, `targetEntityId = orderId`
— объект `RuntimeTarget` расплющен в колонки,
`docs/decisions/command-action-boundary.md` §3) и
`DealActionState.status = CREATED` — всё одной транзакцией. На биржу не
ходит, цену не пересчитывает, условия не проверяет.

## Плановый риск сделки (`R`)

Для **входного** действия executor той же транзакцией пишет
`Deal.plannedRiskEquityBase` (write-once, база первого сайзинга —
`docs/models/domain/aggregate/Deal.md` §«База процента риска») и
`Order.plannedRiskAmount` / `plannedRiskCurrency` **создаваемой ноги** —
величину `risk amount`, посчитанную `RiskValidator` при преконтроле
**этого же** действия (`|entry − stop| × contracts × ctVal + commissions`)
— и **пересчитывает `Deal.plannedRiskAmount` как сумму** по ногам входа
сделки (H6/H11 `DOCS_CHECK_15`). Валидация и создание
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

**Решение: метрика едет полями payload'а** — состав и счёт держит
§`CreateOrderCommandPayload`. Тем же путём, каким уже едут цена и
размер: строит payload та же сторона, что вызывает `RiskValidator`, и в
том же проходе. **Ни перечня, ни счёта здесь не дублируется** (B6
`DOCS_CHECK_23`): прежняя редакция называла четыре поля и отстала от
состава (B4 `DOCS_CHECK_18`), после чего перечень убрали, а **счёт**
оставили — и он устарел следующим же ходом, при вводе восьмого поля
(C4 `DOCS_CHECK_20`), разойдясь с местом истины в том же файле.

**Отвергнутые альтернативы:**

- **поле на `RiskValidationResult`** (`riskAmount`/`riskCurrency`,
  заполняемое и на `ALLOWED`) — работоспособно, но требует, чтобы
  валидатор начал строить не-`BLOCKED` результаты ради переноса числа;
  RVO решения превращается в транспорт метрики. Оставлено вторым
  вариантом на случай, если метрика понадобится ещё одному потребителю;
- **поле на `CalculatedSize`** — против ратифицированной границы
  «risk-метрики не едут в calculated-RVO»;
- **пересчёт в executor'е** — дублирует формулу сайзинга и заводит второй
  экземпляр того же класса отказа, ради устранения которого знак ставки
  снимается **одним местом** (`docs/decisions/pnl-finalization-mechanics.md`
  реш.4).

**Вместе с риском пишутся его операнды** (H6 `DOCS_CHECK_10`; **дом
операндов пересмотрен** H3 `GAPS_CLOSE_11`): той же транзакцией —
`plannedEntryPrice` (reference-цена входа, по которой считался риск) и
`plannedSizeContracts` (заявленный размер) — **на создаваемую сущность ноги
входа, то есть на `Order`, а не на `Deal`**. Редакция
«`Deal.plannedEntryPrice` / `Deal.plannedSizeContracts`» **снята**: на
`Deal` write-once-поле при многоногом входе сохраняло бы число **первой**
ноги и молча выдавало его за сделку; в §Персистентность `deals` этих
колонок нет.

**Писатель — этот executor, и только он** (`RISK-Q4` закрыт 2026-08-20;
назначение per-leg — H5 `DOCS_CHECK_12`): `CreateOrderExecutor` пишет
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
(`docs/models/domain/aggregate/Deal.md` §«Плановый риск»;
`docs/models/domain/core/Order.md` §«Плановый риск и его операнды»).

**Куда пишутся шесть чисел** (H6/H11 `DOCS_CHECK_15`, решение
пользователя; пятое добавлено H5 `DOCS_CHECK_16`, шестое — Р3
`GAPS_CLOSE_16`): все шесть — **на `Order`** создаваемой ноги
(`planned_risk_amount`, `planned_risk_currency`, `planned_entry_price`,
`planned_size_contracts`, **`planned_contract_value`**,
**`planned_stop_price`**), и **той же транзакцией** executor пересчитывает
суммы на `Deal`.

**Пятое число — `plannedContractValue` (`ctVal` на момент постановки).**
Значение приходит тем же payload'ом: риск-преконтроль читает навес
`InstrumentExternalRules`, чтобы посчитать риск, и отдаёт прочитанный
`ctVal` вместе с результатом — отдельного чтения навеса ход не добавляет.
Заполняются либо **все шесть**, либо ни одного: смешанного состояния нет,
и на этом инварианте стоят вывод финализатора из читателей навеса и
резолвимость `stop_i` после снятия встроенной защиты доборной ноги
(`docs/models/domain/core/Order.md` §«`plannedContractValue` — пятое
число»). Прежняя редакция («знаменатель остаётся на `Deal`, переехали
только операнды») **снята**: она держала риск и его операнды на разных
уровнях и тем рвала тождество, которое их связывает
(`docs/components/FinalizeDealExitExecutor.md` §epsilon).

**Числа риска на `Deal` пересчитываются здесь целиком — все четыре**
(T2 `DOCS_CHECK_18`, правило выровнено B3 `DOCS_CHECK_19`). Создание
ноги входа меняет сразу два операнда: состав слагаемых и **действующую
защиту** — risk-creating вход ставится с attached SL
(`docs/rules/risk-creating-entry-protection.md` §Правило), и до первой
standalone-защиты действующей является именно она; её уровень садится
write-once шестым числом (`Order.plannedStopPrice`) той же транзакцией.
По общему правилу «кто меняет любой операнд, пересчитывает всю четвёрку»
executor делает это одной транзакцией с созданием ноги. Состав, счёт и
формулы — место истины `docs/models/domain/aggregate/Deal.md` §«Взятый
риск»; здесь не пересказываются. На **открывающем** входе
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
(`docs/models/domain/core/Order.md` §«Пусты у reduce-only-ног»).

**Предикат «входное действие» — риск-преконтроль** (H1
`DOCS_CHECK_16`). Executor различает ноги **не по `orderType` payload'а**:
`Order.Type` с третьим значением `REDUCE_ONLY` разделять умеет, но у
писателя под рукой более прямой операнд — сам факт преконтроля, и удваивать
его типом незачем (три эквивалентных носителя и выбор между ними —
`docs/models/domain/core/Order.md` §«Предикат "нога входа"»).
Различитель — **факт, что действие прошло риск-преконтроль**
(risk-creating / risk-increasing, не reduce-only —
`docs/rules/risk-validator-scope.md` §Вызывается), и его материальный след
в payload'е: шесть чисел планового риска **присутствуют** ровно у входного
действия, потому что производит их преконтроль. Пустой набор чисел ⇒ нога
не входная ⇒ ни поля, ни суммы не трогаются. Правило
агрегации **лимита** при многоногом входе (`GRID_ENTRY`/пирамидинг)
**закрыто** сделочными лимитами (`RISK-Q3` / `RISK-Q3-A`, `GAPS_CLOSE_20`;
`docs/decisions/per-trade-risk-policy.md` §«Три лимита внутри уровня „риск на сделку“»)
— знаменатель им не затронут
(`docs/models/domain/aggregate/Deal.md` §«Плановый риск»).

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` / target-колонки — `docs/models/domain/other/DealActionState.md`.

## CreateOrderCommandPayload

`orderType` (`Order.Type`), `strategyDirection` (`StrategyTradeDirection`),
`side` (buy/sell), `positionSide`, `instrumentExternalId`, `marginMode`,
`executionType`, `sizeContracts`, `price`, `sendPriceToExchange`,
`positionReducingOnly` (доменное намерение → OKX `reduceOnly` в adapter),
`attachedProtection` (`AttachedProtectionPayload`, если order создаётся со
стартовым SL/TP).

**Поля планового риска — только у входного действия** (H5/H6
`DOCS_CHECK_10`): `plannedRiskAmount`, `plannedRiskCurrency`,
`plannedEntryPrice`, `plannedSizeContracts`, **`plannedContractValue`**
(H5 `DOCS_CHECK_16`), **`plannedStopPrice`** (Р3 `GAPS_CLOSE_16`) —
**шесть, и перечень обязан совпадать со счётом** (§«Куда пишутся шесть
чисел»; прежняя редакция перечисляла пять, называя шестью).

**Восьмое поле — `plannedRiskEquityBase`** (C4 `DOCS_CHECK_20`, решение
держателя). Едет тем же payload'ом, в шестёрку **не входит**: это
**база процента**, по которой `SizeCalculator` считал бюджет, а не
операнд тождества риска ноги. Приземляется **на `Deal`**, а не на ногу,
и **write-once** — базу задаёт первый сайзинг сделки
(`docs/models/domain/aggregate/Deal.md` §«База процента риска»);
вторая и последующие ноги везут его тем же полем, executor записи не
делает (guard `where planned_risk_equity_base is null`).

**Седьмое поле — `liquidationDistanceRatio`** (П14 держателя, доведено
B1 `DOCS_CHECK_18`). Едет тем же payload'ом, но **в шестёрку не входит**:
это измеритель запаса до ликвидации, а не операнд тождества риска, и его
пустота законна при заполненной шестёрке. Операнд `liqPx` берётся из
`DealContext` — живого эпизода (`deal.livePosition()`) при **доборе**; на открывающем
входе позиции ещё нет ⇒ поле пусто
(`docs/models/domain/core/Order.md` §«`liquidationDistanceRatio` —
седьмое число»). Итого payload несёт **восемь** чисел: шесть тождества +
измеритель + база процента (`plannedRiskEquityBase`, C4
`DOCS_CHECK_20`); плюс `triggerPriceType` в
`attachedProtection`-подобъекте (C1 `DOCS_CHECK_20`) — он не число и в
счёт не входит.
У не-входных `CREATE_ORDER_COMMAND` (защита,
reduce-only) они пусты — там нет преконтроля, который их производит. Это единственное исключение из «payload
хранит минимум»: минимум означает «не тащить то, что executor возьмёт из
сущности», а эти шесть чисел **в сущности не остаются** —
`plannedEntryPrice` при market-входе не садится даже на `Order.price`
(§«Плановый риск сделки»).

Хранит минимум для создания; client id (`internalId`), external id берутся
из создаваемой сущности. `positionSide`/`marginMode` — generic command-level
intent; OKX adapter всё равно ставит `tdMode=isolated`, `posSide=net` и
валидирует response (см. `docs/models/mapping/Order.md`).

### AttachedProtectionPayload

Параметры attached protection при создании order со стартовым SL/TP
(вложен в `CreateOrderCommandPayload.attachedProtection`). Структура
attached protection — `docs/models/domain/core/Order.md`
(`AttachedAlgoOrder`).

**Payload несёт и `triggerPriceType`** (C1 `DOCS_CHECK_20`) —
объявленную стратегией ценовую базу триггера
(`StopLossSettings.triggerPriceType`). Без неё
`attachAlgoOrds[*].slTriggerPxType` уходил бы пустым и биржа применяла
бы свой default `last`, тогда как запас до ликвидации считается от
`mark` (дом принципа —
`docs/rules/risk-creating-entry-protection.md` §«Ценовая база триггера
защиты объявляется стратегией и доезжает до биржи»).
