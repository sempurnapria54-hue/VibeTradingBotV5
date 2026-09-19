package com.example.strategy.engine.unit.condition;

import static com.example.strategy.engine.unit.condition.ConditionFixture.base;
import static com.example.strategy.engine.unit.condition.ConditionFixture.condition;
import static com.example.strategy.engine.unit.condition.ConditionFixture.enumConstant;
import static com.example.strategy.engine.unit.condition.ConditionFixture.rule;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.condition.ConditionEvaluationContext;
import com.example.strategy.engine.condition.StrategyConditionEvaluator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperator;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRuleType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Фаза рынка: два предиката — группа `U8` документа
 * `.claude/tests/cases/strategy-engine-condition.md`
 * (docs/spec/market-phase-condition.json, величины {@code marketPhaseIs},
 * {@code trendChanged}; смысл —
 * docs/models/domain/other/MarketPhase.md §«Фаза внутри прохода сделки —
 * носитель и предикаты»).
 *
 * <p><b>Базовая сборка:</b> фаза прохода `BULL_TREND`; фазы входа нет;
 * фактов сделки нет; правило `MARKET_PHASE_IS` с константой `BULL_TREND`
 * типа `ENUM`.
 *
 * <p><b>`UNKNOWN` знанием не является</b>, и встреча двух незнаний
 * совпадением не считается: разрешающее умолчание открыло бы вход по
 * ненаблюдённой фазе.
 */
class MarketPhaseRuleTest {

    private final StrategyConditionEvaluator evaluator = new StrategyConditionEvaluator();

    /** Фаза прохода совпала с объявленной. */
    @Test
    @DisplayName("U8.1 — базовая сборка: истина")
    void u8_1_theBaseAssemblyIsTrue() {
        assertThat(phaseIs("BULL_TREND", MarketPhase.Type.BULL_TREND)).isTrue();
    }

    /** Пара к U8.1. */
    @Test
    @DisplayName("U8.2 — фаза прохода RANGE: ложь")
    void u8_2_aDifferentPhaseIsFalse() {
        assertThat(phaseIs("BULL_TREND", MarketPhase.Type.RANGE)).isFalse();
    }

    /** Встреча двух незнаний совпадением не является. */
    @Test
    @DisplayName("U8.3 — фаза прохода UNKNOWN, объявлена UNKNOWN: ложь")
    void u8_3_twoUnknownsDoNotMatch() {
        assertThat(phaseIs("UNKNOWN", MarketPhase.Type.UNKNOWN)).isFalse();
    }

    /** Вторая форма неустановленности. */
    @Test
    @DisplayName("U8.4 — фазы прохода нет вовсе: ложь")
    void u8_4_anAbsentPhaseIsFalse() {
        assertThat(phaseIs("BULL_TREND", null)).isFalse();
    }

