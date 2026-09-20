package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.deal;
import static com.example.tradingcore.unit.risk.RiskFixture.detail;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.strategy.StrategyDetail;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Кумулятивный потолок — группа {@code U12} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина {@code withinCumulative}).
 *
 * <p><b>Базовая сборка</b> — U1.1 при риске акта 92.955; поактный
 * процент равен единице, то есть потолок сделки есть множитель на сотню.
 * Одновременный потолок стратегии поднят до десяти процентов — иначе он
 * срабатывал бы вместе с кумулятивным.
 */
class CumulativeCeilingTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U12.1 — взятое сделкой плюс риск акта ниже потолка: отказа нет")
    void u12_1_theSumWellWithinTheCeilingPasses() {
        assertThat(codes(harness.validate(entryAction(), context("100", "3")))).isEmpty();
    }

    @Test
    @DisplayName("U12.2 — сумма РАВНА потолку: граница включена")
    void u12_2_theSumExactlyAtTheCeilingPasses() {
        assertThat(codes(harness.validate(entryAction(), context("107.045", "2"))))
                .as("107.045 + 92.955 = 200 против потолка 2 × 100")
                .isEmpty();
    }

    @Test
    @DisplayName("U12.3 — сумма выше потолка: в фактическом значении сама сумма")
    void u12_3_theSumOverTheCeilingIsRejectedWithTheSumAsTheActualValue() {
        RiskValidationResult result = harness.validate(entryAction(), context("210", "3"));

        assertThat(codes(result)).containsExactly(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo("302.955");
    }

    @Test
    @DisplayName("U12.4 — заявленный риск сделки пуст: пустое слагаемое читается нулём")
    void u12_4_anEmptyDealRiskTakenReadsAsZero() {
        assertThat(codes(harness.validate(entryAction(), context(null, "1"))))
                .as("вычисление не роняется, и 0 + 92.955 укладывается в потолок 100")
                .isEmpty();
    }

    @Test
    @DisplayName("U12.5 — заявленный риск сам выше потолка, риск акта ноль: отказ есть")
    void u12_5_theDealRiskAloneCanBreachTheCeiling() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), context("350", "3"))))
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
    }

    @Test
    @DisplayName("U12.6 — множитель не объявлен: одновременный и катастрофический всё равно считаются")
    void u12_6_anUndeclaredMultiplierDoesNotStopTheNeighbouringCeilings() {
        DealContext dealContext = contextBuilder(deal(BigDecimal.ZERO, null))
                .strategyDetail(detail("1", null, "0.9", "29.99"))
                .build();

        assertThat(codes(harness.validate(entryAction(), dealContext)))
                .containsExactly(RiskCheckCode.RISK_APPETITE_NOT_CONFIGURED,
                        RiskCheckCode.RISK_PER_DEAL_SIMULTANEOUS_EXCEEDED,
                        RiskCheckCode.DEAL_NOTIONAL_EXCEEDED);
    }

    @Test
    @DisplayName("U12.7 — множитель меньше единицы: потолок сделки уже поактного")
    void u12_7_aMultiplierBelowOneMakesTheDealCeilingTighterThanThePerActionOne() {
        assertThat(codes(harness.validate(entryAction(), context(null, "0.5"))))
                .as("риск акта в поактный потолок 100 укладывается, а в кумулятивный 50 — нет")
                .containsExactly(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
    }

    /** Контекст группы: взятое сделкой за жизнь и множитель кумулятивного потолка. */
    private static DealContext context(String dealRiskTaken, String cumulativeMultiplier) {
        StrategyDetail detail = detail("1", cumulativeMultiplier, "10", "300");
        return contextBuilder(deal(RiskFixture.decimal(dealRiskTaken), null))
                .strategyDetail(detail)
                .build();
    }
}
