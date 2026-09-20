package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.BILLS_FETCHED;
import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contourPropertiesWithoutSection;
import static com.example.tradingcore.unit.calc.CalcFixture.dealWithoutEntry;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingcore.domain.command.calc.DealTerminalFeatures;
import com.example.tradingcore.util.Constants;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Признаки терминала: полнота разбивки, знаменатель и охрана от
 * перезаписи — группа {@code U7} документа
 * `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/components/RefreshBillsExecutor.md; docs/spec/cash-flow-linkage.json
 * §{@code lowerBound}; docs/spec/deal-lifecycle.json
 * §{@code benchmarkAvailabilityOnTerminal};
 * docs/models/domain/aggregate/Deal.md §Енумы; звенья Z14, Z18, Z19).
 *
 * <p><b>Финализированное число свидетельствует и о признаках:</b>
 * четвёрка записана на ПОЛНОМ графе, и аварийный терминал, приходящий на
 * усечённом, пересчитывать её не вправе.
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U6}; глубина архива
 * движений у контура — рабочее значение конфигурации (90 дней), если
 * кейс не задаёт иного.
 */
class TerminalBreakdownAndBenchmarkTest {

    private final TerminalFeaturesHarness harness = new TerminalFeaturesHarness();

    private DealTerminalFeatures applyTo(Deal deal) {
        return harness.apply(context(deal, List.of()), false);
    }

    @Test
    @DisplayName("U7.1 — добыча движений не выполнялась: полнота разбивки «не оценивалось»")
    void u7_1_anEmptyFetchMomentGivesNotAssessed() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setBillsFetchedThrough(null);

        assertThat(applyTo(deal).getBreakdownIncomplete())
                .as("сравнивать нечего, и это единственный триггер значения (Z18)")
                .isEqualTo(Deal.BreakdownCompleteness.NOT_ASSESSED);
    }

    @Test
    @DisplayName("U7.2 — обе нижние границы окна пусты: то же «не оценивалось»")
    void u7_2_anEmptyLowerBoundGivesNotAssessed() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setBillsWindowBegin(null);
        deal.setExternalCreatedAt(null);

        assertThat(applyTo(deal).getBreakdownIncomplete())
                .isEqualTo(Deal.BreakdownCompleteness.NOT_ASSESSED);
    }

    @Test
    @DisplayName("U7.3 — окно добычи короче глубины архива: полнота «полная»")
    void u7_3_aWindowShorterThanTheArchiveDepthIsComplete() {
        assertThat(applyTo(enteredDeal(closedEpisode("10"))).getBreakdownIncomplete())
                .isEqualTo(Deal.BreakdownCompleteness.COMPLETE);
    }

    @Test
    @DisplayName("U7.4 — окно добычи длиннее глубины архива: «неполная по окну»")
    void u7_4_aWindowLongerThanTheArchiveDepthIsIncomplete() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setBillsWindowBegin(BILLS_FETCHED.minusDays(100));

        assertThat(applyTo(deal).getBreakdownIncomplete())
                .isEqualTo(Deal.BreakdownCompleteness.INCOMPLETE_BY_WINDOW);
    }

    @Test
    @DisplayName("U7.5 — окно ровно равно глубине архива: сравнение строгое, равенство в неполноту не уводит")
    void u7_5_aWindowEqualToTheArchiveDepthStaysComplete() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setBillsWindowBegin(BILLS_FETCHED.minusDays(90));

        assertThat(applyTo(deal).getBreakdownIncomplete())
                .as("сравнение с глубиной — строгое «больше» (Z18)")
                .isEqualTo(Deal.BreakdownCompleteness.COMPLETE);
    }

    @Test
    @DisplayName("U7.6 — пустая durable-колонка: граница берётся суррогатом биржевого момента заведения")
    void u7_6_theExchangeCreationMomentSurrogatesTheLowerBound() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setBillsWindowBegin(null);
        deal.setExternalCreatedAt(BILLS_FETCHED.minusDays(100));

        assertThat(applyTo(deal).getBreakdownIncomplete())
                .as("предикат модели подставляет момент заведения, колонку не трогая")
                .isEqualTo(Deal.BreakdownCompleteness.INCOMPLETE_BY_WINDOW);
    }

    @Test
    @DisplayName("U7.7 — контур площадки отсутствует: глубина берётся умолчанием класса контура")
    void u7_7_aMissingContourSectionFallsBackToTheDefaultDepth() {
        TerminalFeaturesHarness withoutSection =
                new TerminalFeaturesHarness(contourPropertiesWithoutSection());
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setBillsWindowBegin(BILLS_FETCHED.minusDays(30));

        assertThat(withoutSection.apply(context(deal, List.of()), false).getBreakdownIncomplete())
                .as("расчёт идёт, а не роняется")
                .isEqualTo(Deal.BreakdownCompleteness.COMPLETE);
    }

    @Test
    @DisplayName("U7.8 — сделка не входила: полнота разбивки и исход сверки пусты")
    void u7_8_aDealThatNeverEnteredGetsNeitherBreakdownNorReconciliation() {
        Deal deal = dealWithoutEntry(closedEpisode("10"));
        deal.setBreakdownIncomplete(Deal.BreakdownCompleteness.COMPLETE);
        deal.setReconciliationStatus(Deal.ReconciliationStatus.MATCHED);

        DealTerminalFeatures features = applyTo(deal);

        assertThat(features.getBreakdownIncomplete()).isNull();
        assertThat(features.getReconciliationStatus()).isNull();
        assertThat(deal.getBreakdownIncomplete())
                .as("пустые признаки кладутся на модель наравне с непустыми: «пусто» означает "
                        + "«неприменим», и прежние значения оно затирает")
                .isNull();
        assertThat(deal.getReconciliationStatus()).isNull();
    }

    @Test
    @DisplayName("U7.9 — вход был, плановая величина риска положительна: знаменатель доступен")
    void u7_9_aPositivePlannedRiskGivesAnAvailableBenchmark() {
        assertThat(applyTo(enteredDeal(closedEpisode("10"))).getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("U7.10 — вошедшая сделка без знаменателя получает на терминале аномалию")
    void u7_10_anEmptyPlannedRiskGivesAMissingBenchmarkWithAReport() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setPlannedRiskAmount(null);

        assertThat(applyTo(deal).getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.MISSING);
        assertThat(harness.journalledCodes()).containsExactly(Constants.Hold.RISK_BENCHMARK_MISSING);
    }

    @Test
    @DisplayName("U7.11 — нулевая плановая величина риска: вырожденный знаменатель читается отсутствующим")
    void u7_11_aZeroPlannedRiskIsReadAsMissing() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setPlannedRiskAmount(BigDecimal.ZERO);

        assertThat(applyTo(deal).getRiskBenchmarkAvailability())
                .as("нулём множитель не делится (Z19)")
                .isEqualTo(Deal.RiskBenchmarkAvailability.MISSING);
    }

    @Test
    @DisplayName("U7.12 — отрицательная плановая величина риска: предикат стои́т на строгой положительности")
    void u7_12_aNegativePlannedRiskIsReadAsMissing() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setPlannedRiskAmount(new BigDecimal("-1"));

        assertThat(applyTo(deal).getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.MISSING);
    }

    @Test
    @DisplayName("U7.13 — сделка не входила: знаменатель «неприменимо», нормальная популяция без отчёта")
    void u7_13_aDealThatNeverEnteredGetsNotApplicable() {
        DealTerminalFeatures features = applyTo(dealWithoutEntry(closedEpisode("10")));

        assertThat(features.getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.NOT_APPLICABLE);
        assertThat(harness.journalledCodes()).doesNotContain(Constants.Hold.RISK_BENCHMARK_MISSING);
    }

    @Test
    @DisplayName("U7.14 — аварийный терминал на сделке с УЖЕ финализированным числом не пишет ничего")
    void u7_14_anAlreadyFinalizedNumberBlocksEveryWrite() {
        Deal deal = enteredDeal(closedEpisode("10", "77"));
        deal.setPlannedRiskAmount(null);
        deal.setCloseOutcome(Deal.CloseOutcome.NORMAL_EXIT);
        deal.setReconciliationStatus(Deal.ReconciliationStatus.MATCHED);
        deal.setBreakdownIncomplete(Deal.BreakdownCompleteness.COMPLETE);
        deal.setRiskBenchmarkAvailability(Deal.RiskBenchmarkAvailability.AVAILABLE);

        DealTerminalFeatures features = harness.apply(context(deal, List.of()), true);

        assertThat(features.getCloseOutcome()).isNull();
        assertThat(features.getReconciliationStatus()).isNull();
        assertThat(features.getBreakdownIncomplete()).isNull();
        assertThat(features.getRiskBenchmarkAvailability()).isNull();
        assertThat(features.getUnrecognizedCloseTypeReported()).isFalse();
        assertThat(deal.getCloseOutcome())
                .as("ни один сеттер модели не вызывается: пустой результат означает «значения уже "
                        + "стоя́т», а не «признак неприменим» (Z14)")
                .isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
        assertThat(deal.getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.AVAILABLE);
        assertThat(harness.journalledCodes())
                .as("журнал не зовётся ни разу, контроль валюты не проводится")
                .isEmpty();
    }

    @Test
    @DisplayName("U7.15 — аварийный терминал БЕЗ предшествующей финализации пишет признак сам")
    void u7_15_withoutAPriorFinalizationTheFeaturesAreWritten() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setRiskBenchmarkAvailability(null);

        harness.apply(context(deal, List.of()), false);

        assertThat(deal.getRiskBenchmarkAvailability())
                .as("контрпример к охране: без него U7.14 удовлетворялся бы пустотой при любом входе")
                .isEqualTo(Deal.RiskBenchmarkAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("U7.16 — пустой признак финализации читается как «не финализировано»: признаки пишутся")
    void u7_16_anEmptyFinalizationFlagStillWritesTheFeatures() {
        Deal deal = enteredDeal(closedEpisode("10"));
        deal.setCloseOutcome(null);

        DealTerminalFeatures features = harness.apply(context(deal, List.of()), null);

        assertThat(features.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
        assertThat(deal.getCloseOutcome())
                .as("ошибка направлена в сторону пересчёта (Z14)")
                .isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
    }

    @Test
    @DisplayName("U7.17 — на модель кладутся все четыре признака одним ходом; возвращается записанное")
    void u7_17_allFourFeaturesArePlacedOnTheModelAtOnce() {
        Deal deal = enteredDeal(closedEpisode("10", "3"));
        deal.setCloseOutcome(Deal.CloseOutcome.NORMAL_EXIT);
        deal.setReconciliationStatus(Deal.ReconciliationStatus.NOT_RUN);
        deal.setBreakdownIncomplete(Deal.BreakdownCompleteness.NOT_ASSESSED);
        deal.setRiskBenchmarkAvailability(Deal.RiskBenchmarkAvailability.MISSING);

        DealTerminalFeatures features = applyTo(deal);

        assertThat(deal.getCloseOutcome()).isEqualTo(features.getCloseOutcome());
        assertThat(deal.getReconciliationStatus()).isEqualTo(features.getReconciliationStatus());
        assertThat(deal.getBreakdownIncomplete()).isEqualTo(features.getBreakdownIncomplete());
        assertThat(deal.getRiskBenchmarkAvailability())
                .isEqualTo(features.getRiskBenchmarkAvailability());
        assertThat(deal.getCloseOutcome())
                .as("прежние значения затёрты: признаки кладутся ЦЕЛИКОМ (Z15)")
                .isEqualTo(Deal.CloseOutcome.LIQUIDATION);
        assertThat(deal.getReconciliationStatus()).isEqualTo(Deal.ReconciliationStatus.MATCHED);
        assertThat(deal.getBreakdownIncomplete()).isEqualTo(Deal.BreakdownCompleteness.COMPLETE);
        assertThat(deal.getRiskBenchmarkAvailability())
                .isEqualTo(Deal.RiskBenchmarkAvailability.AVAILABLE);
    }
}
