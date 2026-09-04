package com.example.tradingbot.integration.model.okx.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Элемент data ответа OKX set-leverage: подтверждённые параметры плеча.
 * Сырой DTO источника; за adapter-границу не выходит.
 */
@Getter
@Setter
public class SetLeverageOkxResponse {

    /** Инструмент. */
    private String instId;

    /** Выставленное плечо. */
    private String lever;

    /** Режим маржи. */
    private String mgnMode;

    /** Сторона позиции. */
    private String posSide;
}
