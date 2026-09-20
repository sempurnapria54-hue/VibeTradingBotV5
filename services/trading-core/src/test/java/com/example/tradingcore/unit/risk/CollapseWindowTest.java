package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.reducingOnlyAction;
import static com.example.tradingcore.unit.risk.RiskFixture.workingContext;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Набор риска в окне сворачивания — группа {@code U16} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/risk-limits.json, величина
 * {@code riskCreatingUnderCollapseRejected}; правило —
 * docs/rules/exit-teardown-order.md §«Окно сворачивания: нового риска не
 * берёт ни один транш»).
 *
 * <p><b>Признак сворачивания приходит ГОТОВЫМ с модели сделки</b> —
 * преконтроль его не выводит, — поэтому окно задаётся статусом сделки, а
 * не подменённым предикатом.
 */
class CollapseWindowTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U16.1 — risk-creating вход, сделка не сворачивается: отказа нет")
    void u16_1_aRiskCreatingEntryOutsideTheCollapseWindowPasses() {
        assertThat(codes(harness.validate(entryAction(), workingContext()))).isEmpty();
    }

    @Test
    @DisplayName("U16.2 — risk-creating вход, сделка сворачивается: набор риска под сворачиванием")
    void u16_2_aRiskCreatingEntryUnderCollapseIsRejected() {
        assertThat(codes(harness.validate(entryAction(), collapsingContext())))
                .containsExactly(RiskCheckCode.RISK_CREATING_UNDER_COLLAPSE);
    }

    @Test
    @DisplayName("U16.3 — действие risk-weakening под сворачиванием: риска оно не набирает")
    void u16_3_aRiskWeakeningActUnderCollapsePasses() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()), collapsingContext())))
                .isEmpty();
    }

    @Test
    @DisplayName("U16.4 — статус сделки пуст: признак сворачивания пустым не бывает")
    void u16_4_anEmptyDealStatusIsNotACollapseWindow() {
        Deal withoutStatus = emptyDeal();
        withoutStatus.setStatus(null);

        assertThat(codes(harness.validate(entryAction(), context(withoutStatus)))).isEmpty();
    }

    @Test
    @DisplayName("U16.5 — действие «только уменьшает позицию» под сворачиванием: отказа нет")
    void u16_5_aPositionReducingActUnderCollapsePasses() {
        assertThat(codes(harness.validate(reducingOnlyAction(), collapsingContext()))).isEmpty();
    }

    /** Контекст базовой сборки со сделкой в окне сворачивания. */
    private static DealContext collapsingContext() {
        Deal collapsing = emptyDeal();
        collapsing.setStatus(Deal.Status.EXIT_PENDING);
        return context(collapsing);
    }
}
