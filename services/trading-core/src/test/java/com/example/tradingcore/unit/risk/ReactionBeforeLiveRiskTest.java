package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.blockedVerdict;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.verdict;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Карта реакций: до живого риска — группа {@code U23} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskBlockResolver.md §«Карта «вердикт → действие»»,
 * строки 3-4; определение бессрочности — там же, §«Бессрочность отказа
 * — свойство КОДА, и вот его определение»).
 *
 * <p><b>Базовая сборка.</b> Стадия транша — предвходовая; живого риска
 * нет ни по позиции, ни по заявкам траншей; вердикт строится прямо.
 */
class ReactionBeforeLiveRiskTest {

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("U23.1 — вердикт целиком из бессрочных кодов: закрытие кандидатной сделки")
    void u23_1_aPermanentVerdictClosesTheCandidate() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                RiskCheckCode.SIZE_BELOW_MIN));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
        assertThat(action.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.STOP_LOSS_INVALID_SIDE);
    }

    @Test
    @DisplayName("U23.2 — вердикт целиком из временны́х кодов: пропуск действия")
    void u23_2_aTemporaryVerdictSkipsTheAction() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U23.3 — смешанный вердикт: свёртка бессрочности — КОНЪЮНКЦИЯ")
    void u23_3_oneTemporaryCodeMakesTheWholeVerdictTemporary() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.STOP_LOSS_INVALID_SIDE,
                RiskCheckCode.FEE_RATE_UNAVAILABLE));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
    }

    @Test
    @DisplayName("U23.4 — решение блокирующее, блокирующих членов нет: терминал не ставится")
    void u23_4_anEmptyBlockingListNeverCloses() {
        RiskBlockAction action = resolve(verdict(RiskDecision.BLOCKED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getCloseReason()).isNull();
    }

    /**
     * Отложенного вердикта карта не знает: несвежий снимок средств
     * добывает предвходовая проверка до преконтроля, и в вердикт он не
     * приходит (docs/components/RiskBlockResolver.md §«Отложенного вердикта
     * карта не знает»). Имена сверяются строкой: снятое значение символом
     * не назвать.
     */
    @Test
    @DisplayName("U23.5 — перечень кодов и реакций обойдён: кода несвежести нет, запроса добычи карта не даёт")
    void u23_5_theMapKnowsNoDeferredVerdict() {
        assertThat(Arrays.stream(RiskCheckCode.values()).map(Enum::name))
                .doesNotContain("BALANCE_NOT_FRESH");
        assertThat(Arrays.stream(RiskBlockAction.Type.values()).map(Enum::name))
                .doesNotContain("REQUEST_REFRESH");
        for (RiskCheckCode code : RiskCheckCode.values()) {
            assertThat(resolve(blockedVerdict(code)).getType())
                    .as("код %s", code)
                    .isIn(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL, RiskBlockAction.Type.SKIP_ACTION,
                            RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        }
    }

    @Test
    @DisplayName("U23.7 — карв-аутный, но бессрочный код: членство на этой стадии ничего не решает")
    void u23_7_carveOutMembershipDoesNotMatterBeforeLiveRisk() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
        assertThat(action.getCloseReason()).isEqualTo(Deal.CloseReason.RISK_CONTROL);
    }

    @Test
    @DisplayName("U23.8 — решение разрешающее: продолжить, причин нет")
    void u23_8_anAllowingVerdictContinues() {
        RiskBlockAction action = resolve(verdict(RiskDecision.ALLOWED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.CONTINUE);
        assertThat(action.getRiskCode()).isNull();
        assertThat(action.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U23.10 — стадия транша не передана вовсе: реакция выводится по остальным операндам")
    void u23_10_anAbsentStageIsNotAnException() {
        assertThatCode(() -> resolver.resolve(context(emptyDeal()), null,
                blockedVerdict(RiskCheckCode.STOP_LOSS_INVALID_SIDE)))
                .doesNotThrowAnyException();
        assertThat(resolver.resolve(context(emptyDeal()), null,
                blockedVerdict(RiskCheckCode.STOP_LOSS_INVALID_SIDE)).getType())
                .isEqualTo(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL);
    }

    /** Реакция карты на названный вердикт до живого риска. */
    private RiskBlockAction resolve(RiskValidationResult verdict) {
        return resolver.resolve(context(emptyDeal()), DealTranche.Status.PRECHECK, verdict);
    }
}
