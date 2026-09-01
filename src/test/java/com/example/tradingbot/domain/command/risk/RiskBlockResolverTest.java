package com.example.tradingbot.domain.command.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.command.DealContext;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckCode;
import com.example.tradingbot.domain.command.risk.RiskCheckResult.RiskCheckStatus;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает карту «вердикт → действие» с её домом
 * (docs/components/RiskBlockResolver.md).
 *
 * <p>Несущее: род реакции даёт СТАДИЯ, а терминал транша ставит только
 * БЕССРОЧНЫЙ вердикт. Временный отказ — занятый бюджет, несвежий баланс —
 * закрывал бы уровень сетки навсегда: бюджет освободится выходом соседнего
 * транша, а транша, который должен был войти, уже не будет.
 */
class RiskBlockResolverTest {

    private final RiskBlockResolver resolver = new RiskBlockResolver();

    @Test
    @DisplayName("Бессрочный отказ до живого риска — терминал кандидата с причиной RISK_CONTROL")
    void permanentVerdictClosesCandidate() {
        RiskBlockAction action = resolver.resolve(context(), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE));

        assertEquals(RiskBlockAction.Type.CLOSE_CANDIDATE_DEAL, action.getType());
        assertEquals(Deal.CloseReason.RISK_CONTROL, action.getCloseReason());
    }

    @Test
    @DisplayName("Временный отказ до живого риска терминала не даёт — действие пропускается")
    void temporaryVerdictSkipsAction() {
        RiskBlockAction action = resolver.resolve(context(), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.BALANCE_NOT_ENOUGH));

        assertEquals(RiskBlockAction.Type.SKIP_ACTION, action.getType());
        // Причина закрытия не пишется: транш не закрыт, он ждёт следующего прохода.
        assertEquals(null, action.getCloseReason());
    }

    @Test
    @DisplayName("Бессрочность вердикта — конъюнкция: один временный код делает вердикт временным")
    void mixedVerdictIsTemporary() {
        RiskBlockAction action = resolver.resolve(context(), DealTranche.Status.PRECHECK,
                blocked(RiskCheckCode.STOP_LOSS_INVALID_SIDE, RiskCheckCode.BALANCE_NOT_ENOUGH));

        assertEquals(RiskBlockAction.Type.SKIP_ACTION, action.getType());
    }

    @Test
    @DisplayName("Стадия с живым риском уводит сделку ошибочной тропой независимо от бессрочности")
    void liveRiskStageMovesDealToError() {
        for (DealTranche.Status status : List.of(DealTranche.Status.ENTRY_SUBMITTED,
                DealTranche.Status.MANAGING, DealTranche.Status.EXIT_PENDING)) {
            RiskBlockAction action = resolver.resolve(context(), status,
                    blocked(RiskCheckCode.BALANCE_NOT_ENOUGH));
            assertEquals(RiskBlockAction.Type.MOVE_DEAL_TO_ERROR, action.getType(),
                    "стадия " + status + " несёт живой риск");
        }
    }

    @Test
    @DisplayName("Признак бессрочности объявлен у каждого кода и разводит две тропы превышения")
    void permanenceIsDeclaredPerCode() {
        // Неделимый лот и расхождение расчёта — разные значения, оба бессрочные:
        // повтор ни одного из них не пройдёт без правки стратегии.
        assertTrue(RiskCheckCode.SIZE_MIN_LOT_EXCEEDS_RISK_BUDGET.isPermanent());
        assertTrue(RiskCheckCode.STOP_LOSS_INVALID_SIDE.isPermanent());
        // Операндные отказы временны: состояние меняется само.
        assertFalse(RiskCheckCode.BALANCE_NOT_ENOUGH.isPermanent());
        assertFalse(RiskCheckCode.POSITION_STATE_UNKNOWN.isPermanent());
    }

    private DealContext context() {
        Deal deal = new Deal();
        deal.setId(1L);
        return DealContext.builder().deal(deal).build();
    }

    private RiskValidationResult blocked(RiskCheckCode... codes) {
        List<RiskCheckResult> checks = List.of(codes).stream()
                .map(code -> RiskCheckResult.builder()
                        .code(code)
                        .status(RiskCheckStatus.BLOCKED)
                        .build())
                .toList();
        return RiskValidationResult.builder()
                .decision(RiskValidationResult.RiskDecision.BLOCKED)
                .checks(checks)
                .comment("проба")
                .build();
    }
}
