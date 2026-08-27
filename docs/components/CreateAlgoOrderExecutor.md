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
входного нет; `docs/models/domain/core/AlgoOrder.md` §Назначение —
там же условие возврата). Плановый риск ноги и его операнды
(`plannedRiskAmount` / `plannedRiskCurrency` / `plannedEntryPrice` /
`plannedSizeContracts`) пишет **только** `CreateOrderExecutor` на `Order`
ноги входа, он же пересчитывает сумму на `Deal`
(H6/H11 `DOCS_CHECK_15`;
`docs/models/domain/core/Order.md` §«Плановый риск и его операнды»);
этот executor к **плановому** риску не причастен. Четвёртое число сделки
(`protectionRelievedRiskAmount`) он при этом **пишет** — §ниже: оно про
защиту, а не про плановый риск.

- **Обоснование прежнего канала потеряло предмет** (`RISK-Q4`): клауза
  «`RiskValidator` на этой тропе уже вызывается — метрика посчитана»
  описывала входную алго-тропу, которой нет. `RiskValidator` на
  алго-тропе действительно вызывается, но по ветке **risk-weakening**
  (защитный algo-order, не обеспечивающий требуемый контроль риска, —
  `docs/rules/risk-validator-scope.md`), а не как вход: метрика
  планового риска здесь не производится.
- **Прежняя сцепка носителей снята без правки их по существу.** Оба
  носителя, поставленные `RISK-Q4` в конфликт, оказались верны:
  §Назначение `AlgoOrder` входа среди применений не перечисляет —
  истинно; `risk-validator-scope.md` включает `CREATE_ALGO_ORDER_COMMAND`
  в множество валидируемых — тоже истинно (ветка risk-weakening). Ложным
  было прочтение «валидируется ⇒ вход алго-ордером достижим».

## Риск, снятый защитой — четвёртое число сделки этот executor пишет

**Клауза «к плановому риску не причастен» — про первое число, не про
четвёртое** (T2 `DOCS_CHECK_18`). Постановка standalone-защиты меняет
операнд `Deal.protectionRelievedRiskAmount` — уровень стопа действующей
защиты, — поэтому по общему правилу «пересчитывает тот исполнитель,
который меняет операнд» (`docs/models/domain/aggregate/Deal.md` §«Взятый
риск — второе число») пересчёт делает **этот** executor, своей же
транзакцией. То же для place-ноги `REPLACE` (ремодел защиты).

- **Что пересчитывается:** только `protectionRelievedRiskAmount`.
  Входные суммы (`plannedRiskAmount`, `incurredRiskAmount`) и
  `currentRiskAmount` он не трогает — их операнды здесь не меняются.
- **Форма вычитаемого — закрытая, дом формулы —
  `docs/models/domain/aggregate/Deal.md` §«Форма вычитаемого»**: та же
  форма, что у планового риска ноги, с подстановкой действующего стопа и
  поногово-пропорциональной доли филлов; комиссионный член входит, знак
  не клэмпится. Здесь формула не пересказывается
  (`.claude/rules/policy-home.md`).
- **Операнд у executor'а под рукой:** уровень стопа — в
  `stopLossPrice` payload'а, он же садится в
  `AlgoOrder.condition.trigger.stopLoss.value` той же транзакцией;
  остальные операнды — persisted-числа ног входа.
- **Защита ещё не подтверждена биржей — и это учтено:** действующей
  считается **последняя поставленная** защита (§«Форма вычитаемого»),
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

**Полей планового риска payload не несёт** (`RISK-Q4` закрыт): они
остаются только у `CreateOrderCommandPayload` — входного действия
алго-ордером не существует, и пустая четвёрка полей на защитном payload
читалась бы как «тропа есть». Прежняя редакция («только у входного
действия; у защитных пусты») снята вместе с посылкой входной алго-тропы.
