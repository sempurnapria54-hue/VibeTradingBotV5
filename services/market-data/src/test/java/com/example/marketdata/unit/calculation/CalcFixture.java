package com.example.marketdata.unit.calculation;

import com.example.marketdata.domain.service.indicator.AtrCalculator;
import com.example.marketdata.domain.service.indicator.BollingerBandsCalculator;
import com.example.marketdata.domain.service.indicator.EfficiencyRatioCalculator;
import com.example.marketdata.domain.service.indicator.EmaCalculator;
import com.example.marketdata.domain.service.indicator.IndicatorCalculator;
import com.example.marketdata.domain.service.indicator.MacdCalculator;
import com.example.marketdata.domain.service.indicator.ObvCalculator;
import com.example.marketdata.domain.service.indicator.RsiCalculator;
import com.example.marketdata.domain.service.indicator.StochasticCalculator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.ConstantValueType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.AtrParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.BollingerBandsParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EfficiencyRatioParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.EmaParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.IndicatorParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MacdParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.MarketStructureParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.ObvParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.RsiParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StochasticParams;
import com.example.tradingbot.domain.model.aggregate.strategy.setting.StrategyMarketPhaseRule;
import com.example.tradingbot.domain.model.trade.candle.Candle;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Базовая сборка групп документа
 * `.claude/tests/cases/market-data-indicators.md`.
 *
 * <p><b>Субстрата у предмета нет вовсе:</b> ни контейнеров, ни контекста
 * каркаса, ни стабов, ни моков — вычислитель конструируется {@code new},
 * резолвер структуры тоже, а единственный коллаборатор резолвера фазы
 * (вычислитель условий) берётся <b>настоящим</b>: ввода-вывода у него нет,
 * и признак мокания на него не распространяется.
 *
 * <p><b>Состояние собирается настоящими полями доменных моделей</b>
 * (.claude/rules/codestyle.md §«Тесты доменных моделей»): свеча несёт
 * открытие, максимум, минимум, закрытие и объём; параметры — свои поля;
 * клауза — свой тип и своё условие.
 *
 * <p><b>Временем управлять нечем:</b> ни один вход не читает часов, и
 * отметка времени значения выводится из момента открытия бара. Поэтому
 * первый бар любого ряда стои́т на фиксированной минуте UTC
 * {@link #FIRST_BAR_MILLIS}, а шаг между барами равен минуте.
 *
 * <p><b>Окна структуры выведены под свою геометрию, а не подобраны.</b> У
 * каждого названо, какие свинг-пивоты оно даёт и какой тип из них следует;
 * глубина поиска пивотов у базовых параметров равна единице, поэтому
 * пивотом может стать любой бар, кроме двух краёв.
 */
final class CalcFixture {

    /** Момент открытия первого бара любого ряда — фиксированная минута UTC. */
    static final long FIRST_BAR_MILLIS = 1_700_000_100_000L;

    /** Шаг между барами ряда — минута. */
    static final long BAR_STEP_MILLIS = 60_000L;

    /** Идентификатор инструмента базовой сборки. */
    static final Long INSTRUMENT_ID = 11L;

    /** Идентификатор идентичности вычисления базовой сборки — отличим от инструмента. */
    static final Long COMPUTATION_ID = 22L;

    private CalcFixture() {
    }

    // --- свечи и ряды ------------------------------------------------------

    /** Бар с раздельными максимумом, минимумом, закрытием и объёмом. */
    static Candle bar(int index, String high, String low, String close, String volume) {
        Candle candle = new Candle();
        candle.setCandleGroupId(7L);
        candle.setOpenTimestamp(FIRST_BAR_MILLIS + index * BAR_STEP_MILLIS);
        candle.setOpen(new BigDecimal(close));
        candle.setHigh(new BigDecimal(high));
        candle.setLow(new BigDecimal(low));
        candle.setClose(new BigDecimal(close));
        candle.setVolume(new BigDecimal(volume));
        return candle;
    }

    /** Ряд, у которого весь бар сжат в цену закрытия: максимум и минимум равны ей. */
    static List<Candle> closeSeries(String... closes) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < closes.length; index++) {
            candles.add(bar(index, closes[index], closes[index], closes[index], "1"));
        }
        return candles;
    }

    /** Ряд из равных цен закрытия — вырожденное окно всякой оконной формулы. */
    static List<Candle> flatSeries(int count, String close) {
        String[] closes = new String[count];
        Arrays.fill(closes, close);
        return closeSeries(closes);
    }

    /** Ряд с различимыми ценами и объёмами: закрытие растёт на единицу, размах равен четырём. */
    static List<Candle> risingSeries(int count) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            BigDecimal close = BigDecimal.valueOf(100L + index);
            candles.add(bar(index, close.add(BigDecimal.TWO).toPlainString(),
                    close.subtract(BigDecimal.TWO).toPlainString(), close.toPlainString(),
                    String.valueOf(10 + index)));
        }
        return candles;
    }

    /** Окно из троек «максимум, минимум, закрытие» — по бару на тройку. */
    static List<Candle> window(String[] highs, String[] lows, String[] closes) {
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < highs.length; index++) {
            candles.add(bar(index, highs[index], lows[index], closes[index], "1"));
        }
        return candles;
    }

    /** Момент открытия бара с этим номером — то же, что отметка времени его значения. */
    static OffsetDateTime barAt(int index) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(FIRST_BAR_MILLIS + index * BAR_STEP_MILLIS),
                ZoneOffset.UTC);
    }

    /** Отметки времени баров с этими номерами включительно. */
    static List<OffsetDateTime> barsAt(int from, int toInclusive) {
        List<OffsetDateTime> stamps = new ArrayList<>();
        for (int index = from; index <= toInclusive; index++) {
            stamps.add(barAt(index));
        }
        return stamps;
    }

    /** Ряд чисел для числовых помощников. */
    static List<BigDecimal> numbers(String... values) {
        return Stream.of(values).map(BigDecimal::new).collect(Collectors.toList());
    }

    // --- окна структуры ----------------------------------------------------

    /**
     * Восходящий тренд с монотонно растущим закрытием: свинг-максимумы
     * {@code 120,122,124,126}, свинг-минимумы {@code 85,87,89}, сопротивление
     * {@code 126}, поддержка {@code 85}. Монотонность закрытия нужна ветви
     * внутреннего прокси: он даёт на ней ровно единицу.
     */
    static List<Candle> monotoneUptrendWindow() {
        return window(new String[] {"100", "120", "105", "122", "107", "124", "109", "126", "115"},
                new String[] {"80", "95", "85", "97", "87", "99", "89", "101", "95"},
                new String[] {"90", "96", "97", "98", "99", "100", "101", "102", "103"});
    }

    /** Нисходящее зеркало предыдущего: максимумы и минимумы падают, закрытие монотонно падает. */
    static List<Candle> monotoneDowntrendWindow() {
        return window(new String[] {"115", "126", "109", "124", "107", "122", "105", "120", "100"},
                new String[] {"95", "101", "89", "99", "87", "97", "85", "95", "80"},
                new String[] {"103", "102", "101", "100", "99", "98", "97", "96", "95"});
    }

    /**
     * Диапазон: четыре пивота на {@code 110} и три на {@code 90}, ширина
     * полосы {@code 20%} от середины {@code 100}, геометрии тренда нет ни
     * восходящей, ни нисходящей.
     */
    static List<Candle> rangeWindow() {
        return window(new String[] {"100", "110", "95", "110", "95", "110", "95", "110", "100"},
                new String[] {"90", "99", "90", "99", "90", "99", "90", "99", "95"},
                new String[] {"95", "105", "92", "105", "92", "105", "92", "105", "98"});
    }

    /** Диапазон плюс два бара, закрытых выше сопротивления с запасом буфера. */
    static List<Candle> rangeBreakoutUpWindow() {
        return window(new String[] {"100", "110", "95", "110", "95", "110", "95", "110", "100", "116", "118"},
                new String[] {"90", "99", "90", "99", "90", "99", "90", "99", "95", "100", "110"},
                new String[] {"95", "105", "92", "105", "92", "105", "92", "105", "98", "115", "117"});
    }

    /** Диапазон плюс два бара, закрытых ниже поддержки с запасом буфера. */
    static List<Candle> rangeBreakoutDownWindow() {
        return window(new String[] {"100", "110", "95", "110", "95", "110", "95", "110", "100", "92", "86"},
                new String[] {"90", "99", "90", "99", "90", "99", "90", "99", "95", "84", "82"},
                new String[] {"95", "105", "92", "105", "92", "105", "92", "105", "98", "85", "83"});
    }

    /**
     * То же, что {@link #rangeBreakoutUpWindow()}, плюс бар возврата внутрь
     * полосы: удержание за уровнем завершилось на предпоследнем баре окна.
     * Максимум бара возврата поднят выше соседнего, чтобы состав пивотов —
     * а с ним и сопротивление {@code 110} — не изменился.
     */
    static List<Candle> rangeBreakoutReturnWindow() {
        return window(new String[] {"100", "110", "95", "110", "95", "110", "95", "110", "100", "116", "118", "119"},
                new String[] {"90", "99", "90", "99", "90", "99", "90", "99", "95", "100", "110", "100"},
                new String[] {"95", "105", "92", "105", "92", "105", "92", "105", "98", "115", "117", "105"});
    }

    /**
     * Восходящая геометрия, у которой потолок — <b>одинокий</b> пивот
     * {@code 108}, а кластер из двух пивотов стои́т на {@code 100}: касаний у
     * крайнего пивота одно, у кластера два. Свинг-минимумы {@code 85,90}
     * растут, поэтому при поданном скаляре эффективности тип — восходящий
     * тренд, и граничные уровни выдаются.
     */
    static List<Candle> loneSpikeUptrendWindow() {
        return window(new String[] {"95", "100", "92", "100", "94", "108", "99"},
                new String[] {"85", "95", "85", "92", "90", "99", "90"},
                new String[] {"90", "98", "88", "95", "92", "105", "95"});
    }

    /**
     * Тот же одинокий потолок {@code 108} плюс два бара пробоя: касаний у
     * потолка одно, поэтому диапазон не признаётся, минимумы не растут —
     * тренда тоже нет, и тип выходит «неизвестно» при закрытом за уровнем
     * хвосте.
     */
    static List<Candle> unknownTypeBreakoutWindow() {
        return window(new String[] {"95", "100", "92", "100", "94", "108", "99", "113", "115"},
                new String[] {"85", "95", "85", "92", "90", "99", "90", "105", "110"},
                new String[] {"90", "98", "88", "95", "92", "105", "95", "112", "114"});
    }

    /** Окно, где свинг-минимум ровно один: геометрия одной из сторон не определена. */
    static List<Candle> singleSwingLowWindow() {
        return window(new String[] {"100", "110", "95", "112", "100"},
                new String[] {"90", "100", "84", "102", "95"},
                new String[] {"95", "105", "90", "108", "98"});
    }

    /**
     * Окно, у которого максимум несут <b>краевые</b> бары: подтверждающих
     * баров с одной стороны у них нет, и пивотами они не становятся.
     */
    static List<Candle> edgeSpikeWindow() {
        return window(new String[] {"130", "100", "100", "100", "130"},
                new String[] {"120", "90", "90", "90", "120"},
                new String[] {"125", "95", "95", "95", "125"});
    }

    /**
     * Окно, у которого кластер пивотов <b>разнесён</b>: свинг-максимумы
     * {@code 110,109}, свинг-минимумы {@code 90,91}, полоса {@code 20%} от
     * середины {@code 100}. Толеранс долей цены ({@code 0.55} у потолка,
     * {@code 0.45} у пола) второго пивота не захватывает и даёт по одному
     * касанию; толеранс от скаляра волатильности {@code 4} при дефолтном
     * множителе {@code 0.5} равен {@code 2} и захватывает оба. Геометрии
     * тренда нет ни в одну сторону: максимумы падают, а минимумы растут.
     */
    static List<Candle> spreadClusterWindow() {
        return window(new String[] {"100", "110", "95", "96", "97", "109", "100", "99", "98"},
                new String[] {"95", "99", "90", "92", "93", "99", "94", "91", "96"},
                new String[] {"98", "105", "92", "94", "95", "105", "97", "95", "97"});
    }

    /** Окно из нулевых цен: середина полосы равна нулю. */
    static List<Candle> zeroPriceWindow() {
        return window(new String[] {"0", "0", "0", "0", "0", "0", "0", "0", "0"},
                new String[] {"0", "0", "0", "0", "0", "0", "0", "0", "0"},
                new String[] {"0", "0", "0", "0", "0", "0", "0", "0", "0"});
    }

    // --- параметры ---------------------------------------------------------

    static EmaParams emaParams(Integer period, Integer warmup) {
        EmaParams params = new EmaParams(period);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static AtrParams atrParams(Integer period, Integer warmup) {
        AtrParams params = new AtrParams(period);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static RsiParams rsiParams(Integer period, Integer warmup) {
        RsiParams params = new RsiParams(period);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static MacdParams macdParams(Integer fast, Integer slow, Integer signal, Integer warmup) {
        MacdParams params = new MacdParams(fast, slow, signal);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static BollingerBandsParams bollingerParams(Integer period, String multiplier, Integer warmup) {
        BollingerBandsParams params = new BollingerBandsParams(period, new BigDecimal(multiplier));
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static StochasticParams stochasticParams(Integer kPeriod, Integer dPeriod, Integer smooth, Integer warmup) {
        StochasticParams params = new StochasticParams(kPeriod, dPeriod, smooth);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static ObvParams obvParams(Integer warmup) {
        ObvParams params = new ObvParams(true);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    static EfficiencyRatioParams efficiencyParams(Integer period, Integer warmup) {
        EfficiencyRatioParams params = new EfficiencyRatioParams(period);
        params.setTimeframe(TimeFrame.ONE_MINUTE);
        params.setWarmup(warmup);
        return params;
    }

    /**
     * Параметры структуры со <b>всеми</b> обязательными числами: глубина
     * поиска пивотов {@code 1}, требуемых касаний {@code 2}, полоса
     * диапазона {@code [5%, 30%]}, буфер пробоя {@code 1%}, баров удержания
     * {@code 2}. Порог эффективности и множитель толеранса не заданы —
     * применяются провизорные дефолты резолвера.
     */
    static MarketStructureParams structureParams() {
        MarketStructureParams params = new MarketStructureParams();
        params.setLookbackBars(9);
        params.setSwingLookbackBars(1);
        params.setMinTouches(2);
        params.setMinRangeWidthPercents(new BigDecimal("5"));
        params.setMaxRangeWidthPercents(new BigDecimal("30"));
        params.setBreakoutBufferPercents(new BigDecimal("1"));
        params.setBreakoutConfirmationBars(2);
        return params;
    }

    // --- вычислители одним перечнем ---------------------------------------

    /** Все восемь вычислителей слоя — перечень для клеймов обо всех сразу. */
    static List<IndicatorCalculator> allCalculators() {
        return List.of(new EmaCalculator(), new AtrCalculator(), new RsiCalculator(), new MacdCalculator(),
                new StochasticCalculator(), new BollingerBandsCalculator(), new ObvCalculator(),
                new EfficiencyRatioCalculator());
    }

    /** Параметры, на которых вычислитель этого типа производит значения по {@link #risingSeries(int)}. */
    static IndicatorParams paramsFor(IndicatorCalculator calculator) {
        return switch (calculator.getType()) {
            case EMA -> emaParams(3, null);
            case ATR -> atrParams(3, null);
            case RSI -> rsiParams(3, null);
            case MACD -> macdParams(3, 6, 3, null);
            case STOCHASTIC -> stochasticParams(3, 2, 2, null);
            case BOLLINGER_BANDS -> bollingerParams(3, "2", null);
            case OBV -> obvParams(null);
            case EFFICIENCY_RATIO -> efficiencyParams(3, null);
        };
    }

    // --- клаузы фазы -------------------------------------------------------

    /** Клауза «условие → фаза» с константным условием заданной истинности. */
    static StrategyMarketPhaseRule phaseClause(MarketPhase.Type type, boolean satisfied) {
        return new StrategyMarketPhaseRule(type, constantCondition(satisfied));
    }

    /** Клауза без правил вовсе: пустое условие истинно. */
    static StrategyMarketPhaseRule unconditionalClause(MarketPhase.Type type) {
        StrategyCondition condition = new StrategyCondition();
        condition.setRules(new ArrayList<>());
        return new StrategyMarketPhaseRule(type, condition);
    }

    /** Клауза, читающая индикатор, которого в контексте нет. */
    static StrategyMarketPhaseRule absentOperandClause(MarketPhase.Type type) {
        StrategyConditionOperand left = new StrategyConditionOperand();
        left.setSourceType(StrategyConditionSourceType.INDICATOR);
        left.setIndicatorKey("ключа такого нет");
        return new StrategyMarketPhaseRule(type, condition(left, constant("1")));
    }

    /** Условие из одного сравнения констант: истинно либо ложно по построению. */
    static StrategyCondition constantCondition(boolean satisfied) {
        return condition(constant("1"), constant(satisfied ? "1" : "2"));
    }

    private static StrategyCondition condition(StrategyConditionOperand left, StrategyConditionOperand right) {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setLevel(1);
        rule.setRuleType(StrategyConditionRuleType.INDICATOR_COMPARE);
        rule.setOperator(StrategyConditionOperator.EQ);
        rule.setLeftOperand(left);
        rule.setRightOperand(right);
        StrategyCondition condition = new StrategyCondition();
        condition.setRules(List.of(rule));
        return condition;
    }

    private static StrategyConditionOperand constant(String value) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(StrategyConditionSourceType.CONSTANT);
        operand.setValueType(ConstantValueType.NUMBER);
        operand.setValue(value);
        return operand;
    }

    // --- состав класса как предмет наблюдения ------------------------------

    /** Имена объявленных полей экземпляра — состав модели, читаемый без прогона входа. */
    static List<String> fieldNames(Class<?> type) {
        return declaredInstanceFields(type).map(Field::getName).collect(Collectors.toList());
    }

    /** Типы объявленных полей экземпляра — перечень коллабораторов класса. */
    static List<Class<?>> fieldTypes(Class<?> type) {
        return declaredInstanceFields(type).map(Field::getType).collect(Collectors.toList());
    }

    /** Имена всех объявленных полей, включая статические: логгер {@code @Slf4j} статичен. */
    static List<String> allFieldNames(Class<?> type) {
        return Stream.of(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toList());
    }

    private static Stream<Field> declaredInstanceFields(Class<?> type) {
        return Stream.of(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()));
    }
}
