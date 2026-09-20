package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.CONTRACT_VALUE;
import static com.example.tradingcore.unit.risk.RiskFixture.FEE;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.rules;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Поактный потолок и его развод на два кода — группа {@code U11}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина {@code actWithinPerAction};
 * развод кодов — docs/processes/risk-evaluation.md §«Карв-аут
 * исчерпанного бюджета сделки»).
 *
 * <p><b>Базовая сборка</b> — U1.1 при риске акта 92.955. Одновременный
 * потолок стратегии у детали группы поднят до десяти процентов: иначе он
 * срабатывал бы вместе с поактным и предмет группы делился бы на два
 * кода.
 */
class PerActionCeilingTest {

    /** Потолок 100: риск акта заметно ниже. */
    private static final StrategyDetail ROOMY = detail("1", "3", "10", "300");

    /** Потолок 92.955: риск акта равен ему в точности. */
    private static final StrategyDetail EXACTLY_AT_THE_CEILING = detail("0.92955", "3", "10", "300");

    /** Потолок 92.95: риск акта его перебирает. */
    private static final StrategyDetail A_HAIR_BELOW = detail("0.9295", "3", "10", "300");

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U11.1 — риск акта заметно ниже потолка: отказа нет")
    void u11_1_anActWellWithinTheBudgetPasses() {
        assertThat(codes(harness.validate(entryAction(), context(ROOMY)))).isEmpty();
    }

    @Test
    @DisplayName("U11.2 — риск акта РАВЕН потолку: граница включена")
    void u11_2_anActExactlyAtTheBudgetPasses() {
        assertThat(codes(harness.validate(entryAction(), context(EXACTLY_AT_THE_CEILING)))).isEmpty();
    }

    @Test
    @DisplayName("U11.3 — риск акта выше потолка при размере выше минимального лота")
    void u11_3_anActOverTheBudgetIsRejectedWithTheActRiskAsTheActualValue() {
        RiskValidationResult result = harness.validate(entryAction(), context(A_HAIR_BELOW));

        assertThat(codes(result)).containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo("92.955");
    }

    @Test
    @DisplayName("U11.4 — размер РАВЕН минимальному лоту: ветвь подъёма потолком не ограничена")
    void u11_4_aSizeExactlyAtTheMinimumLotGetsTheIndivisibleLotCode() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "10", FEE));

        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW))))
                .containsExactly(RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET);
    }

    @Test
    @DisplayName("U11.5 — размер НИЖЕ минимального лота: тот же код плюс отказ по минимуму")
    void u11_5_aSizeBelowTheMinimumLotKeepsTheIndivisibleLotCode() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", "20", FEE));

        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW))))
                .containsExactly(RiskCheckCode.SIZE_BELOW_MIN,
                        RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET);
    }

    @Test
    @DisplayName("U11.6 — минимальный лот у правил пуст: карв-аут не применяется")
    void u11_6_anAbsentMinimumLotFallsBackToTheCalculationMismatchCode() {
        harness.givenRules(rules(CONTRACT_VALUE, "1", null, FEE));

        assertThat(codes(harness.validate(entryAction(), context(A_HAIR_BELOW))))
                .containsExactly(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U11.7 — процент риска на действие не объявлен: ни один из потолков не считается")
    void u11_7_anUndeclaredPerActionPercentStopsEveryCeiling() {
        assertThat(codes(harness.validate(entryAction(), context(detail(null, "3", "10", "300")))))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U11.8 — детали стратегии в контексте нет вовсе: тот же отказ той же строкой")
    void u11_8_anAbsentStrategyDetailStopsEveryCeiling() {
        assertThat(codes(harness.validate(entryAction(), context(null))))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("U11.9 — риск акта отрицателен: неравенство выполняется")
    void u11_9_aNegativeActRiskSatisfiesTheInequality() {
        assertThat(codes(harness.validate(entryAction("10", ANCHOR, "3100"), context(A_HAIR_BELOW))))
                .doesNotContain(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                        RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET);
    }

    @Test
    @DisplayName("U11.10 — действие risk-weakening при нулевом потолке: слагаемое акта — ноль")
    void u11_10_aRiskWeakeningActPassesAZeroCeiling() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()),
                context(detail("0", "3", "10", "300"))))).isEmpty();
    }

    /** Контекст базовой сборки с названной деталью стратегии. */
    private static DealContext context(StrategyDetail detail) {
        return contextBuilder(emptyDeal()).strategyDetail(detail).build();
    }
}
