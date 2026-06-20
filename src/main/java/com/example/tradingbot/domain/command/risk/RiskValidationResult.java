package com.example.tradingbot.domain.command.risk;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Итог проверки риска, который возвращает RiskValidator. RVO, не
 * persisted. Контролируемый результат risk-policy проверки; unexpected
 * exceptions в него не превращаются (для них RuntimeErrorCode). Реакция на
 * decision принимается не здесь: RiskBlockResolver маппит BLOCKED в
 * RiskBlockAction, FSM handler исполняет. См.
 * docs/components/models/RiskValidationResult.md.
 */
@Value
@Builder
public class RiskValidationResult {

    /** Итоговое решение risk-layer. */
    RiskDecision decision;

    /** Детальные результаты отдельных проверок. */
    List<RiskCheckResult> checks;

    /** Короткое пояснение для логов / будущей истории исполнения. */
    String comment;

    /** Итоговое решение risk-layer. */
    public enum RiskDecision {

        /** Действие разрешено. */
        ALLOWED,

        /** Есть риск-предупреждения, но действие не блокируется. */
        WARNING,

        /** Действие заблокировано risk-policy. */
        BLOCKED
    }
}
