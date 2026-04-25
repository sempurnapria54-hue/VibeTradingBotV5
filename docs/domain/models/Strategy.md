# Модель стратегии

> Статус документа: актуальная версия strategy-layer.
>
> Эта дока описывает только модель стратегии: правила, условия и ожидаемые действия.
>
> Runtime-процессы вынесены в отдельные документы:
>
> * `01. Жизненный цикл сделки`
> * `02. Сервисные команды`
> * `03. Калькуляторы действий стратегии`
> * `04. Расчёт индикаторов и рыночных snapshots`
> * `05. Аудит и история исполнения`

---

# Главная идея

* **Стратегия** хранит торговые правила, условия и ожидаемые действия.
* **FSM сделки** управляет runtime-сделкой и её этапами.
* **Стратегия не хранит сервисные команды** и не пытается управлять runtime-сущностями напрямую.
* Стратегия говорит **что** должно быть создано / изменено / отменено и **при каких условиях**.
* Стейт-машина и orchestration-слой решают, **когда именно** интерпретировать эти правила.
* `StrategyActionCalculator` рассчитывает runtime-параметры действия стратегии: цену, размер и риск.
* `ServiceCommandFactory` превращает рассчитанное действие в атомарные `ServiceCommand`.
* `Order`, `AlgoOrder`, `Position` остаются чистыми runtime-сущностями биржи и не хранят `strategyActionId`.
* Связь действия стратегии с runtime-сущностью хранится в `DealActionState`.
* Аудит и история исполнения фиксируют факты, но не являются источником runtime-логики FSM.

---

# 1. Strategy

```java
public class Strategy extends Auditable {

    /**
     * Технический ID БД.
     */
    private Long id;

    /**
     * Безопасный внешний / межсервисный идентификатор стратегии.
     */
    private String internalId;

    /**
     * Инструмент, для которого предназначена стратегия.
     */
    private Long instrumentId;

    /**
     * Человекочитаемое имя стратегии.
     */
    private String name;

    /**
     * Версия append-only стратегии.
     *
     * При изменении стратегия не редактируется,
     * а создаётся новая версия.
     */
    private Integer version;

    /**
     * Статус контейнера стратегии.
     */
    private StrategyStatus status;

    /**
     * Ровно одна detail на одну фазу рынка.
     */
    private List<StrategyDetail> details;
}
```

## StrategyStatus

```java
public enum StrategyStatus {

    /**
     * Стратегия создана, но ещё не введена в активное использование.
     */
    CREATED,

    /**
     * Единственная активная стратегия инструмента.
     *
     * Новые сделки по инструменту могут создаваться только по активной стратегии.
     */
    ACTIVE,

    /**
     * Стратегия существует, но временно не участвует в создании новых сделок.
     *
     * Уже открытые сделки должны продолжать жить по pinned-версии стратегии,
     * если отдельная политика остановки стратегии не говорит иначе.
     */
    INACTIVE,

    /**
     * Логически удалённая стратегия.
     *
     * Новые сделки по ней не создаются.
     * Для старых сделок поведение должно определяться отдельной политикой.
     */
    DELETED
}
```

---

# 2. StrategyDetail

В доменной модели и в JSON детали стратегии группируются по `Deal.Status`,
потому что это хорошо совпадает с текущей FSM сделки.

```java
public class StrategyDetail {

    /**
     * Технический ID БД.
     */
    private Long id;

    /**
     * Владелец detail.
     */
    private Long strategyId;

    /**
     * Для какой фазы рынка работает detail.
     */
    private MarketPhase.Type marketPhaseType;

    /**
     * Как торгуем в этой фазе рынка.
     */
    private PhaseEntryPolicy phaseEntryPolicy;

    /**
     * Риск на сделку в процентах от доступного капитала.
     */
    private BigDecimal riskPerTradePercent;

    /**
     * Максимально допустимое плечо.
     *
     * Фактическое плечо не должно превышать ни это значение,
     * ни глобальный лимит risk policy.
     */
    private Integer maxLeverage;

    /**
     * High-level ориентир reward/risk.
     *
     * Используется как настройка стратегии и ориентир для валидации,
     * но конкретные TP/SL всё равно задаются в action settings.
     */
    private BigDecimal targetRiskRewardRatio;

    /**
     * Шаги стратегии, сгруппированные по статусу сделки.
     *
     * Для чтения это выглядит удобно:
     * PRECHECK -> [...]
     * ENTRY_FINALIZED -> [...]
     * MANAGING -> [...]
     */
    private Map<Deal.Status, List<StrategyStep>> stepsByStatus;
}
```

