package com.example.strategy.engine.unit.calc;

import static com.example.strategy.engine.unit.calc.CalcFixture.ASK_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.BID_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.LAST_PRICE;
import static com.example.strategy.engine.unit.calc.CalcFixture.base;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryAction;
import static com.example.strategy.engine.unit.calc.CalcFixture.entryOrder;
import static com.example.strategy.engine.unit.calc.CalcFixture.levels;
import static com.example.strategy.engine.unit.calc.CalcFixture.liveEpisode;
import static com.example.strategy.engine.unit.calc.CalcFixture.marketPlacement;
import static com.example.strategy.engine.unit.calc.CalcFixture.placement;
import static com.example.strategy.engine.unit.calc.CalcFixture.position;
import static com.example.strategy.engine.unit.calc.CalcFixture.prices;
import static com.example.strategy.engine.unit.calc.CalcFixture.rules;
import static com.example.strategy.engine.unit.calc.CalcFixture.rulesWithTick;
import static com.example.strategy.engine.unit.calc.CalcFixture.structures;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.strategy.engine.calc.CalculatedPrice;
import com.example.strategy.engine.calc.CalculationContext;
import com.example.strategy.engine.calc.PriceCalculator;
import com.example.strategy.engine.calc.PriceMode;
import com.example.strategy.engine.calc.StrategyPricePurpose;
import com.example.strategy.engine.exception.CalculationException;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceBaseType;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceOffsetSide;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPricePlacement;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyPriceSource;
import com.example.tradingbot.domain.model.aggregate.strategy.action.StrategyTradeDirection;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.model.trade.market_structure.MarketPriceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * База цены размещения заявки — группа `U1` документа
 * `.claude/tests/cases/strategy-engine-calculation.md`
 * (docs/components/PriceCalculator.md §Формулы).
 *
 * <p><b>Базовая сборка:</b> направление `LONG`; шаг цены {@code 0.1},
 * ставка комиссии {@code 0.0005}; снапшот — последняя {@code 3000}, бид
 * {@code 2999.5}, аск {@code 3000.5}; входная заявка с плановой ценой
 * {@code 3000}, живого эпизода нет; действие — обычная заявка входа с
 * размещением {@code {рыночная, последняя, ниже, 1}}.
 *
 * <p><b>Отсутствие выхода называется наравне с выходом:</b> у входной
 * заявки пусты стоп, тейк и трейлинг, и строка, называющая только
 * посчитанное, была бы зелена у калькулятора, заполнившего лишнее.
 *
 * <p><b>Отказ называется КОДОМ, а не фактом броска:</b> «бросает
 * исключение» зелено на любом отказе, включая неожиданный, а предмет
 * здесь — именно контролируемая ошибка со своим кодом.
 *
 * <p><b>Сменяет часть прежней пробы предмета.</b> Класс
 * {@code com.example.strategy.engine.calc.PriceLevelTest} проверял ровно
 * те ожидания, которые теперь стоя́т клетками, и вторая запись одного
 * ожидания расходится с первой при первой же правке
 * (.claude/rules/carrier-levels.md). Его клетки-преемницы здесь — `U1.1`
 * (последняя цена резолвится из снапшота), `U1.5` и `U1.6` (марк- и
 * индексная цена отказывают, а не проксируют последнюю).
 */
class PlacementPriceTest {

    private final PriceCalculator calculator = new PriceCalculator();

    /** Базовая сборка: лимитная цена уезжает на биржу, защитные разделы пусты. */
    @Test
    @DisplayName("U1.1 — базовая сборка: база 3000, сырая 2970, округлённая 2970, цена уезжает на биржу")
    void u1_1_theBaseAssemblyGivesAnExplicitLimitPrice() {
        CalculatedPrice price = calculator.calculate(base(entryAction(marketPlacement())).build());

        assertThat(price.getPurpose()).isEqualTo(StrategyPricePurpose.ORDER_LIMIT_PRICE);
        assertThat(price.getPriceMode()).isEqualTo(PriceMode.EXPLICIT);
        assertThat(price.getBasePrice()).isEqualByComparingTo("3000");
        assertThat(price.getRawPrice()).isEqualByComparingTo("2970");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2970");
        assertThat(price.getSendPriceToExchange()).isTrue();
        assertThat(price.getStopLossPrice()).as("стоп пуст").isNull();
        assertThat(price.getTakeProfitPrice()).as("тейк пуст").isNull();
        assertThat(price.getTrailingPrice()).as("трейлинг пуст").isNull();
    }

