package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.dealWithoutEntry;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.episodeWithoutCloseRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.calc.DealTerminalFeatures;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Признаки терминала: контроль валюты и журнальные отчёты — группа
 * {@code U8} документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/rules/pnl-reconciliation.md §«Проверка валюты чисел записей
 * закрытия»; docs/models/domain/aggregate/Deal.md §Енумы;
 * docs/rules/error-handling-policy.md; звенья Z15, Z20, Z21).
 *
 * <p><b>Исходов контроля три, а не два:</b> совпал, разошёлся, сравнить
 * не с чем. Без третьей ветви «не проверяли» становится неотличимо от
 * «проверили, всё в порядке» — разрешающая ошибка.
 *
 * <p><b>Строка одна на ребро, а не на эпизод:</b> происшествие здесь —
 * само терминальное ребро, и вторая строка того же кода к разбору ничего
 * не добавляет.
 *
 * <p><b>Клетка {@code U8.12} на этом уровне непрогоняема и потому не
 * написана:</b> отказ журнала, приходящий не в момент вызова, а при
 * сбросе персистентного контекста вызывающего, существует только внутри
 * транзакции, которой у предмета нет. Ожидание дома — терминал отчётом
 * не блокируется; ловушка звена Z21 момента записи не покрывает (находка
 * {@code F5}). Прогоняемой клетка станет у ящика ядра, поднимающего
 * контекст и базу.
 *
 * <p><b>Базовая сборка:</b> та же, что у {@code U6}; журнал подменён и
 * записывает последовательность вызовов с кодами, расчётная валюта
 * инструмента {@code USDT}, если кейс не задаёт иного.
 */
class TerminalCurrencyAndJournalTest {

    private final TerminalFeaturesHarness harness = new TerminalFeaturesHarness();

    /** Эпизод с добытой записью закрытия, чья валюта названа кейсом. */
    private static Position episodeInCurrency(String net, String resultCurrency) {
        Position episode = closedEpisode(net);
        episode.setExternalResultCurrency(resultCurrency);
        return episode;
    }

    private DealTerminalFeatures applyTo(Deal deal) {
        return harness.apply(context(deal, List.of()), false);
    }

    @Test
    @DisplayName("U8.1 — ни одной добытой записи закрытия: контроль не проводится")
    void u8_1_withoutFetchedCloseRecordsTheControlIsNotPerformed() {
        applyTo(enteredDeal(episodeWithoutCloseRecord()));

        assertThat(harness.journalledCodes())
                .as("популяция контроля — прочитанные положения закрытия (Z20)")
                .doesNotContain(Constants.Hold.RESULT_CURRENCY_MISMATCH,
                        Constants.Hold.RESULT_CURRENCY_UNVERIFIABLE);
    }

    @Test
    @DisplayName("U8.2 — валюта записи совпала с расчётной: контроль пройден молча")
    void u8_2_aMatchingCurrencyIsNotJournalled() {
        applyTo(enteredDeal(episodeInCurrency("10", CalcFixture.SETTLE)));

        assertThat(harness.journalledCodes()).isEmpty();
    }

    @Test
    @DisplayName("U8.3 — валюта записи разошлась: один отчёт, терминал не блокируется")
    void u8_3_aMismatchedCurrencyIsJournalledOnceAndDoesNotBlockTheTerminal() {
        DealTerminalFeatures features = applyTo(enteredDeal(episodeInCurrency("10", "USDC")));

        assertThat(harness.journalledCodes())
                .containsExactly(Constants.Hold.RESULT_CURRENCY_MISMATCH);
        assertThat(features.getCloseOutcome())
                .as("признаки записаны: реакция отчётная, а не блокирующая")
                .isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
    }

    @Test
    @DisplayName("U8.4 — расчётная валюта инструмента пуста: третий исход, а не молчание")
    void u8_4_anUnresolvableSettleCurrencyGivesTheThirdOutcome() {
        Deal deal = enteredDeal(episodeInCurrency("10", CalcFixture.SETTLE));

        harness.apply(context(deal, List.of(), null), false);

        assertThat(harness.journalledCodes())
                .containsExactly(Constants.Hold.RESULT_CURRENCY_UNVERIFIABLE);
    }

    @Test
    @DisplayName("U8.5 — две разошедшиеся записи: одна строка на ребро, а не на эпизод")
    void u8_5_twoMismatchedRecordsGiveOneRowPerEdge() {
        Deal deal = enteredDeal(episodeInCurrency("10", "USDC"), episodeInCurrency("5", "USDC"));

        applyTo(deal);

        assertThat(harness.journalledCodes())
                .as("происшествие — само терминальное ребро (Z20)")
                .containsExactly(Constants.Hold.RESULT_CURRENCY_MISMATCH);
    }

