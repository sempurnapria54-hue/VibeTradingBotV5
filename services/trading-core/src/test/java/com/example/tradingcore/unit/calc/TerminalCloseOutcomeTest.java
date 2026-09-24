package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.CalcFixture.closedEpisode;
import static com.example.tradingcore.unit.calc.CalcFixture.context;
import static com.example.tradingcore.unit.calc.CalcFixture.contextBuilder;
import static com.example.tradingcore.unit.calc.CalcFixture.dealWithoutEntry;
import static com.example.tradingcore.unit.calc.CalcFixture.enteredDeal;
import static com.example.tradingcore.unit.calc.CalcFixture.episodeWithoutCloseRecord;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.calc.DealTerminalFeatures;
import com.example.tradingcore.util.Constants;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Признаки терминала: торговый исход закрытия — группа {@code U6}
 * документа `.claude/tests/cases/trading-core-calc.md` (дом —
 * docs/spec/position-close-outcome.json; звенья Z16, Z17).
 *
 * <p><b>Старшинство: ликвидация &gt; принудительное сокращение &gt;
 * неизвестность &gt; штатный выход.</b> Неизвестное выше штатного
 * намеренно: обратный порядок скрывал бы ликвидацию соседнего эпизода за
 * штатным выходом.
 *
 * <p><b>Базовая сборка:</b> сверка настоящая, журнал подменён и считает
 * свои вызовы с их кодами; контекст прохода — как у {@code U1}; признак
 * «число уже финализировано» ложен во всех кейсах группы. Сырые типы
 * закрытия площадки: {@code 1}, {@code 2} — штатный выход; {@code 3},
 * {@code 4} — ликвидация; {@code 5}, {@code 6} — принудительное
 * сокращение.
 *
 * <p><b>Один кейс группы красен по построению</b> и помечен
 * {@code @Tag("debt")}: {@code U6.18} предъявляет находку {@code F3}
 * (охрана полноты графа читает пустое не так, как читает его охрана
 * итога).
 */
class TerminalCloseOutcomeTest {

    private final TerminalFeaturesHarness harness = new TerminalFeaturesHarness();

    private DealTerminalFeatures applyTo(Deal deal) {
        return harness.apply(context(deal, List.of()), false);
    }