## PhaseEntryPolicy

```java
public enum PhaseEntryPolicy {

    /**
     * Торгуем по направлению фазы.
     */
    FOLLOW_PHASE,

    /**
     * Торгуем против доминирующей фазы.
     */
    CONTRARIAN,

    /**
     * Во флэте используем grid-сценарий.
     */
    GRID,

    /**
     * В данной фазе не торгуем.
     */
    NO_TRADE
}
```

## Матрица допустимости

* `BULL_TREND` -> `FOLLOW_PHASE | CONTRARIAN | NO_TRADE`
* `BEAR_TREND` -> `FOLLOW_PHASE | CONTRARIAN | NO_TRADE`
* `RANGE` -> `GRID | NO_TRADE`
* `UNKNOWN` -> `NO_TRADE`

---

# 3. StrategyStep

Один `StrategyStep` =

* одно общее условие применимости;
* пакет действий, который нужно выполнить целиком, если условие истинно.

```java
public class StrategyStep {

    /**
     * Бизнес-смысл шага.
     */
    private StrategyStepType stepType;

    /**
     * Общее условие применимости шага.
     */
    private StrategyCondition condition;

    /**
     * Все действия, которые нужно выполнить,
     * если condition выполнено.
     */
    private List<StrategyAction> actions;
}
```

## StrategyStepType

```java
public enum StrategyStepType {

    /**
     * Вход в сделку.
     *
     * На этапе поиска входа этот step проверяется EntryScannerJob.
     * После создания Deal FSM может использовать entry-step в PRECHECK,
     * чтобы создать entry order.
     */
    ENTRY,

    /**
     * Основная защита после подтверждения позиции.
     */
    MAIN_PROTECTION,

    /**
     * Изменение уже существующей защиты.
     *
     * Примеры:
     * - перенос stop-loss в безубыток;
     * - включение trailing;
     * - перестройка защитного algo-ордера.
     */
    PROTECTION_ADJUSTMENT,

    /**
     * Частичная фиксация прибыли или частичный выход.
     */
    PARTIAL_EXIT,

    /**
     * Grid-входы во флэте.
     */
    GRID_ENTRY,

    /**
     * Управление grid-сценарием.
     *
     * Примеры:
     * - снять сетку при breakout;
     * - перестроить сетку;
     * - очистить часть уровней.
     */
    GRID_MANAGEMENT,

    /**
     * Плановый выход из позиции по правилам стратегии.
     */
    EXIT,

    /**
     * Аварийное действие для закрытия риска.
     */
    FAIL_SAFE
}
```

---

# 4. StrategyCondition

`StrategyCondition` — это набор rules.
Все rules внутри одного condition должны быть истинны.

## Порядок проверки rules

Если нужен детерминированный порядок проверки rules,
то он задаётся полем `level` у `StrategyConditionRule`.

Это **не** глобальный порядок шагов стратегии,
а только локальный порядок проверки правил внутри одного condition.

```java
public class StrategyCondition {

    /**
     * Rules проверяются по level ASC.
     */
    private List<StrategyConditionRule> rules;
}
```

## StrategyConditionRule

```java
public class StrategyConditionRule {

    /**
     * Порядок проверки правила внутри condition.
     */
    private Integer level;

    /**
     * Тип правила.
     *
     * Для простых бизнес-условий можно использовать конкретные ruleType:
     * NO_OPEN_POSITION, POSITION_OPENED, PROFIT_PERCENTS_REACHED и т.д.
     *
     * Для гибких входных условий можно использовать generic-типы:
     * INDICATOR_COMPARE, PRICE_COMPARE, MARKET_PHASE_IS и т.д.
     */
    private StrategyConditionRuleType ruleType;

    /**
     * Таймфрейм, на котором проверяется правило.
     *
     * Примеры:
     * 1m, 3m, 5m, 15m, 1H, 4H.
     *
     * Если правило не зависит от таймфрейма, поле может быть null.
     */
    private String timeframe;

    /**
     * Источник данных для левой части условия.
     *
     * Примеры:
     * PRICE, INDICATOR, SIGNAL, MARKET_PHASE, MARKET_STRUCTURE, POSITION, ORDER.
     */
    private StrategyConditionSourceType sourceType;

    /**
     * Левая часть условия.
     *
     * Примеры:
     * RSI_14,
     * EMA_FAST,
     * MACD_HISTOGRAM,
     * CLOSE_PRICE,
     * RANGE_HIGH,
     * MARKET_PHASE.
     */
    private String leftOperand;

    /**
     * Оператор проверки.
     */
    private StrategyConditionOperator operator;

    /**
     * Правая часть условия.
     *
     * Это может быть число, enum, другой индикатор,
     * уровень структуры рынка или ссылка на runtime-факт.
     */
    private StrategyConditionOperand rightOperand;

    /**
     * Универсальный процентный параметр.
     *
     * Используется только если ruleType этого требует.
     *
     * Примеры:
     * PROFIT_PERCENTS_REACHED = 1.5
     * LOSS_PERCENTS_REACHED = 0.5
     */
    private BigDecimal percents;

    /**
     * Дополнительные параметры правила.
     *
     * Примеры:
     * periods,
     * confirmationBars,
     * candleShift,
     * threshold,
     * expectedDirection,
     * paramsVersion.
     */
    private JsonNode params;
}
```

