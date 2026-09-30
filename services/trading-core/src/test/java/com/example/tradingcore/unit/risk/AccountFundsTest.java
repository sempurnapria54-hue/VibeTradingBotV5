package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.balanceSnapshot;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryAction;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.minutesAgo;
import static com.example.tradingcore.unit.risk.RiskFixture.priceBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.protectionAction;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static com.example.tradingcore.unit.risk.RiskFixture.withPrice;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.strategy.engine.calc.CalculatedStrategyAction;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.balance.BalanceContainer;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Проверки средств счёта — группа {@code U31} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskValidator.md §«Проверки средств счёта»; форма —
 * docs/spec/risk-limits.json, величины {@code actRequiredMargin},
 * {@code balanceNotEnoughBlocksAction} и {@code borrowOrDebtDetected}).
 *
 * <p><b>Снимок средств собирается настоящими полями</b> — моментом у
 * площадки и остатками строки расчётной валюты; предикаты свежести и
 * выбора строки считаются сами.
 *
 * <p><b>Базовая сборка</b> — U1.1 со свежим снимком: маржа акта при плече
 * 10 равна {@code 3000 / 10 + 0.0005 × 3000 = 301.5}.
 */
class AccountFundsTest {

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U31.1 — свободного остатка хватает: отказа нет")
    void u31_1_enoughFreeBalancePasses() {
        assertThat(codes(harness.validate(entryAction(), funded(fresh("1000", "1000", "1000"))))).isEmpty();
    }

    @Test
    @DisplayName("U31.2 — свободного остатка не хватает на маржу и комиссию акта: единственный отказ")
    void u31_2_notEnoughFreeBalanceIsRejected() {
        RiskValidationResult result = harness.validate(entryAction(), funded(fresh("1000", "1000", "301")));

        assertThat(codes(result)).containsExactly(RiskCheckCode.BALANCE_NOT_ENOUGH);
        assertThat(result.getChecks().getFirst().getActualValue())
                .isEqualByComparingTo(new BigDecimal("301.5"));
    }

    @Test
    @DisplayName("U31.3 — свободный остаток РАВЕН требованию: граница включена")
    void u31_3_freeBalanceExactlyAtTheRequirementPasses() {
        assertThat(codes(harness.validate(entryAction(), funded(fresh("1000", "1000", "301.5"))))).isEmpty();
    }

    @Test
    @DisplayName("U31.4 — снимок старше толерантности: проверки средств не меряются")
    void u31_4_aStaleSnapshotLeavesTheFundsChecksUnmeasured() {
        BalanceContainer stale = balanceSnapshot(minutesAgo(10), "-5", "1000", "1");

        assertThat(codes(harness.validate(entryAction(), funded(stale)))).isEmpty();
    }

    @Test
    @DisplayName("U31.5 — снимка средств нет вовсе: проверки средств не меряются")
    void u31_5_anAbsentSnapshotLeavesTheFundsChecksUnmeasured() {
        assertThat(codes(harness.validate(entryAction(), funded(null)))).isEmpty();
    }

    @Test
    @DisplayName("U31.6 — толерантность не объявлена: свежесть не измерена, проверки не меряются")
    void u31_6_anUndeclaredToleranceLeavesTheFundsChecksUnmeasured() {
        harness.givenBalanceFreshness(null);

        assertThat(codes(harness.validate(entryAction(), funded(fresh("-5", "1000", "1"))))).isEmpty();
    }

