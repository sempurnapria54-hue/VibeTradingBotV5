# Модель стратегии

> Статус документа: актуальная версия strategy-layer.
>
> Эта дока описывает только модель стратегии: правила, условия, настройки рыночных данных и ожидаемые действия.
>
> Runtime-процессы вынесены в отдельные документы:
>
> * `01. Жизненный цикл сделки`
> * `02. Сервисные команды`
> * `03. Калькуляторы действий стратегии`
> * `04. Расчёт индикаторов и рыночных данных`
> * `05. Аудит и история исполнения`

---

# Главная идея

* **Стратегия** хранит торговые правила, условия, настройки расчёта рыночных данных и ожидаемые действия.
* **FSM сделки** управляет runtime-сделкой и её этапами.
* **Стратегия не хранит сервисные команды** и не пытается управлять runtime-сущностями напрямую.
* Стратегия говорит **что** должно быть создано / изменено / отменено и **при каких условиях**.
* Стратегия говорит, какие индикаторы, структуры рынка и фазы рынка должны быть заранее подготовлены.
* Стейт-машина и orchestration-слой решают, **когда именно** интерпретировать эти правила.
* `StrategyActionCalculator` рассчитывает runtime-параметры действия стратегии: цену, размер и риск.
* `ServiceCommandFactory` превращает рассчитанное действие в атомарные `ServiceCommand`.
* `Order`, `AlgoOrder`, `Position` остаются чистыми runtime-сущностями биржи и не хранят `strategyActionId`.
* У каждого `StrategyAction` есть явный `key`, заданный в JSON стратегии.
* Для `AMEND` / `CANCEL` используется `targetActionKey`, который ссылается на `key` другого action в той же `StrategyDetail`.
* При сохранении стратегии `targetActionKey` валидируется и резолвится во внутреннюю ссылку на target action.
* Runtime-связь действия стратегии с runtime-сущностью хранится в `DealActionState` через `strategyActionId` и `RuntimeTarget`.
* Аудит и история исполнения фиксируют факты, но не являются источником runtime-логики FSM.

## Архитектурные инварианты strategy-layer

Эта часть фиксирует не историю обсуждения, а текущие правила модели стратегии.

* `Strategy.ACTIVE` — административное разрешение стратегии к работе. Это не гарантия, что прямо сейчас можно открыть сделку.
* `Strategy.INACTIVE` блокирует только новые сделки. Уже открытые сделки продолжают сопровождаться по pinned `StrategyDetail`.
* `Strategy.DELETED` блокирует новые сделки и переводит уже открытые сделки в graceful shutdown.
* Устаревшие рыночные данные не меняют `Strategy.Status`. Свежесть проверяется runtime-сервисом `MarketDataExpirationChecker`.
* Срок свежести данных задаётся через `expirationDuration` в `StrategyIndicatorSetting`, `StrategyMarketStructureSetting`, `StrategyMarketPhaseSetting`.
* Поведение при устаревании данных задаётся на уровне `StrategyStep.marketDataExpiredSetting`.
* `Deal.entryReason` хранит короткую причину создания сделки. Подробный entry context фиксируется в `05. Аудит и история исполнения`.
* `Deal.entryStepType` хранит только `ENTRY`, `GRID_ENTRY` или `null`. Он не управляет runtime-логикой FSM.
* `PROTECTION_SWITCHED` используется только если реально выполняется замена temporary attached protection на standalone main protection.
* `ServiceCommand` — runtime object. Стратегия не хранит сервисные команды и не является command queue.
* `Order`, `AlgoOrder`, `Position` не хранят `strategyActionId`, `strategyActionKey`, role или level стратегии.
* Runtime-связь `StrategyAction -> Order / AlgoOrder / Position` хранится в `DealActionState` через `strategyActionId` и `RuntimeTarget`.
* Direct partial close позиции запрещён как постоянный инвариант стратегии и приложения.
* `StrategyPositionAction` поддерживает только полное закрытие позиции через `CLOSE_FULL`.
* Любое частичное уменьшение позиции выражается только через трассируемые `StrategyOrderAction` / `StrategyAlgoOrderAction` с reduce-only semantics, `DealActionState`, stable client id и recovery через fills/history/refresh.

---

# 1. Общие правила модели

## 1.1. Стратегия immutable

Стратегия создаётся как immutable-конфигурация.

Если нужно изменить правила стратегии, создаётся новая стратегия, а не редактируется существующая.

Это относится к:

* `Strategy`;
* `StrategyDetail`;
* `StrategyMarketPhaseSetting`;
* `StrategyIndicatorSetting`;
* `StrategyMarketStructureSetting`;
* `MarketPhaseParams`;
* `IndicatorParams`;
* `MarketStructureParams`;
* `StrategyStep`;
* `StrategyMarketDataExpiredSetting`;
* `StrategyCondition`;
* `StrategyConditionRule`;
* `StrategyAction` и его подтипам.

## 1.2. Все хранимые модели наследуются от Auditable

Все классы, которые сохраняются в БД, должны наследоваться от `Auditable`.

Это нужно для единого аудита технических дат создания и обновления.

## 1.3. Жизненный цикл задаёт Strategy.Status

У вложенных immutable-настроек не нужны отдельные статусы.

Жизненный цикл всей стратегии задаёт `Strategy.Status`.

`Strategy.Status` описывает административное состояние стратегии, а не runtime-свежесть рыночных данных.

Если стратегия `INACTIVE`, новые сделки по ней не создаются.

Уже открытые сделки продолжают жить по pinned `StrategyDetail`.

Если стратегия `DELETED`, новые расчёты и новые сделки по ней не запускаются.

Уже открытые сделки переводятся в graceful shutdown.

Устаревшие свечи, индикаторы, структура рынка или фаза рынка не меняют `Strategy.Status`.

Для этого используется runtime-проверка свежести данных через `MarketDataExpirationChecker` и политика `StrategyStep.marketDataExpiredSetting`.

## 1.4. key нужен только у StrategyAction

`key` нужен у `StrategyAction`, потому что через него работает `targetActionKey`.

В settings `key` не используем:

* `StrategyIndicatorSetting` — без `key`;
* `StrategyMarketStructureSetting` — без `key`;
* `StrategyMarketPhaseSetting` — без `key`.

Связи между settings делаются объектными ссылками.

## 1.5. Domain и Entity работают объектами

В доменной модели и JPA Entity используем объектные связи.

В БД это будет храниться через FK / join-table.

Для загрузки стратегии целиком нужны отдельные repository-методы с `JOIN FETCH` или `@EntityGraph`.

Подробности по runtime-загрузке стратегии и pinned `StrategyDetail` см. в документе:

```text
01. Жизненный цикл сделки
```

---

