package com.example.tradingbot.domain.model.anomaly;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

@Getter
@Setter
public class AnomalyReport extends Auditable {

    /**
     * Внутренний идентификатор отчёта об аномалии.
     */
    private Long id;

    /**
     * Межсервисный идентификатор.
     */
    private String internalId;

    /**
     * Идентификатор биржи, в рамках которой зафиксирована аномалия.
     */
    private Long exchangeId;

    /**
     * Идентификатор инструмента, если аномалия относится к конкретному инструменту.
     */
    private Long instrumentId;

    /**
     * Текущий статус обработки аномалии.
     */
    private Status status;

    /**
     * Уровень критичности аномалии.
     */
    private Severity severity;

    /**
     * Код аномалии для машинной обработки.
     */
    private String code;

    /**
     * Человекочитаемое описание аномалии.
     */
    private String message;

    /**
     * Внутренний снимок состояния до обработки аномалии в JSON-строке.
     */
    private String internalBefore;

    /**
     * Внешний снимок состояния до обработки аномалии в JSON-строке.
     */
    private String externalBefore;

    /**
     * Внутренний снимок состояния после обработки аномалии в JSON-строке.
     */
    private String internalAfter;

    /**
     * Внешний снимок состояния после обработки аномалии в JSON-строке.
     */
    private String externalAfter;

    public enum Status {

        /**
         * Отчёт об аномалии создан.
         */
        CREATED,

        /**
         * Аномалия находится в процессе обработки.
         */
        IN_PROGRESS,

        /**
         * Для аномалии выполнен kill-switch.
         */
        KILL_SWITCH_EXECUTED,

        /**
         * Обработка аномалии завершена успешно.
         */
        COMPLETED,

        /**
         * Обработка аномалии завершилась ошибкой.
         */
        ERROR
    }

    public enum Severity {

        /**
         * Критичная аномалия, требующая экстренной реакции.
         */
        CRITICAL,

        /**
         * Некритичная аномалия.
         */
        NON_CRITICAL
    }

    public void toInProgress() {
        if (isNull(status) || isFalse(status == Status.CREATED)) {
            throw new IllegalStateException("Unexpected error: invalid status transition in AnomalyReport");
        }
        setStatus(AnomalyReport.Status.IN_PROGRESS);
    }

    public void toKillSwitchExecuted(String internalAfter, String externalAfter) {
        if (isNull(status) || isFalse(status == Status.IN_PROGRESS)) {
            throw new IllegalStateException("Unexpected error: invalid status transition in AnomalyReport");
        }
        setStatus(AnomalyReport.Status.KILL_SWITCH_EXECUTED);
        setInternalAfter(internalAfter);
        setExternalAfter(externalAfter);
    }

    public void toCompleted(String internalAfter, String externalAfter) {
        if (isNull(status) || isFalse(status == Status.KILL_SWITCH_EXECUTED)) {
            throw new IllegalStateException("Unexpected error: invalid status transition in AnomalyReport");
        }
        setStatus(AnomalyReport.Status.COMPLETED);
        setInternalAfter(internalAfter);
        setExternalAfter(externalAfter);
    }

    public void toError(String internalAfter, String externalAfter) {
        if (isNull(status) || isFalse(status == Status.KILL_SWITCH_EXECUTED)) {
            throw new IllegalStateException("Unexpected error: invalid status transition in AnomalyReport");
        }
        setStatus(AnomalyReport.Status.COMPLETED);
        setInternalAfter(internalAfter);
        setExternalAfter(externalAfter);
    }


}
