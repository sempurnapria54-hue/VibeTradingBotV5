package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.FOREIGN;
import static com.example.tradingcore.unit.calc.CalcFixture.SETTLE;
import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contextBuilder;
import static com.example.tradingcore.unit.calc.CalcFixture.contourProperties;
import static com.example.tradingcore.unit.calc.CalcFixture.dealWithoutEntry;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.episodeWithoutCloseRecord;
import static com.example.tradingcore.unit.calc.CalcFixture.flow;
import static com.example.tradingcore.unit.calc.CalcFixture.recoveredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.withRate;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.other.DealCashFlow;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.calc.DealResult;
import com.example.tradingcore.domain.command.calc.DealResultCalculator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Итог сделки: доступность числа и его валюта — группа {@code U1}
 * документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/spec/deal-result.json; звенья Z1, Z2).
 *
 * <p><b>Проверяется НЕДОСТУПНОСТЬ, а не занижение.</b> Каждое состояние
 * ниже — то, на котором наивная сумма ответила бы числом: пропущенное
 * слагаемое, недогруженная коллекция, строка чужой валюты без курса.
 *
 * <p><b>Базовая сборка:</b> контур несёт секцию площадки сделки с пустым
 * списком исключений; сделка вошла по составу (транш с налитой входной
 * ногой), расчётная валюта {@code USDT}, оба признака полноты истинны,
 * строк движений нет.
 */
class DealResultAvailabilityTest {

    private final DealResultCalculator calculator = new DealResultCalculator(contourProperties());

    @Test
    @DisplayName("U1.1 — сделка не входила: итог доступен нулём, операнды полноты и контур не читаются")
    void u1_1_aDealThatNeverEnteredHasAnAvailableZeroResult() {
        DealResult result = calculator.calculate(context(dealWithoutEntry(), List.of()));

        assertThat(result.getAvailable()).as("тропа закрытия без входа доступна всегда").isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getResultProfitCurrency()).isEqualTo(SETTLE);

