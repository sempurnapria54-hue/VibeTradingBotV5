package com.example.connector.okx.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** Окно последних свечей инструмента. */
@Getter
@Setter
public class CandleWindowApiQuery {

    @Schema(description = "Идентификатор инструмента на площадке")
    private String externalInstrumentId;

    @Schema(description = "Таймфрейм в словаре площадки: 1m, 5m, 1H, 1Dutc")
    private String externalBar;

    @Schema(description = "Сколько последних свечей вернуть; окно ограничено — безлимитного чтения нет")
    private Integer limit;
}
