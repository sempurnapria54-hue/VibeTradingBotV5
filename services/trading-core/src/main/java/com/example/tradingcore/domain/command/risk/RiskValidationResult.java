package com.example.tradingcore.domain.command.risk;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Итог проверки риска, который возвращает {@code RiskValidator}
 * (docs/components/models/RiskValidationResult.md).
 *
 * <p>Контролируемый результат проверки риск-политики: неожиданные
 * исключения в него не превращаются — их классифицирует граница
 * исполнения (docs/rules/runtime-error-classification.md).
 *
 * <p>Реакция на решение принимается не здесь: карту «вердикт → действие»
 * держит {@code RiskBlockResolver}, исполняет — обработчик FSM
 * (docs/processes/risk-evaluation.md).
 */
@Value
@Builder
public class RiskValidationResult {

    /** Итоговое решение risk-layer. */
    RiskDecision decision;

    /** Детальные результаты отдельных проверок. */
    List<RiskCheckResult> checks;

    /** Короткое пояснение для логов и разбора. */
    String comment;

    /** Итоговое решение risk-layer. */
    public enum RiskDecision {

        /** Действие разрешено. */
        ALLOWED,

        /** Есть риск-предупреждения, но действие не блокируется. */
        WARNING,

        /** Действие заблокировано риск-политикой. */
        BLOCKED
    }
}
