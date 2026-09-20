package com.example.tradingbot.domain.unit.predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.strategy.MarketDataExpiredAction;
import com.example.tradingbot.domain.model.aggregate.strategy.PhaseEntryPolicy;
import com.example.tradingbot.domain.model.aggregate.strategy.StrategyMarketDataExpiredSetting;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyCondition;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionOperand;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionRule;
import com.example.tradingbot.domain.model.aggregate.strategy.condition.StrategyConditionSourceType;
import com.example.tradingbot.domain.model.trade.market_phase.MarketPhase;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Грамматика условия: какие источники данных оно называет — группа `U17`
 * документа `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/market-data-freshness.json, операнд {@code actionKind};
 * docs/models/domain/aggregate/Strategy.md;
 * docs/rules/strategy-condition-contract.md §«Правило и операнды»).
 *
 * <p><b>Базовая сборка:</b> условие с коллекцией правил; у правила —
 * левый и правый операнды со своими типами источника и авторскими
 * именами. Истинность правила здесь не считается: её предмет — соседний
 * документ.
 */
class ConditionGrammarTest {

    /** Матрица «политика входа × тип фазы» — docs/models/domain/aggregate/Strategy.md. */
    private static final Set<MarketPhase.Type> TREND_PHASES =
            EnumSet.of(MarketPhase.Type.BULL_TREND, MarketPhase.Type.BEAR_TREND);

    @Test
    @DisplayName("U17.1 — правило, чей левый операнд — цена")
    void u17_1_aLeftPriceOperandIsRead() {
        assertThat(condition(rule(StrategyConditionSourceType.PRICE, null, null)).readsPrice()).isTrue();
    }

    /** Обе стороны правила читаются. */
    @Test
    @DisplayName("U17.2 — правило, чей правый операнд — цена")
    void u17_2_aRightPriceOperandIsRead() {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setLeftOperand(operand(StrategyConditionSourceType.CONSTANT, null, null));
        rule.setRightOperand(operand(StrategyConditionSourceType.PRICE, null, null));

        assertThat(condition(rule).readsPrice()).isTrue();
    }

    @Test
    @DisplayName("U17.3 — правила без ценовых операндов")
    void u17_3_noPriceOperandIsNotRead() {
        assertThat(condition(rule(StrategyConditionSourceType.POSITION, null, null)).readsPrice()).isFalse();
    }

    @Test
    @DisplayName("U17.4 — коллекция правил пуста")
    void u17_4_anEmptyRuleListReadsNothing() {
        StrategyCondition subject = new StrategyCondition();

        assertThatCode(subject::readsPrice).doesNotThrowAnyException();
        assertThat(subject.readsPrice()).isFalse();
        assertThat(subject.readsMarketPhase()).isFalse();
        assertThat(subject.indicatorKeys()).isEmpty();
        assertThat(subject.structureKeys()).isEmpty();
    }

    /** Вопросы разные, и пустоты у них наступают по разным причинам. */
    @Test
    @DisplayName("U17.5 — операнд с типом «фаза рынка»")
    void u17_5_phaseAndPriceAreDifferentQuestions() {
        StrategyCondition subject = condition(rule(StrategyConditionSourceType.MARKET_PHASE, null, null));

        assertThat(subject.readsMarketPhase()).isTrue();
        assertThat(subject.readsPrice()).isFalse();
    }

    @Test
    @DisplayName("U17.6 — два правила с индикаторными операндами, имена различны")
    void u17_6_distinctIndicatorKeysAreCollected() {
        StrategyCondition subject = condition(
                rule(StrategyConditionSourceType.INDICATOR, "ema-fast", null),
                rule(StrategyConditionSourceType.INDICATOR, "ema-slow", null));

        assertThat(subject.indicatorKeys()).containsExactlyInAnyOrder("ema-fast", "ema-slow");
    }

    /** Дубли схлопываются. */
    @Test
    @DisplayName("U17.7 — два правила с индикаторными операндами, имя одно")
    void u17_7_duplicateIndicatorKeysCollapse() {
        StrategyCondition subject = condition(
                rule(StrategyConditionSourceType.INDICATOR, "ema-fast", null),
                rule(StrategyConditionSourceType.INDICATOR, "ema-fast", null));

        assertThat(subject.indicatorKeys()).containsExactly("ema-fast");
    }

    @Test
    @DisplayName("U17.8 — индикаторный операнд с пустым именем")
    void u17_8_aBlankIndicatorKeyIsNotCollected() {
        StrategyCondition subject = condition(
                rule(StrategyConditionSourceType.INDICATOR, "   ", null),
                rule(StrategyConditionSourceType.INDICATOR, null, null));

        assertThat(subject.indicatorKeys()).isEmpty();
    }

    /** Разведение по типу источника, а не по заполненности поля. */
    @Test
    @DisplayName("U17.9 — структурный операнд")
    void u17_9_structureKeysLiveInTheirOwnSet() {
        StrategyCondition subject = condition(
                rule(StrategyConditionSourceType.MARKET_STRUCTURE, null, "range-1"));

        assertThat(subject.structureKeys()).containsExactly("range-1");
        assertThat(subject.indicatorKeys()).isEmpty();
    }

    /** Отбор идёт по типу источника, затем читается своё поле. */
    @Test
    @DisplayName("U17.10 — тип источника индикатор, но заполнено имя структуры")
    void u17_10_theSourceTypeChoosesTheFieldRead() {
        StrategyCondition subject = condition(
                rule(StrategyConditionSourceType.INDICATOR, null, "range-1"));

        assertThat(subject.indicatorKeys()).isEmpty();
        assertThat(subject.structureKeys()).isEmpty();
    }

    @Test
    @DisplayName("U17.11 — условие с ценовым операндом")
    void u17_11_priceIsMarketData() {
        assertThat(condition(rule(StrategyConditionSourceType.PRICE, null, null)).readsMarketData()).isTrue();
    }

    @Test
    @DisplayName("U17.12 — условие с операндом фазы")
    void u17_12_phaseIsMarketData() {
        assertThat(condition(rule(StrategyConditionSourceType.MARKET_PHASE, null, null)).readsMarketData())
                .isTrue();
    }

    @Test
    @DisplayName("U17.13 — условие с индикаторным именем")
    void u17_13_anIndicatorKeyIsMarketData() {
        assertThat(condition(rule(StrategyConditionSourceType.INDICATOR, "ema-fast", null)).readsMarketData())
                .isTrue();
    }

    @Test
    @DisplayName("U17.14 — условие со структурным именем")
    void u17_14_aStructureKeyIsMarketData() {
        assertThat(condition(rule(StrategyConditionSourceType.MARKET_STRUCTURE, null, "range-1"))
                .readsMarketData()).isTrue();
    }

    /**
     * Гейт свежести на такой шаг не ставится: иначе он останавливал бы
     * выход ровно тогда, когда данные пропали.
     */
    @Test
    @DisplayName("U17.15 — условие только из фактов сделки")
    void u17_15_dealFactsAreNotMarketData() {
        StrategyCondition subject = condition(
                rule(StrategyConditionSourceType.POSITION, null, null),
                rule(StrategyConditionSourceType.ORDER, null, null),
                rule(StrategyConditionSourceType.CONSTANT, null, null));

        assertThat(subject.readsMarketData()).isFalse();
    }

    @ParameterizedTest(name = "U17.16 — {0} в фазе {1}")
    @MethodSource("policyPhasePairs")
    @DisplayName("U17.16 — каждая пара «политика входа, тип фазы» из матрицы порознь")
    void u17_16_theEntryPolicyMatrix(PhaseEntryPolicy policy, MarketPhase.Type phase) {
        boolean expected = switch (policy) {
            case NO_TRADE -> true;
            case FOLLOW_PHASE, CONTRARIAN -> TREND_PHASES.contains(phase);
            case GRID -> MarketPhase.Type.RANGE.equals(phase);
        };

        assertThat(policy.isAllowedFor(phase)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(PhaseEntryPolicy.class)
    @DisplayName("U17.17 — политика, тип фазы пуст")
    void u17_17_anAbsentPhaseAllowsOnlyNoTrade(PhaseEntryPolicy policy) {
        assertThat(policy.isAllowedFor(null)).isEqualTo(PhaseEntryPolicy.NO_TRADE.equals(policy));
    }

    @Test
    @DisplayName("U17.18 — реакция на устаревание — мягкое закрытие")
    void u17_18_gracefulCloseIsNotKillSwitch() {
        assertThat(MarketDataExpiredAction.GRACEFUL_CLOSE.isGracefulClose()).isTrue();
        assertThat(MarketDataExpiredAction.GRACEFUL_CLOSE.isKillSwitch()).isFalse();
    }

    @Test
    @DisplayName("U17.19 — реакция на устаревание — аварийное снятие")
    void u17_19_killSwitchIsNotGracefulClose() {
        assertThat(MarketDataExpiredAction.KILL_SWITCH.isKillSwitch()).isTrue();
        assertThat(MarketDataExpiredAction.KILL_SWITCH.isGracefulClose()).isFalse();
    }

    /**
     * Выбор реакции по состоянию ветви: первый дизъюнкт — ветвь не
     * покрыта (пробел `G1` документа, добран под-шагом 3).
     */
    @Test
    @DisplayName("U17.20 — ветвь несёт риск и не покрыта")
    void u17_20_anUncoveredRiskBearingBranchIsUnprotected() {
        StrategyMarketDataExpiredSetting subject = setting();

        assertThat(subject.isUnprotected(true, false, false)).isTrue();
        assertThat(subject.resolve(true, false, false)).isEqualTo(MarketDataExpiredAction.KILL_SWITCH);
    }

    /** Второй дизъюнкт — уровень не резолвится (пробел `G1`). */
    @Test
    @DisplayName("U17.21 — ветвь несёт риск, покрыта, уровень не резолвится")
    void u17_21_anUnresolvedStopMakesTheBranchUnprotected() {
        StrategyMarketDataExpiredSetting subject = setting();

        assertThat(subject.isUnprotected(true, true, true)).isTrue();
        assertThat(subject.resolve(true, true, true)).isEqualTo(MarketDataExpiredAction.KILL_SWITCH);
    }

    /** Ветвь несёт риск и защищена (пробел `G1`). */
    @Test
    @DisplayName("U17.22 — ветвь несёт риск, покрыта, уровень резолвится")
    void u17_22_aCoveredBranchTakesTheProtectedAction() {
        StrategyMarketDataExpiredSetting subject = setting();

        assertThat(subject.isUnprotected(true, true, false)).isFalse();
        assertThat(subject.resolve(true, true, false)).isEqualTo(MarketDataExpiredAction.WAIT);
    }

    /** Без несомого риска ветвь защищённой считается всегда (пробел `G1`). */
    @Test
    @DisplayName("U17.23 — риска нет; операнды пусты")
    void u17_23_aRiskFreeBranchIsNeverUnprotected() {
        StrategyMarketDataExpiredSetting subject = setting();

        assertThat(subject.isUnprotected(false, false, true)).isFalse();
        assertThat(subject.isUnprotected(null, null, null)).isFalse();
        assertThat(subject.resolve(null, null, null)).isEqualTo(MarketDataExpiredAction.WAIT);
    }

    private static Stream<Arguments> policyPhasePairs() {
        return Stream.of(PhaseEntryPolicy.values())
                .flatMap(policy -> Stream.of(MarketPhase.Type.values())
                        .map(phase -> Arguments.of(policy, phase)));
    }

    private static StrategyMarketDataExpiredSetting setting() {
        return new StrategyMarketDataExpiredSetting(MarketDataExpiredAction.WAIT,
                MarketDataExpiredAction.KILL_SWITCH);
    }

    private static StrategyCondition condition(StrategyConditionRule... rules) {
        return new StrategyCondition(List.of(rules));
    }

    private static StrategyConditionRule rule(StrategyConditionSourceType sourceType,
                                              String indicatorKey, String structureKey) {
        StrategyConditionRule rule = new StrategyConditionRule();
        rule.setLeftOperand(operand(sourceType, indicatorKey, structureKey));
        return rule;
    }

    private static StrategyConditionOperand operand(StrategyConditionSourceType sourceType,
                                                    String indicatorKey, String structureKey) {
        StrategyConditionOperand operand = new StrategyConditionOperand();
        operand.setSourceType(sourceType);
        operand.setIndicatorKey(indicatorKey);
        operand.setStructureKey(structureKey);
        return operand;
    }
}