## StrategyConditionRuleType

```java
public enum StrategyConditionRuleType {

    /**
     * По инструменту нет открытой позиции.
     */
    NO_OPEN_POSITION,

    /**
     * По инструменту нет активной сделки.
     *
     * Чаще используется EntryScannerJob перед созданием новой Deal.
     */
    NO_ACTIVE_DEAL,

    /**
     * Входной ордер уже финализирован.
     */
    ENTRY_ORDER_FINALIZED,

    /**
     * Позиция реально открыта.
     */
    POSITION_OPENED,

    /**
     * Attached stop-loss существует.
     */
    ATTACHED_STOP_LOSS_EXISTS,

    /**
     * Основная защита существует.
     */
    MAIN_PROTECTION_EXISTS,

    /**
     * Профит достиг указанного процента.
     */
    PROFIT_PERCENTS_REACHED,

    /**
     * Убыток достиг указанного процента.
     */
    LOSS_PERCENTS_REACHED,

    /**
     * Подтверждён breakout диапазона.
     */
    RANGE_BREAKOUT_CONFIRMED,

    /**
     * Тренд изменился относительно ожидаемого сценария.
     */
    TREND_CHANGED,

    /**
     * Сделка потеряла экономическую эффективность.
     */
    EFFICIENCY_BELOW_THRESHOLD,

    /**
     * Фаза рынка равна ожидаемой.
     *
     * Пример:
     * MARKET_PHASE_IS BULL_TREND.
     */
    MARKET_PHASE_IS,

    /**
     * Сравнение значения индикатора.
     *
     * Примеры:
     * RSI_14 > 50,
     * EMA_FAST > EMA_SLOW,
     * MACD_HISTOGRAM > 0.
     */
    INDICATOR_COMPARE,

    /**
     * Сравнение цены с уровнем или другой ценой.
     *
     * Примеры:
     * CLOSE_PRICE > RANGE_HIGH,
     * MARK_PRICE < ENTRY_PRICE.
     */
    PRICE_COMPARE,

    /**
     * Пересечение цены / индикатора уровня сверху вниз или снизу вверх.
     *
     * Примеры:
     * CLOSE crossed above RANGE_HIGH,
     * EMA_FAST crossed above EMA_SLOW.
     */
    CROSSOVER,

    /**
     * Проверка сигнала или score из signal/quorum слоя.
     */
    SIGNAL_SCORE_REACHED,

    /**
     * Проверка объёмного фильтра.
     */
    VOLUME_FILTER_PASSED,

    /**
     * Проверка, что свеча закрылась и данные можно использовать без look-ahead.
     */
    CANDLE_CLOSED
}
```

## StrategyConditionSourceType

```java
public enum StrategyConditionSourceType {

    /**
     * Цена: last, mark, index, bid, ask, close и т.д.
     */
    PRICE,

    /**
     * Значение технического индикатора.
     */
    INDICATOR,

    /**
     * Сигнал или score из signal/quorum слоя.
     */
    SIGNAL,

    /**
     * Фаза рынка.
     */
    MARKET_PHASE,

    /**
     * Структура рынка: range, swing, support, resistance.
     */
    MARKET_STRUCTURE,

    /**
     * Позиция.
     */
    POSITION,

    /**
     * Ordinary order.
     */
    ORDER,

    /**
     * Algo-order.
     */
    ALGO_ORDER,

    /**
     * Баланс / доступная маржа / собственные средства.
     */
    BALANCE,

    /**
     * Время, сессия, день недели или тайминг свечи.
     */
    TIME,

    /**
     * Константное значение.
     */
    CONSTANT
}
```

