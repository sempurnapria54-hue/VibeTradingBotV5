package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Старшинство причин закрытия и наследование — группа `U8` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/spec/deal-lifecycle.json, {@code trancheCloseReasonRank},
 * {@code topTrancheReasonRank}, {@code dealCloseReasonBySeniority};
 * docs/lifecycles/DealTranche.md §«Писатель причины закрытия транша —
 * обработчик терминального ребра»).
 *
 * <p><b>Базовая сборка:</b> сделка с траншами, у части которых
 * проставлена причина закрытия; у сделки — своя причина для обратного
 * направления.
 */
class CloseReasonSeniorityTest {

    @Test
    @DisplayName("U8.1 — транши с причинами «закрыто извне» и «тейк-профит»")
    void u8_1_theSeniorReasonWins() {
        assertThat(dealOf(DealTranche.CloseReason.EXTERNAL_CLOSE, DealTranche.CloseReason.TAKE_PROFIT)
                .closeReasonBySeniority()).isEqualTo(Deal.CloseReason.EXTERNAL_CLOSE);
    }

    @Test
    @DisplayName("U8.2 — транши с причинами «штатный выход» и «остановка убытка»")
    void u8_2_stopLossOutranksStrategyExit() {
        assertThat(dealOf(DealTranche.CloseReason.STRATEGY_EXIT, DealTranche.CloseReason.STOP_LOSS)
                .closeReasonBySeniority()).isEqualTo(Deal.CloseReason.STOP_LOSS);
    }

    /** Порядок закрытия старшинства не даёт. */
    @Test
    @DisplayName("U8.3 — транш, закрывшийся первым, несёт младшую причину")
    void u8_3_theOrderOfClosingDoesNotRank() {
        assertThat(dealOf(DealTranche.CloseReason.TAKE_PROFIT, DealTranche.CloseReason.STOP_LOSS)
                .closeReasonBySeniority()).isEqualTo(Deal.CloseReason.STOP_LOSS);
    }

    /** Благоприятная причина не подставляется. */
    @Test
    @DisplayName("U8.4 — ни один транш причины не несёт")
    void u8_4_noReasonsGiveEmptiness() {
        assertThat(dealOf(null, null).closeReasonBySeniority()).isNull();
    }

    @Test
    @DisplayName("U8.5 — коллекция траншей пуста")
    void u8_5_anEmptyTrancheListGivesEmptiness() {
        Deal subject = new Deal();
        subject.setTranches(List.of());

        assertThat(subject.closeReasonBySeniority()).isNull();
    }

    /** Значение вне перечня старшинства даёт замыкающий ранг и не выигрывает. */
    @Test
    @DisplayName("U8.6 — у транша стои́т значение вне перечня старшинства")
    void u8_6_anOutOfListReasonNeverWins() {
        assertThat(dealOf(DealTranche.CloseReason.UNKNOWN).closeReasonBySeniority()).isNull();
        assertThat(dealOf(DealTranche.CloseReason.UNKNOWN, DealTranche.CloseReason.TAKE_PROFIT)
                .closeReasonBySeniority()).isEqualTo(Deal.CloseReason.TAKE_PROFIT);
    }

    @ParameterizedTest
    @EnumSource(value = DealTranche.CloseReason.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U8.7 — каждое из семи значений перечня старшинства порознь")
    void u8_7_theRankedSetMapsOneToOne(DealTranche.CloseReason reason) {
        assertThat(dealOf(reason).closeReasonBySeniority()).isEqualTo(Deal.CloseReason.valueOf(reason.name()));
    }

    @Test
    @DisplayName("U8.8 — у сделки стои́т причина «закрыто извне»")
    void u8_8_theTrancheInheritsTheSameValue() {
        assertThat(dealClosedBy(Deal.CloseReason.EXTERNAL_CLOSE).inheritedTrancheCloseReason())
                .isEqualTo(DealTranche.CloseReason.EXTERNAL_CLOSE);
    }

    /** Аварийная тропа сделочная — на транше это значение не пишется. */
    @Test
    @DisplayName("U8.9 — у сделки стои́т аварийная причина")
    void u8_9_theEmergencyReasonIsNotInherited() {
        assertThat(dealClosedBy(Deal.CloseReason.EMERGENCY_CLOSE).inheritedTrancheCloseReason()).isNull();
    }

    @Test
    @DisplayName("U8.10 — у сделки причина пуста")
    void u8_10_anAbsentDealReasonIsNotInherited() {
        assertThat(dealClosedBy(null).inheritedTrancheCloseReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = Deal.CloseReason.class, names = "EMERGENCY_CLOSE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("U8.11 — каждое из семи наследуемых значений порознь")
    void u8_11_theInheritedSetMapsOneToOne(Deal.CloseReason reason) {
        assertThat(dealClosedBy(reason).inheritedTrancheCloseReason())
                .isEqualTo(DealTranche.CloseReason.valueOf(reason.name()));
    }

    private static Deal dealOf(DealTranche.CloseReason... reasons) {
        Deal deal = new Deal();
        deal.setTranches(Arrays.stream(reasons).map(reason -> {
            DealTranche tranche = trancheWithExposure(null);
            tranche.setCloseReason(reason);
            return tranche;
        }).toList());
        return deal;
    }

    private static Deal dealClosedBy(Deal.CloseReason reason) {
        Deal deal = new Deal();
        deal.setCloseReason(reason);
        return deal;
    }
}
