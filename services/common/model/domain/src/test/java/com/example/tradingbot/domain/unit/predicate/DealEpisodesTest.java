package com.example.tradingbot.domain.unit.predicate;

import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.at;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.dec;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.episode;
import static com.example.tradingbot.domain.unit.predicate.PredicateFixture.trancheWithExposure;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.position.Position;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Эпизоды сделки, окно линковки и суммы издержек — группа `U7` документа
 * `.claude/tests/cases/domain-model-predicates.md`
 * (docs/models/domain/core/Position.md §«Живой риск»;
 * docs/spec/cash-flow-linkage.json, {@code lowerBound};
 * docs/rules/statistics-aggregates.md).
 *
 * <p><b>Базовая сборка:</b> сделка с коллекцией эпизодов позиции: у
 * каждого статус, нетто-размер, момент создания и биржевые числа
 * издержек. Нижняя граница окна линковки и биржевой момент заведения
 * сделки — своими полями.
 */
class DealEpisodesTest {

    @Test
    @DisplayName("U7.1 — эпизод активен и нетто-размер положителен")
    void u7_1_anActiveSizedEpisodeIsLive() {
        Position live = episode(Position.Status.ACTIVE, "5", null);
        Deal subject = dealWith(live);

        assertThat(subject.livePosition()).isSameAs(live);
        assertThat(subject.hasLivePositionRisk()).isTrue();
    }

    /**
     * <b>Ожидание взято из дома, и дерево кода несёт иначе.</b> Дом
     * называет живым эпизодом конъюнкцию двух фактов — активный статус и
     * положительный нетто-размер, — а реализация резолвит дизъюнкцией
     * «живой риск ЛИБО просто активная запись» (находка `D-1`,
     * `.claude/work/backlog.md` §«Живой эпизод сделки резолвится шире, чем
     * объявлено домом»). Красный прогон и есть предъявление долга; метка
     * снимется правкой владельца.
     */
    @Test
    @Tag("debt")
    @DisplayName("U7.2 — эпизод активен, нетто-размер нулевой")
    void u7_2_anActiveEpisodeWithoutSizeIsNotLive() {
        Deal subject = dealWith(episode(Position.Status.ACTIVE, "0", null));

        assertThat(subject.livePosition()).isNull();
    }

    @Test
    @DisplayName("U7.3 — все эпизоды закрыты")
    void u7_3_closedEpisodesLeaveNoLiveOne() {
        Deal subject = dealWith(episode(Position.Status.CLOSED, "5", "1"));

        assertThat(subject.livePosition()).isNull();
        assertThat(subject.hasLivePositionRisk()).isFalse();
    }

    /** Состояние, которого модель не производит, но признак его называет. */
    @Test
    @DisplayName("U7.4 — два эпизода с живым риском")
    void u7_4_twoLiveEpisodesAreCounted() {
        Deal subject = dealWith(episode(Position.Status.ACTIVE, "5", null),
                episode(Position.Status.ACTIVE, "7", null));

        assertThat(subject.moreThanOneLiveEpisode()).isTrue();
    }

    @Test
    @DisplayName("U7.5 — один эпизод с живым риском")
    void u7_5_oneLiveEpisodeIsNotMoreThanOne() {
        Deal subject = dealWith(episode(Position.Status.ACTIVE, "5", null));

        assertThat(subject.moreThanOneLiveEpisode()).isFalse();
    }

    @Test
    @DisplayName("U7.6 — эпизод закрыт и готового нетто-результата не несёт")
    void u7_6_aClosedEpisodeWithoutResultAwaitsTheRecord() {
        Position awaiting = episode(Position.Status.CLOSED, "0", null);
        Deal subject = dealWith(awaiting);

        assertThat(subject.episodesAwaitingCloseRecord()).containsExactly(awaiting);
    }

    @Test
    @DisplayName("U7.7 — эпизод закрыт и нетто-результат несёт")
    void u7_7_aClosedEpisodeWithResultDoesNotAwait() {
        Deal subject = dealWith(episode(Position.Status.CLOSED, "0", "12"));

        assertThat(subject.episodesAwaitingCloseRecord()).isEmpty();
    }

    /** Предикат требует закрытости. */
    @Test
    @DisplayName("U7.8 — эпизод активен и нетто-результата не несёт")
    void u7_8_anActiveEpisodeDoesNotAwaitTheRecord() {
        Deal subject = dealWith(episode(Position.Status.ACTIVE, "5", null));

        assertThat(subject.episodesAwaitingCloseRecord()).isEmpty();
    }