## StrategyConditionOperator

```java
public enum StrategyConditionOperator {

    /**
     * Равно.
     */
    EQ,

    /**
     * Не равно.
     */
    NE,

    /**
     * Больше.
     */
    GT,

    /**
     * Больше или равно.
     */
    GTE,

    /**
     * Меньше.
     */
    LT,

    /**
     * Меньше или равно.
     */
    LTE,

    /**
     * Значение находится между границами.
     */
    BETWEEN,

    /**
     * Значение не находится между границами.
     */
    NOT_BETWEEN,

    /**
     * Пересечение уровня снизу вверх.
     */
    CROSSED_ABOVE,

    /**
     * Пересечение уровня сверху вниз.
     */
    CROSSED_BELOW,

    /**
     * Условие истинно.
     */
    IS_TRUE,

    /**
     * Условие ложно.
     */
    IS_FALSE,

    /**
     * Сущность или значение существует.
     */
    EXISTS,

    /**
     * Сущность или значение отсутствует.
     */
    NOT_EXISTS
}
```

## StrategyConditionOperand

```java
public class StrategyConditionOperand {

    /**
     * Источник данных операнда.
     */
    private StrategyConditionSourceType sourceType;

    /**
     * Тип значения.
     *
     * Примеры:
     * NUMBER,
     * STRING,
     * ENUM,
     * PRICE_FIELD,
     * INDICATOR_VALUE,
     * MARKET_STRUCTURE_LEVEL,
     * BOOLEAN.
     */
    private String valueType;

    /**
     * Имя поля или значения.
     *
     * Примеры:
     * RSI_14,
     * EMA_FAST,
     * RANGE_HIGH,
     * BULL_TREND.
     */
    private String name;

    /**
     * Числовое значение, если операнд — число.
     */
    private BigDecimal numberValue;

    /**
     * Строковое значение, если операнд — строка или enum.
     */
    private String stringValue;

    /**
     * Дополнительные параметры операнда.
     */
    private JsonNode params;
}
```

---

# 5. StrategyAction

Действия делаются типизированными.

Это лучше, чем один плоский объект с большим количеством nullable-полей.

```java
public interface StrategyAction {
}
```

Важно:

> `StrategyAction` — это не `ServiceCommand`.
>
> `StrategyAction` описывает ожидаемое действие стратегии.
>
> Runtime-сущность, созданная или изменённая по этому action, связывается через `DealActionState`.

---

# 6. StrategyOrderAction

Обычный ордер.

Attached protection встроена внутрь order-action.

```java
public class StrategyOrderAction implements StrategyAction {

    /**
     * CREATE / AMEND / CANCEL.
     *
     * Для обычного ордера допустимы:
     * CREATE, AMEND, CANCEL.
     */
    private StrategyActionType actionType;

    /**
     * Реальный доменный тип order.
     *
     * Сейчас это:
     * ENTRY
     * ENTRY_ATTACHED_STOP_LOSS
     */
    private Order.Type orderType;

    /**
     * Нормализованное направление стратегии.
     *
     * LONG -> runtime mapper / command factory маппит в buy для entry order.
     * SHORT -> runtime mapper / command factory маппит в sell для entry order.
     */
    private StrategyTradeDirection direction;

    /**
     * Доля расчётного объёма сценария.
     *
     * Пример:
     * 25 = 25% от объёма, который посчитает SizeCalculator.
     */
    private BigDecimal allocationPercents;

    /**
     * Уровень действия внутри стратегии.
     *
     * Примеры:
     * - grid entry #1..#4;
     * - серия входов в одном шаге;
     * - несколько однотипных actions в одном StrategyStep.
     *
     * Важно:
     * level живёт в стратегии.
     * level не переносится в Order / AlgoOrder как runtime-role.
     * Runtime-связь action -> сущность хранится через DealActionState.
     */
    private Integer level;

    /**
     * Как вычислить цену ордера.
     *
     * Для market-like входа может быть null.
     */
    private StrategyPricePlacement placement;

    /**
     * Attached-защита, если order создаётся вместе с attached SL.
     *
     * Для ENTRY = null.
     * Для ENTRY_ATTACHED_STOP_LOSS = обязательно заполнена.
     */
    private StrategyAttachedProtectionSettings attachedProtection;
}
```

## StrategyTradeDirection

