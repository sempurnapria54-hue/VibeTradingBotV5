package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contourProperties;
import static com.example.tradingcore.unit.calc.CalcFixture.contourPropertiesWithoutSection;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.episodeWithoutCloseRecord;
import static com.example.tradingcore.unit.calc.CalcFixture.flow;
import static com.example.tradingcore.unit.calc.CalcFixture.reconciledEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.workingTolerance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Сверка: обязанность и порядок охран — группа {@code U3} документа
 * `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/spec/pnl-reconciliation.json §{@code dutyArisen};
 * docs/rules/pnl-reconciliation.md §«Обязанность сверки»; звенья Z6, Z7).
 *
 * <p><b>Обязанность не наступила — это «не были обязаны», а не «посчитали
 * и сошлось».</b> Третьего значения у перечня нет, и различение несёт
 * терминальный статус, а не признак.
 *
 * <p><b>Базовая сборка:</b> три числа допуска заданы рабочими значениями
 * конфигурации; контур несёт секцию площадки; сделка — один эпизод с
 * добытой записью закрытия, непустой порог доказанного покрытия и
 * непустой момент добычи движений.
 */
class ReconciliationDutyTest {

    private final DealReconciliationCalculator calculator =
            new DealReconciliationCalculator(contourProperties(), workingTolerance());

    /** Строки разбивки, сходящиеся с четырьмя числами {@link CalcFixture#reconciledEpisode()}. */
    private static List<DealCashFlow> matchingFlows() {
        return List.of(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"));
    }

    @Test
    @DisplayName("U3.1 — базовая сборка: левые и правые стороны сходятся")
    void u3_1_theSidesAgreeOnTheBaseAssembly() {
        Deal deal = enteredDeal(reconciledEpisode());

        assertThat(calculator.reconcile(context(deal, matchingFlows())))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U3.2 — запись закрытия не добыта у одного из эпизодов: сверка не была обязана")
    void u3_2_anUnfetchedCloseRecordMeansTheDutyDidNotArise() {
        Deal deal = enteredDeal(reconciledEpisode(), episodeWithoutCloseRecord());

        assertThat(calculator.reconcile(context(deal, matchingFlows())))
                .as("исход «не гонялась», а не «сошлась»")
                .isEqualTo(Deal.ReconciliationStatus.NOT_RUN);
    }

    @Test
    @DisplayName("U3.3 — порог доказанного покрытия пуст: сверка не была обязана")
    void u3_3_anEmptyCoverageThresholdMeansTheDutyDidNotArise() {
        Deal deal = enteredDeal(reconciledEpisode());
        deal.setCoverageProvenThrough(null);

        assertThat(calculator.reconcile(context(deal, matchingFlows())))
                .isEqualTo(Deal.ReconciliationStatus.NOT_RUN);
    }

    @Test
    @DisplayName("U3.4 — движения не добывались: сверка не была обязана")
    void u3_4_anEmptyFetchMomentMeansTheDutyDidNotArise() {
        Deal deal = enteredDeal(reconciledEpisode());
        deal.setBillsFetchedThrough(null);

        assertThat(calculator.reconcile(context(deal, matchingFlows())))
                .isEqualTo(Deal.ReconciliationStatus.NOT_RUN);
    }

    @Test
    @DisplayName("U3.5 — охрана обязанности стои́т первой: пустые операнды до арифметики не доходят")
    void u3_5_theDutyGuardStandsBeforeEveryOtherOperand() {
        DealReconciliationCalculator withoutSection =
                new DealReconciliationCalculator(contourPropertiesWithoutSection(), workingTolerance());
        Deal deal = enteredDeal(reconciledEpisode());
        deal.setCoverageProvenThrough(null);

        assertThatCode(() -> assertThat(withoutSection.reconcile(context(deal, matchingFlows(), null)))
                .isEqualTo(Deal.ReconciliationStatus.NOT_RUN))
                .as("ни валюта, ни контур, ни допуск не читаются (Z6)")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U3.6 — вошедшая сделка без единого эпизода: обязанность наступила, стороны нулевые")
    void u3_6_anEnteredDealWithoutEpisodesIsStillObligedAndMatches() {
        assertThat(calculator.reconcile(context(enteredDeal(), List.of())))
                .as("агрегат «записи добыты у всех» по пустому списку истинен (Z7)")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U3.7 — все три конъюнкта выполнены, расхождение сверх допуска: разошлась")
    void u3_7_aDiscrepancyBeyondTheToleranceGivesMismatched() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "20"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

}