    @Test
    @DisplayName("U8.6 — одна совпала, другая разошлась: предикат стои́т на существовании расхождения")
    void u8_6_oneMismatchAmongMatchesIsEnough() {
        Deal deal = enteredDeal(episodeInCurrency("10", CalcFixture.SETTLE),
                episodeInCurrency("5", "USDC"));

        applyTo(deal);

        assertThat(harness.journalledCodes())
                .containsExactly(Constants.Hold.RESULT_CURRENCY_MISMATCH);
    }

    @Test
    @DisplayName("U8.7 — пустая валюта записи при непустой расчётной: расхождение, а не совпадение")
    void u8_7_anEmptyRecordCurrencyIsAMismatch() {
        applyTo(enteredDeal(episodeInCurrency("10", null)));

        assertThat(harness.journalledCodes())
                .as("пустое левое значение расчётной валюте не равно (Z20)")
                .containsExactly(Constants.Hold.RESULT_CURRENCY_MISMATCH);
    }

    @Test
    @DisplayName("U8.8 — доступность знаменателя «отсутствует»: отчёт потерянного знаменателя")
    void u8_8_aMissingBenchmarkIsJournalled() {
        Deal deal = enteredDeal(episodeInCurrency("10", CalcFixture.SETTLE));
        deal.setPlannedRiskAmount(null);

        applyTo(deal);

        assertThat(harness.journalledCodes()).containsExactly(Constants.Hold.RISK_BENCHMARK_MISSING);
    }

    @Test
    @DisplayName("U8.9 — знаменатель «неприменимо» либо «доступен»: отчёта с этим кодом нет")
    void u8_9_anApplicableBenchmarkIsNotJournalled() {
        applyTo(enteredDeal(episodeInCurrency("10", CalcFixture.SETTLE)));
        assertThat(harness.journalledCodes()).doesNotContain(Constants.Hold.RISK_BENCHMARK_MISSING);

        TerminalFeaturesHarness notApplicable = new TerminalFeaturesHarness();
        notApplicable.apply(context(dealWithoutEntry(episodeInCurrency("10", CalcFixture.SETTLE)),
                List.of()), false);
        assertThat(notApplicable.journalledCodes())
                .doesNotContain(Constants.Hold.RISK_BENCHMARK_MISSING);
    }

    @Test
    @DisplayName("U8.10 — два отчёта: сперва нераспознанный тип, затем невозможность контроля валюты")
    void u8_10_theTailOrderIsFixed() {
        Deal deal = enteredDeal(closedEpisode("10", "77"));

        harness.apply(context(deal, List.of(), null), false);

        assertThat(harness.journalledCodes())
                .as("порядок хвоста фиксирован: отчёты по признакам раньше контроля валюты (Z15)")
                .containsExactly(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE,
                        Constants.Hold.RESULT_CURRENCY_UNVERIFIABLE);
    }

    @Test
    @DisplayName("U8.11 — отказ журнала на первом вызове: второй всё равно делается, терминал не отменяется")
    void u8_11_aFailingJournalNeitherEscapesNorSkipsTheNextReport() {
        doThrow(new RuntimeException("journal down")).when(harness.reports()).journal(any(), any());
        Deal deal = enteredDeal(closedEpisode("10", "77"));

        assertThatCode(() -> harness.apply(context(deal, List.of(), null), false))
                .as("ловушка стои́т вокруг КАЖДОГО вызова (Z21)")
                .doesNotThrowAnyException();
        assertThat(harness.journalledCodes())
                .containsExactly(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE,
                        Constants.Hold.RESULT_CURRENCY_UNVERIFIABLE);
        assertThat(deal.getCloseOutcome())
                .as("терминал отказом журнала не отменяется")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
    }

    @Test
    @DisplayName("U8.13 — признак финализации истинен при разошедшейся валюте: вызовов журнала нет")
    void u8_13_theFinalizationGuardOutranksTheCurrencyControl() {
        Deal deal = enteredDeal(episodeInCurrency("10", "USDC"));

        harness.apply(context(deal, List.of()), true);

        assertThat(harness.journalledCodes())
                .as("охрана входной ветви стои́т выше контроля (Z14)")
                .isEmpty();
    }

    @Test
    @DisplayName("U8.14 — недобытая запись в сравнение своей валютой не входит")
    void u8_14_anUnfetchedRecordDoesNotEnterTheCurrencyComparison() {
        Position unfetched = episodeWithoutCloseRecord();
        unfetched.setExternalResultCurrency("USDC");
        Deal deal = enteredDeal(episodeInCurrency("10", CalcFixture.SETTLE), unfetched);

        applyTo(deal);

        assertThat(harness.journalledCodes())
                .as("снятие отбора по добытости дало бы строку расхождения там, где дом предписывает "
                        + "молчание (Z20)")
                .doesNotContain(Constants.Hold.RESULT_CURRENCY_MISMATCH);
    }
}