    /** Лучшая покупка — своя база; округление длинной стороны идёт вниз. */
    @Test
    @DisplayName("U1.2 — источник «лучшая покупка»: база 2999.5, сырая 2969.505, округлённая 2969.5")
    void u1_2_theBestBidIsItsOwnBase() {
        CalculatedPrice price = calculator.calculate(withSource(StrategyPriceSource.BEST_BID_PRICE));

        assertThat(price.getBasePrice()).isEqualByComparingTo("2999.5");
        assertThat(price.getRawPrice()).isEqualByComparingTo("2969.505");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2969.5");
    }

    /** Лучшая продажа — своя база. */
    @Test
    @DisplayName("U1.3 — источник «лучшая продажа»: база 3000.5, сырая 2970.495, округлённая 2970.4")
    void u1_3_theBestAskIsItsOwnBase() {
        CalculatedPrice price = calculator.calculate(withSource(StrategyPriceSource.BEST_ASK_PRICE));

        assertThat(price.getBasePrice()).isEqualByComparingTo("3000.5");
        assertThat(price.getRawPrice()).isEqualByComparingTo("2970.495");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2970.4");
    }

    /**
     * Середина — полусумма лучших цен, а не последняя: формула живёт у
     * дока перевода, и это уже припарковано.
     */
    @Test
    @DisplayName("U1.4 — источник «середина»: база 3000 — полусумма лучших цен, а не последняя")
    void u1_4_theMidPriceIsTheHalfSumOfTheBestPrices() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.MID_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .marketPriceData(prices("2500", "2999.5", "3000.5"))
                .build();

        CalculatedPrice price = calculator.calculate(context);