    @Test
    @DisplayName("U31.7 — у свежего снимка нет строки расчётной валюты: снимок негоден")
    void u31_7_aFreshSnapshotWithoutTheSettlementRowIsInvalid() {
        BalanceContainer foreign = balanceSnapshot(minutesAgo(0), "BTC", "1000", "1000", "1000");

        assertThat(codes(harness.validate(entryAction(), funded(foreign))))
                .containsExactly(RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U31.8 — свободный остаток строки не наблюдён: снимок негоден, а не остаток нулевой")
    void u31_8_anUnobservedAvailableBalanceIsInvalidNotZero() {
        assertThat(codes(harness.validate(entryAction(), funded(fresh("1000", "1000", null)))))
                .containsExactly(RiskCheckCode.BALANCE_INVALID);
    }

    @Test
    @DisplayName("U31.9 — акт риска не создаёт: достаточность средств не меряется")
    void u31_9_aProtectiveActIsNotMeasuredForFreeBalance() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()),
                funded(fresh("1000", "1000", "1"))))).isEmpty();
    }

    @Test
    @DisplayName("U31.10 — живая входная нога, площадкой не подтверждённая, прибавляется к требованию")
    void u31_10_anUnconfirmedLiveEntryLegAddsToTheRequirement() {
        DealContext context = fundedDeal(dealWithLeg(null), fresh("1000", "1000", "600"));

        assertThat(codes(harness.validate(entryAction(), context)))
                .containsExactly(RiskCheckCode.BALANCE_NOT_ENOUGH);
    }

    @Test
    @DisplayName("U31.11 — нога, подтверждённая до момента снимка, в остатке уже учтена")
    void u31_11_aLegConfirmedBeforeTheSnapshotIsAlreadyReflected() {
        OffsetDateTime snapshotAt = minutesAgo(1);
        DealContext context = fundedDeal(dealWithLeg(snapshotAt.minusMinutes(1)),
                balanceSnapshot(snapshotAt, "1000", "1000", "600"));

        assertThat(codes(harness.validate(entryAction(), context))).isEmpty();
    }

    @Test
    @DisplayName("U31.12 — нога, подтверждённая позже момента снимка, прибавляется к требованию")
    void u31_12_aLegConfirmedAfterTheSnapshotAddsToTheRequirement() {
        OffsetDateTime snapshotAt = minutesAgo(2);
        DealContext context = fundedDeal(dealWithLeg(snapshotAt.plusMinutes(1)),
                balanceSnapshot(snapshotAt, "1000", "1000", "600"));

        assertThat(codes(harness.validate(entryAction(), context)))
                .containsExactly(RiskCheckCode.BALANCE_NOT_ENOUGH);
    }

    @Test
    @DisplayName("U31.13 — отрицательный денежный остаток расчётной валюты: обязательство")
    void u31_13_aNegativeCashBalanceIsALiability() {
        RiskValidationResult result = harness.validate(entryAction(), funded(fresh("-5", "1000", "1000")));

        assertThat(codes(result)).containsExactly(RiskCheckCode.BORROW_OR_DEBT_DETECTED);
        assertThat(result.getChecks().getFirst().getActualValue()).isEqualByComparingTo(new BigDecimal("-5"));
    }

    @Test
    @DisplayName("U31.14 — отрицательный капитал расчётной валюты: обязательство")
    void u31_14_aNegativeEquityIsALiability() {
        assertThat(codes(harness.validate(entryAction(), funded(fresh("10", "-1", "1000")))))
                .containsExactly(RiskCheckCode.BORROW_OR_DEBT_DETECTED);
    }

    @Test
    @DisplayName("U31.15 — нулевые остаток и капитал обязательством не являются")
    void u31_15_zeroCashAndEquityAreNoLiability() {
        assertThat(codes(harness.validate(entryAction(), funded(fresh("0", "0", "1000"))))).isEmpty();
    }

    @Test
    @DisplayName("U31.16 — признак обязательства отвергает и защитное действие: область — всякий акт")
    void u31_16_aLiabilityRejectsAProtectiveActToo() {
        assertThat(codes(harness.validate(protectionAction(STOP.toPlainString()),
                funded(fresh("-5", "1000", "1000")))))
                .containsExactly(RiskCheckCode.BORROW_OR_DEBT_DETECTED);
    }

    @Test
    @DisplayName("U31.17 — обязательство и нехватка сразу: два члена в порядке проверок")
    void u31_17_liabilityAndShortfallAccumulateInOrder() {
        assertThat(codes(harness.validate(entryAction(), funded(fresh("-5", "1000", "1")))))
                .containsExactly(RiskCheckCode.BORROW_OR_DEBT_DETECTED, RiskCheckCode.BALANCE_NOT_ENOUGH);
    }

    @Test
    @DisplayName("U31.18 — цена риск-создающего акта не резолвлена: нотинал и маржа не измерены, два отказа")
    void u31_18_anUnresolvedActPriceLeavesTheMarginUnmeasured() {
        Deal deal = emptyDeal();
        deal.setPositions(List.of(episode("1", ANCHOR)));
        CalculatedStrategyAction unpriced = withPrice(entryAction("5", ANCHOR, STOP.toPlainString()),
                priceBuilder(null, STOP.toPlainString()).build());

        assertThat(codes(harness.validate(unpriced, fundedDeal(deal, fresh("1000", "1000", "1000")))))
                .containsExactly(RiskCheckCode.CALCULATED_ACTION_INVALID, RiskCheckCode.CALCULATED_ACTION_INVALID);
    }

    /** Свежий снимок: обновлён площадкой только что. */
    private static BalanceContainer fresh(String cash, String equity, String available) {
        return balanceSnapshot(minutesAgo(0), cash, equity, available);
    }

    /** Контекст базовой сборки поверх пустой сделки с названным снимком средств. */
    private static DealContext funded(BalanceContainer snapshot) {
        return fundedDeal(emptyDeal(), snapshot);
    }

    /** Контекст базовой сборки поверх названной сделки с названным снимком средств. */
    private static DealContext fundedDeal(Deal deal, BalanceContainer snapshot) {
        return contextBuilder(deal).balanceContainer(snapshot).build();
    }

    /**
     * Сделка с одной живой неисполненной входной ногой в 10 контрактов по
     * 3000 и нулевым заявленным риском — потолков нога не трогает, а к
     * требованию маржи прибавляет {@code 3000 × (0.1 + 0.0005) = 301.5}.
     *
     * @param confirmedAt момент подтверждения ноги площадкой; пусто — не
     *                    подтверждена
     */
    private static Deal dealWithLeg(OffsetDateTime confirmedAt) {
        Order leg = entryLeg(Order.Status.ACTIVE, "0", "10", "0");
        leg.setExternalCreatedAt(confirmedAt);
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(List.of(leg), List.of())));
        return deal;
    }
}
