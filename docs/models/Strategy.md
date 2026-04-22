# Модель стратегии — финальная версия

Ниже зафиксирована финальная форма strategy-layer, к которой мы пришли.

Главная идея:

* **стратегия** хранит торговые правила, условия и ожидаемые действия;
* **FSM сделки** управляет реальными сущностями `Deal / Order / AttachedAlgoOrder / AlgoOrder / Position`;
* стратегия **не хранит** сервисные команды и не пытается управлять runtime-сущностями напрямую;
* стратегия говорит **что** должно быть создано/изменено/отменено и **при каких условиях**;
* стейт-машина и orchestration-слой решают, **когда именно** интерпретировать эти правила и в какие низкоуровневые команды их превратить.

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
     * При изменении стратегия не редактируется, а создаётся заново.
     */
    private Integer version;

    /**
     * Статус контейнера стратегии.
     */
    private StrategyStatus status;

    /**
     * Ровно одна detail на одну фазу рынка.
     */
    private List<StrategyDetails> details;
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
     */
    ACTIVE,

    /**
     * Стратегия существует, но временно не участвует в резолве.
     */
    INACTIVE,

    /**
     * Логически удалённая стратегия.
     */
    DELETED
}
```

---

# 2. StrategyDetails

В доменной модели и в JSON детали стратегии группируются по `Deal.Status`,
потому что это хорошо совпадает с текущей FSM сделки.

```java
public class StrategyDetails {

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
     */
    private Integer maxLeverage;

    /**
     * High-level ориентир reward/risk.
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

```java
public class StrategyConditionRule {

    /**
     * Порядок проверки правила внутри condition.
     */
    private Integer level;

    /**
     * Тип правила.
     */
    private StrategyConditionRuleType ruleType;

    /**
     * Универсальный процентный параметр.
     * Используется только если ruleType этого требует.
     */
    private BigDecimal percents;
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
    EFFICIENCY_BELOW_THRESHOLD
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

---

# 6. StrategyOrderAction

Обычный ордер.

Attached protection встроена внутрь order-action.

```java
public class StrategyOrderAction implements StrategyAction {

    /**
     * CREATE / AMEND / CANCEL / CLOSE_FULL / CLOSE_PARTIAL
     *
     * Для обычного ордера в большинстве случаев:
     * CREATE / AMEND / CANCEL.
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
     * LONG -> resolver маппит в buy для entry order
     * SHORT -> resolver маппит в sell для entry order
     */
    private StrategyTradeDirection direction;

    /**
     * Доля расчётного объёма сценария.
     *
     * Пример:
     * 25 = 25% от объёма, который уже посчитал PositionCalculator.
     */
    private BigDecimal allocationPercents;

    /**
     * Уровень действия.
     *
     * Примеры:
     * - grid entry #1..#4
     * - серия входов в одном шаге
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

## Общий direction для strategy-layer

```java
public enum StrategyTradeDirection {

    /**
     * Длинное направление стратегии.
     *
     * Используется, когда стратегия ожидает заработок
     * на росте цены инструмента.
     *
     * В strategy-layer это нормализованное направление.
     * Дальше resolver уже маппит его в конкретные значения
     * runtime-моделей и биржевых полей.
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
     * Дальше resolver уже маппит его в конкретные значения
     * runtime-моделей и биржевых полей.
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
     * Если baseType = MARKET_PRICE,
     * то указываем, какую именно рыночную цену брать: LAST / INDEX / MARK.
     *
     * Для RANGE_LOW / RANGE_HIGH / ENTRY_PRICE = null.
     */
    private TriggerPriceType marketPriceType;

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
     * Цена входа в позицию (entry price).
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
     * В отличие от RANGE_LOW / RANGE_HIGH / ENTRY_PRICE,
     * здесь дополнительно требуется указать,
     * какую именно рыночную цену брать:
     * LAST / INDEX / MARK.
     *
     * Примеры:
     * - поставить ордер относительно текущей mark price;
     * - рассчитать уровень от текущей last price;
     * - сместиться от актуальной index price.
     */
    MARKET_PRICE
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

### Почему это отдельная модель

* `StrategyTradeDirection` отвечает за **торговое направление**: `LONG / SHORT`
* `StrategyPriceOffsetSide` отвечает за **геометрию смещения цены**: `ABOVE / BELOW`
* `TriggerPriceType` отвечает за **тип рыночной цены**: `LAST / INDEX / MARK`

Это три разных смысла, поэтому их не надо смешивать.

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
     * CREATE / AMEND / CANCEL / CLOSE_FULL / CLOSE_PARTIAL
     *
     * Для algo-ордера в большинстве случаев:
     * CREATE / AMEND / CANCEL.
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
     * - TP1 / TP2 / TP3
     * - несколько защит в одном step
     */
    private Integer level;