# 2. Strategy

`Strategy` — главный immutable-контейнер торговой стратегии.

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
     * Статус контейнера стратегии.
     *
     * Статус стратегии управляет жизненным циклом всех вложенных immutable-настроек.
     *
     * Важно:
     * status не описывает runtime-свежесть рыночных данных.
     * Устаревшие данные обрабатываются через MarketDataExpirationChecker
     * и StrategyStep.marketDataExpiredSetting.
     */
    private Status status;

    /**
     * Настройка расчёта рыночной фазы.
     *
     * Одна настройка описывает алгоритм классификации рынка,
     * который может вернуть разные MarketPhase.Type:
     * BULL_TREND, BEAR_TREND, RANGE, UNKNOWN.
     */
    private StrategyMarketPhaseSetting marketPhaseSetting;

    /**
     * Ровно одна detail на одну фазу рынка.
     *
     * EntryScannerJob выбирает detail по результату MarketPhaseJob:
     * MarketPhase.Type -> StrategyDetail.marketPhaseType.
     */
    private List<StrategyDetail> details;

    public enum Status {

        /**
         * Стратегия создана, но ещё не введена в активное использование.
         */
        CREATED,

        /**
         * Единственная активная стратегия инструмента.
         *
         * Новые сделки по инструменту могут создаваться только по активной стратегии,
         * если рыночные данные не устарели, условия входа выполнены,
         * а risk/invariant checks пройдены.
         *
         * ACTIVE не означает, что прямо сейчас можно открыть сделку.
         * Это административное разрешение стратегии к работе.
         */
        ACTIVE,

        /**
         * Стратегия существует, но временно не участвует в создании новых сделок.
         *
         * Уже открытые сделки продолжают жить по pinned-версии StrategyDetail.
         */
        INACTIVE,

        /**
         * Логически удалённая стратегия.
         *
         * Новые сделки по ней не создаются.
         * Уже открытые сделки переводятся в graceful shutdown.
         */
        DELETED
    }
}
```

## 2.1. Как передать настройки определения нескольких фаз рынка

Отдельная сущность для нескольких phase profiles не нужна.

Одна `StrategyMarketPhaseSetting` описывает алгоритм, который умеет классифицировать рынок в несколько фаз:

```text
StrategyMarketPhaseSetting
  -> IndicatorSettings: EMA, MACD, ATR, Bollinger Bands
  -> MarketStructureSettings: RANGE, UPTREND, DOWNTREND
  -> MarketPhaseParams:
       algorithmType
       minTrendScore
       minRangeScore
       confirmationBars
```

`MarketPhaseJob` применяет эту настройку и сохраняет один актуальный результат:

```text
MarketPhase.Type = BULL_TREND | BEAR_TREND | RANGE | UNKNOWN
```

После этого `EntryScannerJob` выбирает detail:

```text
MarketPhase.Type
  -> StrategyDetail.marketPhaseType
```

Инвариант:

```text
внутри одной Strategy должна быть не более одной StrategyDetail на один MarketPhase.Type
```

Подробности по `MarketPhaseJob`, хранению `MarketPhase` и freshness-проверкам см. в документе:

```text
04. Расчёт индикаторов и рыночных данных
```

Подробности по тому, как `EntryScannerJob` выбирает `StrategyDetail`, см. в документе:

```text
01. Жизненный цикл сделки
```

---

# 3. StrategyMarketPhaseSetting

`StrategyMarketPhaseSetting` — настройка стратегии, по которой `MarketPhaseJob` считает фазу рынка.

Живёт на уровне `Strategy`, потому что фаза рынка нужна **до выбора `StrategyDetail`**.

```java
public class StrategyMarketPhaseSetting extends Auditable {

    /**
     * Технический ID настройки.
     */
    private Long id;

    /**
     * Таймфрейм, на котором считается фаза рынка.
     */
    private TimeFrame timeframe;

    /**
     * Immutable-параметры расчёта фазы рынка.
     */
    private MarketPhaseParams params;

    /**
     * Настройки индикаторов, которые нужны для расчёта фазы рынка.
     *
     * Например:
     * - EMA для определения направления тренда;
     * - MACD для подтверждения импульса;
     * - ATR для оценки волатильности;
     * - Bollinger bandwidth для определения range / squeeze.
     */
    private List<StrategyIndicatorSetting> indicatorSettings;

    /**
     * Настройки структуры рынка, которые нужны для расчёта фазы рынка.
     *
     * Например:
     * - RANGE для определения боковика;
     * - UPTREND для подтверждения бычьей структуры;
     * - DOWNTREND для подтверждения медвежьей структуры.
     */
    private List<StrategyMarketStructureSetting> marketStructureSettings;

    /**
     * Сколько времени последняя рассчитанная фаза рынка считается свежей.
     *
     * Например:
     * PT5M  -> 5 минут;
     * PT15M -> 15 минут;
     * PT1H  -> 1 час.
     *
     * Если последняя MarketPhase старше expirationDuration,
     * MarketDataExpirationChecker считает её устаревшей.
     */
    private Duration expirationDuration;
}
```

---

# 4. MarketPhaseParams

`MarketPhaseParams` — immutable-параметры алгоритма расчёта фазы рынка.

```java
public class MarketPhaseParams extends Auditable {

    /**
     * Технический ID параметров.
     */
    private Long id;

    /**
     * Тип алгоритма расчёта фазы рынка.
     */
    private AlgorithmType algorithmType;

    /**
     * Минимальный score, чтобы признать рынок трендовым.
     */
    private BigDecimal minTrendScore;

    /**
     * Минимальный score, чтобы признать рынок диапазонным.
     */
    private BigDecimal minRangeScore;

    /**
     * Количество закрытых свечей для подтверждения смены фазы.
     */
    private Integer confirmationBars;

    public enum AlgorithmType {

        /**
         * Фаза определяется только по структуре рынка.
         */
        STRUCTURE_ONLY,

        /**
         * Фаза определяется только по значениям индикаторов.
         */
        INDICATORS_ONLY,

        /**
         * Фаза определяется по структуре рынка и подтверждается индикаторами.
         */
        STRUCTURE_AND_INDICATORS
    }
}
```

Подробности по `MarketPhaseJob`, `MarketPhase` и freshness-проверкам см. в документе:

```text
04. Расчёт индикаторов и рыночных данных
```

---

# 5. StrategyDetail

`StrategyDetail` — набор торговых правил для конкретной фазы рынка.

В доменной модели и в JSON детали стратегии группируются по `Deal.Status`, потому что это хорошо совпадает с текущей FSM сделки.

```java
public class StrategyDetail extends Auditable {

    /**
     * Технический ID БД.
     */
    private Long id;

