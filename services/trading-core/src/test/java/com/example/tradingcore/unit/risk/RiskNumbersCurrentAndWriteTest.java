package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.contextBuilder;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.core.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.DealContext;
import com.example.tradingcore.domain.command.risk.DealRiskNumbers;
import com.example.tradingcore.domain.command.risk.DealRiskNumbersService;
import com.example.tradingcore.persistence.service.DealDataService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Четвёрка: действующий уровень, обращение ставки и запись — группа
 * {@code U28} документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/spec/deal-risk-numbers.json, величины {@code dealCurrentRisk} и
 * {@code protectionRelievedRisk}).
 *
 * <p><b>Ставка ноги восстанавливается ОБРАЩЕНИЕМ сайзинга</b>, и
 * наблюдается она снятым защитой риском: при заявленном риске 929.55
 * обращение даёт 0.0005, при 959.1 — 0.001, и на недвинутом уровне 2910
 * снятое защитой в обоих случаях обязано быть нулём алгебраически.
 */
class RiskNumbersCurrentAndWriteTest {

    private static final Long LIVE_EPISODE_ID = 9L;

    private static final Long CLOSED_EPISODE_ID = 8L;

    /** Заявленный риск ноги, обращение которого даёт ставку 0.0005. */
    private static final String RISK_AT_BASE_RATE = "929.55";

    private final DealDataService dealDataService = mock(DealDataService.class);

    private final DealRiskNumbersService service = new DealRiskNumbersService(dealDataService);

    @Test
    @DisplayName("U28.1 — неотработанная доля считается от налива ЖИВОГО эпизода, а не пожизненного")
    void u28_1_theUnworkedShareIsScopedToTheLiveEpisode() {
        Order liveEpisodeLeg = leg("100", "100", "50", LIVE_EPISODE_ID);
        Order closedEpisodeLeg = leg("100", "100", "50", CLOSED_EPISODE_ID);
        Deal deal = dealOf(List.of(liveEpisodeLeg, closedEpisodeLeg), List.of(), liveEpisode("50"));

        assertThat(service.compute(deal).getCurrentRiskAmount())
                .as("пожизненный знаменатель 100 дал бы 25")
                .isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("U28.2 — эпизода нет: неотработанная доля ноль")
    void u28_2_withoutAnEpisodeTheUnworkedShareIsZero() {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(List.of(leg("100", "100", "50", LIVE_EPISODE_ID)), List.of())));

