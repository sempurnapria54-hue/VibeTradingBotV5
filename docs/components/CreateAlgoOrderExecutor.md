# CreateAlgoOrderExecutor

## На какой вопрос отвечает этот файл

Кто исполняет `CREATE_ALGO_ORDER_COMMAND` (компонент-executor): что делает.

## Назначение

Получает `CREATE_ALGO_ORDER_COMMAND`. Создаёт локальный `AlgoOrder` со статусом
`CREATED`, генерирует `internalId`, сохраняет рассчитанные
SL/TP/trailing-параметры, обновляет `DealActionState.target =
RuntimeTarget(ALGO_ORDER, algoOrderId)` и `DealActionState.status =
CREATED`. На биржу не ходит.

## Плановый риск сделки — алго-тропа операндов не несёт (`RISK-Q4` закрыт)

**Парная клауза к `CreateOrderExecutor` снята — не «пока развилка
открыта», а навсегда для текущей модели:** входной тропы алго-ордером не
существует (`AlgoOrder.ConditionType` — семь protective/closing значений,
входного нет; `docs/models/domain/core/AlgoOrder.md` —
там же условие возврата). Плановый риск ноги и его операнды
(`plannedRiskAmount` / `plannedRiskCurrency` / `plannedEntryPrice` /
`plannedSizeContracts`) пишет **только** `CreateOrderExecutor` на `Order`
ноги входа, он же пересчитывает сумму на `Deal`;
этот executor к **плановому** риску не причастен. Четвёртое число сделки
(`protectionRelievedRiskAmount`) он при этом **пишет** —: оно про
защиту, а не про плановый риск.

- **Обоснование прежнего канала потеряло предмет**: клауза
  «`RiskValidator` на этой тропе уже вызывается — метрика посчитана»
  описывала входную алго-тропу, которой нет. `RiskValidator` на
  алго-тропе действительно вызывается, но по ветке **risk-weakening**
  (защитный algo-order, не обеспечивающий требуемый контроль риска, —
  `docs/rules/risk-validator-scope.md`), а не как вход: метрика
  планового риска здесь не производится.

## Риск, снятый защитой — четвёртое число сделки этот executor пишет

**Клауза «к плановому риску не причастен» — про первое число, не про
четвёртое**. Постановка standalone-защиты меняет
операнд `Deal.protectionRelievedRiskAmount` — уровень стопа действующей
защиты, — поэтому по общему правилу «пересчитывает тот исполнитель,
который меняет операнд» (`docs/models/domain/aggregate/Deal.md`) пересчёт делает **этот** executor, своей же
транзакцией. То же для place-ноги `REPLACE` (ремодел защиты).

- **Что пересчитывается:** **все четыре числа целиком** — по общему
  правилу «кто меняет любой операнд, пересчитывает всю четвёрку». Меняемый здесь операнд один — действующая
  защита; остальные три числа пересчёт вернёт теми же значениями, и это
  дешевле, чем держать разделение писателей по полям, которое делало
  значения функцией порядка событий.
- **Форма вычитаемого — закрытая, дом формулы —
  `docs/models/domain/aggregate/Deal.md`**: та же
  форма, что у планового риска ноги, с подстановкой действующего стопа и
  поногово-пропорциональной доли филлов; комиссионный член входит, знак
  не клэмпится. Здесь формула не пересказывается
  (`.claude/rules/policy-home.md`).
- **Операнд у executor'а под рукой:** уровень стопа — в
  `stopLossPrice` payload'а, он же садится в
  `AlgoOrder.condition.trigger.stopLoss.value` той же транзакцией;
  остальные операнды — persisted-числа ног входа.
- **Защита ещё не подтверждена биржей — и это учтено:** действующей
  считается **новейшая живая** защита своей категории — селектор
  поногово́го `stopCurrent_i`, не сделочного `stopCurrentLive`,
  поэтому пересчёт при локальном создании корректен по определению
  операнда; переходное окно двойной защиты держится один проход.

Общая семантика `CREATE_*` — `docs/components/ServiceCommandExecutor.md`.
`DealActionState` / `RuntimeTarget` — `docs/models/domain/other/DealActionState.md`.

## CreateAlgoOrderCommandPayload

`conditionType` (`ConditionType`: SL/OCO_FULL/PARTIAL_TAKE_PROFIT/TRAILING
и т.д.), `side`, `positionSide`, `instrumentExternalId`, `marginMode`,
`positionReducingOnly` (для защитных почти всегда `true`), `sizeContracts`,
`stopLossPrice` (`ResolvedStopLossPrice`), `takeProfitPrice`
(`ResolvedTakeProfitPrice`), `trailingPrice` (`ResolvedTrailingPrice`).
`closeFraction` не передаётся — остаётся sizing intent; command-layer
получает готовый `sizeContracts`.