    @Test
    @DisplayName("U6.1 — полное закрытие одним эпизодом: штатный выход")
    void u6_1_aSingleNormalEpisodeGivesNormalExit() {
        DealTerminalFeatures features = applyTo(enteredDeal(closedEpisode("10", "1")));

        assertThat(features.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
        assertThat(harness.journalledCodes())
                .as("журнальных отчётов по нераспознанному типу нет")
                .doesNotContain(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
    }

    @Test
    @DisplayName("U6.2 — частичное закрытие — тот же штатный выход, а не отдельный исход")
    void u6_2_aPartialCloseIsTheSameNormalExit() {
        assertThat(applyTo(enteredDeal(closedEpisode("10", "2"))).getCloseOutcome())
                .isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
    }

    @Test
    @DisplayName("U6.3 — ликвидация имеет производителя: сырое значение 3")
    void u6_3_theLiquidationTypeIsRecognised() {
        assertThat(applyTo(enteredDeal(closedEpisode("10", "3"))).getCloseOutcome())
                .isEqualTo(Deal.CloseOutcome.LIQUIDATION);
    }

    @Test
    @DisplayName("U6.4 — частичная ликвидация даёт то же значение, что полная")
    void u6_4_aPartialLiquidationGivesTheSameValue() {
        assertThat(applyTo(enteredDeal(closedEpisode("10", "4"))).getCloseOutcome())
                .isEqualTo(Deal.CloseOutcome.LIQUIDATION);
    }

    @Test
    @DisplayName("U6.5 — принудительное сокращение: оба сырых значения дают один исход")
    void u6_5_bothForcedReductionTypesGiveOneOutcome() {
        assertThat(applyTo(enteredDeal(closedEpisode("10", "5"))).getCloseOutcome())
                .isEqualTo(Deal.CloseOutcome.FORCED_REDUCTION);
        assertThat(new TerminalFeaturesHarness()
                .apply(context(enteredDeal(closedEpisode("10", "6")), List.of()), false)
                .getCloseOutcome())
                .isEqualTo(Deal.CloseOutcome.FORCED_REDUCTION);
    }

    @Test
    @DisplayName("U6.6 — незнакомое значение не маппится в штатный выход: корзина плюс отчёт")
    void u6_6_anUnknownTypeGivesTheBasketAndTheReport() {
        DealTerminalFeatures features = applyTo(enteredDeal(closedEpisode("10", "77")));

        assertThat(features.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.UNDETERMINED);
        assertThat(features.getUnrecognizedCloseTypeReported()).isTrue();
        assertThat(harness.journalledCodes())
                .as("ровно один вызов журнала с кодом нераспознанного типа")
                .containsExactly(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
    }

    @Test
    @DisplayName("U6.7 — добытая запись с пустым типом закрытия: та же корзина и тот же отчёт")
    void u6_7_anEmptyCloseTypeOnAFetchedRecordGivesTheBasketAndTheReport() {
        DealTerminalFeatures features = applyTo(enteredDeal(closedEpisode("10", null)));

        assertThat(features.getCloseOutcome())
                .as("пустое значение приводится к пустой строке, чтобы обе формы непригодности "
                        + "шли одной ветвью: `Set.of#contains` на пустом — отказ, а не ложь (Z17)")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
        assertThat(features.getUnrecognizedCloseTypeReported()).isTrue();
        assertThat(harness.journalledCodes()).containsExactly(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
    }

    @Test
    @DisplayName("U6.8 — недобытый операнд даёт ту же корзину, но отчёта не производит")
    void u6_8_anUnfetchedRecordGivesTheBasketWithoutAReport() {
        DealTerminalFeatures features = applyTo(enteredDeal(episodeWithoutCloseRecord()));

        assertThat(features.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.UNDETERMINED);
        assertThat(features.getUnrecognizedCloseTypeReported())
                .as("недобытая запись наблюдаема тропой добычи; ранний возврат по пустому числу "
                        + "стои́т выше перечней (Z17)")
                .isFalse();
        assertThat(harness.journalledCodes()).doesNotContain(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
    }

    @Test
    @DisplayName("U6.9 — вошедшая сделка без единого эпизода: корзина неизвестности без отчёта")
    void u6_9_anEnteredDealWithoutEpisodesGivesTheBasket() {
        DealTerminalFeatures features = applyTo(enteredDeal());

        assertThat(features.getCloseOutcome())
                .as("утверждать штатный выход не из чего")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
        assertThat(features.getUnrecognizedCloseTypeReported())
                .as("популяция предиката отчёта пуста")
                .isFalse();
    }

    @Test
    @DisplayName("U6.10 — ликвидация одного эпизода не тонет в штатных выходах соседей")
    void u6_10_aLiquidationDoesNotDrownAmongNormalExits() {
        Deal deal = enteredDeal(closedEpisode("1", "1"), closedEpisode("2", "1"),
                closedEpisode("3", "3"));

        assertThat(applyTo(deal).getCloseOutcome()).isEqualTo(Deal.CloseOutcome.LIQUIDATION);
    }

    @Test
    @DisplayName("U6.11 — ликвидация старше принудительного сокращения")
    void u6_11_liquidationOutranksForcedReduction() {
        Deal deal = enteredDeal(closedEpisode("1", "5"), closedEpisode("2", "3"));

        assertThat(applyTo(deal).getCloseOutcome()).isEqualTo(Deal.CloseOutcome.LIQUIDATION);
    }

    @Test
    @DisplayName("U6.12 — наблюдённое принудительное сокращение сильнее неизвестности соседа")
    void u6_12_forcedReductionOutranksTheUnknownNeighbour() {
        Deal deal = enteredDeal(closedEpisode("1", "5"), episodeWithoutCloseRecord());

        assertThat(applyTo(deal).getCloseOutcome()).isEqualTo(Deal.CloseOutcome.FORCED_REDUCTION);
    }

    @Test
    @DisplayName("U6.13 — неизвестность одного эпизода не даёт объявить сделке штатный выход")
    void u6_13_anUnknownNeighbourOutranksTheNormalExit() {
        Deal deal = enteredDeal(closedEpisode("1", "1"), episodeWithoutCloseRecord());

        assertThat(applyTo(deal).getCloseOutcome())
                .as("порядок старшинства обратным не бывает")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
    }

    @Test
    @DisplayName("U6.14 — нераспознанное значение соседа поднимает сделку в корзину, и отчёт заводится")
    void u6_14_anUnrecognisedNeighbourRaisesTheBasketAndTheReport() {
        Deal deal = enteredDeal(closedEpisode("1", "1"), closedEpisode("2", "77"));

        DealTerminalFeatures features = applyTo(deal);

        assertThat(features.getCloseOutcome()).isEqualTo(Deal.CloseOutcome.UNDETERMINED);
        assertThat(harness.journalledCodes()).containsExactly(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
    }

    @Test
    @DisplayName("U6.15 — граф предъявлен не целиком: корзина, охрана полноты стои́т выше перебора")
    void u6_15_anIncompleteGraphGivesTheBasket() {
        DealContext dealContext = contextBuilder(
                enteredDeal(closedEpisode("1", "1"), closedEpisode("2", "1")), List.of(),
                CalcFixture.SETTLE)
                .graphComplete(false)
                .build();

        assertThat(harness.apply(dealContext, false).getCloseOutcome())
                .as("агрегат «есть ли ликвидация» по недогруженному списку ложен молча")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
    }

    @Test
    @DisplayName("U6.16 — тот же расклад на полном графе — штатный выход")
    void u6_16_theSameLayoutOnACompleteGraphGivesNormalExit() {
        Deal deal = enteredDeal(closedEpisode("1", "1"), closedEpisode("2", "1"));

        assertThat(applyTo(deal).getCloseOutcome())
                .as("пара примеров и делает охрану полноты различающей")
                .isEqualTo(Deal.CloseOutcome.NORMAL_EXIT);
    }

    @Test
    @DisplayName("U6.17 — тропа закрытия без входа: признак неприменим, а не UNDETERMINED")
    void u6_17_aDealThatNeverEnteredHasNoCloseOutcome() {
        assertThat(applyTo(dealWithoutEntry(closedEpisode("10", "1"))).getCloseOutcome()).isNull();
    }

    @Test
    @Tag("debt")
    @DisplayName("U6.18 — пустой признак полноты графа: корзина неизвестности, симметрично охране итога")
    void u6_18_anEmptyGraphCompletenessFlagGivesTheBasket() {
        DealContext dealContext = contextBuilder(enteredDeal(closedEpisode("1", "1")), List.of(),
                CalcFixture.SETTLE)
                .graphComplete(null)
                .build();

        assertThat(harness.apply(dealContext, false).getCloseOutcome())
                .as("операнд спеки объявляет охрану СИММЕТРИЧНОЙ охране итога, а итог на том же "
                        + "пустом значении становится недоступным (U1.12). Сегодня охрана читает "
                        + "пустое как «граф полон» и даёт штатный выход (находка F3, Z16)")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
    }

    @Test
    @DisplayName("U6.19 — добытая запись с типом-пустой-строкой: корзина и ровно один отчёт")
    void u6_19_anEmptyStringCloseTypeGivesTheBasketAndTheReport() {
        Position episode = closedEpisode("10", "");

        DealTerminalFeatures features = applyTo(enteredDeal(episode));

        assertThat(features.getCloseOutcome())
                .as("перечни пустую строку принимают и ложь отдают (Z17)")
                .isEqualTo(Deal.CloseOutcome.UNDETERMINED);
        assertThat(harness.journalledCodes())
                .as("охрана предиката отчёта пустую строку непригодной признаёт")
                .containsExactly(Constants.Hold.UNRECOGNIZED_CLOSE_TYPE);
    }
}
