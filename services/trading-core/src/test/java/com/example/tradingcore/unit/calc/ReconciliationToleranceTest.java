package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.EXCHANGE;
import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contourProperties;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDealWithLegs;
import static com.example.tradingcore.unit.calc.CalcFixture.entryLeg;
import static com.example.tradingcore.unit.calc.CalcFixture.flow;
import static com.example.tradingcore.unit.calc.CalcFixture.reconciledEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.tolerance;
import static com.example.tradingcore.unit.calc.CalcFixture.unitFeeEntryLeg;
import static com.example.tradingcore.unit.calc.CalcFixture.withExchangeNumbers;
import static com.example.tradingcore.unit.calc.CalcFixture.workingTolerance;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.config.ExchangeContourProperties;
import com.example.tradingcore.config.PnlReconciliationProperties;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сверка: допуск и запрос биржевой ступени — группа {@code U5} документа
 * `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/spec/pnl-reconciliation.json §{@code epsilon},
 * §{@code expectedDealFee}; docs/rules/pnl-reconciliation.md §Допуск,
 * §«Реакция на расхождение», §«Разведочный режим допуска»; звенья
 * Z11-Z13).
 *
 * <p><b>Допуск один на сделку:</b> больший из пола и меньшего из двух
 * членов. Каждый кейс ниже предъявляет, КАКОЙ член связывает, парой
 * исходов вокруг границы — одиночный зелёный прогон этого не различает.
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U3}; три числа допуска
 * задаются кейсом явно, сделка несёт входные ноги с плановым риском,
 * плановыми ценами входа и стопа, плановым размером и стоимостью
 * контракта. Ожидаемая round-trip комиссия ноги {@link
 * CalcFixture#unitFeeEntryLeg()} равна единице расчётной валюты.
 */
class ReconciliationToleranceTest {

    /** Сделка с ногой единичной ожидаемой комиссии и эпизодом сверки. */
    private static Deal dealWith(Order... legs) {
        return enteredDealWithLegs(List.of(legs), reconciledEpisode());
    }

    private static DealReconciliationCalculator calculator(PnlReconciliationProperties tolerance) {
        return new DealReconciliationCalculator(contourProperties(), tolerance);
    }

    /** Строки разбивки с названной суммой реализованного результата и комиссией −1. */
    private static List<DealCashFlow> flowsWithRealized(String realized) {
        return List.of(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, realized),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"));
    }

    @Test
    @DisplayName("U5.1 — расхождение в пределах допуска: относительный член связывает")
    void u5_1_theRelativeMemberBindsOnALargeTurnover() {
        Deal deal = enteredDealWithLegs(List.of(unitFeeEntryLeg()),
                withExchangeNumbers(closedEpisode("994"), "995", "-1", "0", "0"));
        DealContext dealContext = context(deal, flowsWithRealized("1000"));

        assertThat(calculator(tolerance("0.01", "100", "0.01")).reconcile(dealContext))
                .as("относительный член 10.01 связывает расхождение 5")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(tolerance("0", "100", "0.01")).reconcile(dealContext))
                .as("обнулённая доля оборота оставляет пол 0.01 — то же расхождение уже сверх допуска")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.2 — на малом обороте связывает пол допуска")
    void u5_2_theFloorBindsOnASmallTurnover() {
        Deal deal = enteredDealWithLegs(List.of(unitFeeEntryLeg()),
                withExchangeNumbers(closedEpisode("8.7"), "9.7", "-1", "0", "0"));
        DealContext dealContext = context(deal, flowsWithRealized("10"));

        assertThat(calculator(tolerance("0.01", "1", "0.5")).reconcile(dealContext))
                .as("пол 0.5 вынесен наружу минимума и связывает расхождение 0.3")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(tolerance("0.01", "1", "0")).reconcile(dealContext))
                .as("без пола связывал бы относительный член 0.11, и расхождение вышло бы за него")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.3 — омиссионный член меньше композиционного и связывает")
    void u5_3_theSmallerOfTheTwoMembersIsTaken() {
        PnlReconciliationProperties properties = tolerance("1", "1", "0");
        Deal within = enteredDealWithLegs(List.of(unitFeeEntryLeg()),
                withExchangeNumbers(closedEpisode("8.1"), "9.1", "-1", "0", "0"));
        Deal beyond = enteredDealWithLegs(List.of(unitFeeEntryLeg()),
                withExchangeNumbers(closedEpisode("7.9"), "8.9", "-1", "0", "0"));

        assertThat(calculator(properties).reconcile(context(within, flowsWithRealized("10"))))
                .as("допуск равен омиссионному члену 1.0, хотя относительный равен 11")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(properties).reconcile(context(beyond, flowsWithRealized("10"))))
                .as("вторая сторона границы: 1.1 омиссионным членом уже не связывается")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.4 — сделка без входных ног: омиссионный член нулевой, допуск вырождается в пол")
    void u5_4_aDealWithoutEntryLegsFallsBackToTheFloor() {
        Deal deal = enteredDeal(reconciledEpisode());

        assertThat(calculator(tolerance("0.5", "2", "0.5"))
                .reconcile(context(deal, flowsWithRealized("11"))))
                .as("минимум схлопывается, и относительный член 6 допуска не даёт")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.5 — неисполненная нога комиссии не создаёт и в омиссионный член не входит")
    void u5_5_anUnfilledLegContributesNothingToTheOmissionMember() {
        PnlReconciliationProperties properties = tolerance("0.5", "2", "0.5");
        List<DealCashFlow> flows = flowsWithRealized("11");

        assertThat(calculator(properties).reconcile(context(dealWith(unitFeeEntryLeg()), flows)))
                .as("налитая нога даёт омиссионный член 2.0 и связывает расхождение 1")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(properties).reconcile(context(dealWith(entryLeg("11", "1", "")), flows)))
                .as("пустой налив: множество — предикат ВЗЯТОГО риска (Z12)")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
        assertThat(calculator(properties).reconcile(context(dealWith(entryLeg("11", "1", "0")), flows)))
                .as("нулевой налив читается так же")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.6 — снятая с частичным филлом нога комиссию создаёт и в член входит долей налива")
    void u5_6_aPartiallyFilledLegEntersWeightedByItsFillShare() {
        PnlReconciliationProperties properties = tolerance("0.5", "2", "0");
        List<DealCashFlow> flows = flowsWithRealized("11.5");
        Order halfFilled = entryLeg("11", "1", "0.5");
        halfFilled.setStatus(Order.Status.CANCELED);

        assertThat(calculator(properties).reconcile(context(dealWith(unitFeeEntryLeg()), flows)))
                .as("полный налив даёт омиссионный член 2.0 и связывает расхождение 1.5")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(properties).reconcile(context(dealWith(halfFilled), flows)))
                .as("половинный налив взвешивает член вдвое — 1.0, и то же расхождение выходит за него")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.7 — нога с пустым плановым риском выпадает из множества ног")
    void u5_7_aLegWithoutAPlannedRiskLeavesTheSet() {
        PnlReconciliationProperties properties = tolerance("0.5", "2", "0");
        List<DealCashFlow> flows = flowsWithRealized("11.5");

        assertThat(calculator(properties).reconcile(context(dealWith(unitFeeEntryLeg()), flows)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(properties).reconcile(context(dealWith(entryLeg("", "1", "1")), flows)))
                .as("конъюнкт непустоты планового риска резолвится домом чисел риска")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.8 — нога с нулевым плановым размером: доля налива вырождается в ноль, а не в деление")
    void u5_8_aZeroPlannedSizeDegeneratesTheFillShareToZero() {
        PnlReconciliationProperties properties = tolerance("0.5", "2", "0");
        List<DealCashFlow> flows = flowsWithRealized("11.5");

        assertThat(calculator(properties).reconcile(context(dealWith(unitFeeEntryLeg()), flows)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(calculator(properties).reconcile(context(dealWith(entryLeg("11", "0", "1")), flows)))
                .as("слагаемое ноги нулевое, и расчёт не роняется")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.9 — три числа допуска не заданы вовсе: допуск нулевой, ошибка запрещающая")
    void u5_9_unsetToleranceNumbersGiveAZeroTolerance() {
        Deal deal = enteredDealWithLegs(List.of(unitFeeEntryLeg()), reconciledEpisode());

        assertThat(calculator(new PnlReconciliationProperties())
                .reconcile(context(deal, flowsWithRealized("10.001"))))
                .as("умолчания класса свойств — нули (Z11)")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U5.10 — исход «разошлась» в боевом режиме: ступень запрашивается")
    void u5_10_aMismatchInLiveModeRequestsTheRung() {
        assertThat(liveMode().rungRequested(liveContext(), Deal.ReconciliationStatus.MISMATCHED))
                .isTrue();
    }

    @Test
    @DisplayName("U5.11 — исход «разошлась» в разведочном режиме: ступень не запрашивается")
    void u5_11_aMismatchInExploratoryModeDoesNotRequestTheRung() {
        DealReconciliationCalculator exploratory =
                new DealReconciliationCalculator(contourProperties(), workingTolerance());

        assertThat(exploratory.rungRequested(liveContext(), Deal.ReconciliationStatus.MISMATCHED))
                .as("до калибровки расхождение неотличимо от «допуск не тот»; умолчание режима истинно")
                .isFalse();
    }

    @Test
    @DisplayName("U5.12 — исход «сошлась» в боевом режиме: ступень не запрашивается")
    void u5_12_aMatchDoesNotRequestTheRung() {
        assertThat(liveMode().rungRequested(liveContext(), Deal.ReconciliationStatus.MATCHED)).isFalse();
    }

    @Test
    @DisplayName("U5.13 — исход «не гонялась» в боевом режиме: ступень не запрашивается")
    void u5_13_aNotRunOutcomeDoesNotRequestTheRung() {
        assertThat(liveMode().rungRequested(liveContext(), Deal.ReconciliationStatus.NOT_RUN))
                .as("запрос стои́т на равенстве исходу «разошлась», а не на его отрицании (Z13)")
                .isFalse();
    }

    @Test
    @DisplayName("U5.14 — пустой признак разведочного режима: ступень не запрашивается")
    void u5_14_anEmptyExploratoryFlagDoesNotRequestTheRung() {
        ExchangeContourProperties properties = contourProperties();
        properties.getExchanges().get(EXCHANGE).setReconciliationExploratory(null);
        DealReconciliationCalculator calculator =
                new DealReconciliationCalculator(properties, workingTolerance());

        assertThat(calculator.rungRequested(liveContext(), Deal.ReconciliationStatus.MISMATCHED))
                .as("пустое читается как «разведочный» — состояние недостижимо, поле инициализировано "
                        + "истиной (Z13)")
                .isFalse();
    }

    @Test
    @DisplayName("U5.15 — исход «разошлась» в разведочном режиме: отчёт без ступени запрашивается")
    void u5_15_aMismatchInExploratoryModeRequestsTheJournal() {
        DealReconciliationCalculator exploratory =
                new DealReconciliationCalculator(contourProperties(), workingTolerance());

        assertThat(exploratory.journalOnlyRequested(liveContext(), Deal.ReconciliationStatus.MISMATCHED))
                .as("разведочный режим заведён ради разбора: расхождение видно не одной колонкой сделки")
                .isTrue();
    }

    @Test
    @DisplayName("U5.16 — исход «разошлась» в боевом режиме: отчёта без ступени нет — его заводит ступень")
    void u5_16_aMismatchInLiveModeDoesNotRequestTheJournal() {
        assertThat(liveMode().journalOnlyRequested(liveContext(), Deal.ReconciliationStatus.MISMATCHED))
                .isFalse();
    }

    @Test
    @DisplayName("U5.17 — пустой признак режима: отчёт запрашивается — ровно одна из двух реакций")
    void u5_17_anEmptyExploratoryFlagRequestsTheJournal() {
        ExchangeContourProperties properties = contourProperties();
        properties.getExchanges().get(EXCHANGE).setReconciliationExploratory(null);
        DealReconciliationCalculator calculator =
                new DealReconciliationCalculator(properties, workingTolerance());

        assertThat(calculator.journalOnlyRequested(liveContext(), Deal.ReconciliationStatus.MISMATCHED))
                .as("ступени пустой режим не просит (U5.14), значит просит отчёта")
                .isTrue();
        assertThat(calculator.journalOnlyRequested(liveContext(), Deal.ReconciliationStatus.MATCHED))
                .isFalse();
    }

    /** Калькулятор с боевым (не разведочным) режимом допуска у контура площадки. */
    private static DealReconciliationCalculator liveMode() {
        ExchangeContourProperties properties = contourProperties();
        properties.getExchanges().get(EXCHANGE).setReconciliationExploratory(false);
        return new DealReconciliationCalculator(properties, workingTolerance());
    }

    private static DealContext liveContext() {
        return context(enteredDeal(reconciledEpisode()), List.of());
    }
}