        DealContext hostile = contextBuilder(dealWithoutEntry(),
                List.of(withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                        DealCashFlow.RateStatus.RATE_UNAVAILABLE, "")), SETTLE)
                .graphComplete(false)
                .flowsComplete(false)
                .build();

        assertThat(calculator.calculate(hostile).getAvailable())
                .as("входная ветвь отвечает РАНЬШЕ чтения операндов полноты и контура площадки (Z1)")
                .isTrue();
    }

    @Test
    @DisplayName("U1.2 — сделка не входила при пустой расчётной валюте: ноль без валюты")
    void u1_2_aDealThatNeverEnteredKeepsTheEmptyCurrencyAsUnresolvable() {
        DealResult result = calculator.calculate(context(dealWithoutEntry(), List.of(), null));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getResultProfitCurrency())
                .as("пустота означает нерезолвимость, а не «сделка не входила»")
                .isNull();
    }

    @Test
    @DisplayName("U1.3 — сделка заведена восстановлением: тропа входа берётся вторым дизъюнктом")
    void u1_3_aRecoveredDealTakesTheEntryPath() {
        Deal deal = recoveredDeal(closedEpisode("12.5"));

        DealResult result = calculator.calculate(context(deal, List.of()));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit())
                .as("итог считается по эпизодам, а не выдаётся нулём")
                .isEqualByComparingTo(new BigDecimal("12.5"));
    }

    @Test
    @DisplayName("U1.4 — один эпизод в расчётной валюте: итог равен net эпизода")
    void u1_4_oneEpisodeGivesItsOwnNet() {
        DealResult result = calculator.calculate(context(enteredDeal(closedEpisode("12.5")), List.of()));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(result.getResultProfitCurrency()).isEqualTo(SETTLE);
    }

    @Test
    @DisplayName("U1.5 — два эпизода: итог — сумма, а не число последнего")
    void u1_5_theSumRunsOverEveryEpisode() {
        Deal deal = enteredDeal(closedEpisode("12.5"), closedEpisode("-4"));

        assertThat(calculator.calculate(context(deal, List.of())).getResultProfit())
                .isEqualByComparingTo(new BigDecimal("8.5"));
    }

    @Test
    @DisplayName("U1.6 — недобытая запись закрытия одного эпизода делает итог недоступным, а не заниженным")
    void u1_6_anEpisodeWithoutACloseRecordMakesTheResultUnavailable() {
        Deal deal = enteredDeal(closedEpisode("12.5"), episodeWithoutCloseRecord());

        DealResult result = calculator.calculate(context(deal, List.of()));

        assertThat(result.getAvailable()).isFalse();
        assertThat(result.getResultProfit()).as("заниженная сумма не публикуется").isNull();
        assertThat(result.getResultProfitCurrency())
                .as("валюта отдаётся и при недоступном итоге")
                .isEqualTo(SETTLE);
    }

    @Test
    @DisplayName("U1.7 — граф предъявлен не целиком: итог недоступен отдельным конъюнктом")
    void u1_7_anIncompleteGraphMakesTheResultUnavailable() {
        DealContext dealContext = contextBuilder(enteredDeal(closedEpisode("12.5")), List.of(), SETTLE)
                .graphComplete(false)
                .build();

        DealResult result = calculator.calculate(dealContext);

        assertThat(result.getAvailable())
                .as("агрегат по недогруженному списку истинен молча")
                .isFalse();
        assertThat(result.getResultProfit()).isNull();
    }

    @Test
    @DisplayName("U1.8 — разбивка движений не добывалась: итог недоступен")
    void u1_8_aBreakdownThatWasNotFetchedMakesTheResultUnavailable() {
        DealContext dealContext = contextBuilder(enteredDeal(closedEpisode("12.5")), List.of(), SETTLE)
                .flowsComplete(false)
                .build();

        DealResult result = calculator.calculate(dealContext);

        assertThat(result.getAvailable())
                .as("слагаемое чужой валюты, молча равное нулю, запрещено")
                .isFalse();
        assertThat(result.getResultProfit()).isNull();
    }

    @Test
    @DisplayName("U1.9 — строка чужой валюты с неполученным курсом блокирует итог")
    void u1_9_aForeignRowAwaitingItsRateBlocksTheResult() {
        DealCashFlow pending = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                DealCashFlow.RateStatus.RATE_UNAVAILABLE, "");

        DealResult result = calculator.calculate(
                context(enteredDeal(closedEpisode("12.5")), List.of(pending)));

        assertThat(result.getAvailable()).isFalse();
        assertThat(result.getResultProfit()).isNull();
    }

    @Test
    @DisplayName("U1.10 — нерезолвимая расчётная валюта строки блокирует так же, как неполученный курс")
    void u1_10_anUnresolvableSettleCurrencyOnTheRowBlocksTheResultToo() {
        DealCashFlow broken = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE, "");

        DealResult result = calculator.calculate(
                context(enteredDeal(closedEpisode("12.5")), List.of(broken)));

        assertThat(result.getAvailable()).as("оба статуса блокируют").isFalse();
        assertThat(result.getResultProfit()).isNull();
    }

    @Test
    @DisplayName("U1.11 — нулевой net считается добытой записью закрытия")
    void u1_11_aZeroNetCountsAsAFetchedCloseRecord() {
        Deal deal = enteredDeal(closedEpisode("0"), closedEpisode("7"));

        DealResult result = calculator.calculate(context(deal, List.of()));

        assertThat(result.getAvailable())
                .as("предикат добытости стои́т на непустоте, а не на истинности числа")
                .isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("7"));
    }

    @Test
    @DisplayName("U1.12 — пустой признак полноты графа читается ложью: итог недоступен")
    void u1_12_anEmptyGraphCompletenessFlagIsReadAsFalse() {
        DealContext dealContext = contextBuilder(enteredDeal(closedEpisode("12.5")), List.of(), SETTLE)
                .graphComplete(null)
                .build();

        assertThat(calculator.calculate(dealContext).getAvailable())
                .as("ошибка направлена в запрещающую сторону (Z2); состояние недостижимо — "
                        + "фабрика контекста кладёт вычисленное значение")
                .isFalse();
    }

    @Test
    @DisplayName("U1.13 — пустой признак полноты разбивки читается ложью: итог недоступен")
    void u1_13_anEmptyFlowsCompletenessFlagIsReadAsFalse() {
        DealContext dealContext = contextBuilder(enteredDeal(closedEpisode("12.5")), List.of(), SETTLE)
                .flowsComplete(null)
                .build();

        assertThat(calculator.calculate(dealContext).getAvailable()).isFalse();
    }

    @Test
    @DisplayName("U1.14 — вошедшая сделка без единого эпизода: итог доступен нулём")
    void u1_14_anEnteredDealWithoutEpisodesStillHasAnAvailableZero() {
        DealResult result = calculator.calculate(context(enteredDeal(), List.of()));

        assertThat(result.getAvailable())
                .as("агрегат «записи добыты у всех» по пустому списку истинен")
                .isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("U1.15 — нерезолвимая расчётная валюта без строк разбивки: число есть, валюты нет")
    void u1_15_anUnresolvableSettleCurrencyWithoutRowsStillGivesTheNumber() {
        DealResult result = calculator.calculate(context(enteredDeal(closedEpisode("12.5")), List.of(), null));

        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getResultProfit()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(result.getResultProfitCurrency()).isNull();
    }

    @Test
    @DisplayName("U1.16 — нерезолвимая валюта, строка с применённым курсом: итог доступен без слагаемого")
    void u1_16_anAppliedRateRowDoesNotBlockWhenTheSettleCurrencyIsUnresolvable() {
        DealCashFlow applied = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-0.002"),
                DealCashFlow.RateStatus.APPLIED, "30000");

        DealResult result = calculator.calculate(
                context(enteredDeal(closedEpisode("12.5")), List.of(applied), null));

        assertThat(result.getAvailable())
                .as("область блокировки строку накрывает, но статус курса её не блокирует")
                .isTrue();
        assertThat(result.getResultProfit())
                .as("при пустой валюте область слагаемого пуста: строка в него НЕ входит")
                .isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(result.getResultProfitCurrency()).isNull();
    }

    @Test
    @DisplayName("U1.17 — нерезолвимая валюта, строка со сломанным статусом курса: итог недоступен")
    void u1_17_anUnresolvableSettleCurrencyWidensTheBlockingScopeToEveryRow() {
        DealCashFlow broken = withRate(flow(DealCashFlow.CashFlowCategory.TRADE_FEE, FOREIGN, "-1"),
                DealCashFlow.RateStatus.SETTLE_CURRENCY_UNAVAILABLE, "");

        DealResult result = calculator.calculate(
                context(enteredDeal(closedEpisode("12.5")), List.of(broken), null));

        assertThat(result.getAvailable())
                .as("при пустой валюте область блокировки накрывает ВСЕ неисключённые строки")
                .isFalse();
    }
}
