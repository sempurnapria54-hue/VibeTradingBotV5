package com.example.tradingbot.domain.deal;

import com.example.tradingbot.domain.command.ServiceCommand;
import com.example.tradingbot.domain.model.aggregate.deal.DealTranche;
import com.example.tradingbot.domain.safety.HoldSignal;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * Результат одного прохода FSM по ТРАНШУ: команды к исполнению этим
 * проходом (по принципу «одна актуальная команда за проход» обычно 0..1)
 * и целевой статус транша, если выходные проверки разрешили переход. RVO.
 *
 * <p>Отдельный тип от {@link DealTransition} намеренно: уровни несут
 * разные наборы статусов и разные причины закрытия, и общий тип с
 * nullable-полями обоих уровней позволял бы вернуть статус транша там,
 * где применяется переход сделки.
 *
 * <p>Переход применяется вызывающим и только после проверки
 * {@link DealTrancheStateMachine#transitionAllowed}: результат прохода —
 * НАМЕРЕНИЕ, а не разрешение.
 *
 * <p>См. docs/components/DealTrancheStateMachine.md,
 * docs/lifecycles/DealTranche.md.
 */
@Value
@Builder
public class TrancheTransition {

    /** Команды к исполнению этим проходом (обычно одна). */
    @Singular
    List<ServiceCommand> commands;

    /** Целевой статус транша; {@code null} — остаться в текущем. */
    DealTranche.Status nextStatus;

    /** Причина закрытия транша при переходе в терминал (опционально). */
    DealTranche.CloseReason closeReason;

    /** Сигнал поднять реактивный safety-холд scope (опционально). */
    HoldSignal holdSignal;

    /**
     * Требование увести СДЕЛКУ ошибочной тропой. Транш статус сделки не
     * пишет — он его ПРОСИТ: увод в ошибку есть решение уровня сделки, и
     * применяет его сделочный проход.
     *
     * <p>Поднимается только на предусловиях безопасности (чужой живой
     * риск, непокрытая позиция, недостижимое предусловие входа). Отказ
     * СТРАТЕГИЙНОЙ строки исполнения сюда НЕ ведёт: реакцию на него даёт
     * лестница ступеней, а сделка остаётся в своём статусе
     * (docs/lifecycles/DealActionState.md,
     * docs/components/DealOrchestratorJob.md).
     */
    Boolean escalateDealToError;

    /** Остаться в текущем статусе без команд. */
    public static TrancheTransition stay() {
        return TrancheTransition.builder().build();
    }

    /** Исполнить одну команду этим проходом, статус не менять. */
    public static TrancheTransition command(ServiceCommand command) {
        return TrancheTransition.builder().command(command).build();
    }

    /** Попросить сделочный проход увести сделку ошибочной тропой. */
    public static TrancheTransition escalateToDealError() {
        return TrancheTransition.builder().escalateDealToError(true).build();
    }

    /** Перейти в статус транша (выходная проверка пройдена), без команды. */
    public static TrancheTransition transition(DealTranche.Status nextStatus) {
        return TrancheTransition.builder().nextStatus(nextStatus).build();
    }
}
