package com.example.tradingcore.unit.risk;

import static com.example.tradingcore.unit.risk.RiskFixture.blockedVerdict;
import static com.example.tradingcore.unit.risk.RiskFixture.context;
import static com.example.tradingcore.unit.risk.RiskFixture.emptyDeal;
import static com.example.tradingcore.unit.risk.RiskFixture.verdict;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingcore.domain.command.risk.RiskBlockAction;
import com.example.tradingcore.domain.command.risk.RiskBlockResolver;
import com.example.tradingcore.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingcore.domain.command.risk.RiskValidationResult;
import com.example.tradingcore.domain.command.risk.RiskValidationResult.RiskDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Карта реакций: живой риск и карв-аут — группа {@code U22} документа
 * `.claude/tests/cases/trading-core-risk.md` (дом —
 * docs/components/RiskBlockResolver.md §«Карта «вердикт → действие»»,
 * строка 2; перечень карв-аута —
 * docs/processes/risk-evaluation.md §«Карв-аут исчерпанного бюджета
 * сделки»).
 *
 * <p><b>Базовая сборка.</b> Стадия транша — отправленный вход, то есть
 * живой риск виден по ней одной; вердикт строится прямо.
 */
class ReactionUnderLiveRiskTest {

    private static final DealTranche.Status LIVE_RISK_STAGE = DealTranche.Status.ENTRY_SUBMITTED;

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("U22.1 — вердикт целиком из кодов карв-аута: пропуск действия, причина — старший код")
    void u22_1_aFullyCarvedOutVerdictSkipsTheAction() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                RiskCheckCode.INSTRUMENT_SAFETY_HOLD));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
        assertThat(action.getCloseReason()).isNull();
    }

    @Test
    @DisplayName("U22.2 — вердикт из одного кода вне карв-аута: ошибочная тропа сделки")
    void u22_2_aCodeOutsideTheCarveOutGoesToTheDealErrorPath() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isEqualTo(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U22.3 — смешанный вердикт: свёртка членства — КОНЪЮНКЦИЯ")
    void u22_3_oneForeignCodeReturnsTheVerdictToTheErrorPath() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                RiskCheckCode.RISK_PER_ACTION_EXCEEDED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U22.4 — те же коды переставлены: реакция та же, причина — первый член перечня")
    void u22_4_theReasonFollowsThePositionInTheList() {
        RiskBlockAction straight = resolve(blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED,
                RiskCheckCode.RISK_PER_ACTION_EXCEEDED));
        RiskBlockAction swapped = resolve(blockedVerdict(RiskCheckCode.RISK_PER_ACTION_EXCEEDED,
                RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED));

        assertThat(swapped.getType()).isEqualTo(straight.getType());
        assertThat(straight.getRiskCode()).isEqualTo(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED);
        assertThat(swapped.getRiskCode()).isEqualTo(RiskCheckCode.RISK_PER_ACTION_EXCEEDED);
    }

    @Test
    @DisplayName("U22.5 — решение блокирующее, блокирующих членов нет: членством пустой перечень не считается")
    void u22_5_anEmptyBlockingListIsNotAMembership() {
        RiskBlockAction action = resolve(verdict(RiskDecision.BLOCKED));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
        assertThat(action.getRiskCode()).isNull();
    }

    @Test
    @DisplayName("U22.6 — несвежий снимок при живом риске: рассогласование учёта, а не запрос добычи")
    void u22_6_aStaleBalanceUnderLiveRiskIsAnAccountingMismatch() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.BALANCE_NOT_FRESH));

        assertThat(action.getType()).isEqualTo(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR);
    }

    @Test
    @DisplayName("U22.7 — живой риск виден только по стадии: стадия сама по себе его означает")
    void u22_7_theStageAloneMarksLiveRisk() {
        RiskBlockAction action = resolve(blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED));

        assertThat(action.getType())
                .as("сделка пуста: ни эпизода, ни заявок — живой риск даёт только стадия")
                .isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
    }

    @Test
    @DisplayName("U22.8 — причина закрытия у обеих реакций строки пуста")
    void u22_8_neitherReactionOfThisRowCarriesACloseReason() {
        assertThat(resolve(blockedVerdict(RiskCheckCode.RISK_PER_DEAL_CUMULATIVE_EXCEEDED)).getCloseReason())
                .as("пропуск действия")
                .isNull();
        assertThat(resolve(blockedVerdict(RiskCheckCode.RISK_PER_ACTION_EXCEEDED)).getCloseReason())
                .as("ошибочная тропа сделки")
                .isNull();
    }

    @Test
    @DisplayName("U22.9 — перенос, который цена ещё не прошла, и незаданное плечо: пропуск действия, не авария")
    void u22_9_aDeferredTransferAndAnUnassignedLeverageStayInsideTheCarveOut() {
        // Без членства в карв-ауте отложенный перенос при живой позиции
        // уводил бы сделку в аварийный контур — то есть отказ, заведённый
        // сберечь позицию, снимал бы с неё защиту исполнением по рынку.
        assertThat(resolve(blockedVerdict(RiskCheckCode.STOP_LOSS_BEYOND_MARK_PRICE)).getType())
                .isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
        assertThat(resolve(blockedVerdict(RiskCheckCode.LEVERAGE_NOT_CONFIGURED)).getType())
                .isEqualTo(RiskBlockAction.Type.SKIP_ACTION);
    }

    /** Реакция карты на названный вердикт при живом риске по стадии. */
    private RiskBlockAction resolve(RiskValidationResult verdict) {
        return resolver.resolve(context(emptyDeal()), LIVE_RISK_STAGE, verdict);
    }
}
