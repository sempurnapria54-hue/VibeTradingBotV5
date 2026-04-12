package com.example.tradingbot.domain.model.deal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KillSwitchResult {

    /**
     * Успешно ли завершился kill-switch полностью по инструменту.
     */
    private boolean success;

    /**
     * Внутренний after-снимок (БД состояние) после выполнения kill-switch.
     */
    private String internalAfter;

    /**
     * Внешний after-снимок (биржевое состояние) после выполнения kill-switch.
     */
    private String externalAfter;

    /**
     * Пояснение результата выполнения или причины ошибки.
     */
    private String message;
}
