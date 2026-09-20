package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.FOREIGN;
import static com.example.tradingcore.unit.calc.CalcFixture.SETTLE;
import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contourProperties;
import static com.example.tradingcore.unit.calc.CalcFixture.contourPropertiesWithoutSection;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.flow;
import static com.example.tradingcore.unit.calc.CalcFixture.withRate;
import static com.example.tradingcore.unit.calc.CalcFixture.withType;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.domain.command.calc.DealResult;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Итог сделки: слагаемое чужой валюты и асимметрия областей — группа
 * {@code U2} документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/rules/pnl-reconciliation.md §«Асимметрия областей: из суммы
 * исключено, из блокировки — нет»; docs/spec/deal-result.json
 * §{@code inCrossCurrencyScope}, §{@code inCrossCurrencyBlockingScope};
 * звенья Z3-Z5).
 *
 * <p><b>Область блокировки шире области слагаемого, и это не описка:</b>
 * шире она на принимающую корзину нераспознанного и на все неисключённые
 * строки при нерезолвимой расчётной валюте.
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U1}; расчётная валюта
 * {@code USDT}, один эпизод с числом {@code 100}, оба признака полноты
 * истинны. Меняется только состав строк движений и список исключений
 * контура.
 */
class DealResultCrossCurrencyTest {

    /** Эпизод базовой сборки: net сто в расчётной валюте. */
    private static Deal dealWithOneHundred() {
        return enteredDeal(closedEpisode("100"));
    }

    private static DealResultCalculator calculator(String... exclusions) {
        return new DealResultCalculator(contourProperties(exclusions));
    }

    @Test
    @DisplayName("U2.1 — движение в расчётной валюте слагаемым чужой валюты не является")
    void u2_1_aSettleCurrencyRowIsNeitherATermNorABlocker() {
        DealCashFlow settleRow = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, SETTLE, "-1");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(settleRow)));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit())
                .as("слагаемое равно нулю: число равно сумме по эпизодам")
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("U2.2 — движение чужой валюты с применённым курсом входит слагаемым")
    void u2_2_aForeignRowWithAnAppliedRateBecomesATerm() {
        DealCashFlow converted = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-0.002"),
                DealCashFlow.RateStatus.APPLIED, "30000");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(converted)));

        assertThat(result.getResultProfit())
                .as("слагаемое −60: сумма движения, переведённая по применённому курсу")
                .isEqualByComparingTo(new BigDecimal("40"));
    }

    @Test
    @DisplayName("U2.3 — два движения чужих валют: слагаемое — сумма обоих переводов")
    void u2_3_twoForeignRowsAddUp() {
        DealCashFlow first = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-0.002"),
                DealCashFlow.RateStatus.APPLIED, "30000");
        DealCashFlow second = withRate(flow(DealCashFlow.CashFlowCategory.FUNDING, "ETH", "-0.01"),
                DealCashFlow.RateStatus.APPLIED, "2000");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(first, second)));

        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    @DisplayName("U2.4 — нераспознанная категория с применённым курсом не блокирует и в слагаемое не входит")
    void u2_4_theUnclassifiedBasketIsNeitherATermNorABlockerWhenTheRateIsApplied() {
        DealCashFlow basket = withRate(flow(DealCashFlow.CashFlowCategory.OTHER, FOREIGN, "-0.002"),
                DealCashFlow.RateStatus.APPLIED, "30000");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(basket)));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("U2.5 — нераспознанная категория без курса итог БЛОКИРУЕТ, хотя в слагаемое не входит")
    void u2_5_theUnclassifiedBasketBlocksTheResultWithoutBecomingATerm() {
        DealCashFlow basket = withRate(flow(DealCashFlow.CashFlowCategory.OTHER, FOREIGN, "-0.002"),
                DealCashFlow.RateStatus.RATE_UNAVAILABLE, "");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(basket)));

        assertThat(result.getAvailable())
                .as("область блокировки шире области слагаемого на принимающую корзину")
                .isFalse();
    }

    @Test
    @DisplayName("U2.6 — исключённое биржей движение чужой валюты итог не блокирует и в слагаемое не входит")
    void u2_6_anExcludedForeignRowLeavesBothScopes() {
        DealCashFlow excluded = withType(
                withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                        DealCashFlow.RateStatus.RATE_UNAVAILABLE, ""), "8", null);

        DealResult result = calculator("8").calculate(context(dealWithOneHundred(), List.of(excluded)));

        assertThat(result.getAvailable())
                .as("исключение вычитается из ОБЕИХ областей")
                .isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("U2.7 — исключение парой «тип/подтип» накрывает строку с этим подтипом")
    void u2_7_anExclusionByPairCoversTheRowCarryingThatSubType() {
        DealCashFlow excluded = withType(
                withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                        DealCashFlow.RateStatus.RATE_UNAVAILABLE, ""), "2", "407");

        DealResult result = calculator("2/407").calculate(context(dealWithOneHundred(), List.of(excluded)));

        assertThat(result.getAvailable()).as("охрана читает и тип, и пару").isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("U2.8 — исключение парой строку без подтипа не накрывает: пара точечна")
    void u2_8_anExclusionByPairDoesNotCoverTheBareType() {
        DealCashFlow bare = withType(
                withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                        DealCashFlow.RateStatus.RATE_UNAVAILABLE, ""), "2", null);

        DealResult result = calculator("2/407").calculate(context(dealWithOneHundred(), List.of(bare)));

        assertThat(result.getAvailable())
                .as("тип покрывает все свои подтипы, но пара тип не покрывает")
                .isFalse();
    }

    @Test
    @DisplayName("U2.9 — строка чужой валюты со статусом «не требуется»: ни слагаемого, ни блокировки")
    void u2_9_aForeignRowWhoseRateIsNotRequiredIsNeitherATermNorABlocker() {
        DealCashFlow notRequired = flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(notRequired)));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("U2.10 — применённый курс при ПУСТОМ курсе: строка молча выпадает, арифметика не роняется")
    void u2_10_anAppliedStatusWithAnEmptyRateFallsOutSilently() {
        DealCashFlow broken = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-0.002"),
                DealCashFlow.RateStatus.APPLIED, "");

        DealResult result = calculator().calculate(context(dealWithOneHundred(), List.of(broken)));

        assertThat(result.getAvailable())
                .as("состояние недостижимо — оба поля пишет один маппер строки движения")
                .isTrue();
        assertThat(result.getResultProfit())
                .as("непустота курса — шестой конъюнкт области слагаемого (Z3)")
                .isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("U2.11 — контур площадки в конфигурации отсутствует: пустой контур, расчёт идёт")
    void u2_11_aMissingContourSectionYieldsAnEmptyContourRatherThanNothing() {
        DealCashFlow converted = withType(
                withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-0.002"),
                        DealCashFlow.RateStatus.APPLIED, "30000"), "8", null);
        DealResultCalculator calculator = new DealResultCalculator(contourPropertiesWithoutSection());

        DealResult result = calculator.calculate(context(dealWithOneHundred(), List.of(converted)));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit())
                .as("ни одна строка не исключена, обе области берут всё (Z5)")
                .isEqualByComparingTo(new BigDecimal("40"));
    }
}