```java
public enum StrategyTradeDirection {

    /**
     * Длинное направление стратегии.
     *
     * Используется, когда стратегия ожидает заработок
     * на росте цены инструмента.
     *
     * В strategy-layer это нормализованное направление.
     * Дальше runtime mapper / command factory уже маппит его
     * в конкретные значения runtime-моделей и биржевых полей.
     *
     * Примеры:
     * - для обычного entry order это обычно будет buy;
     * - для позиции это соответствует long.
     */
    LONG,

    /**
     * Короткое направление стратегии.
     *
     * Используется, когда стратегия ожидает заработок
     * на падении цены инструмента.
     *
     * В strategy-layer это нормализованное направление.
     * Дальше runtime mapper / command factory уже маппит его
     * в конкретные значения runtime-моделей и биржевых полей.
     *
     * Примеры:
     * - для обычного entry order это обычно будет sell;
     * - для позиции это соответствует short.
     */
    SHORT
}
```

---

# 7. StrategyPricePlacement

Если мы отказались от отдельного `gridSettings`,
то параметры позиционирования цены grid-ордеров должны жить в `StrategyOrderAction`.

```java
public class StrategyPricePlacement {

    /**
     * От какой базы считаем цену.
     */
    private StrategyPriceBaseType baseType;

    /**
     * Источник рыночной цены, если baseType = MARKET_PRICE.
     *
     * Примеры:
     * LAST_PRICE,
     * MARK_PRICE,
     * INDEX_PRICE,
     * BEST_BID_PRICE,
     * BEST_ASK_PRICE,
     * MID_PRICE.
     *
     * Для RANGE_LOW / RANGE_HIGH / ENTRY_PRICE обычно null.
     */
    private StrategyPriceSource priceSource;

    /**
     * Куда смещаемся относительно базы.
     */
    private StrategyPriceOffsetSide offsetSide;

    /**
     * Процент смещения от базы.
     *
     * Пример:
     * 10 = смещение на 10% от выбранной базы/диапазона.
     */
    private BigDecimal percents;
}
```

## StrategyPriceBaseType

```java
public enum StrategyPriceBaseType {

    /**
     * Нижняя граница диапазона (range low).
     *
     * Используется как базовый ценовой уровень,
     * от которого нужно сместиться вверх или вниз.
     *
     * Примеры:
     * - поставить long grid-ордер немного выше нижней границы диапазона;
     * - рассчитать вход от нижней границы флэта.
     */
    RANGE_LOW,

    /**
     * Верхняя граница диапазона (range high).
     *
     * Используется как базовый ценовой уровень,
     * от которого нужно сместиться вверх или вниз.
     *
     * Примеры:
     * - поставить short grid-ордер немного ниже верхней границы диапазона;
     * - рассчитать вход от верхней границы флэта.
     */
    RANGE_HIGH,

    /**
     * Цена входа в позицию.
     *
     * Используется как база, когда цену нового действия
     * нужно рассчитывать относительно уже существующей сделки.
     *
     * Примеры:
     * - поставить защиту чуть выше/ниже цены входа;
     * - рассчитать новый стоп от точки входа;
     * - сместить уровень относительно средней цены позиции.
     */
    ENTRY_PRICE,

    /**
     * Текущая рыночная цена.
     *
     * Здесь дополнительно требуется указать priceSource:
     * LAST_PRICE, MARK_PRICE, INDEX_PRICE, BEST_BID_PRICE,
     * BEST_ASK_PRICE или MID_PRICE.
     */
    MARKET_PRICE
}
```

## StrategyPriceSource

```java
public enum StrategyPriceSource {

    /**
     * Последняя цена сделки.
     */
    LAST_PRICE,

    /**
     * Mark price.
     */
    MARK_PRICE,

    /**
     * Index price.
     */
    INDEX_PRICE,

    /**
     * Лучший bid из стакана.
     */
    BEST_BID_PRICE,

    /**
     * Лучший ask из стакана.
     */
    BEST_ASK_PRICE,

    /**
     * Средняя между bid и ask.
     */
    MID_PRICE
}
```

## StrategyPriceOffsetSide

