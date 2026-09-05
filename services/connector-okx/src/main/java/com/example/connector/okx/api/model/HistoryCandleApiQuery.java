package com.example.connector.okx.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Окно исторических свечей инструмента. */
@Getter
@Setter
public class HistoryCandleApiQuery {

    @Schema(description = "Идентификатор инструмента на площадке")
    private String externalInstrumentId;

    @Schema(description = "Таймфрейм в словаре площадки: 1m, 5m, 1H, 1Dutc")
    private String externalBar;

    @Schema(description = "Верхняя граница окна, миллисекунды эпохи: свечи берутся назад от неё")
    private Long afterMillis;

    @Schema(description = "Предел числа свечей; безлимитного чтения истории нет")
    private Integer limit;
}
