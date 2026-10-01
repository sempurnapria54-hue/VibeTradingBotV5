package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Позиционный тир изолированной маржи наружу — строка справочных правил
 * инструмента (docs/models/domain/other/InstrumentExternalRules.md).
 */
@Getter
@Setter
public class PositionTierApiResponse {

    @Schema(description = "Нижняя граница размера позиции тира, в контрактах")
    private BigDecimal minSize;

    @Schema(description = "Верхняя граница размера позиции тира, в контрактах")
    private BigDecimal maxSize;

    @Schema(description = "Ставка поддерживающей маржи тира — доля нотинала позиции")
    private BigDecimal maintenanceMarginRate;
}
