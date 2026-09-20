package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.NEIGHBOUR_TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.risk.DealRiskNumbers;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.persistence.service.DealDataService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Четвёрка чисел риска: отбор ног и их доли — группа {@code U27}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/deal-risk-numbers.json, величина {@code dealPlannedRisk};
 * перечень писателей — docs/models/domain/aggregate/Deal.md §«Писатели
 * четвёрки и их триггеры»).
 *
 * <p><b>Базовая сборка.</b> Сделка с одним траншем и одной входной
 * ногой: заявленный риск 100, плановый размер 100, налив — половина;
 * эпизод жив и несёт эту ногу; действующего уровня защиты у транша нет.
 *
 * <p><b>Мок здесь один и он граница</b> — хранилище сделки; сам счёт
 * ведётся на настоящем графе.
 */
class RiskNumbersLegSelectionTest {

    /** Живой эпизод базовой сборки. */
    private static final Long LIVE_EPISODE_ID = 9L;

    /** Закрытый эпизод той же сделки. */
    private static final Long CLOSED_EPISODE_ID = 8L;

    private final DealRiskNumbersService service = new DealRiskNumbersService(mock(DealDataService.class));

    @Test
    @DisplayName("U27.1 — нога живая: в заявленный риск входит ЦЕЛИКОМ, невзирая на долю налива")
    void u27_1_aLiveLegEntersThePlannedRiskInFull() {
        assertThat(compute(leg(Order.Status.ACTIVE, "100", "100", "50")).getPlannedRiskAmount())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("U27.2 — нога исполнена целиком: входит целиком")
    void u27_2_aCompletedLegEntersInFull() {
        assertThat(compute(leg(Order.Status.COMPLETED, "100", "100", "100")).getPlannedRiskAmount())
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("U27.3 — нога снята: входит НАЛИТОЙ долей — за налитую сделка рисковала")
    void u27_3_aCanceledLegEntersByItsFilledShare() {
        assertThat(compute(leg(Order.Status.CANCELED, "100", "100", "50")).getPlannedRiskAmount())
                .isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("U27.4 — нога в ошибочном состоянии: входит налитой долей")
    void u27_4_anErroredLegEntersByItsFilledShare() {
        assertThat(compute(leg(Order.Status.ERROR, "100", "100", "50")).getPlannedRiskAmount())
                .isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("U27.5 — нога снята без единого налива: вклад ноль")
    void u27_5_aCanceledLegWithoutAnyFillContributesNothing() {
        assertThat(compute(leg(Order.Status.CANCELED, "100", "100", "0")).getPlannedRiskAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U27.6 — у ноги нет заявленного риска: в отбор не входит ни одним числом")
    void u27_6_aLegWithoutAPlannedRiskStaysOutOfEveryNumber() {
        Order withoutRisk = leg(Order.Status.ACTIVE, null, "100", "50");

        assertAllFourAreZero(compute(withoutRisk));
    }

    @Test
    @DisplayName("U27.7 — плановый размер ноги ноль: доля налива ноль, деления нет")
    void u27_7_aZeroPlannedSizeYieldsAZeroShareWithoutDivision() {
        assertThat(compute(leg(Order.Status.CANCELED, "100", "0", "0")).getPlannedRiskAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U27.8 — взятое на входе: налитая доля у ЛЮБОЙ ноги, независимо от состояния")
    void u27_8_theIncurredShareIgnoresTheLegState() {
        assertThat(compute(leg(Order.Status.ACTIVE, "100", "100", "50")).getIncurredRiskAmount())
                .as("живая нога")
                .isEqualByComparingTo("50");
        assertThat(compute(leg(Order.Status.CANCELED, "100", "100", "50")).getIncurredRiskAmount())
                .as("снятая нога — та же доля")
                .isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("U27.9 — нога без налива: во взятое не входит")
    void u27_9_aLegWithoutAFillStaysOutOfTheIncurredRisk() {
        assertThat(compute(leg(Order.Status.ACTIVE, "100", "100", "0")).getIncurredRiskAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U27.10 — ноги на разных траншах: все четыре числа агрегатные")
    void u27_10_everyNumberAggregatesAcrossTranches() {
        Order first = leg(Order.Status.ACTIVE, "100", "100", "50");
        Order second = entryLeg(NEIGHBOUR_TRANCHE_ID, Order.Status.ACTIVE, "100", "100", "50");
        second.setPositionId(LIVE_EPISODE_ID);
        Deal deal = dealOf(List.of(tranche(TRANCHE_ID, List.of(first), List.of()),
                tranche(NEIGHBOUR_TRANCHE_ID, List.of(second), List.of())), liveEpisode("100"));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getPlannedRiskAmount()).isEqualByComparingTo("200");
        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("U27.11 — нога закрытого эпизода: в пару чисел живого эпизода не входит")
    void u27_11_aClosedEpisodeLegStaysOutOfTheEpisodePair() {
        Order closedEpisodeLeg = leg(Order.Status.COMPLETED, "100", "100", "100");
        closedEpisodeLeg.setPositionId(CLOSED_EPISODE_ID);
        Deal deal = dealOf(List.of(tranche(List.of(closedEpisodeLeg), List.of())), liveEpisode("100"));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getPlannedRiskAmount()).as("в заявленный риск входит").isEqualByComparingTo("100");
        assertThat(numbers.getIncurredRiskAmount()).as("во взятое входит").isEqualByComparingTo("100");
        assertThat(numbers.getCurrentRiskAmount()).as("в пару живого эпизода — нет").isEqualByComparingTo("0");
        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U27.12 — у ноги нет ссылки на эпизод: в пару чисел живого эпизода не входит")
    void u27_12_aLegWithoutAnEpisodeAxisStaysOutOfTheEpisodePair() {
        Order withoutAxis = leg(Order.Status.ACTIVE, "100", "100", "100");
        withoutAxis.setPositionId(null);
        Deal deal = dealOf(List.of(tranche(List.of(withoutAxis), List.of())), liveEpisode("100"));

        DealRiskNumbers numbers = service.compute(deal);

        assertThat(numbers.getCurrentRiskAmount()).isEqualByComparingTo("0");
        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U27.13 — нога не входного типа: ни в одно из четырёх чисел не входит")
    void u27_13_aLegOutsideTheEntryTypesStaysOutOfEveryNumber() {
        Order outsideTheTypes = leg(Order.Status.ACTIVE, "100", "100", "50");
        outsideTheTypes.setType(null);

        assertAllFourAreZero(compute(outsideTheTypes));
    }

    /** Входная нога базовой сборки, привязанная к живому эпизоду. */
    private static Order leg(Order.Status status, String plannedRisk, String plannedSize, String filled) {
        Order leg = entryLeg(status, plannedRisk, plannedSize, filled);
        leg.setPositionId(LIVE_EPISODE_ID);
        return leg;
    }

    /** Живой эпизод с названным нетто-размером. */
    private static Position liveEpisode(String size) {
        Position position = episode(size, ANCHOR);
        position.setId(LIVE_EPISODE_ID);
        return position;
    }

    /** Четвёрка, посчитанная по сделке с единственной названной ногой. */
    private DealRiskNumbers compute(Order leg) {
        return service.compute(dealOf(List.of(tranche(List.of(leg), List.of())), liveEpisode("50")));
    }

    /** Сделка базовой сборки с названными траншами и эпизодом. */
    private static Deal dealOf(List<DealTranche> tranches, Position episode) {
        Deal deal = emptyDeal();
        deal.setTranches(tranches);
        deal.setPositions(List.of(episode));
        return deal;
    }

    private static void assertAllFourAreZero(DealRiskNumbers numbers) {
        assertThat(numbers.getPlannedRiskAmount()).isEqualByComparingTo("0");
        assertThat(numbers.getIncurredRiskAmount()).isEqualByComparingTo("0");
        assertThat(numbers.getCurrentRiskAmount()).isEqualByComparingTo("0");
        assertThat(numbers.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }
}
