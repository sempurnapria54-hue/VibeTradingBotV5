package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import com.example.tradingbot.domain.safety.HoldSignal;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Результат одного прохода FSM по сделке: команды к исполнению этим
 * проходом (по принципу «одна актуальная команда за проход» обычно 0..1) и
 * целевой статус, если выходные проверки разрешили переход. RVO.
 *
 * <p>Терминальные/статусные рёбра, которые делают сами финализационные
 * executor'ы (MARK_DEAL_CLOSED → CLOSED, MARK_DEAL_ERROR → ERROR),
 * возвращаются как команда с {@code nextStatus == null}: статус двигает
 * executor. Прямые переходы по выходным проверкам (PRECHECK →
 * ENTRY_SUBMITTED, candidate-close PRECHECK → CLOSED) — через
 * {@code nextStatus} (+ closeReason/shutdownReason), их применяет
 * оркестратор. См. docs/components/DealStateMachine.md.
 *
 * <p>Опциональный {@link HoldSignal} несёт сигнал «поднять реактивный
 * safety-холд scope» (L3/L4): handler классифицировал CRITICAL и одновременно
 * уводит свою сделку в ERROR (команда MARK_DEAL_ERROR) и просит холд. Сам
 * холд координирует {@link com.example.tradingbot.domain.safety.SafetyHoldCoordinator}
 * над сделкой в проходе оркестратора (дизайн холдов шага 6).
 */
@Value
@Builder
public class DealTransition {

    /** Команды к исполнению этим проходом (обычно одна). */
    @Singular
    List<ServiceCommand> commands;

    /** Целевой статус сделки; {@code null} — остаться в текущем. */
    Deal.Status nextStatus;

    /** Причина закрытия при переходе в закрывающий статус (опционально). */
    Deal.CloseReason closeReason;

    /** Причина graceful shutdown (опционально). */
    Deal.ShutdownReason shutdownReason;

    /** Сигнал поднять реактивный safety-холд scope (опционально). */
    HoldSignal holdSignal;

    /** Остаться в текущем статусе без команд. */
    public static DealTransition stay() {
        return DealTransition.builder().build();
    }

    /** Исполнить одну команду этим проходом, статус не менять. */
    public static DealTransition command(ServiceCommand command) {
        return DealTransition.builder().command(command).build();
    }

    /** Перейти в статус (выходная проверка пройдена), без команды. */
    public static DealTransition transition(Deal.Status nextStatus) {
        return DealTransition.builder().nextStatus(nextStatus).build();
    }
}
