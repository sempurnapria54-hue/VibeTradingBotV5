package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.NEIGHBOUR_TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.STOP;
import static com.example.tradingcore.unit.risk.RiskFixture.TRANCHE_ID;
import static com.example.tradingcore.unit.risk.RiskFixture.blockedVerdict;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.entryLeg;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static com.example.tradingcore.unit.risk.RiskFixture.protection;
import static com.example.tradingcore.unit.risk.RiskFixture.tranche;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Признак живого риска — группа {@code U24} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskBlockResolver.md §«Карта «вердикт → действие»»;
 * операнды — docs/spec/protection-coverage.json, величина
 * {@code hasLiveEpisode}).
 *
 * <p><b>Вердикт группы пинится ОДНИМ бессрочным кодом ВНЕ карв-аута</b>,
 * и пин обязателен: бессрочность и членство в карв-ауте пересекаются, и
 * на пересекающемся коде при живом риске реакция была бы пропуском
 * действия, а не аварийной тропой. Отсюда читается признак: закрытие
 * кандидатной сделки означает «живого риска нет», ошибочная тропа —
 * «есть».
 */
class LiveRiskPredicateTest {

    /** Бессрочный код вне карв-аута: различает обе ветви признака. */
    private static final RiskCheckCode PERMANENT_OUTSIDE_THE_CARVE_OUT = RiskCheckCode.RISK_PER_ACTION_EXCEEDED;

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("U24.1 — базовая сборка: живого риска нет, реакция — закрытие кандидатной сделки")
    void u24_1_anIdleCandidateHasNoLiveRisk() {
        assertThat(resolve(candidateDeal(), DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
    }

    @Test
    @DisplayName("U24.2 — эпизод жив и несёт риск: реакция — ошибочная тропа")
    void u24_2_aLiveEpisodeMarksLiveRisk() {
        Deal deal = candidateDeal();
        deal.setPositions(List.of(episode("10", ANCHOR)));

        assertThat(resolve(deal, DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.3 — стадия «отправленный вход»: живой риск есть по стадии")
    void u24_3_theEntrySubmittedStageMarksLiveRisk() {
        assertThat(resolve(candidateDeal(), DealTranche.Status.ENTRY_SUBMITTED))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.4 — стадия «подтверждённый вход»: то же")
    void u24_4_theEntryFinalizedStageMarksLiveRisk() {
        assertThat(resolve(candidateDeal(), DealTranche.Status.ENTRY_FINALIZED))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.5 — стадия «переключение защиты»: то же")
    void u24_5_theProtectionSwitchedStageMarksLiveRisk() {
        assertThat(resolve(candidateDeal(), DealTranche.Status.PROTECTION_SWITCHED))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.6 — стадия «сопровождение»: то же")
    void u24_6_theManagingStageMarksLiveRisk() {
        assertThat(resolve(candidateDeal(), DealTranche.Status.MANAGING))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.7 — стадия «выход транша»: то же")
    void u24_7_theExitPendingStageMarksLiveRisk() {
        assertThat(resolve(candidateDeal(), DealTranche.Status.EXIT_PENDING))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.8 — стадия предвходовая, у транша живая входная заявка: третий дизъюнкт")
    void u24_8_aLiveOrderMarksLiveRisk() {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(List.of(entryLeg(Order.Status.ACTIVE, "0", "10", "0")), List.of())));

        assertThat(resolve(deal, DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.9 — стадия предвходовая, у транша живая условная заявка: живой риск есть")
    void u24_9_aLiveAlgoOrderMarksLiveRisk() {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(List.of(), List.of(protection(STOP.toPlainString())))));

        assertThat(resolve(deal, DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.10 — живая заявка висит на СОСЕДНЕМ транше: обход идёт по всем")
    void u24_10_aLiveOrderOfANeighbouringTrancheMarksLiveRiskToo() {
        Deal deal = emptyDeal();
        deal.setTranches(List.of(tranche(TRANCHE_ID, List.of(), List.of()),
                tranche(NEIGHBOUR_TRANCHE_ID,
                        List.of(entryLeg(NEIGHBOUR_TRANCHE_ID, Order.Status.ACTIVE, "0", "10", "0")),
                        List.of())));

        assertThat(resolve(deal, DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U24.11 — эпизод есть, но риска не несёт: живого риска нет")
    void u24_11_anEpisodeWithoutRiskIsNotLiveRisk() {
        Deal deal = candidateDeal();
        deal.setPositions(List.of(episode("0", ANCHOR)));

        assertThat(resolve(deal, DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
    }

    @Test
    @DisplayName("U24.12 — траншей у сделки нет вовсе: живого риска нет, исключения нет")
    void u24_12_aDealWithoutTranchesIsNotAnException() {
        Deal deal = emptyDeal();
        deal.setTranches(null);

        assertThatCode(() -> resolve(deal, DealTranche.Status.PRECHECK)).doesNotThrowAnyException();
        assertThat(resolve(deal, DealTranche.Status.PRECHECK))
                .isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
    }

    /** Кандидатная сделка: один предвходовой транш без живых заявок и защит. */
    private static Deal candidateDeal() {
        Deal deal = emptyDeal();
        DealTranche tranche = tranche(List.of(), List.of());
        tranche.setStatus(DealTranche.Status.PRECHECK);
        deal.setTranches(List.of(tranche));
        return deal;
    }

    /** Род реакции карты на пин-вердикт при названных сделке и стадии. */
    private RiskBlockAction.Type resolve(Deal deal, DealTranche.Status status) {
        return resolver.resolve(context(deal), status, blockedVerdict(PERMANENT_OUTSIDE_THE_CARVE_OUT)).getType();
    }
}
