package com.example.connector.okx.integration.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело запроса OKX set-leverage (POST /api/v5/account/set-leverage):
 * выставить рабочее плечо инструмента перед постановкой entry-ордера.
 * Сериализуется по именам полей (NON_NULL). См.
 * docs/components/PrecheckHandler.md (set-leverage перед постановкой).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SetLeverageOkxRequest {

    /** Инструмент. */
    private String instId;

    /** Плечо (целое, строкой). */
    private String lever;

    /** Режим маржи (isolated). */
    private String mgnMode;

    /** Сторона позиции (net). */
    private String posSide;
}
