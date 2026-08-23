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

**Решение: метрика едет полями payload'а** — `plannedRiskAmount`,
`plannedRiskCurrency`, `plannedEntryPrice`, `plannedSizeContracts`
(§`CreateOrderCommandPayload`). Тем же путём, каким уже едут цена и
размер: строит payload та же сторона, что вызывает `RiskValidator`, и в
том же проходе.

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

**Куда пишутся пять чисел** (H6/H11 `DOCS_CHECK_15`, решение пользователя;
пятое добавлено H5 `DOCS_CHECK_16`): все пять — **на `Order`** создаваемой
ноги (`planned_risk_amount`, `planned_risk_currency`,
`planned_entry_price`, `planned_size_contracts`,
**`planned_contract_value`**), и **той же транзакцией** executor
пересчитывает суммы на `Deal`.

**Пятое число — `plannedContractValue` (`ctVal` на момент постановки).**
Значение приходит тем же payload'ом: риск-преконтроль читает навес
`InstrumentExternalRules`, чтобы посчитать риск, и отдаёт прочитанный
`ctVal` вместе с результатом — отдельного чтения навеса ход не добавляет.
Заполняются либо **все пять**, либо ни одного: смешанного состояния нет,
и на этом инварианте стоит вывод финализатора из читателей навеса
(`docs/models/domain/core/Order.md` §«`plannedContractValue` — пятое
число»). Прежняя редакция («знаменатель остаётся на `Deal`, переехали
только операнды») **снята**: она держала риск и его операнды на разных
уровнях и тем рвала тождество, которое их связывает
(`docs/components/FinalizeDealExitExecutor.md` §epsilon).

**Write-once — на ноге; сумма на сделке не write-once.** Уже заполненный
плановый риск ноги не перетирается — ни REPLACE-ремоделом стопа, ни
добором; то же для его операндов: `R` ноги — риск **на её входе**,
бенчмарк измерения результата. Поле `Deal` при этом двигается при
появлении **новой** ноги входа — растёт состав слагаемых, а не
переписывается уже принятое. Для не-входных `CREATE_ORDER_COMMAND`
(защита, reduce-only) поля не пишутся и сумма не меняется.

**Предикат «входное действие» — риск-преконтроль, не `Order.Type`** (H1
`DOCS_CHECK_16`). Executor не различает входные и не-входные ноги по
`orderType` payload'а: `Order.Type` двузначен и обе константы носят
не-входные ордера тоже (`docs/models/domain/core/Order.md` §«Предикат
"нога входа"»). Различитель — **факт, что действие прошло риск-преконтроль**
(risk-creating / risk-increasing, не reduce-only —
`docs/rules/risk-validator-scope.md` §Вызывается), и его материальный след
в payload'е: пять чисел планового риска **присутствуют** ровно у входного
действия, потому что производит их преконтроль. Пустой набор чисел ⇒ нога
не входная ⇒ ни поля, ни суммы не трогаются. Правило
агрегации **лимита** при многоногом входе (`GRID_ENTRY`/пирамидинг)
остаётся открытым вопросом `RISK-Q3` — знаменатель им не затронут
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
(H5 `DOCS_CHECK_16`). У не-входных `CREATE_ORDER_COMMAND` (защита,
reduce-only) они пусты — там нет преконтроля, который их производит. Это единственное исключение из «payload
хранит минимум»: минимум означает «не тащить то, что executor возьмёт из
сущности», а эти пять чисел **в сущности не остаются** —
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
