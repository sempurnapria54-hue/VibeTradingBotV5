package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.NEIGHBOUR_TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.codes;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.pairState;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Снятие отдельной защиты при живой экспозиции — группа {@code U19}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/protection-coverage.json, величина {@code removalAllowed};
 * правило — docs/rules/live-risk-protection.md §«Снятие защиты —
 * риск-увеличивающее действие»).
 *
 * <p><b>Базовая сборка.</b> Транш с экспозицией в десять контрактов,
 * одной входной ногой и двумя живыми защитами — снимаемой (размер 15) и
 * остающейся (размер 12); ступени у пары нет. После снятия покрытие
 * равно 12 против экспозиции 10.
 *
 * <p><b>Клетки U19.11 и U19.12 здесь не прогоняются:</b> у них нет
 * ожидания — дом объявляет набор проверок ветки тем же, а точка входа
 * мерит два предиката (находка R-4, документ §«Кейсы, не прогоняемые
 * сегодня»).
 */
class ProtectionRemovalTest {

    /** Снимаемая защита: её покрытие из суммы и уходит. */
    private static final Long REMOVED_ID = 70L;

    /** Остающаяся защита транша. */
    private static final Long REMAINING_ID = 71L;

    private final RiskHarness harness = new RiskHarness();

    @Test
    @DisplayName("U19.1 — базовая сборка: решение разрешающее, перечень отказов пуст")
    void u19_1_aRemovalLeavingEnoughCoverageIsAllowed() {
        RiskValidationResult result = removalFrom(trancheWith("10", "12"));

        assertThat(result.getDecision()).isEqualTo(RiskDecision.ALLOWED);
        assertThat(result.getChecks()).isEmpty();
    }

    @Test
    @DisplayName("U19.2 — покрытие после снятия РАВНО экспозиции транша: граница включена")
    void u19_2_coverageExactlyAtTheExposurePasses() {
        assertThat(codes(removalFrom(trancheWith("10", "10")))).isEmpty();
    }

    @Test
    @DisplayName("U19.3 — покрытие ниже экспозиции: в пояснении названы оба числа")
    void u19_3_coverageBelowTheExposureIsRejectedAndNamesBothNumbers() {
        RiskValidationResult result = removalFrom(trancheWith("10", "5"));

        assertThat(codes(result)).containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
        assertThat(result.getChecks().getFirst().getComment()).contains("5").contains("10");
    }

    @Test
    @DisplayName("U19.4 — живая защита есть, а заявок нет вовсе: предикат отказал вычислением")
    void u19_4_aLiveProtectionWithoutAnyOrdersIsAnIncompleteGraph() {
        DealTranche withoutOrders = tranche(TRANCHE_ID, List.of(),
                List.of(protection(REMOVED_ID, TRANCHE_ID, STOP.toPlainString(), "15")));

        assertThat(codes(removalFrom(withoutOrders)))
                .containsExactly(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    @Test
    @DisplayName("U19.5 — защита соседнего транша нехватку не покрывает: область инварианта — транш")
    void u19_5_aNeighbouringTrancheProtectionIsNotAnOperand() {
        DealTranche neighbour = tranche(NEIGHBOUR_TRANCHE_ID, List.of(),
                List.of(protection(72L, NEIGHBOUR_TRANCHE_ID, STOP.toPlainString(), "1000")));
        DealTranche own = trancheWith("10", "5");

        assertThat(codes(harness.validator().validateProtectionRemoval(
                context(dealWith(own, neighbour)), own, REMOVED_ID)))
                .containsExactly(RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U19.6 — стои́т ступень радиуса, покрытие сохраняется: блок-сет накрывает снятие")
    void u19_6_aStandingRungBlocksTheRemovalEvenWithEnoughCoverage() {
        harness.givenPairState(pairState(Instrument.SafetyRung.TRADE_BLOCKED));

        assertThat(codes(removalFrom(trancheWith("10", "12"))))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD);
    }

    @Test
    @DisplayName("U19.7 — ступень и падение покрытия: два отказа, ступень первой")
    void u19_7_theRungPrecedesTheCoverageCode() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));

        assertThat(codes(removalFrom(trancheWith("10", "5"))))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                        RiskCheckCode.PROTECTION_COVERAGE_REDUCED);
    }

    @Test
    @DisplayName("U19.8 — ступень и неполный граф транша: неполнота идёт последней")
    void u19_8_theRungPrecedesTheIncompleteGraphCode() {
        harness.givenPairState(pairState(Instrument.SafetyRung.ENTRY_BLOCKED));
        DealTranche withoutOrders = tranche(TRANCHE_ID, List.of(),
                List.of(protection(REMOVED_ID, TRANCHE_ID, STOP.toPlainString(), "15")));

        assertThat(codes(removalFrom(withoutOrders)))
                .containsExactly(RiskCheckCode.INSTRUMENT_SAFETY_HOLD,
                        RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    @Test
    @DisplayName("U19.9 — снимаемая защита траншу не принадлежит: покрытие равно нынешнему")
    void u19_9_removingAForeignProtectionChangesNothing() {
        DealTranche tranche = trancheWith("10", "5");

        assertThat(codes(harness.validator().validateProtectionRemoval(
                context(dealWith(tranche)), tranche, 999L)))
                .as("сумма покрытий 15 + 5 остаётся целой и экспозицию 10 накрывает")
                .isEmpty();
    }

    @Test
    @DisplayName("U19.10 — экспозиция транша ноль, покрытия не остаётся: неравенство выполняется")
    void u19_10_aZeroExposureNeedsNoCoverage() {
        DealTranche tranche = tranche(TRANCHE_ID, List.of(entryLeg(Order.Status.ACTIVE, "0", "10", "0")),
                List.of(protection(REMOVED_ID, TRANCHE_ID, STOP.toPlainString(), "15")));
        tranche.setEntryFilled(BigDecimal.ZERO);

        assertThat(codes(harness.validator().validateProtectionRemoval(
                context(dealWith(tranche)), tranche, REMOVED_ID))).isEmpty();
    }

    /** Транш базовой сборки: названные экспозиция и размер остающейся защиты. */
    private static DealTranche trancheWith(String entryFilled, String remainingCoverage) {
        Order leg = entryLeg(Order.Status.COMPLETED, "0", entryFilled, entryFilled);
        AlgoOrder removed = protection(REMOVED_ID, TRANCHE_ID, STOP.toPlainString(), "15");
        AlgoOrder remaining = protection(REMAINING_ID, TRANCHE_ID, STOP.toPlainString(), remainingCoverage);
        DealTranche tranche = tranche(TRANCHE_ID, List.of(leg), List.of(removed, remaining));
        tranche.setEntryFilled(new BigDecimal(entryFilled));
        return tranche;
    }

    /** Ветвь снятия защиты на названном транше базовой сборки. */
    private RiskValidationResult removalFrom(DealTranche tranche) {
        return harness.validator().validateProtectionRemoval(context(dealWith(tranche)), tranche, REMOVED_ID);
    }

    /** Сделка базовой сборки с названными траншами. */
    private static Deal dealWith(DealTranche... tranches) {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranches));
        return deal;
    }
}