```java
public enum StrategyPriceOffsetSide {

    /**
     * Сместить расчётную цену выше выбранной базы.
     *
     * Примеры:
     * - от RANGE_LOW вверх на 10%;
     * - от ENTRY_PRICE вверх на 0.5%;
     * - от MARKET_PRICE вверх на 1%.
     *
     * Обычно используется, когда хотим поставить цену
     * выше опорного уровня.
     */
    ABOVE,

    /**
     * Сместить расчётную цену ниже выбранной базы.
     *
     * Примеры:
     * - от RANGE_HIGH вниз на 10%;
     * - от ENTRY_PRICE вниз на 0.5%;
     * - от MARKET_PRICE вниз на 1%.
     *
     * Обычно используется, когда хотим поставить цену
     * ниже опорного уровня.
     */
    BELOW
}
```

### Почему это отдельные модели

* `StrategyTradeDirection` отвечает за **торговое направление**: `LONG / SHORT`.
* `StrategyPriceOffsetSide` отвечает за **геометрию смещения цены**: `ABOVE / BELOW`.
* `StrategyPriceSource` отвечает за **источник рыночной цены**: `LAST_PRICE / MARK_PRICE / INDEX_PRICE / BID / ASK / MID`.
* `TriggerPriceType` отвечает за **тип trigger-цены на бирже** для algo-orders: `LAST / INDEX / MARK`.

Это разные смыслы, поэтому их не надо смешивать.

---

# 8. StrategyAttachedProtectionSettings

```java
public class StrategyAttachedProtectionSettings {

    /**
     * Сейчас по домену это фактически ATTACHED_STOP_LOSS.
     */
    private AttachedAlgoOrder.Type attachedType;

    /**
     * Настройки стартового stop-loss.
     */
    private StopLossSettings stopLossSettings;
}
```

---

# 9. StrategyAlgoOrderAction

Standalone algo-order.

Из модели убраны:

* `BreakevenSettings`
* `PartialTakeProfitSettings`
* `ExitEfficiencySettings`

Причина:

* breakeven — это отдельный `StrategyStep` в `MANAGING`;
* partial take profit — это несколько `StrategyAlgoOrderAction` в одном step;
* exit by efficiency — это отдельный exit-step через condition.

```java
public class StrategyAlgoOrderAction implements StrategyAction {

    /**
     * CREATE / AMEND / CANCEL.
     *
     * Для algo-ордера допустимы:
     * CREATE, AMEND, CANCEL.
     */
    private StrategyActionType actionType;

    /**
     * Реальный доменный тип algo condition.
     */
    private ConditionType conditionType;

    /**
     * Уровень действия.
     *
     * Примеры:
     * - TP1 / TP2 / TP3;
     * - несколько защит в одном step.
     *
     * Важно:
     * level живёт в стратегии.
     * Runtime-связь action -> algo-order хранится через DealActionState.
     */
    private Integer level;

    /**
     * Настройки stop-loss.
     *
     * Используется для:
     * - STOP_LOSS;
     * - OCO_FULL;
     * - PARTIAL_STOP_LOSS, если такой condition type появится.
     */
    private StopLossSettings stopLossSettings;

    /**
     * Настройки trailing.
     *
     * Используется для TRAILING_PERCENTS.
     */
    private TrailingSettings trailingSettings;

    /**
     * Доля закрываемой позиции в процентах.
     *
     * Пример:
     * 25 = закрыть 25% позиции.
     *
     * В runtime это потом конвертируется в fraction 0..1.
     */
    private BigDecimal closeFractionPercents;

    /**
     * При каком профите срабатывает действие.
     *
     * Примеры:
     * - TAKE_PROFIT;
     * - PARTIAL_TAKE_PROFIT;
     * - OCO_FULL, если нужен TP-компонент.
     */
    private BigDecimal triggerProfitPercents;

    /**
     * Тип trigger-цены на бирже: LAST / INDEX / MARK.
     */
    private TriggerPriceType triggerPriceType;
}
```

---

# 10. StrategyPositionAction

Действия над позицией.

```java
public class StrategyPositionAction implements StrategyAction {

    /**
     * CLOSE_FULL / CLOSE_PARTIAL.
     */
    private StrategyActionType actionType;

    /**
     * Уровень действия.
     *
     * Используется, если в одном step есть несколько position actions.
     */
    private Integer level;

    /**
     * Для частичного закрытия.
     */
    private BigDecimal closeFractionPercents;
}
```

---

# 11. Общий action type

По договорённости оставляем один общий enum для всех действий.