    /**
     * Настройки stop-loss.
     *
     * Используется для:
     * - STOP_LOSS
     * - OCO_FULL
     * - PARTIAL_STOP_LOSS
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
     * - TAKE_PROFIT
     * - PARTIAL_TAKE_PROFIT
     * - OCO_FULL (если нужен TP-компонент)
     */
    private BigDecimal triggerProfitPercents;

    /**
     * MARK / LAST / INDEX.
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

По договорённости `StrategyPositionActionType` схлопывается с `StrategyActionOperationType`.
Оставляем один общий enum для всех действий.

```java
public enum StrategyActionType {

    /**
     * Создать новую сущность по правилу стратегии.
     *
     * Примеры:
     * - создать обычный entry order;
     * - создать attached stop-loss вместе с entry;
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
     * - отменить pending обычный ордер;
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

# 12. StopLossSettings

`triggerPriceType` обязателен, потому что runtime trigger-based algo conditions у тебя реально завязаны на `TriggerPriceType`.

```java
public class StopLossSettings {

    /**
     * ENTRY_PRICE_PERCENT / ATR_PERCENT / MARKET_STRUCTURE_BUFFER_PERCENT
     */
    private String calculationType;

    /**
     * Универсальное процентное расстояние.
     */
    private BigDecimal distancePercents;

    /**
     * LAST / INDEX / MARK.
     */
    private TriggerPriceType triggerPriceType;
}
```

---

# 13. TrailingSettings

`TrailingSettings` остаётся, потому что trailing реально выражается как отдельная форма algo condition.

```java
public class TrailingSettings {

    /**
     * После какого профита можно включить trailing.
     * Если null — сразу.
     */
    private BigDecimal activationProfitPercents;

    /**
     * Расстояние trailing от экстремума.
     */
    private BigDecimal callbackPercents;