    /**
     * Для какой фазы рынка работает detail.
     *
     * Фаза берётся из MarketPhase, рассчитанной по Strategy.marketPhaseSetting.
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
     * Настройки индикаторов, нужные уже после выбора StrategyDetail.
     *
     * Например:
     * - ATR для SL;
     * - RSI для ENTRY condition;
     * - EMA для фильтра сопровождения;
     * - Bollinger bandwidth для проверки волатильности.
     */
    private List<StrategyIndicatorSetting> indicatorSettings;

    /**
     * Настройки структуры рынка, нужные уже после выбора StrategyDetail.
     *
     * Например:
     * - RANGE_LOW / RANGE_HIGH для grid;
     * - SWING_LOW / SWING_HIGH для SL;
     * - SUPPORT / RESISTANCE для условий входа и выхода.
     */
    private List<StrategyMarketStructureSetting> marketStructureSettings;

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

## 5.1. PhaseEntryPolicy

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

## 5.2. Матрица допустимости

* `BULL_TREND` -> `FOLLOW_PHASE | CONTRARIAN | NO_TRADE`
* `BEAR_TREND` -> `FOLLOW_PHASE | CONTRARIAN | NO_TRADE`
* `RANGE` -> `GRID | NO_TRADE`
* `UNKNOWN` -> `NO_TRADE`

---

# 6. Волатильность в стратегии

Отдельная сущность `VolatilitySetting` на первом этапе не нужна.

Волатильность представляется через заранее рассчитанные индикаторы:

* `ATR`;
* `Bollinger Bands bandwidth`;
* при необходимости — другие future-индикаторы волатильности.

Если стратегия использует волатильность для расчёта фазы рынка, это описывается через:

```text
StrategyMarketPhaseSetting.indicatorSettings
  -> StrategyIndicatorSetting(indicatorType = ATR, destiny = MARKET_PHASE)
  -> StrategyIndicatorSetting(indicatorType = BOLLINGER_BANDS, destiny = MARKET_PHASE)
```

Если стратегия использует волатильность после выбора `StrategyDetail`, это описывается через:

```text
StrategyDetail.indicatorSettings
  -> StrategyIndicatorSetting(indicatorType = ATR, destiny = ACTION_PRICE / PROTECTION / ENTRY_CONDITION)
```

Калькулятор не должен считать волатильность по свечам в runtime.

Он должен читать готовые `IndicatorValue` через `IndicatorService`.

Подробности по `IndicatorJob`, `IndicatorValue`, warmup и freshness см. в документе:

```text
04. Расчёт индикаторов и рыночных данных
```

Если нужно учитывать текущий spread / bid / ask, это не волатильность в смысле индикатора.

Это runtime-цена из `MarketPriceData`, которую собирает `StrategyActionCalculator` перед расчётом.

Подробности по `MarketPriceData` и `CalculationContext` см. в документе:

```text
03. Калькуляторы действий стратегии
```

---

# 7. StrategyIndicatorSetting

`StrategyIndicatorSetting` — настройка стратегии для расчёта нужных `IndicatorValue`.

Такая настройка может использоваться:

* внутри `StrategyMarketPhaseSetting` — если индикатор нужен для расчёта фазы;
* внутри `StrategyDetail` — если индикатор нужен после выбора детали.

```java
public class StrategyIndicatorSetting extends Auditable {

    /**
     * Технический ID настройки.
     */
    private Long id;

    /**
     * Таймфрейм индикатора.
     */
    private TimeFrame timeframe;

    /**
     * Тип индикатора.
     */
    private IndicatorValue.Type indicatorType;

    /**
     * Immutable-параметры индикатора.
     */
    private IndicatorParams params;

    /**
     * Назначение настройки.
     *
     * Отвечает на вопрос: для чего стратегии нужен этот индикатор.
     */
    private Destiny destiny;

    /**
     * Сколько времени последнее значение индикатора считается свежим.
     *
     * Если последнее IndicatorValue старше expirationDuration,
     * MarketDataExpirationChecker считает его устаревшим.
     */
    private Duration expirationDuration;

    public enum Destiny {

        /**
         * Индикатор нужен для расчёта MarketPhase.
         */
        MARKET_PHASE,

        /**
         * Индикатор нужен для условия входа.
         */
        ENTRY_CONDITION,

        /**
         * Индикатор нужен для расчёта цены action.
         *
         * Например ATR для SL.
         */
        ACTION_PRICE,

        /**
         * Индикатор нужен для расчёта защиты.
         */
        PROTECTION,

        /**
         * Индикатор нужен для условия выхода.
         */
        EXIT_CONDITION
    }
}
```

---

# 8. IndicatorParams

`IndicatorParams` — immutable-параметры расчёта индикатора.

```java
public abstract class IndicatorParams extends Auditable {

    /**
     * Технический ID параметров.
     */
    private Long id;

    /**
     * Тип индикатора.
     */
    private IndicatorValue.Type indicatorType;
}
```

Примеры наследников:

```java
public class AtrParams extends IndicatorParams {

    /**
     * Период ATR.
     */
    private Integer period;
}
```

```java
public class EmaParams extends IndicatorParams {

    /**
     * Период EMA.
     */
    private Integer period;
}
```

```java
public class RsiParams extends IndicatorParams {

    /**
     * Период RSI.
     */
    private Integer period;
}
```

```java
public class MacdParams extends IndicatorParams {

    /**
     * Быстрый период MACD.
     */
    private Integer fastPeriod;

    /**
     * Медленный период MACD.
     */
    private Integer slowPeriod;

    /**
     * Период signal line.
     */
    private Integer signalPeriod;
}
```

```java
public class BollingerBandsParams extends IndicatorParams {

    /**
     * Период Bollinger Bands.
     */
    private Integer period;

    /**
     * Множитель стандартного отклонения.
     */
    private BigDecimal deviationMultiplier;
}
```

```java
public class StochasticParams extends IndicatorParams {

    /**
     * Период %K.
     */
    private Integer kPeriod;

    /**
     * Период %D.
     */
    private Integer dPeriod;

    /**
     * Сглаживание.
     */
    private Integer smoothPeriod;
}
```

```java
public class ObvParams extends IndicatorParams {

    /**
     * Пока дополнительных параметров нет.
     */
    private Boolean enabled;
}
```

---

# 9. StrategyMarketStructureSetting

`StrategyMarketStructureSetting` — настройка стратегии для расчёта нужной структуры рынка.

Такая настройка может использоваться:

* внутри `StrategyMarketPhaseSetting` — если структура нужна для расчёта фазы;
* внутри `StrategyDetail` — если структура нужна после выбора детали.

```java
public class StrategyMarketStructureSetting extends Auditable {

    /**
     * Технический ID настройки.
     */
    private Long id;

    /**
     * Таймфрейм, на котором должна рассчитываться структура рынка.
     */
    private TimeFrame timeframe;

    /**
     * Тип структуры рынка, которую нужно подготовить.
     */
    private MarketStructure.Type structureType;

    /**
     * Immutable-параметры расчёта структуры рынка.
     */
    private MarketStructureParams params;

    /**
     * Назначение настройки.
     *
     * Отвечает на вопрос: для чего стратегии нужна эта структура рынка.
     */
    private Destiny destiny;

    /**
     * Сколько времени последняя рассчитанная структура рынка считается свежей.
     *
     * Если последняя MarketStructure старше expirationDuration,
     * MarketDataExpirationChecker считает её устаревшей.
     */
    private Duration expirationDuration;

    public enum Destiny {

        /**
         * Структура нужна для расчёта MarketPhase.
         */
        MARKET_PHASE,

        /**
         * Структура нужна для проверки условий входа.
         */
        ENTRY_CONDITION,

        /**
         * Структура нужна для расчёта цены action.
         *
         * Например grid от RANGE_LOW / RANGE_HIGH.
         */
        ACTION_PRICE,

        /**
         * Структура нужна для расчёта защиты.
         *
         * Например SL за SWING_LOW / SWING_HIGH.
         */
        PROTECTION,

        /**
         * Структура нужна для условий выхода.
         */
        EXIT_CONDITION
    }
}
```

---

# 10. MarketStructureParams

`MarketStructureParams` — immutable-параметры расчёта структуры рынка.

```java
public class MarketStructureParams extends Auditable {

    /**
     * Технический ID параметров.
     */
    private Long id;

    /**
     * Размер окна свечей для анализа.
     */
    private Integer lookbackBars;

    /**
     * Минимальное количество касаний уровня.
     */
    private Integer minTouches;

    /**
     * Минимальная ширина range в процентах.
     */
    private BigDecimal minRangeWidthPercents;

    /**
     * Максимальная ширина range в процентах.
     */
    private BigDecimal maxRangeWidthPercents;

    /**
     * Буфер подтверждения пробоя.
     *
     * Например 15 = 15% от ширины диапазона.
     */
    private BigDecimal breakoutBufferPercents;

    /**
     * Количество закрытых свечей для подтверждения пробоя.
     */
    private Integer breakoutConfirmationBars;

    /**
     * Окно для поиска swing high / swing low.
     */
    private Integer swingLookbackBars;
}
```

Подробности по `MarketStructureJob`, `MarketStructure` и `MarketPriceLevel` см. в документе:

```text
04. Расчёт индикаторов и рыночных данных
```

---

# 11. StrategyStep

Один `StrategyStep` =

* одно общее условие применимости;
* пакет действий, который нужно выполнить целиком, если условие истинно.

```java
public class StrategyStep extends Auditable {

    /**
     * Технический ID шага.
     */
    private Long id;

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

    /**
     * Обязательная политика поведения, если данные,
     * нужные именно этому step, устарели или отсутствуют.
     *
     * Проверяются только те данные, которые реально нужны step:
     * - StrategyConditionRule.indicatorSetting;
     * - StrategyConditionRule.marketStructureSetting;
     * - StrategyOrderAction.placement.marketStructureSetting;
     * - StrategyAlgoOrderAction.stopLossSettings.indicatorSetting;
     * - StrategyAlgoOrderAction.stopLossSettings.marketStructureSetting;
     * - StrategyMarketPhaseSetting, если step зависит от MarketPhase.
     */
    private StrategyMarketDataExpiredSetting marketDataExpiredSetting;
}
```

## 11.1. StrategyStepType

```java
public enum StrategyStepType {

    /**
     * Вход в сделку.
     *
     * На этапе поиска входа этот step проверяется EntryScannerJob.
     * После создания Deal FSM может использовать entry-step в PRECHECK,
     * чтобы создать entry order.
     *
     * Если сделка создана через этот step:
     * - Deal.entryReason = STRATEGY;
     * - Deal.entryStepType = ENTRY.
     *
     * PRECHECK имеет право повторно проверить ENTRY condition
     * перед созданием live risk.
     * Если condition стал false и live risk ещё не создан,
     * Deal закрывается с closeReason = ENTRY_CONDITION_EXPIRED.
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
     *
     * Важно:
     * partial exit не выполняется через direct position action.
     * Частичное уменьшение позиции всегда выражается через
     * reduce-only StrategyOrderAction или StrategyAlgoOrderAction.
     */
    PARTIAL_EXIT,

    /**
     * Grid-входы во флэте.
     *
     * Если сделка создана через этот step:
     * - Deal.entryReason = STRATEGY;
     * - Deal.entryStepType = GRID_ENTRY.
     *
     * PRECHECK имеет право повторно проверить GRID_ENTRY condition
     * перед созданием live risk.
     * Если condition стал false и live risk ещё не создан,
     * Deal закрывается с closeReason = ENTRY_CONDITION_EXPIRED.
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

## 11.2. Связь StrategyStepType с Deal.entryStepType

`Deal.entryStepType` хранит только тип entry-step, по которому была создана сделка.

Допустимые значения:

```text
ENTRY
GRID_ENTRY
null
```

Правила:

```text
StrategyStepType.ENTRY
  -> Deal.entryReason = STRATEGY
  -> Deal.entryStepType = ENTRY

StrategyStepType.GRID_ENTRY
  -> Deal.entryReason = STRATEGY
  -> Deal.entryStepType = GRID_ENTRY

Manual / recovery / unknown creation
  -> Deal.entryReason = MANUAL / RECOVERY / UNKNOWN
  -> Deal.entryStepType = null или known ENTRY / GRID_ENTRY, если это точно восстановлено по фактам
```

Остальные `StrategyStepType` не могут быть значением `Deal.entryStepType`:

```text
MAIN_PROTECTION
PROTECTION_ADJUSTMENT
PARTIAL_EXIT
GRID_MANAGEMENT
EXIT
FAIL_SAFE
```

Причина:

> `entryStepType` отвечает только на вопрос, какой entry-step создал Deal.
>
> Он не управляет FSM и не является источником runtime-логики.

## 11.3. StrategyMarketDataExpiredSetting

`StrategyMarketDataExpiredSetting` определяет, что делать, если данные для конкретного `StrategyStep` устарели или отсутствуют.

Он не определяет, когда данные устарели.

Срок свежести задаётся рядом с настройкой данных:

```text
StrategyIndicatorSetting.expirationDuration
StrategyMarketStructureSetting.expirationDuration
StrategyMarketPhaseSetting.expirationDuration
```

Runtime-проверку выполняет `MarketDataExpirationChecker`.

```java
public class StrategyMarketDataExpiredSetting extends Auditable {

