package com.example.tradingbot.domain.model.anomaly;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnomalyReport extends Auditable {

    /**
     * Внутренний идентификатор отчёта об аномалии.
     */
    private Long id;

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
}
