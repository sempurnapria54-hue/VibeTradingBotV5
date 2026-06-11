package com.example.tradingbot.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело OKX close-position (POST /trade/close-position). mgnMode/posSide
 * — adapter-константы; ccy — settle currency; autoCxl снимает активные
 * ордера. null-поля не сериализуются. См. docs/models/mapping/Position.md.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClosePositionOkxRequest {

    /** Инструмент. */
    private String instId;

    /** Режим маржи (adapter-константа isolated). */
    private String mgnMode;

    /** Сторона позиции (adapter-константа net). */
    private String posSide;

    /** Валюта расчётов (settle currency). */
    private String ccy;

    /** Снять активные ордера при закрытии. */
    private Boolean autoCxl;
}
