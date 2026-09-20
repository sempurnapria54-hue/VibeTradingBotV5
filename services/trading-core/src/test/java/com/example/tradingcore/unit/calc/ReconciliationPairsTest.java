package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.FOREIGN;
import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contourProperties;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.flow;
import static com.example.tradingcore.unit.calc.CalcFixture.reconciledEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.withExchangeNumbers;
import static com.example.tradingcore.unit.calc.CalcFixture.withFeeComponent;
import static com.example.tradingcore.unit.calc.CalcFixture.withType;
import static com.example.tradingcore.unit.calc.CalcFixture.workingTolerance;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.domain.command.calc.DealReconciliationCalculator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Сверка: четыре пары, гранулярность и конвенция знака — группа
 * {@code U4} документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/spec/pnl-reconciliation.json §{@code totalDiscrepancy},
 * §{@code flowAmountNetOfFee}, §{@code separateFeeGranularity},
 * §{@code rightFunding}; docs/rules/pnl-reconciliation.md §«Левая сторона
 * гранулярность-независима»; звенья Z8-Z10).
 *
 * <p><b>Композиция независима от гранулярности записи источника:</b>
 * комбинированная запись и раздельная пара строк дают один исход, а
 * информационное эхо комиссии на торговой строке комиссию не задваивает.
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U3}; обязанность
 * наступила во всех кейсах группы, расчётная валюта {@code USDT}, допуск
 * задан рабочими числами. Входных ног у сделки нет, поэтому омиссионный
 * член нулевой и допуск вырождается в пол — {@code 0.05}.
 *
 * <p><b>Один кейс группы красен по построению</b> и помечен
 * {@code @Tag("debt")}: {@code U4.11} предъявляет находку {@code F1} —
 * различитель гранулярности считается по уже прореженной области, а
 * спека ставит его на непрореженные строки расчётной валюты.
 */
class ReconciliationPairsTest {

    private final DealReconciliationCalculator calculator =
            new DealReconciliationCalculator(contourProperties(), workingTolerance());

    private static DealReconciliationCalculator calculatorExcluding(String... exclusions) {
        return new DealReconciliationCalculator(contourProperties(exclusions), workingTolerance());
    }

