package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Фаза рынка наружу.
 *
 * <p>Своей метки времени у фазы нет: она не персистируется и вычисляется
 * на момент запроса, а свежесть наследует от входов
 * (docs/components/MarketPhaseService.md).
 */
@Getter
@Setter
public class MarketPhaseApiResponse {

    @Schema(description = "Инструмент, для которого классифицирована фаза")
    private String instrumentInternalId;

    @Schema(description = "Тип фазы; UNKNOWN — вход недоступен либо ни одна клауза не истинна")
    private String type;
}
