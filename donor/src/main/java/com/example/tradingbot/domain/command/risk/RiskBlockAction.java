package com.example.tradingbot.domain.command.risk;

import com.example.tradingbot.domain.command.RuntimeErrorCode;
import com.example.tradingbot.domain.model.aggregate.deal.Deal;
import lombok.Builder;
import lombok.Value;

/**
 * Действие, которое RiskBlockResolver возвращает handler'у по результату
 * risk-проверки, чтобы handler не содержал большой switch по всем
 * risk-кодам. RVO, не persisted. Исполнение RiskBlockAction — у FSM
 * handler. См. docs/components/models/RiskBlockAction.md.
 */
@Value
@Builder
public class RiskBlockAction {

    /** Что должен сделать FSM handler. */
    Type type;

    /** Причина закрытия, если нужно закрыть candidate Deal. */
    Deal.CloseReason closeReason;

    /** Код ошибки, если сделку нужно перевести в ERROR. */
    RuntimeErrorCode errorCode;

    /** Короткое пояснение для логов / будущей истории. */
    String comment;

    /** Что должен сделать FSM handler по результату risk-проверки. */
    public enum Type {

        /** Продолжить выполнение action. */
        CONTINUE,

        /** Продолжить, но сохранить предупреждение в логах / будущей истории. */
        CONTINUE_WITH_WARNING,

        /** Закрыть candidate Deal без ошибки (live risk ещё не создан). */
        CLOSE_CANDIDATE_DEAL,

        /** Перевести сделку в ERROR (далее ErrorHandler / safety-flow). */
        MOVE_DEAL_TO_ERROR,

        /** Не выполнять текущий action, запросить refresh фактов. */
        REQUEST_REFRESH,

        /** Пропустить action как более не актуальный. */
        SKIP_ACTION
    }
}