    /** Без объявленной фазы сравнивать не с чем. Охрана второго рубежа — создание. */
    @Test
    @DisplayName("U8.5 — константный операнд у правила отсутствует: ложь")
    void u8_5_anAbsentDeclaredPhaseIsFalse() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_PHASE_IS,
                StrategyConditionOperator.EQ, null, null));

        assertThat(evaluator.evaluate(condition, context(MarketPhase.Type.BULL_TREND, null))).isFalse();
    }

    /** Неизвестная фаза — ложь. Охрана второго рубежа — создание. */
    @Test
    @DisplayName("U8.6 — константа MOONWALK: ложь")
    void u8_6_anUnknownPhaseLiteralIsFalse() {
        assertThat(phaseIs("MOONWALK", MarketPhase.Type.BULL_TREND)).isFalse();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Объявлено
     * «фаза не равна», и предикат обязан ответить отрицанием
     * (docs/models/domain/aggregate/Strategy.md §Условия против
     * docs/spec/market-phase-condition.json); создание оператор у правила
     * ТРЕБУЕТ и до равенства его не сужает, а код оператор не читает и
     * отвечает истиной. Красный прогон и есть предъявление находки `F-4`
     * (`.claude/work/backlog.md` §«Предикат фазы игнорирует оператор, а
     * создание его требует»).
     */
    @Test
    @Tag("debt")
    @DisplayName("U8.7 — оператор NE, фаза BULL_TREND, объявлена BULL_TREND: ложь (дом), код даёт истину")
    void u8_7_thePhasePredicateMustHonourTheOperator() {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_PHASE_IS,
                StrategyConditionOperator.NE, null, enumConstant("BULL_TREND")));

        assertThat(evaluator.evaluate(condition, context(MarketPhase.Type.BULL_TREND, null)))
                .as("объявлено «фаза не равна» — предикат обязан ответить отрицанием")
                .isFalse();
    }

    /** Уход от фазы, при которой входили. */
    @Test
    @DisplayName("U8.8 — TREND_CHANGED, проход BEAR_TREND, вход BULL_TREND: истина")
    void u8_8_theTrendChangedAwayFromTheEntryPhase() {
        assertThat(trendChanged(null, MarketPhase.Type.BEAR_TREND, MarketPhase.Type.BULL_TREND)).isTrue();
    }

    /** Пара к U8.8. */
    @Test
    @DisplayName("U8.9 — тот же тип, обе фазы BULL_TREND: ложь")
    void u8_9_anUnchangedTrendIsFalse() {
        assertThat(trendChanged(null, MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND)).isFalse();
    }

    /** Без конъюнкта установленности сделка выходила бы по пропаже операнда. */
    @Test
    @DisplayName("U8.10 — тот же тип, проход UNKNOWN, вход BULL_TREND: ложь")
    void u8_10_anUnknownPassPhaseDoesNotChangeTheTrend() {
        assertThat(trendChanged(null, MarketPhase.Type.UNKNOWN, MarketPhase.Type.BULL_TREND)).isFalse();
    }

    /** У восстановленной сделки фазы входа нет — сравнивать не с чем. */
    @Test
    @DisplayName("U8.11 — тот же тип, фазы входа нет: ложь")
    void u8_11_anAbsentEntryPhaseIsFalse() {
        assertThat(trendChanged(null, MarketPhase.Type.BEAR_TREND, null)).isFalse();
    }

    /** Вторая форма неустановленности фазы входа. */
    @Test
    @DisplayName("U8.12 — тот же тип, фаза входа UNKNOWN: ложь")
    void u8_12_anUnknownEntryPhaseIsFalse() {
        assertThat(trendChanged(null, MarketPhase.Type.BEAR_TREND, MarketPhase.Type.UNKNOWN)).isFalse();
    }

    /** Объявленная фаза смене тренда БЕЗРАЗЛИЧНА: предикат сравнивает две наблюдённые. */
    @Test
    @DisplayName("U8.13 — тот же тип, объявлено RANGE, обе фазы BULL_TREND: ложь")
    void u8_13_theDeclaredPhaseIsIrrelevantToTheTrendChange() {
        assertThat(trendChanged(enumConstant("RANGE"),
                MarketPhase.Type.BULL_TREND, MarketPhase.Type.BULL_TREND)).isFalse();
    }

    /** Пара к U8.13: исход не изменился ни от объявления, ни от его значения. */
    @Test
    @DisplayName("U8.14 — тот же тип и тот же операнд RANGE, проход BEAR_TREND, вход BULL_TREND: истина")
    void u8_14_theDeclaredPhaseDoesNotChangeTheOutcomeEither() {
        assertThat(trendChanged(enumConstant("RANGE"),
                MarketPhase.Type.BEAR_TREND, MarketPhase.Type.BULL_TREND)).isTrue();
    }

    private Boolean phaseIs(String declared, MarketPhase.Type passPhase) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.MARKET_PHASE_IS,
                StrategyConditionOperator.EQ, null, enumConstant(declared)));
        return evaluator.evaluate(condition, context(passPhase, null));
    }

    private Boolean trendChanged(StrategyConditionOperand declared, MarketPhase.Type passPhase,
                                 MarketPhase.Type entryPhase) {
        StrategyCondition condition = condition(rule(StrategyConditionRuleType.TREND_CHANGED,
                StrategyConditionOperator.EQ, null, declared));
        return evaluator.evaluate(condition, context(passPhase, entryPhase));
    }

    private ConditionEvaluationContext context(MarketPhase.Type passPhase, MarketPhase.Type entryPhase) {
        return base().marketPhase(passPhase).entryMarketPhase(entryPhase).build();
    }
}