    /**
     * Что делать, если данные устарели,
     * но позиция защищена активной защитой.
     */
    private MarketDataExpiredAction protectedPositionAction;

    /**
     * Что делать, если данные устарели,
     * а позиция не защищена.
     */
    private MarketDataExpiredAction unprotectedPositionAction;
}
```

```java
public enum MarketDataExpiredAction {

    /**
     * Ждать свежих данных.
     *
     * Step не выполняется, но сделка остаётся в текущем состоянии.
     */
    WAIT,

    /**
     * Заблокировать выполнение этого StrategyStep.
     *
     * Refresh / cancel / close / safety commands остаются разрешены,
     * но actions этого step не выполняются.
     */
    BLOCK_STEP,

    /**
     * Начать мягкое закрытие сделки.
     */
    GRACEFUL_CLOSE,

    /**
     * Немедленно снять риск через kill-switch.
     */
    KILL_SWITCH
}
```

Важно:

> `marketDataExpiredSetting` обязателен для каждого `StrategyStep`.
>
> Default-policy на уровне `StrategyDetail` не используем, потому что разные steps могут зависеть от разных данных и разных таймфреймов.

## 11.4. Связь StrategyStep с PROTECTION_SWITCHED

`PROTECTION_SWITCHED` не является обязательным этапом для всех сделок.

Стратегия может предусматривать разные сценарии:

```text
1. Temporary attached protection заменяется на standalone main protection.
2. Attached protection остаётся основной защитой.
3. Main protection создаётся отдельным action без switch-сценария.
4. Стратегия / risk policy явно разрешает сопровождение без обязательной protection-замены.
```

Поэтому `StrategyStep` определяет, нужен ли protection switch фактически.

Если strategy steps не требуют замены temporary attached protection на main protection, FSM не должна искусственно переводить сделку в `PROTECTION_SWITCHED`.

Подробная логика переходов описана в документе:

```text
FSM этапы сделки
```

---

# 12. StrategyCondition

`StrategyCondition` — это набор rules.

Все rules внутри одного condition должны быть истинны.

## 12.1. Порядок проверки rules

Если нужен детерминированный порядок проверки rules, то он задаётся полем `level` у `StrategyConditionRule`.

Это **не** глобальный порядок шагов стратегии, а только локальный порядок проверки правил внутри одного condition.

```java
public class StrategyCondition extends Auditable {