```java
public enum StrategyActionType {

    /**
     * Создать новую сущность по правилу стратегии.
     *
     * Примеры:
     * - создать ordinary entry order;
     * - создать entry order с attached stop-loss;
     * - создать standalone algo-order защиты;
     * - создать partial take profit уровень.
     */
    CREATE,

    /**
     * Изменить уже существующую сущность.
     *
     * Примеры:
     * - передвинуть stop-loss ближе к entry;
     * - обновить основную защиту;
     * - включить или перестроить trailing;
     * - изменить параметры уже существующего algo-ордера.
     */
    AMEND,

    /**
     * Отменить существующую сущность.
     *
     * Примеры:
     * - отменить pending ordinary order;
     * - снять grid-ордера при breakout;
     * - отменить attached protection после переключения на main protection;
     * - отменить активный algo-order перед перестройкой защиты.
     */
    CANCEL,

    /**
     * Полностью закрыть позицию.
     *
     * Используется для действий над позицией, когда стратегия требует:
     * - плановый полный выход;
     * - аварийное закрытие риска;
     * - завершение basket-позиции в grid-сценарии.
     */
    CLOSE_FULL,

    /**
     * Частично закрыть позицию.
     *
     * Используется для действий над позицией, когда стратегия требует:
     * - частичный выход по позиции;
     * - сокращение позиции по этапам;
     * - частичную фиксацию объёма вне algo-механики.
     *
     * Обычно требует указания closeFractionPercents.
     */
    CLOSE_PARTIAL
}
```

---

# 12. Семантика общего StrategyActionType

Важно явно зафиксировать допустимые значения по подтипам action:

* `StrategyOrderAction` использует только: `CREATE`, `AMEND`, `CANCEL`.
* `StrategyAlgoOrderAction` использует только: `CREATE`, `AMEND`, `CANCEL`.
* `StrategyPositionAction` использует только: `CLOSE_FULL`, `CLOSE_PARTIAL`.

То есть общий enum один, но допустимые значения валидируются по конкретному подтипу действия.

---

# 13. Семантика actionKind в JSON

В JSON-примерах может использоваться поле `actionKind` со значениями:

* `ORDER`
* `ALGO_ORDER`
* `POSITION`

Это не отдельное поле доменной модели, а **JSON discriminator**,
который нужен только для сериализации / десериализации и выбора конкретного подтипа `StrategyAction`.

---

# 14. Семантика placement для StrategyOrderAction

`placement` используется так:

* при `actionType = CREATE` — для расчёта цены нового ордера;
* при `actionType = AMEND` — как новая целевая схема позиционирования цены, если стратегия действительно перестраивает ордер;
* при `actionType = CANCEL` — обычно цена не нужна, а нужная runtime-сущность определяется через `DealActionState`.

Важно:

> `placement` не должен быть основным способом идентификации runtime-сущности.
> Для связи action стратегии с runtime-сущностью используется `DealActionState`.

---

# 15. Семантика OCO_FULL в StrategyAlgoOrderAction

Если `conditionType = OCO_FULL`, то:

* stop-loss компонент строится из `stopLossSettings`;
* take-profit компонент строится из `triggerProfitPercents` и `triggerPriceType`;
* `closeFractionPercents` определяет, какую долю позиции закрывает OCO.

Для полного закрытия позиции обычно используется:

```text
closeFractionPercents = 100
```

---

# 16. StopLossSettings

`triggerPriceType` обязателен, потому что runtime trigger-based algo conditions реально завязаны на `TriggerPriceType`.

```java
public class StopLossSettings {

    /**
     * Как считать stop-loss.
     */
    private StopLossCalculationType calculationType;

    /**
     * Универсальное процентное расстояние.
     *
     * Смысл зависит от calculationType:
     * - ENTRY_PRICE_PERCENT: процент от entry price;
     * - ATR_PERCENT: процент от ATR, где 150 = 1.5 ATR;
     * - MARKET_STRUCTURE_BUFFER_PERCENT: buffer от структурного уровня.
     */
    private BigDecimal distancePercents;

    /**
     * Тип trigger-цены на бирже: LAST / INDEX / MARK.
     */
    private TriggerPriceType triggerPriceType;
}
```

## StopLossCalculationType

```java
public enum StopLossCalculationType {

    /**
     * Stop-loss считается как процент от цены входа.
     *
     * LONG:
     * SL = entryPrice - entryPrice * distancePercents / 100
     *
     * SHORT:
     * SL = entryPrice + entryPrice * distancePercents / 100
     */
    ENTRY_PRICE_PERCENT,

    /**
     * Stop-loss считается как расстояние от ATR.
     *
     * distancePercents = 150 означает 1.5 ATR.
     */
    ATR_PERCENT,

    /**
     * Stop-loss ставится за уровень структуры рынка.
     *
     * Примеры:
     * - LONG SL ниже swing low;
     * - SHORT SL выше swing high;
     * - grid SL за range low / range high.
     */
    MARKET_STRUCTURE_BUFFER_PERCENT
}
```

