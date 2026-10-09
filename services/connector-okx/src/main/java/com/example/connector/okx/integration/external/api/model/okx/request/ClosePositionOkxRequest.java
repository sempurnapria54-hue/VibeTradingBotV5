package com.example.connector.okx.integration.external.api.model.okx.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

/**
 * Тело OKX close-position (POST /trade/close-position). posSide —
 * adapter-константа; mgnMode — режим маржи аргумента операции, пусто —
 * adapter-константа isolated; ccy — settle currency; autoCxl снимает стоящие
 * заявки на закрытие (reduce-only), которые иначе отвергли бы закрытие, —
 * входные заявки он не снимает. null-поля не сериализуются. См.
 * docs/models/mapping/Position.md, docs/integrations/okx/contracts/position.md.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClosePositionOkxRequest {

    /** Инструмент. */
    private String instId;

    /**
     * Режим маржи закрываемой записи: режим аргумента операции; пусто —
     * adapter-константа isolated (режим контура).
     */
    private String mgnMode;

    /** Сторона позиции (adapter-константа net). */
    private String posSide;

    /** Валюта расчётов (settle currency); необязательна — пустая в тело не уходит. */
    private String ccy;

    /**
     * Снять стоящие заявки на закрытие (reduce-only) при закрытии позиции
     * рыночной заявкой; входные заявки флаг не снимает.
     */
    private Boolean autoCxl;
}