    /**
     * Rules проверяются по level ASC.
     */
    private List<StrategyConditionRule> rules;
}
```

## 12.2. StrategyConditionRule

```java
public class StrategyConditionRule extends Auditable {

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
     * Если правило не зависит от таймфрейма, поле может быть null.
     */
    private TimeFrame timeframe;

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
     * Если rule использует конкретную настройку индикатора,
     * то ссылка хранится объектом.
     *
     * Примеры:
     * - RSI для INDICATOR_COMPARE;
     * - EMA для CROSSOVER;
     * - ATR для фильтра волатильности.
     */
    private StrategyIndicatorSetting indicatorSetting;

    /**
     * Если rule использует конкретную структуру рынка,
     * то ссылка хранится объектом.
     *
     * Примеры:
     * - RANGE_HIGH для breakout;
     * - RANGE_LOW для grid-entry;
     * - SWING_LOW / SWING_HIGH для условий защиты.
     */
    private StrategyMarketStructureSetting marketStructureSetting;

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
}
```

---

# 13. StrategyConditionRuleType

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

---

# 14. StrategyConditionSourceType

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

---

# 15. StrategyConditionOperator

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

---

# 16. StrategyConditionOperand

```java
public class StrategyConditionOperand extends Auditable {

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
}
```

---

# 17. StrategyAction

Действия делаются типизированными.

Это лучше, чем один плоский объект с большим количеством nullable-полей.

```java
public interface StrategyAction {

    /**
     * Стабильный ключ action внутри StrategyDetail.
     *
     * Задаётся явно в JSON при создании стратегии.
     * Нужен для ссылок между actions и для первичной валидации targetActionKey.
     */
    String getKey();
}
```

Важно:

> `StrategyAction` — это не `ServiceCommand`.
>
> `StrategyAction` описывает ожидаемое действие стратегии.
>
> Runtime-сущность, созданная или изменённая по этому action, связывается через `DealActionState`.

Подробности по `DealActionState` и `ServiceCommand` см. в документе:

```text
02. Сервисные команды
```

---

# 18. StrategyOrderAction

Обычный ордер.

Attached protection встроена внутрь order-action.

```java
public class StrategyOrderAction extends Auditable implements StrategyAction {

    /**
     * Стабильный ключ action внутри StrategyDetail.
     *
     * Задаётся явно в JSON при создании стратегии.
     * Должен быть уникален в рамках одной StrategyDetail.
     */
    private String key;

    /**
     * Для AMEND / CANCEL: key action, который создал target order.
     *
     * Для CREATE должен быть null.
     * При сохранении стратегии валидируется и резолвится во внутреннюю ссылку
     * на target action.
     */
    private String targetActionKey;

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
     * Признак, что order должен только уменьшать существующую позицию.
     *
     * Обязателен для partial exit / partial take profit через ordinary order.
     * Такой action не должен открывать или увеличивать позицию.
     */
    private Boolean reduceOnly;

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

---

# 19. StrategyTradeDirection

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

# 20. StrategyPricePlacement

Если мы отказались от отдельного `gridSettings`, то параметры позиционирования цены grid-ордеров должны жить в `StrategyOrderAction`.

```java
public class StrategyPricePlacement extends Auditable {

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
     * Для RANGE_LOW / RANGE_HIGH / SWING_LOW / SWING_HIGH / ENTRY_PRICE обычно null.
     */
    private StrategyPriceSource priceSource;

    /**
     * Настройка структуры рынка, если baseType берётся из MarketStructure.
     *
     * Обязательна для:
     * RANGE_LOW,
     * RANGE_HIGH,
     * SWING_LOW,
     * SWING_HIGH,
     * SUPPORT,
     * RESISTANCE.
     *
     * Для ENTRY_PRICE и MARKET_PRICE обычно null.
     */
    private StrategyMarketStructureSetting marketStructureSetting;

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

## 20.1. StrategyPriceBaseType

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
     * Последний значимый swing low.
     *
     * Используется как структурный уровень,
     * от которого можно считать цену входа, stop-loss или защитный буфер.
     *
     * Примеры:
     * - поставить LONG stop-loss ниже swing low;
     * - поставить отложенный вход около swing low;
     * - проверить, что цена не пробила локальную структуру.
     */
    SWING_LOW,

    /**
     * Последний значимый swing high.
     *
     * Используется как структурный уровень,
     * от которого можно считать цену входа, stop-loss или защитный буфер.
     *
     * Примеры:
     * - поставить SHORT stop-loss выше swing high;
     * - поставить отложенный вход около swing high;
     * - проверить, что цена не пробила локальную структуру.
     */
    SWING_HIGH,

    /**
     * Уровень поддержки.
     *
     * Используется как опорный уровень для входа, выхода или фильтра.
     */
    SUPPORT,

    /**
     * Уровень сопротивления.
     *
     * Используется как опорный уровень для входа, выхода или фильтра.
     */
    RESISTANCE,

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

## 20.2. StrategyPriceSource

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

## 20.3. StrategyPriceOffsetSide

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

## 20.4. Почему это отдельные модели

* `StrategyTradeDirection` отвечает за **торговое направление**: `LONG / SHORT`.
* `StrategyPriceOffsetSide` отвечает за **геометрию смещения цены**: `ABOVE / BELOW`.
* `StrategyPriceSource` отвечает за **источник рыночной цены**: `LAST_PRICE / MARK_PRICE / INDEX_PRICE / BID / ASK / MID`.
* `TriggerPriceType` отвечает за **тип trigger-цены на бирже** для algo-orders: `LAST / INDEX / MARK`.

Это разные смыслы, поэтому их не надо смешивать.

Подробности по расчёту цены см. в документе:

```text
03. Калькуляторы действий стратегии
```

---

# 21. StrategyAttachedProtectionSettings

```java
public class StrategyAttachedProtectionSettings extends Auditable {

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

# 22. StrategyAlgoOrderAction

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
public class StrategyAlgoOrderAction extends Auditable implements StrategyAction {