        assertThat(price.getBasePrice()).as("полусумма бида и аска, а не последняя 2500")
                .isEqualByComparingTo("3000");
    }

    /** Марк-цены снапшот не несёт: отказ, а не подстановка последней. */
    @Test
    @DisplayName("U1.5 — источник «марк-цена»: отказ PRICE_SOURCE_UNAVAILABLE, подстановки нет")
    void u1_5_theMarkPriceSourceRefuses() {
        assertRefuses(withSource(StrategyPriceSource.MARK_PRICE), "PRICE_SOURCE_UNAVAILABLE");
    }

    /** Индексная цена — тот же отказ и по той же причине. */
    @Test
    @DisplayName("U1.6 — источник «индексная цена»: тот же отказ PRICE_SOURCE_UNAVAILABLE")
    void u1_6_theIndexPriceSourceRefusesToo() {
        assertRefuses(withSource(StrategyPriceSource.INDEX_PRICE), "PRICE_SOURCE_UNAVAILABLE");
    }

    /** Снапшота нет вовсе — базы размещения не из чего резолвить. */
    @Test
    @DisplayName("U1.7 — снапшота цен нет вовсе: отказ NO_REFERENCE_PRICE")
    void u1_7_anAbsentSnapshotRefuses() {
        CalculationContext context = base(entryAction(marketPlacement()))
                .marketPriceData(null)
                .build();

        assertRefuses(context, "NO_REFERENCE_PRICE");
    }

    /** Объявленный источник пуст — отказ, а не переход к соседнему. */
    @Test
    @DisplayName("U1.8 — объявленный источник пуст: отказ NO_REFERENCE_PRICE, а не соседний источник")
    void u1_8_anEmptyDeclaredSourceRefusesInsteadOfFallingBack() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.BEST_BID_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .marketPriceData(prices(LAST_PRICE, null, ASK_PRICE))
                .build();

        assertRefuses(context, "NO_REFERENCE_PRICE");
    }

    /**
     * База «от цены входа» без факта — плановая цена своей ноги. Ожидания
     * в `docs/**` нет: резолв живёт только в javadoc — находка `F-5`.
     */
    @Test
    @DisplayName("U1.9 — база «цена входа», факта нет: база 3000 — плановая цена своей ноги")
    void u1_9_theEntryPriceBaseWithoutAFactIsThePlannedLegPrice() {
        CalculatedPrice price = calculator.calculate(entryPriceBase().build());

        assertThat(price.getBasePrice()).isEqualByComparingTo("3000");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2970");
    }

    /** Налив своей ноги базу размещения подменяет — этим она и отличается от якоря уровня. */
    @Test
    @DisplayName("U1.10 — база «цена входа», средняя налива 2995: база 2995 — факт своей ноги")
    void u1_10_theFillOfItsOwnLegReplacesTheBase() {
        CalculationContext context = entryPriceBase()
                .entryOrder(entryOrder(LAST_PRICE, "2995"))
                .build();

        assertThat(calculator.calculate(context).getBasePrice()).isEqualByComparingTo("2995");
    }

    /** Живой эпизод старше налива ноги. */
    @Test
    @DisplayName("U1.11 — база «цена входа», живой эпизод со средней 2990: база 2990")
    void u1_11_theLiveEpisodeAverageWinsOverTheLegFill() {
        CalculationContext context = entryPriceBase()
                .entryOrder(entryOrder(LAST_PRICE, "2995"))
                .activePosition(liveEpisode("2990"))
                .build();

        assertThat(calculator.calculate(context).getBasePrice()).isEqualByComparingTo("2990");
    }

    /**
     * База размещения живого риска не спрашивает — этим она и отличается
     * от якоря уровня (`U3.4`), который ветвится именно по нему.
     */
    @Test
    @DisplayName("U1.12 — эпизод ACTIVE с наблюдённым размером 0: база 2990 — живого риска база не спрашивает")
    void u1_12_thePlacementBaseDoesNotAskForLiveRisk() {
        CalculationContext context = entryPriceBase()
                .activePosition(position(Position.Status.ACTIVE, "0", "2990"))
                .build();

        assertThat(calculator.calculate(context).getBasePrice()).isEqualByComparingTo("2990");
    }

    /** Структурная база берётся из уровня объявленного типа. */
    @Test
    @DisplayName("U1.13 — база «минимум свинга» 2900: сырая 2871, округлённая 2871")
    void u1_13_theSwingLowBaseIsTakenFromTheStructure() {
        CalculatedPrice price = calculator.calculate(structuralBase(StrategyPriceBaseType.SWING_LOW,
                MarketPriceLevel.Type.SWING_LOW, "2900"));

        assertThat(price.getBasePrice()).isEqualByComparingTo("2900");
        assertThat(price.getRawPrice()).isEqualByComparingTo("2871");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2871");
    }

    /** Структуры по объявленному ключу нет — отказ. */
    @Test
    @DisplayName("U1.15 — структуры по объявленному ключу нет: отказ MISSING_STRUCTURE")
    void u1_15_anAbsentStructureRefuses() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.SWING_LOW,
                StrategyPriceSource.LAST_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .build();

        assertRefuses(context, "MISSING_STRUCTURE");
    }

    /**
     * У базы размещения фолбэка на границу диапазона нет — им пользуется
     * только уровень остановки убытка (`U4.9`).
     */
    @Test
    @DisplayName("U1.16 — минимума свинга в структуре нет, низ диапазона есть: отказ MISSING_STRUCTURE")
    void u1_16_thePlacementBaseHasNoRangeFallback() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.SWING_LOW,
                StrategyPriceSource.LAST_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .marketStructures(structures(levels(MarketPriceLevel.Type.RANGE_LOW, "2890")))
                .build();

        assertRefuses(context, "MISSING_STRUCTURE");
    }

    /** Сторона смещения «выше» прибавляет смещение к базе. */
    @Test
    @DisplayName("U1.17 — сторона смещения «выше»: сырая 3030, округлённая 3030")
    void u1_17_theAboveOffsetSideAddsTheOffset() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.LAST_PRICE, StrategyPriceOffsetSide.ABOVE, "1")))
                .build();

        CalculatedPrice price = calculator.calculate(context);

        assertThat(price.getRawPrice()).isEqualByComparingTo("3030");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3030");
    }

    /** Пустые проценты — «смещения нет», а не отказ. */
    @Test
    @DisplayName("U1.18 — проценты не объявлены: смещение 0, цена равна базе 3000, отказа нет")
    void u1_18_absentPercentsMeanNoOffset() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.LAST_PRICE, StrategyPriceOffsetSide.BELOW, null)))
                .build();

        CalculatedPrice price = calculator.calculate(context);

        assertThat(price.getRawPrice()).isEqualByComparingTo("3000");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3000");
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * объявляет пустую сторону смещения как «смещения нет»
     * (docs/models/domain/aggregate/Strategy.md §«StrategyPricePlacement»),
     * а код вычитает смещение и даёт {@code 2970}: заявка встаёт ниже
     * объявленной базы, и создание такое объявление пропускает. Красный
     * прогон и есть предъявление находки `F-6`
     * (`.claude/work/backlog.md` §«Пустая сторона смещения означает в доме
     * отсутствие смещения, а в коде — вычитание»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U1.19 — сторона смещения не объявлена, проценты 1: цена равна базе 3000 (дом), код даёт 2970")
    void u1_19_anAbsentOffsetSideMeansNoOffsetInTheHome() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.LAST_PRICE, null, "1")))
                .build();

        assertThat(calculator.calculate(context).getRoundedPrice())
                .as("пустая сторона — отсутствие смещения, а не вычитание")
                .isEqualByComparingTo("3000");
    }

    /** У короткой стороны округление идёт вверх. */
    @Test
    @DisplayName("U1.20 — направление SHORT, источник «лучшая покупка»: округлённая 2969.6 — вверх")
    void u1_20_theShortSideRoundsUp() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.BEST_BID_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .strategyDirection(StrategyTradeDirection.SHORT)
                .build();

        assertThat(calculator.calculate(context).getRoundedPrice()).isEqualByComparingTo("2969.6");
    }

    /** Шага цены нет — округлять не по чему. */
    @Test
    @DisplayName("U1.21 — шага цены у правил инструмента нет: отказ MISSING_TICK_SIZE")
    void u1_21_anAbsentTickSizeRefuses() {
        CalculationContext context = base(entryAction(marketPlacement()))
                .instrumentExternalRules(rulesWithTick(null))
                .build();

        assertRefuses(context, "MISSING_TICK_SIZE");
    }

    /** Нулевой шаг цены — тот же отказ, а не деление на ноль. */
    @Test
    @DisplayName("U1.22 — шаг цены равен нулю: отказ MISSING_TICK_SIZE, а не деление на ноль")
    void u1_22_aZeroTickSizeRefusesInsteadOfDividingByZero() {
        CalculationContext context = base(entryAction(marketPlacement()))
                .instrumentExternalRules(rulesWithTick("0"))
                .build();

        assertRefuses(context, "MISSING_TICK_SIZE");
    }

    /** Цена после округления неположительна — отказ. */
    @Test
    @DisplayName("U1.23 — база 0.05 при шаге 0.1: отказ INVALID_PRICE_AFTER_ROUNDING")
    void u1_23_aNonPositivePriceAfterRoundingRefuses() {
        CalculationContext context = base(entryAction(marketPlacement()))
                .marketPriceData(prices("0.05", BID_PRICE, ASK_PRICE))
                .build();

        assertRefuses(context, "INVALID_PRICE_AFTER_ROUNDING");
    }

    /** Правил инструмента нет вовсе — тот же отказ шага цены. */
    @Test
    @DisplayName("U1.24 — правил инструмента нет вовсе: отказ MISSING_TICK_SIZE")
    void u1_24_absentInstrumentRulesRefuse() {
        CalculationContext context = base(entryAction(marketPlacement()))
                .instrumentExternalRules(null)
                .build();

        assertRefuses(context, "MISSING_TICK_SIZE");
    }

    /**
     * Негатив источника «середина»: пустой бид даёт пустую середину, а не
     * одну из сторон спреда. Строка добрана под-шагом 3 по пробелу `G1`.
     */
    @Test
    @DisplayName("U1.25 — источник «середина», бид не наблюдён: отказ NO_REFERENCE_PRICE, а не одна из сторон")
    void u1_25_anEmptyBidMakesTheMidPriceEmpty() {
        CalculationContext context = base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE,
                StrategyPriceSource.MID_PRICE, StrategyPriceOffsetSide.BELOW, "1")))
                .marketPriceData(prices(LAST_PRICE, null, ASK_PRICE))
                .build();

        assertRefuses(context, "NO_REFERENCE_PRICE");
    }

    /** Максимум свинга — своя база того же резолва. Строка добрана по пробелу `G2`. */
    @Test
    @DisplayName("U1.26 — база «максимум свинга» 3100: сырая 3069")
    void u1_26_theSwingHighBaseIsTakenFromTheStructure() {
        CalculatedPrice price = calculator.calculate(structuralBase(StrategyPriceBaseType.SWING_HIGH,
                MarketPriceLevel.Type.SWING_HIGH, "3100"));

        assertThat(price.getBasePrice()).isEqualByComparingTo("3100");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3069");
    }

    /** Верх диапазона — та же тропа резолва. Строка добрана по пробелу `G2`. */
    @Test
    @DisplayName("U1.27 — база «верх диапазона» 3200: сырая 3168")
    void u1_27_theRangeHighBaseIsTakenFromTheStructure() {
        CalculatedPrice price = calculator.calculate(structuralBase(StrategyPriceBaseType.RANGE_HIGH,
                MarketPriceLevel.Type.RANGE_HIGH, "3200"));

        assertThat(price.getBasePrice()).isEqualByComparingTo("3200");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3168");
    }

    /** Поддержка — та же тропа резолва. Строка добрана по пробелу `G2`. */
    @Test
    @DisplayName("U1.28 — база «поддержка» 2800: сырая 2772")
    void u1_28_theSupportBaseIsTakenFromTheStructure() {
        CalculatedPrice price = calculator.calculate(structuralBase(StrategyPriceBaseType.SUPPORT,
                MarketPriceLevel.Type.SUPPORT, "2800"));

        assertThat(price.getBasePrice()).isEqualByComparingTo("2800");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("2772");
    }

    /** Сопротивление — та же тропа резолва. Строка добрана по пробелу `G2`. */
    @Test
    @DisplayName("U1.29 — база «сопротивление» 3300: сырая 3267")
    void u1_29_theResistanceBaseIsTakenFromTheStructure() {
        CalculatedPrice price = calculator.calculate(structuralBase(StrategyPriceBaseType.RESISTANCE,
                MarketPriceLevel.Type.RESISTANCE, "3300"));

        assertThat(price.getBasePrice()).isEqualByComparingTo("3300");
        assertThat(price.getRoundedPrice()).isEqualByComparingTo("3267");
    }

    // --- отклонения от базовой сборки --------------------------------------

    private CalculationContext withSource(StrategyPriceSource source) {
        return base(entryAction(placement(StrategyPriceBaseType.MARKET_PRICE, source,
                StrategyPriceOffsetSide.BELOW, "1")))
                .build();
    }

    private CalculationContext.CalculationContextBuilder entryPriceBase() {
        return base(entryAction(placement(StrategyPriceBaseType.ENTRY_PRICE, StrategyPriceSource.LAST_PRICE,
                StrategyPriceOffsetSide.BELOW, "1")));
    }

    private CalculationContext structuralBase(StrategyPriceBaseType baseType, MarketPriceLevel.Type levelType,
                                              String levelPrice) {
        StrategyPricePlacement declared = placement(baseType, StrategyPriceSource.LAST_PRICE,
                StrategyPriceOffsetSide.BELOW, "1");
        return base(entryAction(declared))
                .instrumentExternalRules(rules())
                .marketStructures(structures(levels(levelType, levelPrice)))
                .build();
    }

    private void assertRefuses(CalculationContext context, String code) {
        assertThatThrownBy(() -> calculator.calculate(context))
                .isInstanceOf(CalculationException.class)
                .extracting(thrown -> ((CalculationException) thrown).getError().getCode())
                .isEqualTo(code);
    }
}