---

# 17. TrailingSettings

`TrailingSettings` остаётся, потому что trailing реально выражается как отдельная форма algo condition.

```java
public class TrailingSettings {

    /**
     * После какого профита можно включить trailing.
     *
     * Если null — trailing включается сразу.
     */
    private BigDecimal activationProfitPercents;

    /**
     * Расстояние trailing от экстремума.
     *
     * Обычно передаётся на биржу как callback ratio / callback percent.
     */
    private BigDecimal callbackPercents;

    /**
     * Дополнительный буфер после активации.
     */
    private BigDecimal activationBufferPercents;
}
```

---

# 18. Связь стратегии с DealActionState

Стратегия не хранит runtime-состояние выполнения.

Runtime-связь хранится отдельно:

```text
StrategyAction
  -> DealActionState
     -> targetEntityType
     -> targetEntityId
```

Это нужно, чтобы:

* `Order`, `AlgoOrder`, `Position` оставались чистыми биржевыми сущностями;
* FSM могла восстановиться после рестарта;
* не хранить `strategyActionId` прямо в биржевых сущностях;
* не строить runtime-логику по аудиту.

Ключевой инвариант:

```text
UNIQUE(deal_id, strategy_action_id)
```

Но он находится в `DealActionState`, а не в `Order` / `AlgoOrder`.

---

# 19. Связь стратегии с калькуляторами

Стратегия хранит не готовые цены, размеры и риск,
а правила их расчёта.

Runtime-расчёт делает:

```text
StrategyActionCalculator
  -> PriceCalculator
  -> SizeCalculator
  -> RiskCalculator
```

Примеры:

```text
StrategyOrderAction.placement
  -> PriceCalculator считает price

StrategyOrderAction.allocationPercents
  -> SizeCalculator считает sizeContracts

StrategyDetail.riskPerTradePercent / maxLeverage
  -> RiskCalculator проверяет риск
```

Важно:

> StrategyActionCalculator собирает свежий CalculationContext в runtime,
> потому что цена и рыночные данные могут измениться между сбором DealContext и выполнением команды.

---

# 20. Связь стратегии с индикаторами и snapshots

Стратегия может ссылаться на условия, которые проверяются по индикаторам и market snapshots.

Но стратегия сама не считает индикаторы.

Индикаторы и snapshots готовят:

* `IndicatorJob`
* `MarketStructureJob`
* `MarketPhaseJob`

Их используют:

* `EntryScannerJob` — для поиска входа;
* `StrategyConditionEvaluator` — для проверки условий step;
* `StrategyActionCalculator` — для расчёта цены, размера и риска.

---

# 21. JSON-примеры

JSON-примеры можно хранить в отдельном файле:

```text
Strategy API examples.md
```

Это сделано, чтобы основной документ оставался компактным и был сфокусирован именно на модели.

При обновлении JSON-примеров нужно учитывать:

* `StrategyPricePlacement.priceSource` вместо старого `marketPriceType`;
* `StopLossSettings.calculationType` как enum `StopLossCalculationType`;
* расширенную модель `StrategyConditionRule`;
* отсутствие `strategyActionId` в runtime-сущностях;
* использование `DealActionState` для runtime-связи action -> entity.

---

# 22. Что изменилось относительно прошлой версии

1. `PriceResolver` переименован концептуально в `PriceCalculator`.
2. Добавлен `StrategyActionCalculator` как оркестратор `PriceCalculator`, `SizeCalculator`, `RiskCalculator`.
3. `StrategyConditionRule` расширен, чтобы описывать ENTRY-условия по индикаторам, цене, фазе рынка и структуре.
4. `StrategyPricePlacement.marketPriceType` заменён на `StrategyPriceSource priceSource`.
5. `StopLossSettings.calculationType` стал enum `StopLossCalculationType`, а не строкой.
6. Уточнено, что `level` живёт в стратегии и не переносится в `Order` / `AlgoOrder` как runtime-role.
7. Уточнено, что `Order`, `AlgoOrder`, `Position` не хранят `strategyActionId`.
8. Добавлен раздел про `DealActionState`.
9. Добавлены ссылки на новые процессные документы.