    /**
     * Стабильный ключ action внутри StrategyDetail.
     *
     * Задаётся явно в JSON при создании стратегии.
     * Должен быть уникален в рамках одной StrategyDetail.
     */
    private String key;

    /**
     * Для AMEND / CANCEL: key action, который создал target algo-order.
     *
     * Для CREATE должен быть null.
     * При сохранении стратегии валидируется и резолвится во внутреннюю ссылку
     * на target action.
     */
    private String targetActionKey;

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

# 23. StrategyPositionAction

Действие над позицией.

`StrategyPositionAction` предназначен только для полного закрытия позиции.

Важно:

```text
StrategyPositionAction.CLOSE_PARTIAL запрещён как постоянный инвариант стратегии и приложения.
```

Частичное уменьшение позиции не является `StrategyPositionAction`.
Оно всегда выражается через трассируемые `StrategyOrderAction` / `StrategyAlgoOrderAction` с reduce-only semantics.

```java
public class StrategyPositionAction extends Auditable implements StrategyAction {

    /**
     * Стабильный ключ action внутри StrategyDetail.
     *
     * Задаётся явно в JSON при создании стратегии.
     * Должен быть уникален в рамках одной StrategyDetail.
     */
    private String key;

    /**
     * Только CLOSE_FULL.
     */
    private StrategyActionType actionType;

    /**
     * Уровень действия.
     *
     * Используется, если в одном step есть несколько position actions.
     */
    private Integer level;
}
```

---

# 24. Общий action type

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
     * - создать partial take profit уровень через Order / AlgoOrder.
     */
    CREATE,

    /**
     * Изменить уже существующую сущность.
     *
     * Примеры:
     * - передвинуть stop-loss ближе к entry;
     * - обновить основную защиту;
     * - включить или перестроить trailing;
     * - изменить параметры уже существующего order/algo-order.
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
     * Используется только для StrategyPositionAction.
     * Частичное закрытие позиции через StrategyPositionAction запрещено.
     */
    CLOSE_FULL
}
```

---

# 25. Семантика общего StrategyActionType

Важно явно зафиксировать допустимые значения по подтипам action:

* `StrategyOrderAction` использует только: `CREATE`, `AMEND`, `CANCEL`.
* `StrategyAlgoOrderAction` использует только: `CREATE`, `AMEND`, `CANCEL`.
* `StrategyPositionAction` использует только: `CLOSE_FULL`.

То есть общий enum один, но допустимые значения валидируются по конкретному подтипу действия.

Инвариант:

```text
CLOSE_PARTIAL не является допустимым StrategyActionType.
Частичное уменьшение позиции всегда моделируется через Order / AlgoOrder action с reduce-only semantics.
```

---

# 26. Семантика actionKind в JSON

В JSON-примерах может использоваться поле `actionKind` со значениями:

* `ORDER`
* `ALGO_ORDER`
* `POSITION`

Это не отдельное поле доменной модели, а **JSON discriminator**, который нужен только для сериализации / десериализации и выбора конкретного подтипа `StrategyAction`.

---

# 27. Семантика key и targetActionKey

## 27.1. key

`key` — стабильный ключ action внутри одной `StrategyDetail`.

Он задаётся явно в JSON при создании стратегии.

Пример:

```jsonc
{
  "actionKind": "ORDER",
  "key": "grid-entry-long-1",
  "actionType": "CREATE",
  "orderType": "ENTRY",
  "direction": "LONG",
  "level": 1
}
```

`key` нужен для:

* валидации стратегии;
* ссылок из `AMEND` / `CANCEL` actions на ранее описанные actions;
* резолва `targetActionKey` во внутреннюю ссылку на target action;
* читаемого debug/timeline.

## 27.2. targetActionKey

`targetActionKey` — это ключ action, который создал runtime-сущность, над которой нужно выполнить `AMEND` или `CANCEL`.

Пример:

```jsonc
{
  "actionKind": "ORDER",
  "key": "cancel-grid-entry-long-1",
  "actionType": "CANCEL",
  "targetActionKey": "grid-entry-long-1",
  "orderType": "ENTRY",
  "direction": "LONG",
  "level": 1
}
```

При сохранении стратегии:

```text
targetActionKey
  -> валидируется внутри StrategyDetail
  -> резолвится во внутреннюю ссылку на target action
```

Runtime-логика после сохранения стратегии:

```text
AMEND / CANCEL action
  -> имеет ссылку на target StrategyAction
  -> находит DealActionState(dealId, target strategyActionId)
  -> получает RuntimeTarget
  -> создаёт ServiceCommand с конкретным orderId / algoOrderId
```

## 27.3. Валидация

При создании стратегии нужно проверить:

1. `key` обязателен у каждого `StrategyAction`.
2. `key` уникален в рамках одной `StrategyDetail`.
3. `targetActionKey` должен ссылаться на существующий `action.key` в той же `StrategyDetail`.
4. `targetActionKey` обязателен для `AMEND` и `CANCEL` у `ORDER` / `ALGO_ORDER` actions.
5. `CREATE` не должен иметь `targetActionKey`.
6. `ORDER AMEND` / `ORDER CANCEL` должны ссылаться на `ORDER CREATE`.
7. `ALGO_ORDER AMEND` / `ALGO_ORDER CANCEL` должны ссылаться на `ALGO_ORDER CREATE`.
8. Нельзя ссылаться на action из другой `StrategyDetail`.
9. `StrategyPositionAction.actionType` должен быть только `CLOSE_FULL`.
10. Direct partial close через `StrategyPositionAction` запрещён всегда.
11. Partial exit должен быть выражен через `StrategyOrderAction` или `StrategyAlgoOrderAction` с reduce-only semantics.
12. Partial exit action не должен открывать или увеличивать позицию.

Подробности по runtime-связи `StrategyAction -> DealActionState -> RuntimeTarget -> ServiceCommand` см. в документе:

```text
02. Сервисные команды
```

---

# 28. Семантика placement для StrategyOrderAction

`placement` используется так:

* при `actionType = CREATE` — для расчёта цены нового ордера;
* при `actionType = AMEND` — как новая целевая схема позиционирования цены, если стратегия действительно перестраивает ордер;
* при `actionType = CANCEL` — обычно цена не нужна, а нужная runtime-сущность определяется через `targetActionKey -> target StrategyAction -> DealActionState`.

Важно:

> `placement` не должен быть основным способом идентификации runtime-сущности.
> Для связи action стратегии с runtime-сущностью используется `DealActionState`.

---

# 29. Семантика OCO_FULL в StrategyAlgoOrderAction

Если `conditionType = OCO_FULL`, то:

* stop-loss компонент строится из `stopLossSettings`;
* take-profit компонент строится из `triggerProfitPercents` и `triggerPriceType`;
* `closeFractionPercents` определяет, какую долю позиции закрывает OCO.

Для полного закрытия позиции обычно используется:

```text
closeFractionPercents = 100
```

---

# 30. StopLossSettings

`triggerPriceType` обязателен, потому что runtime trigger-based algo conditions реально завязаны на `TriggerPriceType`.

```java
public class StopLossSettings extends Auditable {

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

