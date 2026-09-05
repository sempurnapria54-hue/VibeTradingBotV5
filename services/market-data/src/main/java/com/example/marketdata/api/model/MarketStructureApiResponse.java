package com.example.marketdata.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Готовая структура рынка наружу. */
@Getter
@Setter
public class MarketStructureApiResponse {

    @Schema(description = "Идентичность вычисления, из которой получен результат")
    private String marketStructureConfigInternalId;

    @Schema(description = "Инструмент, по которому посчитана структура")
    private String instrumentInternalId;

    @Schema(description = "Тип структуры рынка")
    private String type;

    @Schema(description = "Начало окна свечей расчёта")
    private OffsetDateTime windowStartAt;

    @Schema(description = "Конец окна свечей расчёта; точка отсчёта свежести")
    private OffsetDateTime windowEndAt;

    @Schema(description = "Свеча, на которой структура подтверждена")
    private OffsetDateTime confirmedAt;

    @Schema(description = "Ценовые уровни структуры")
    private List<MarketPriceLevelApiResponse> levels;

    @Schema(description = "Тип уровня, сломанного подтверждённым пробоем; пусто — пробоя нет")
    private String breakoutBrokenLevelType;

    @Schema(description = "Направление подтверждённого пробоя; пусто — пробоя нет")
    private String breakoutDirection;

    @Schema(description = "Цена сломанного уровня")
    private BigDecimal breakoutLevelPrice;

    @Schema(description = "Свеча подтверждения пробоя")
    private OffsetDateTime breakoutConfirmedAt;
}