        assertThat(service.compute(deal).getCurrentRiskAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U28.3 — налив живого эпизода ноль: ноль, деления нет")
    void u28_3_aZeroEpisodeFillYieldsZeroWithoutDivision() {
        Deal deal = dealOf(List.of(leg("100", "100", "0", LIVE_EPISODE_ID)), List.of(), liveEpisode("50"));

        assertThatCode(() -> service.compute(deal)).doesNotThrowAnyException();
        assertThat(service.compute(deal).getCurrentRiskAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U28.4 — внешний размер эпизода пуст: ноль")
    void u28_4_anEmptyEpisodeSizeYieldsZero() {
        Position sizeless = liveEpisode("50");
        sizeless.setExternalSize(null);
        Deal deal = dealOf(List.of(leg("100", "100", "50", LIVE_EPISODE_ID)), List.of(), sizeless);

        assertThat(service.compute(deal).getCurrentRiskAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U28.5 — действующего уровня защиты нет: риск ноги равен взятому")
    void u28_5_withoutACurrentStopTheLegRiskEqualsTheIncurredOne() {
        Deal deal = dealOf(List.of(leg(RISK_AT_BASE_RATE, "100", "100", LIVE_EPISODE_ID)),
                List.of(), liveEpisode("100"));

        assertThat(service.compute(deal).getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U28.6 — уровень за безубытком: снятое защитой больше взятого, знак не клэмпится")
    void u28_6_aLevelBeyondBreakevenRelievesMoreThanTaken() {
        Deal deal = dealOf(List.of(leg(RISK_AT_BASE_RATE, "100", "100", LIVE_EPISODE_ID)),
                List.of(protection(70L, TRANCHE_ID, "3010", "100")), liveEpisode("100"));

        assertThat(service.compute(deal).getProtectionRelievedRiskAmount()).isEqualByComparingTo("999.5");
    }

    @Test
    @DisplayName("U28.7 — две живые защиты, длинное направление: действующим берётся НИЖНИЙ")
    void u28_7_theLeastFavourableLevelWins() {
        Deal deal = dealOf(List.of(leg(RISK_AT_BASE_RATE, "100", "100", LIVE_EPISODE_ID)),
                List.of(protection(70L, TRANCHE_ID, "2950", "100"),
                        protection(71L, TRANCHE_ID, "2910", "100")),
                liveEpisode("100"));

        assertThat(service.compute(deal).getProtectionRelievedRiskAmount())
                .as("по уровню 2950 снятое защитой вышло бы 399.8")
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U28.8 — ставка восстанавливается обращением закрытой формы сайзинга")
    void u28_8_theFeeRateIsRecoveredByInvertingTheSizingForm() {
        Deal deal = dealOf(List.of(leg("959.1", "100", "100", LIVE_EPISODE_ID)),
                List.of(protection(70L, TRANCHE_ID, "2910", "100")), liveEpisode("100"));

        assertThat(service.compute(deal).getProtectionRelievedRiskAmount())
                .as("ставка 0.001 восстановлена; базовая 0.0005 дала бы 29.55")
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("U28.9 — база обращения вырождена в ноль: ставка ноль, деления нет")
    void u28_9_aDegenerateInversionBaseYieldsAZeroRate() {
        Order degenerate = leg("100", "100", "100", LIVE_EPISODE_ID);
        degenerate.setPlannedContractValue(BigDecimal.ZERO);
        Deal deal = dealOf(List.of(degenerate), List.of(protection(70L, TRANCHE_ID, "2910", "100")),
                liveEpisode("100"));

        assertThatCode(() -> service.compute(deal)).doesNotThrowAnyException();
        assertThat(service.compute(deal).getProtectionRelievedRiskAmount())
                .as("стоимость контракта ноль гасит и риск при действующем стопе")
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("U28.10 — у ноги нет планового уровня стопа: ставка ноль")
    void u28_10_aLegWithoutAPlannedStopYieldsAZeroRate() {
        Order withoutPlannedStop = leg(RISK_AT_BASE_RATE, "100", "100", LIVE_EPISODE_ID);
        withoutPlannedStop.setPlannedStopPrice(null);
        Deal deal = dealOf(List.of(withoutPlannedStop), List.of(protection(70L, TRANCHE_ID, "2910", "100")),
                liveEpisode("100"));

        assertThat(service.compute(deal).getProtectionRelievedRiskAmount())
                .as("при нулевой ставке риск на уровне 2910 равен 900, а взятое — 929.55")
                .isEqualByComparingTo("29.55");
    }

    @Test
    @DisplayName("U28.11 — пересчёт при полном графе: четвёрка переписана, запись вызвана один раз")
    void u28_11_aCompleteGraphRewritesAllFourNumbersAndWritesOnce() {
        Deal deal = dealOf(List.of(leg(RISK_AT_BASE_RATE, "100", "100", LIVE_EPISODE_ID)),
                List.of(protection(70L, TRANCHE_ID, "3010", "100")), liveEpisode("100"));

        assertThat(service.recompute(context(deal, true))).isTrue();

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo(RISK_AT_BASE_RATE);
        assertThat(deal.getIncurredRiskAmount()).isEqualByComparingTo(RISK_AT_BASE_RATE);
        assertThat(deal.getCurrentRiskAmount()).isEqualByComparingTo(RISK_AT_BASE_RATE);
        assertThat(deal.getProtectionRelievedRiskAmount()).isEqualByComparingTo("999.5");
        verify(dealDataService).applyRiskNumbers(deal);
    }

    @Test
    @DisplayName("U28.12 — пересчёт при неполном графе: числа не тронуты, звено не завершается")
    void u28_12_anIncompleteGraphLeavesTheNumbersUntouched() {
        assertUntouched(context(dealWithoutEpisode(), false));
    }

    @Test
    @DisplayName("U28.13 — пересчёт при пустом признаке полноты графа: то же, что у неполного")
    void u28_13_anEmptyGraphFlagReadsAsNotPresented() {
        assertUntouched(context(dealWithoutEpisode(), null));
    }

    @Test
    @DisplayName("U28.15 — граф неполон при сборке, а правка писателя его дополнила: пересчёт идёт")
    void u28_15_aWriterThatCompletedTheGraphRecomputes() {
        Deal deal = dealWithPreviousNumbers();

        assertThat(service.recompute(context(deal, false)))
                .as("эпизод заведён самим писателем: признак сборки снят до него")
                .isTrue();

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo(RISK_AT_BASE_RATE);
        verify(dealDataService).applyRiskNumbers(deal);
    }

    @Test
    @DisplayName("U28.14 — пересчёт при сделке без единой ноги: все четыре числа нули, запись идёт")
    void u28_14_aDealWithoutLegsWritesFourZeroes() {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(List.of(), List.of())));

        assertThat(service.recompute(context(deal, true))).isTrue();

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo("0");
        assertThat(deal.getIncurredRiskAmount()).isEqualByComparingTo("0");
        assertThat(deal.getCurrentRiskAmount()).isEqualByComparingTo("0");
        assertThat(deal.getProtectionRelievedRiskAmount()).isEqualByComparingTo("0");
        verify(dealDataService).applyRiskNumbers(deal);
    }

    private void assertUntouched(DealContext dealContext) {
        Deal deal = dealContext.getDeal();

        assertThat(service.recompute(dealContext)).isFalse();

        assertThat(deal.getPlannedRiskAmount()).isEqualByComparingTo("777");
        verify(dealDataService, never()).applyRiskNumbers(any());
    }

    /** Сделка с уже записанной четвёркой: её и не должен тронуть отказавший пересчёт. */
    private static Deal dealWithPreviousNumbers() {
        Deal deal = dealOf(List.of(leg(RISK_AT_BASE_RATE, "100", "100", LIVE_EPISODE_ID)),
                List.of(protection(70L, TRANCHE_ID, "2910", "100")), liveEpisode("100"));
        deal.setPlannedRiskAmount(new BigDecimal("777"));
        return deal;
    }

    /**
     * Та же сделка, чей налив наблюдён, а эпизода в графе нет: граф неполон
     * и при сборке, и после правки писателя. Экспозиция транша выводится
     * сборкой графа — ею же и здесь, иначе налив не был бы наблюдён.
     */
    private static Deal dealWithoutEpisode() {
        Deal deal = dealWithPreviousNumbers();
        deal.setPositions(List.of());
        deal.deriveTrancheExposures();
        return deal;
    }

    /** Входная нога живого эпизода базовой сборки. */
    private static Order leg(String plannedRisk, String plannedSize, String filled, Long episodeId) {
        Order leg = entryLeg(Order.Status.COMPLETED, plannedRisk, plannedSize, filled);
        leg.setPositionId(episodeId);
        return leg;
    }

    /** Живой эпизод с названным нетто-размером. */
    private static Position liveEpisode(String size) {
        Position position = episode(size, ANCHOR);
        position.setId(LIVE_EPISODE_ID);
        return position;
    }

    /** Сделка базовой сборки: один транш с названными ногами и защитами. */
    private static Deal dealOf(List<Order> legs, List<AlgoOrder> protections, Position episode) {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(legs, protections)));
        deal.setPositions(List.of(episode));
        return deal;
    }

    /** Контекст пересчёта с названным признаком полноты графа. */
    private static DealContext context(Deal deal, Boolean graphComplete) {
        return contextBuilder(deal).graphComplete(graphComplete).build();
    }
}