    @Test
    @DisplayName("U7.9 — нижняя граница окна линковки заполнена")
    void u7_9_theDurableLowerBoundWins() {
        Deal subject = dealWith();
        subject.setBillsWindowBegin(at(5));
        subject.setExternalCreatedAt(at(0));

        assertThat(subject.billsWindowLowerBound()).isEqualTo(at(5));
    }

    /** Суррогат подставляется в чтении; различитель провенанса — пустота колонки. */
    @Test
    @DisplayName("U7.10 — нижняя граница пуста, биржевой момент заведения есть")
    void u7_10_theExchangeCreationMomentIsTheSurrogate() {
        Deal subject = dealWith();
        subject.setExternalCreatedAt(at(0));

        assertThat(subject.billsWindowLowerBound()).isEqualTo(at(0));
    }

    /** Подстановки «сейчас» нет. */
    @Test
    @DisplayName("U7.11 — пусты оба")
    void u7_11_bothAbsentGiveEmptiness() {
        assertThat(dealWith().billsWindowLowerBound()).isNull();
    }

    @Test
    @DisplayName("U7.12 — два эпизода с непустым финансированием")
    void u7_12_fundingIsSummedAsAPositiveCost() {
        Deal subject = dealWith(withFunding(episode(Position.Status.CLOSED, "0", "1"), "2"),
                withFunding(episode(Position.Status.CLOSED, "0", "1"), "3"));

        assertThat(subject.accumulatedFundingCost()).isEqualByComparingTo("5");
    }

    /** Пустое слагаемое не делает сумму пустой. */
    @Test
    @DisplayName("U7.13 — эпизод с пустым финансированием рядом с непустым")
    void u7_13_anAbsentAddendReadsAsZero() {
        Deal subject = dealWith(episode(Position.Status.CLOSED, "0", "1"),
                withFunding(episode(Position.Status.CLOSED, "0", "1"), "3"));

        assertThat(subject.accumulatedFundingCost()).isEqualByComparingTo("3");
    }

    /** Сумма приведена к издержке положительной вычитанием из нуля. */
    @Test
    @DisplayName("U7.14 — эпизоды с сырым отрицательным полем комиссии")
    void u7_14_feeIsNormalisedToAPositiveCost() {
        Position first = episode(Position.Status.CLOSED, "0", "1");
        first.setExternalFee(dec("-2"));
        Position second = episode(Position.Status.CLOSED, "0", "1");
        second.setExternalFee(dec("-3"));

        assertThat(dealWith(first, second).accumulatedFeeCost()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("U7.15 — эпизод со штрафом принудительного закрытия")
    void u7_15_liquidationPenaltyIsNormalisedTheSameWay() {
        Position penalised = episode(Position.Status.CLOSED, "0", "1");
        penalised.setExternalLiquidationPenalty(dec("-4"));

        assertThat(dealWith(penalised).accumulatedLiquidationPenaltyCost()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("U7.16 — коллекция эпизодов пуста")
    void u7_16_anEmptyEpisodeListGivesZerosAndEmptiness() {
        Deal subject = dealWith();

        assertThat(subject.accumulatedFundingCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(subject.accumulatedFeeCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(subject.accumulatedLiquidationPenaltyCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(subject.livePosition()).isNull();
        assertThat(subject.episodesAwaitingCloseRecord()).isEmpty();
    }

    /** Чтения строки из базы не происходит: идентичность берётся у графа. */
    @Test
    @DisplayName("U7.17 — транш с известным ключом строки")
    void u7_17_trancheIdentityIsReadFromTheLoadedGraph() {
        assertThat(dealWithTranche().trancheInternalId(7L)).isEqualTo("tr-7");
    }

    @Test
    @DisplayName("U7.18 — ключ, которого в загруженном графе нет")
    void u7_18_anUnknownKeyGivesEmptiness() {
        assertThat(dealWithTranche().trancheInternalId(8L)).isNull();
    }

    private static Deal dealWith(Position... episodes) {
        Deal deal = new Deal();
        deal.setPositions(List.of(episodes));
        return deal;
    }

    private static Position withFunding(Position episode, String funding) {
        episode.setExternalFundingCost(dec(funding));
        return episode;
    }

    private static Deal dealWithTranche() {
        DealTranche tranche = trancheWithExposure("10");
        tranche.setId(7L);
        tranche.setInternalId("tr-7");
        Deal deal = new Deal();
        deal.setTranches(List.of(tranche));
        return deal;
    }
}
