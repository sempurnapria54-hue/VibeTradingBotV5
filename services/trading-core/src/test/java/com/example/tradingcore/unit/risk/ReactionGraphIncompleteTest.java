package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.ANCHOR;
import static com.example.tradingcore.unit.risk.RiskFixture.blockedVerdict;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.episode;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Карта реакций: неполнота графа первой строкой — группа {@code U21}
 * документа `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskBlockResolver.md §«Карта «вердикт → действие»»,
 * строка 1; довод — docs/processes/risk-evaluation.md §«Отказ по
 * неполноте графа реакцию не делит со схемой»).
 *
 * <p><b>Вердикт строится ПРЯМО:</b> карта получает его значением, и
 * прогон валидатора ради него смешал бы две красноты — «карта решила не
 * так» и «валидатор отказал не тем».
 */
class ReactionGraphIncompleteTest {

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("U21.1 — вердикт из одной неполноты графа, живого риска нет: ошибочная тропа")
    void u21_1_anIncompleteGraphGoesToTheDealErrorPath() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                blockedVerdict(RiskCheckCode.DEAL_GRAPH_INCOMPLETE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
        assertThat(action.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U21.2 — тот же вердикт при живом риске: стадия его реакцию не делит")
    void u21_2_theSameVerdictWithLiveRiskKeepsTheReaction() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()), DealTranche.Status.PRECHECK,
                blockedVerdict(RiskCheckCode.DEAL_GRAPH_INCOMPLETE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U21.3 — тот же вердикт на стадии сопровождения: та же реакция")
    void u21_3_theSameVerdictAtTheManagingStageKeepsTheReaction() {
        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.MANAGING,
                blockedVerdict(RiskCheckCode.DEAL_GRAPH_INCOMPLETE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U21.4 — неполнота графа и код карв-аута: причина — неполнота, а не свёртка")
    void u21_4_theGraphCodeWinsOverTheCarveOutSeniority() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()), DealTranche.Status.MANAGING,
                blockedVerdict(RiskCheckCode.DEAL_GRAPH_INCOMPLETE,
                        RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    @Test
    @DisplayName("U21.5 — неполнота графа стои́т второй: строка ищет код, а не его позицию")
    void u21_5_thePositionOfTheGraphCodeDoesNotMatter() {
        RiskBlockAction action = resolver.resolve(context(dealWithLiveEpisode()), DealTranche.Status.MANAGING,
                blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                        RiskCheckCode.DEAL_GRAPH_INCOMPLETE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.DEAL_GRAPH_INCOMPLETE);
    }

    @Test
    @DisplayName("U21.6 — неполнота пришла членом «пройдено»: свёртка читает только блокирующие")
    void u21_6_aPassedGraphMemberDoesNotTriggerTheRow() {
        RiskValidationResult passedGraphMember = RiskValidationResult.builder()
                .decision(RiskDecision.BLOCKED)
                .checks(List.of(RiskCheckResult.builder()
                        .code(RiskCheckCode.DEAL_GRAPH_INCOMPLETE)
                        .status(RiskCheckStatus.PASSED)
                        .comment("graph presented")
                        .build()))
                .comment("blocked without blocking members")
                .build();

        RiskBlockAction action = resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK,
                passedGraphMember);

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getRiskCode()).isNull();
        assertThat(action.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U21.7 — терминал транша по этой реакции не ставится ни на какой стадии")
    void u21_7_noStageEverCarriesACloseReasonForThisRow() {
        for (DealTranche.Status status : DealTranche.Status.values()) {
            assertThat(resolver.resolve(context(dealWithLiveEpisode()), status,
                    blockedVerdict(RiskCheckCode.DEAL_GRAPH_INCOMPLETE)).getCloseReason())
                    .as("стадия %s", status)
                    .isNull();
        }
    }

    /** Сделка с живым эпизодом: живой риск виден и без стадии. */
    private static Deal dealWithLiveEpisode() {
        Deal deal = emptyDeal();
        deal.setPositions(List.of(episode("10", ANCHOR)));
        return deal;
    }
}
