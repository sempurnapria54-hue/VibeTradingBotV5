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

    /**
     * Отказы отдельных проверок в порядке проверок: пройденная проверка в
     * перечень не пишется (docs/components/models/RiskCheckResult.md).
     */
    List<RiskCheckResult> checks;

    /** Короткое пояснение для логов и разбора. */
    String comment;

    /**
     * Итоговое решение risk-layer. Третьего решения нет: предупредительного
     * исхода у преконтроля нет ни у одной проверки.
     */
    public enum RiskDecision {

        /** Действие разрешено: перечень отказов пуст. */
        ALLOWED,

        /** Действие заблокировано риск-политикой: перечень отказов непуст. */
        BLOCKED
    }
}