    /**
     * Настройка индикатора, если stop-loss считается от индикатора.
     *
     * Например:
     * - ATR для ATR_PERCENT.
     */
    private StrategyIndicatorSetting indicatorSetting;

    /**
     * Настройка структуры рынка, если stop-loss считается от уровня структуры.
     *
     * Например:
     * - SWING_LOW / SWING_HIGH;
     * - RANGE_LOW / RANGE_HIGH;
     * - SUPPORT / RESISTANCE.
     */
    private StrategyMarketStructureSetting marketStructureSetting;
}
```

## 30.1. StopLossCalculationType

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

# 31. TrailingSettings

`TrailingSettings` остаётся, потому что trailing реально выражается как отдельная форма algo condition.

```java
public class TrailingSettings extends Auditable {

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

# 32. Связь стратегии с DealActionState

Стратегия не хранит runtime-состояние выполнения.

Связь строится так:

```text
StrategyAction.key
  -> используется при создании стратегии для валидации и резолва targetActionKey

StrategyAction.id
  -> используется в runtime
  -> DealActionState.strategyActionId
     -> RuntimeTarget
        -> entityType
        -> entityId
```

Это нужно, чтобы:

* `Order`, `AlgoOrder`, `Position` оставались чистыми биржевыми сущностями;
* FSM могла восстановиться после рестарта;
* не хранить `strategyActionId` прямо в биржевых сущностях;
* не строить runtime-логику по аудиту.

Ключевые инварианты:

```text
UNIQUE(strategy_detail_id, key)
UNIQUE(deal_id, strategy_action_id)
```

Важно:

> `key` нужен для JSON/API и ссылок между actions внутри стратегии.
>
> Runtime работает через `strategyActionId`, а не через `strategyActionKey`.

Подробности см. в документе:

```text
02. Сервисные команды
```

---

# 33. Связь стратегии с калькуляторами

Стратегия хранит не готовые цены, размеры и риск, а правила их расчёта.

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

Если данные, нужные action / step, устарели и `StrategyStep.marketDataExpiredSetting` запрещает выполнение, `StrategyActionCalculator` не должен рассчитывать торговый action.

Safety-команды и cleanup-команды при этом остаются разрешены.

Подробности по `StrategyActionCalculator`, `CalculationContext`, `PriceCalculator`, `SizeCalculator`, `RiskCalculator`, `MarketPriceData` и `InstrumentExternalRules` см. в документе:

```text
03. Калькуляторы действий стратегии
```

---

# 34. Связь стратегии с расчётными job'ами

Стратегия может ссылаться на условия, которые проверяются по индикаторам, структуре рынка и фазе рынка.

Но стратегия сама не считает индикаторы, структуру и фазу.

Данные готовят:

* `IndicatorJob`;
* `MarketStructureJob`;
* `MarketPhaseJob`.

Их используют:

* `EntryScannerJob` — для поиска входа;
* `StrategyConditionEvaluator` — для проверки условий step;
* `StrategyActionCalculator` — для расчёта цены, размера и риска.

Свежесть результатов проверяется через `MarketDataExpirationChecker`.

Job'ы не меняют `Strategy.Status`.

Если новых закрытых свечей нет:

```text
job не создаёт новый result;
старый result остаётся в БД;
MarketDataExpirationChecker со временем считает его expired по expirationDuration.
```

Устаревшие рыночные данные не переводят стратегию в `INACTIVE` и не создают отдельный статус стратегии.

Общая связка:

```text
Strategy
  -> StrategyMarketPhaseSetting
     -> StrategyIndicatorSetting
     -> StrategyMarketStructureSetting

StrategyDetail
  -> StrategyIndicatorSetting
  -> StrategyMarketStructureSetting

IndicatorJob
  -> считает IndicatorValue

MarketStructureJob
  -> считает MarketStructure / MarketPriceLevel

MarketPhaseJob
  -> считает MarketPhase
```

Подробности по `IndicatorJob`, `IndicatorValue`, `MarketStructureJob`, `MarketStructure`, `MarketPriceLevel`, `MarketPhaseJob`, `MarketPhase` и freshness-проверкам см. в документе:

```text
04. Расчёт индикаторов и рыночных данных
```

Подробности по тому, как `EntryScannerJob` использует `MarketPhase` для выбора `StrategyDetail`, см. в документе:

```text
01. Жизненный цикл сделки
```

---

# 35. TimeFrame

`TimeFrame` — чистый доменный enum.

OKX-строки в нём не храним.

```java
public enum TimeFrame {

    ONE_MINUTE,
    THREE_MINUTES,
    FIVE_MINUTES,
    FIFTEEN_MINUTES,
    ONE_HOUR,
    TWO_HOURS,
    FOUR_HOURS,
    ONE_DAY
}
```

Маппинг OKX-строк живёт только в `TimeFrameMapper`:

```java
public class TimeFrameMapper {

    /**
     * Доменный TimeFrame -> строка OKX client/API.
     */
    public String domainToOkxClient(TimeFrame timeframe) {
        // TimeFrame.ONE_HOUR -> "1H"
    }

    /**
     * Строка OKX client/API -> доменный TimeFrame.
     */
    public TimeFrame okxClientToDomain(String externalCode) {
        // "1H" -> TimeFrame.ONE_HOUR
    }
}
```

Правила:

```text
маппинг строгий
без lowerCase / upperCase
TimeFrameResolver пока не нужен
```

Подробности по загрузке свечей и OKX timeframe см. в документе:

```text
04. Расчёт индикаторов и рыночных данных
```

---

# 36. Загрузка стратегии из БД

Связи в entity-моделях объектные, но по умолчанию должны быть `LAZY`.

Для рабочих сценариев нужны отдельные repository-методы.

## 36.1. Загрузка стратегии целиком

Пример через `@EntityGraph`:

```java
@EntityGraph(attributePaths = {
        "marketPhaseSetting",
        "marketPhaseSetting.params",
        "marketPhaseSetting.indicatorSettings",
        "marketPhaseSetting.indicatorSettings.params",
        "marketPhaseSetting.marketStructureSettings",
        "marketPhaseSetting.marketStructureSettings.params",
        "details",
        "details.indicatorSettings",
        "details.indicatorSettings.params",
        "details.marketStructureSettings",
        "details.marketStructureSettings.params",
        "details.stepsByStatus",
        "details.stepsByStatus.condition",
        "details.stepsByStatus.condition.rules",
        "details.stepsByStatus.actions"
})
Optional<StrategyEntity> findFullById(Long id);
```

## 36.2. Загрузка отдельной StrategyDetail целиком

Отдельный метод нужен, потому что `Deal` хранит pinned `StrategyDetail`.

```java
@EntityGraph(attributePaths = {
        "indicatorSettings",
        "indicatorSettings.params",
        "marketStructureSettings",
        "marketStructureSettings.params",
        "stepsByStatus",
        "stepsByStatus.condition",
        "stepsByStatus.condition.rules",
        "stepsByStatus.actions"
})
Optional<StrategyDetailEntity> findFullById(Long id);
```

Подробности по pinned `StrategyDetail` см. в документе:

```text
01. Жизненный цикл сделки
```

---

# 37. JSON-примеры

JSON-примеры можно хранить в отдельном файле:

```text
Strategy API examples.md
```

Это сделано, чтобы основной документ оставался сфокусирован именно на модели.

При обновлении JSON-примеров нужно учитывать:

* у каждого `StrategyAction` должен быть `key`;
* для `AMEND` / `CANCEL` у `ORDER` / `ALGO_ORDER` должен быть `targetActionKey`;
* `targetActionKey` должен ссылаться на `action.key` в той же `StrategyDetail`;
* settings не используют `key`;
* settings используют `expirationDuration`, а не старый `maxAgeBars`;
* у каждого `StrategyStep` должен быть `marketDataExpiredSetting`;
* одна `StrategyMarketPhaseSetting` описывает алгоритм классификации рынка во все поддерживаемые `MarketPhase.Type`;
* `Strategy.details` содержит максимум одну `StrategyDetail` на один `MarketPhase.Type`;
* `StrategyPricePlacement.priceSource` используется только для `MARKET_PRICE`;
* `StrategyPricePlacement.marketStructureSetting` используется для `RANGE_LOW`, `RANGE_HIGH`, `SWING_LOW`, `SWING_HIGH`, `SUPPORT`, `RESISTANCE`;
* `StopLossSettings.calculationType` — enum `StopLossCalculationType`;
* для `ATR_PERCENT` в `StopLossSettings` нужна `StrategyIndicatorSetting` с ATR;
* для `MARKET_STRUCTURE_BUFFER_PERCENT` нужна `StrategyMarketStructureSetting`;
* `StrategyStepType.ENTRY / GRID_ENTRY` влияют на `Deal.entryStepType`;
* `Deal.entryReason` при strategy-входе будет `STRATEGY`;
* `Order`, `AlgoOrder`, `Position` не хранят `strategyActionId`;
* runtime-связь action -> entity строится через `DealActionState.strategyActionId + RuntimeTarget`.

---

# 38. Связь с другими документами

## 38.1. Жизненный цикл сделки

Документ:

```text
01. Жизненный цикл сделки
```

Там описано:

* `EntryScannerJob`;
* выбор `StrategyDetail`;
* создание `Deal`;
* `Deal.entryReason`;
* `Deal.entryStepType`;
* `Deal.shutdownReason`;
* `DealContext`;
* FSM статусы сделки;
* graceful shutdown;
* восстановление после рестарта;
* роль `DealActionState` в runtime.

## 38.2. Сервисные команды

Документ:

```text
02. Сервисные команды
```

Там описано:

* `ServiceCommand`;
* `ServiceCommandType`;
* payload'ы команд;
* `CREATE -> SUBMIT -> REFRESH`;
* `DealActionState`;
* `RuntimeTarget`;
* retry policy;
* связь `targetActionKey -> target StrategyAction -> DealActionState`.

## 38.3. Калькуляторы действий стратегии

Документ:

```text
03. Калькуляторы действий стратегии
```

Там описано:

* `StrategyActionCalculator`;
* `CalculationContext`;
* `PriceCalculator`;
* `SizeCalculator`;
* `RiskCalculator`;
* `MarketPriceData`;
* `InstrumentExternalRules`;
* формулы расчёта цен и размеров.

## 38.4. Расчёт индикаторов и рыночных данных

Документ:

```text
04. Расчёт индикаторов и рыночных данных
```

Там описано:

* `IndicatorJob`;
* `IndicatorValue`;
* `MarketStructureJob`;
* `MarketStructure`;
* `MarketPriceLevel`;
* `MarketPhaseJob`;
* `MarketPhase`;
* `MarketPriceData`;
* `InstrumentExternalRules`;
* `MarketDataExpirationChecker`;
* freshness и idempotency расчётов.

## 38.5. Аудит и история исполнения

Документ:

```text
05. Аудит и история исполнения
```

Там описываются:

* история исполнения сервисных команд;
* история изменений runtime-сущностей;
* timeline сделки;
* entry context;
* shutdownReason;
* protection switch timeline;
* связь аудита с `Deal`, `Order`, `AlgoOrder`, `Position`, `Balance`.

Важно:

> Аудит не является источником runtime-логики FSM.


---