    /**
     * Дополнительный буфер после активации.
     */
    private BigDecimal activationBufferPercents;
}
```

---

# 14. Что убрали

Из финальной модели убраны:

* `stepOrder`
* `dependsOnStepType`
* `BreakevenSettings`
* `PartialTakeProfitSettings`
* `ExitEfficiencySettings`
* `ENTRY_PROTECTION`
* отдельный `gridSettings`

Причины:

* порядок нужен только внутри rules одного condition;
* breakeven — это отдельный step;
* partial TP — это несколько action в одном step;
* exit by efficiency — это отдельный exit-step через condition;
* вход с attached SL — это один бизнес-шаг `ENTRY`;
* grid лучше выражается шагами и `StrategyPricePlacement`, а не special-case объектом.

---

# 15. Три финальных JSONC-примера

Ниже `JSONC`, а не строгий JSON.
Комментарии `//` нужны только для чтения.

## 15.1 BULL_TREND / FOLLOW_PHASE

```jsonc
{
  "marketPhaseType": "BULL_TREND",
  // Деталь действует только в бычьей фазе рынка.

  "phaseEntryPolicy": "FOLLOW_PHASE",
  // Работаем по тренду: ищем long-сценарий.

  "riskPerTradePercent": 1.0,
  // Максимум 1% риска на сделку.

  "maxLeverage": 10,
  // Верхний лимит плеча.

  "targetRiskRewardRatio": 3.0,
  // High-level ориентир стратегии: 1:3.

  "stepsByStatus": {
    "PRECHECK": [
      {
        "stepType": "ENTRY",
        // На PRECHECK готовим входной ордер со страховочным attached SL.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "NO_OPEN_POSITION"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ORDER",
            // Это StrategyOrderAction.

            "actionType": "CREATE",
            "orderType": "ENTRY_ATTACHED_STOP_LOSS",
            // Реальный Order.Type: входной ордер с attached SL.

            "direction": "LONG",
            // Нормализованное торговое направление strategy-layer.

            "allocationPercents": 100,
            // Берём 100% от объёма, который уже посчитал PositionCalculator.

            "level": 1,

            "placement": null,
            // Для такого входа цену может определить отдельный price resolver.

            "attachedProtection": {
              "attachedType": "ATTACHED_STOP_LOSS",
              // Attached protection на входе.

              "stopLossSettings": {
                "calculationType": "ATR_PERCENT",
                "distancePercents": 150,
                // 150% ATR = 1.5 * ATR.

                "triggerPriceType": "MARK"
              }
            }
          }
        ]
      }
    ],

    "ENTRY_FINALIZED": [
      {
        "stepType": "MAIN_PROTECTION",
        // После подтверждения позиции создаём основную защиту.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            // Это StrategyAlgoOrderAction.

            "actionType": "CREATE",
            "conditionType": "OCO_FULL",
            // Основная защита = OCO (SL + TP).

            "level": 1,

            "stopLossSettings": {
              "calculationType": "ATR_PERCENT",
              "distancePercents": 150,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,

            "closeFractionPercents": 100,
            // Закрываем 100% позиции.

            "triggerProfitPercents": 3.0,
            // TP на +3% от entry.

            "triggerPriceType": "MARK"
          }
        ]
      }
    ],

    "MANAGING": [
      {
        "stepType": "PROTECTION_ADJUSTMENT",
        // После +1% переносим защиту ближе к безубытку.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "PROFIT_PERCENTS_REACHED",
              "percents": 1.0
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "AMEND",
            "conditionType": "STOP_LOSS",
            // Обновляем stop-loss до BE-подобного уровня.

            "level": 1,

            "stopLossSettings": {
              "calculationType": "ENTRY_PRICE_PERCENT",
              "distancePercents": 0.10,
              // Новый stop = около entry с буфером 0.10%.

              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "PROTECTION_ADJUSTMENT",
        // После +2% включаем trailing.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "PROFIT_PERCENTS_REACHED",
              "percents": 2.0
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "TRAILING_PERCENTS",
            // Создаём / включаем trailing-защиту.

            "level": 2,

            "stopLossSettings": null,

            "trailingSettings": {
              "activationProfitPercents": 2.0,
              "callbackPercents": 0.70,
              "activationBufferPercents": 0.10
            },

            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "EXIT",
        // Если тренд сломался, стратегия закрывает позицию.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "TREND_CHANGED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "POSITION",
            // Это StrategyPositionAction.

            "actionType": "CLOSE_FULL",
            "level": 1,
            "closeFractionPercents": 100
          }
        ]
      }
    ]
  }
}
```

## 15.2 BEAR_TREND / FOLLOW_PHASE с partial exit лесенкой

```jsonc
{
  "marketPhaseType": "BEAR_TREND",
  // Деталь действует только в медвежьей фазе рынка.

  "phaseEntryPolicy": "FOLLOW_PHASE",
  // Работаем по тренду: short-сценарий.

  "riskPerTradePercent": 1.0,
  "maxLeverage": 10,
  "targetRiskRewardRatio": 3.0,

  "stepsByStatus": {
    "PRECHECK": [
      {
        "stepType": "ENTRY",

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "NO_OPEN_POSITION"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY_ATTACHED_STOP_LOSS",
            "direction": "SHORT",
            "allocationPercents": 100,
            "level": 1,
            "placement": null,

            "attachedProtection": {
              "attachedType": "ATTACHED_STOP_LOSS",
              "stopLossSettings": {
                "calculationType": "ATR_PERCENT",
                "distancePercents": 150,
                "triggerPriceType": "MARK"
              }
            }
          }
        ]
      }
    ],

    "ENTRY_FINALIZED": [
      {
        "stepType": "MAIN_PROTECTION",

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "STOP_LOSS",
            // Сначала ставим базовый стоп без OCO.

            "level": 1,

            "stopLossSettings": {
              "calculationType": "ATR_PERCENT",
              "distancePercents": 150,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      }
    ],

    "MANAGING": [
      {
        "stepType": "PARTIAL_EXIT",
        // Один общий condition -> пакет действий.
        // Здесь сразу создаём TP1/TP2/TP3.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 1,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 1.0,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 2,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 2.0,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 3,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 50,
            "triggerProfitPercents": 3.0,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "PROTECTION_ADJUSTMENT",
        // После +1% подтягиваем stop-loss ближе к entry.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "PROFIT_PERCENTS_REACHED",
              "percents": 1.0
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "AMEND",
            "conditionType": "STOP_LOSS",
            "level": 4,

            "stopLossSettings": {
              "calculationType": "ENTRY_PRICE_PERCENT",
              "distancePercents": 0.10,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      }
    ]
  }
}
```

## 15.3 RANGE / GRID

```jsonc
{
  "marketPhaseType": "RANGE",
  // Деталь действует только во флэте.

  "phaseEntryPolicy": "GRID",
  // Внутри RANGE работаем сеткой.

  "riskPerTradePercent": 0.6,
  // Для grid риск ниже, чем в тренде.

  "maxLeverage": 4,
  // И сильнее ограничиваем плечо.

  "targetRiskRewardRatio": 1.5,

  "stepsByStatus": {
    "PRECHECK": [
      {
        "stepType": "GRID_ENTRY",
        // Один шаг с общим condition создаёт сразу 4 лимитных входа.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "NO_OPEN_POSITION"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": 25,
            "level": 1,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 10
            },
            // Первый long grid-level = +10% от нижней границы range.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": 25,
            "level": 2,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 30
            },
            // Второй long grid-level = +30% от нижней границы range.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": 25,
            "level": 3,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 30
            },
            // Первый short grid-level = -30% от верхней границы range.

            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CREATE",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": 25,
            "level": 4,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 10
            },
            // Второй short grid-level = -10% от верхней границы range.

            "attachedProtection": null
          }
        ]
      }
    ],

    "ENTRY_FINALIZED": [
      {
        "stepType": "MAIN_PROTECTION",
        // После появления basket-позиции ставим общий защитный stop-loss.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "STOP_LOSS",
            "level": 1,

            "stopLossSettings": {
              "calculationType": "MARKET_STRUCTURE_BUFFER_PERCENT",
              "distancePercents": 20,
              "triggerPriceType": "MARK"
            },

            "trailingSettings": null,
            "closeFractionPercents": 100,
            "triggerProfitPercents": null,
            "triggerPriceType": "MARK"
          }
        ]
      }
    ],

    "MANAGING": [
      {
        "stepType": "PARTIAL_EXIT",
        // Во флэте можно заранее выставить лестницу частичного выхода.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 1,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 0.50,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 2,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 25,
            "triggerProfitPercents": 1.00,
            "triggerPriceType": "MARK"
          },
          {
            "actionKind": "ALGO_ORDER",
            "actionType": "CREATE",
            "conditionType": "PARTIAL_TAKE_PROFIT",
            "level": 3,
            "stopLossSettings": null,
            "trailingSettings": null,
            "closeFractionPercents": 50,
            "triggerProfitPercents": 1.50,
            "triggerPriceType": "MARK"
          }
        ]
      },

      {
        "stepType": "GRID_MANAGEMENT",
        // Если диапазон сломан — снимаем все grid-входы.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "RANGE_BREAKOUT_CONFIRMED",
              "percents": 15
              // Breakout подтверждён с буфером 15% ширины диапазона.
            }
          ]
        },

        "actions": [
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": null,
            "level": 1,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 10
            },
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "LONG",
            "allocationPercents": null,
            "level": 2,
            "placement": {
              "baseType": "RANGE_LOW",
              "marketPriceType": null,
              "offsetSide": "ABOVE",
              "percents": 30
            },
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": null,
            "level": 3,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 30
            },
            "attachedProtection": null
          },
          {
            "actionKind": "ORDER",
            "actionType": "CANCEL",
            "orderType": "ENTRY",
            "direction": "SHORT",
            "allocationPercents": null,
            "level": 4,
            "placement": {
              "baseType": "RANGE_HIGH",
              "marketPriceType": null,
              "offsetSide": "BELOW",
              "percents": 10
            },
            "attachedProtection": null
          }
        ]
      },

      {
        "stepType": "EXIT",
        // При смене режима рынка закрываем basket-позицию.

        "condition": {
          "rules": [
            {
              "level": 1,
              "ruleType": "POSITION_OPENED"
            },
            {
              "level": 2,
              "ruleType": "TREND_CHANGED"
            }
          ]
        },

        "actions": [
          {
            "actionKind": "POSITION",
            "actionType": "CLOSE_FULL",
            "level": 1,
            "closeFractionPercents": 100
          }
        ]
      }
    ]
  }
}
```

---

# 16. Итог по смыслу

Эта модель сейчас фиксирует следующее:

* стратегия хранит **торговые правила**;
* шаги сгруппированы по `Deal.Status`, а не живут отдельной осью статусов;
* один шаг = одно condition + пакет действий;
* `level` нужен у `StrategyConditionRule` и у `StrategyAction`, но не у самого step;
* grid выражается не через отдельный `gridSettings`, а через несколько `StrategyOrderAction` с разным `StrategyPricePlacement`;
* partial exits выражаются несколькими `StrategyAlgoOrderAction` в одном шаге;
* breakeven и exit by efficiency — это отдельные steps, а не settings-объекты.