    /** Строки, сходящиеся с четырьмя числами {@link CalcFixture#reconciledEpisode()}. */
    private static List<DealCashFlow> matchingFlows() {
        return List.of(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"));
    }

    @Test
    @DisplayName("U4.1 — все четыре пары сходятся: знак финансирования снимается обратно")
    void u4_1_allFourPairsAgree() {
        Deal deal = enteredDeal(withExchangeNumbers(closedEpisode("4"), "10", "-1", "3", "-2"));
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                flow(DealCashFlow.CashFlowCategory.FUNDING, "-3"),
                flow(DealCashFlow.CashFlowCategory.LIQ_PENALTY, "-2"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("домен хранит финансирование издержкой, разбивка — сырой знаковой суммой")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.2 — расхождения разных знаков друг друга не гасят")
    void u4_2_discrepanciesOfOppositeSignsDoNotCancelEachOther() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "11"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-2"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("общее расхождение — сумма модулей: сложение со знаком дало бы ноль и «сошлась»")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U4.3 — финансирование и ликвидационный штраф разошлись каждый по своей паре")
    void u4_3_fundingAndPenaltyEachDivergeOnTheirOwnPair() {
        Deal deal = enteredDeal(withExchangeNumbers(closedEpisode("4"), "10", "-1", "2", "-1"));
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                flow(DealCashFlow.CashFlowCategory.FUNDING, "-3"),
                flow(DealCashFlow.CashFlowCategory.LIQ_PENALTY, "-2"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("обе разности ненулевые, и обе входят в сумму — пара, удовлетворяемая нулём, исключена")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
    }

    @Test
    @DisplayName("U4.4 — комбинированная запись: комиссия извлекается из торговой строки, а не теряется")
    void u4_4_aCombinedRecordHasItsFeeExtracted() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                withFeeComponent(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "9"), "-1"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.5 — раздельная запись при той же экономике даёт тот же результат")
    void u4_5_aSeparateRecordGivesTheSameOutcome() {
        Deal deal = enteredDeal(reconciledEpisode());

        assertThat(calculator.reconcile(context(deal, matchingFlows())))
                .as("композиция гранулярность-независима; состояние недостижимо на отгруженном "
                        + "отображении категорий площадки")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.6 — эхо комиссии на торговой записи при раздельной гранулярности комиссию не задваивает")
    void u4_6_theFeeEchoIsNotReadAtSeparateGranularity() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                withFeeComponent(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"), "-1"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("прочитанное эхо дало бы «разошлась» величиной комиссии")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.7 — ребейт складывается с комиссией в одну пару")
    void u4_7_aRebateJoinsTheFeePair() {
        Deal deal = enteredDeal(withExchangeNumbers(closedEpisode("9.3"), "10", "-0.7", "0", "0"));
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                flow(DealCashFlow.CashFlowCategory.REBATE, "0.3"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("различитель гранулярности поднимают обе категории")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.8 — движение в чужой валюте в область сверки не входит")
    void u4_8_aForeignCurrencyRowStaysOutOfTheScope() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, FOREIGN, "500"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("вошедшая строка дала бы «разошлась» своей величиной")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.9 — движение из списка исключений площадки в область сверки не входит")
    void u4_9_anExcludedRowStaysOutOfTheScope() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                withType(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "500"), "8", null));

        assertThat(calculatorExcluding("8").reconcile(context(deal, flows)))
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.10 — принимающая корзина в расчётной валюте в область сверки не входит")
    void u4_10_theUnclassifiedBasketStaysOutOfTheScope() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                flow(DealCashFlow.CashFlowCategory.OTHER, "500"));

        assertThat(calculatorExcluding().reconcile(context(deal, flows)))
                .as("конъюнкция области берётся целиком")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @Tag("debt")
    @DisplayName("U4.11 — исключение комиссионного типа списком биржи различитель гранулярности не сбивает")
    void u4_11_anExcludedFeeTypeDoesNotFlipTheGranularityDiscriminator() {
        Deal deal = enteredDeal(withExchangeNumbers(closedEpisode("10"), "10", "0", "0", "0"));
        List<DealCashFlow> flows = List.of(
                withFeeComponent(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"), "-1"),
                withType(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"), "8", null));

        assertThat(calculatorExcluding("8").reconcile(context(deal, flows)))
                .as("ожидание дома: различитель стои́т на НЕПРОРЕЖЕННЫХ строках расчётной валюты, "
                        + "эхо на торговой строке не читается, комиссионная пара сходится. Сегодня "
                        + "различитель считается по прореженной области (Z9), эхо вычитается, и сверка "
                        + "расходится величиной двух комиссий — находка F1")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.12 — два эпизода: правая сторона каждой пары суммируется по эпизодам")
    void u4_12_theRightSideOfEveryPairIsSummedOverEpisodes() {
        Deal deal = enteredDeal(
                withExchangeNumbers(closedEpisode("4.5"), "5", "-0.5", "0", "0"),
                withExchangeNumbers(closedEpisode("4.5"), "5", "-0.5", "0", "0"));

        assertThat(calculator.reconcile(context(deal, matchingFlows())))
                .as("левые стороны собраны под сумму, а не под один эпизод")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.13 — строка с пустой суммой вносит ноль: исход возвращается, а не отказывает")
    void u4_13_anEmptyAmountIsReadAsZero() {
        Deal deal = enteredDeal(reconciledEpisode());
        List<DealCashFlow> flows = List.of(
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"),
                flow(DealCashFlow.CashFlowCategory.TRADE_FEE, "-1"),
                flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, ""));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("состояние недостижимо — колонка `amount` миграции V4 объявлена not null; "
                        + "спека на нём ОТКАЗЫВАЕТ арифметикой, и ожидание взято по коду, "
                        + "а расхождение предъявлено находкой F2 (Z10)")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.14 — пустая расчётная валюта делает область сверки пустой целиком")
    void u4_14_anEmptySettleCurrencyEmptiesTheScopeEntirely() {
        Deal withNumbers = enteredDeal(reconciledEpisode());
        Deal withZeroes = enteredDeal(withExchangeNumbers(closedEpisode("0"), "0", "0", "0", "0"));

        assertThat(calculator.reconcile(context(withNumbers, matchingFlows(), null)))
                .as("левые стороны нулевые, правые считаются по эпизодам: ранний возврат, а не фильтр (Z8)")
                .isEqualTo(Deal.ReconciliationStatus.MISMATCHED);
        assertThat(calculator.reconcile(context(withZeroes, matchingFlows(), null)))
                .as("на нулевых правых сторонах та же пустая область даёт «сошлась»")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.15 — пустая комиссионная компонента при комбинированной гранулярности читается нулём")
    void u4_15_anEmptyFeeComponentIsReadAsZero() {
        Deal deal = enteredDeal(withExchangeNumbers(closedEpisode("10"), "10", "0", "0", "0"));
        List<DealCashFlow> flows = List.of(flow(DealCashFlow.CashFlowCategory.REALIZED_PNL, "10"));

        assertThat(calculator.reconcile(context(deal, flows)))
                .as("«вычитать нечего»: левая пара равна сумме строки")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }

    @Test
    @DisplayName("U4.16 — эпизод с пустыми биржевыми числами всех четырёх видов вносит нули")
    void u4_16_anEpisodeWithEmptyExchangeNumbersContributesZeroes() {
        Deal deal = enteredDeal(withExchangeNumbers(closedEpisode("0"), "", "", "", ""));

        assertThat(calculator.reconcile(context(deal, List.of())))
                .as("пустое слагаемое вносит ноль, а не роняет расчёт (Z10)")
                .isEqualTo(Deal.ReconciliationStatus.MATCHED);
    }
}
