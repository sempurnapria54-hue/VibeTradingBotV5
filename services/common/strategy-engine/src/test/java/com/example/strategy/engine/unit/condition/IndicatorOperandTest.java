package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.BOLLINGER_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.FAST_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.MACD_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.SLOW_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.STOCH_KEY;
import static com.example.strategy.engine.unit.condition.ConditionFixture.atr;
import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.bollinger;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.efficiencyRatio;
import static com.example.strategy.engine.unit.condition.ConditionFixture.ema;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicator;
import static com.example.strategy.engine.unit.condition.ConditionFixture.indicators;
import static com.example.strategy.engine.unit.condition.ConditionFixture.macd;
import static com.example.strategy.engine.unit.condition.ConditionFixture.number;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rsi;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static com.example.strategy.engine.unit.condition.ConditionFixture.stochastic;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.IndicatorComponent;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.trade.indicator.IndicatorValue;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Индикаторный операнд: ключ, раскладка, компонент — группа `U3` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (ключ и адресный компонент —
 * docs/rules/strategy-condition-contract.md §«Настройка индикатора»,
 * docs/models/domain/other/IndicatorValue.md §«Адресный компонент в
 * условии (D1)»; <b>умолчание компонента дом не называет</b> — §«Дом
 * предиката: восемь типов корпус объявляет бездомными сам», звенья
 * {@code #macdComponent}, {@code #stochasticComponent},
 * {@code #bollingerComponent}).
 *
 * <p><b>Базовая сборка:</b> та же, что у `U2`; раскладка последних
 * значений несёт {@code ema_fast} ({@code EMA = 12}), {@code macd}
 * (линия {@code 2}, сигнальная {@code 1}, гистограмма {@code 1}),
 * стохастик ({@code %K = 80}, {@code %D = 70}) и полосы Боллинджера
 * ({@code %B = 0.8}, ширина {@code 4}, верх {@code 110}, середина
 * {@code 100}, низ {@code 90}).
 *
 * <p><b>Умолчание у каждого составного типа СВОЁ</b> (линия, {@code %K},
 * {@code %B}), и общего умолчания у грамматики нет; охрану на пустой и
 * чужой компонент несёт создание стратегии, а не интерпретатор
 * (docs/rules/strategy-validation.md).
 */
class IndicatorOperandTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Ключ операнда резолвится в раскладку АВТОРСКИМ именем, а не идентичностью вычисления. */
    @Test
    @DisplayName("U3.1 — базовая сборка: ключ ema_fast резолвится авторским именем — истина")
    void u3_1_theOperandKeyResolvesByTheAuthorName() {
        assertThat(evaluate(indicator(FAST_KEY, null), "10")).isTrue();
    }

    /** Отсутствующий ключ есть недоступный операнд, а не ноль. */
    @Test
    @DisplayName("U3.2 — ключа ema_slow в раскладке нет: ложь — недоступный операнд, а не ноль")
    void u3_2_aMissingKeyIsAnUnavailableOperand() {
        assertThat(evaluate(indicator(SLOW_KEY, null), "10")).isFalse();
    }

    /** Пустой ключ резолвить не во что. Охрана второго рубежа — создание требует ключ. */
    @Test
    @DisplayName("U3.3 — ключ операнда пуст: ложь")
    void u3_3_anEmptyKeyIsFalse() {
        assertThat(evaluate(indicator(null, null), "10")).isFalse();
    }

    /** У однокомпонентного единственное значение подразумевается. */
    @Test
    @DisplayName("U3.4 — однокомпонентный индикатор, компонент не задан: истина")
    void u3_4_aSingleComponentIndicatorNeedsNoComponent() {
        assertThat(evaluate(indicator(FAST_KEY, null), "11")).isTrue();
    }

    /** Адресный компонент выбирает линию MACD. */
    @Test
    @DisplayName("U3.5 — macd, компонент MACD_LINE, константа 1: истина — сравнивается линия 2")
    void u3_5_theMacdLineIsAddressedByItsComponent() {
        assertThat(evaluate(indicator(MACD_KEY, IndicatorComponent.MACD_LINE), "1")).isTrue();
    }

    /** Пара к U3.5: различает их ровно компонент. */
    @Test
    @DisplayName("U3.6 — macd, компонент SIGNAL_LINE, константа 1: ложь — сигнальная равна 1")
    void u3_6_theSignalLineIsADifferentComponent() {
        assertThat(evaluate(indicator(MACD_KEY, IndicatorComponent.SIGNAL_LINE), "1")).isFalse();
    }

    /** Третий компонент того же значения. */
    @Test
    @DisplayName("U3.7 — macd, компонент HISTOGRAM, константа 0: истина")
    void u3_7_theHistogramIsAddressedByItsComponent() {
        assertThat(evaluate(indicator(MACD_KEY, IndicatorComponent.HISTOGRAM), "0")).isTrue();
    }

    /** У многокомпонентного без компонента берётся ЛИНИЯ. Охрана — создание требует компонент. */
    @Test
    @DisplayName("U3.8 — macd, компонент не задан, константа 1: истина — умолчание типа есть линия 2")
    void u3_8_theMacdDefaultsToItsLine() {
        assertThat(evaluate(indicator(MACD_KEY, null), "1")).isTrue();
    }

    /** У стохастика адресуется %D. */
    @Test
    @DisplayName("U3.9 — стохастик, компонент STOCH_D, константа 75: ложь — берётся %D = 70")
    void u3_9_theStochasticDLineIsAddressed() {
        assertThat(evaluate(indicator(STOCH_KEY, IndicatorComponent.STOCH_D), "75")).isFalse();
    }

    /** Пара к U3.9. */
    @Test
    @DisplayName("U3.10 — стохастик, компонент STOCH_K, константа 75: истина — берётся %K = 80")
    void u3_10_theStochasticKLineIsAddressed() {
        assertThat(evaluate(indicator(STOCH_KEY, IndicatorComponent.STOCH_K), "75")).isTrue();
    }

    /** Умолчание стохастика — своё: %K. */
    @Test
    @DisplayName("U3.11 — стохастик, компонент не задан, константа 75: истина — умолчание %K = 80")
    void u3_11_theStochasticDefaultsToItsKLine() {
        assertThat(evaluate(indicator(STOCH_KEY, null), "75")).isTrue();
    }

    /** Умолчание полос — своё: %B, а не ширина. */
    @Test
    @DisplayName("U3.12 — полосы, компонент не задан, константа 2: ложь — умолчание %B = 0.8")
    void u3_12_theBandsDefaultToPercentB() {
        assertThat(evaluate(indicator(BOLLINGER_KEY, null), "2")).isFalse();
    }

    /** Пара к U3.12: общего умолчания у грамматики нет. */
    @Test
    @DisplayName("U3.13 — полосы, компонент BANDWIDTH, константа 2: истина — ширина 4")
    void u3_13_theBandwidthIsAddressedByItsComponent() {
        assertThat(evaluate(indicator(BOLLINGER_KEY, IndicatorComponent.BANDWIDTH), "2")).isTrue();
    }

    /** Чужой типу компонент МОЛЧА уходит в умолчание типа, а не в отказ. */
    @Test
    @DisplayName("U3.14 — стохастик, компонент UPPER_BAND (чужой типу), константа 75: истина — умолчание %K")
    void u3_14_aForeignComponentSilentlyFallsBackToTheTypeDefault() {
        assertThat(evaluate(indicator(STOCH_KEY, IndicatorComponent.UPPER_BAND), "75")).isTrue();
    }

    /**
     * Остальные компоненты полос адресуются своими именами; проверяемое —
     * соответствие имени компонента полю значения.
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G3`: ветвь у каждого своя,
     * а различение между ними однотипно с покрытыми `BANDWIDTH` (`U3.13`)
     * и {@code %B} (`U3.12`).
     */
    @ParameterizedTest(name = "U3.15 — полосы, компонент {0}, константа {1}: истина")
    @CsvSource({
            "UPPER_BAND, 105",
            "MIDDLE_BAND, 95",
            "LOWER_BAND, 85"
    })
    @DisplayName("U3.15 — компоненты полос адресуют свои поля значения")
    void u3_15_eachBandComponentAddressesItsOwnField(IndicatorComponent component, String threshold) {
        assertThat(evaluate(indicator(BOLLINGER_KEY, component), threshold))
                .as("компонент %s адресует своё поле значения", component)
                .isTrue();
    }

    /**
     * Однокомпонентные типы поимённо: у каждого своя ветвь
     * {@code #scalarOf}, а различение между ними — одно поле на тип.
     *
     * <p>Строка добрана под-шагом 3 по пробелу `G4`; класс покрыт на `EMA`
     * строкой `U3.4`.
     */
    @ParameterizedTest(name = "U3.16 — однокомпонентный {0} со значением 12, константа 10: истина")
    @EnumSource(value = IndicatorValue.Type.class, names = {"RSI", "ATR", "EFFICIENCY_RATIO"})
    @DisplayName("U3.16 — у каждого однокомпонентного типа своё поле, и компонент ему не нужен")
    void u3_16_eachSingleComponentTypeResolvesItsOwnField(IndicatorValue.Type type) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE,
                StrategyConditionOperator.GT, indicator(FAST_KEY, null), number("10")));
        ConditionEvaluationContext context = base()
                .latestIndicators(indicators(FAST_KEY, singleComponent(type)))
                .build();

        assertThat(evaluator.evaluate(condition, context))
                .as("тип %s резолвится своим полем без компонента", type)
                .isTrue();
    }

    private IndicatorValue singleComponent(IndicatorValue.Type type) {
        return switch (type) {
            case RSI -> rsi("12");
            case ATR -> atr("12");
            case EFFICIENCY_RATIO -> efficiencyRatio("12");
            default -> throw new IllegalArgumentException("Тип " + type + " однокомпонентным не является");
        };
    }

    private Boolean evaluate(StrategyConditionOperand left, String threshold) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.INDICATOR_COMPARE,
                StrategyConditionOperator.GT, left, number(threshold)));
        return evaluator.evaluate(condition, context());
    }

    private ConditionEvaluationContext context() {
        return base().latestIndicators(layout()).build();
    }

    private Map<String, IndicatorValue> layout() {
        return indicators(
                FAST_KEY, ema("12"),
                MACD_KEY, macd("2", "1", "1"),
                STOCH_KEY, stochastic("80", "70"),
                BOLLINGER_KEY, bollinger("0.8", "4"));
    }
}
