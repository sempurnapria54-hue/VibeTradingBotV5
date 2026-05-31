package com.example.tradingbot.rest.model.response.strategy;

import com.example.tradingbot.rest.model.strategy.StrategyDetailsModel;
import com.example.tradingbot.rest.model.strategy.StrategyOpenApiExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Полное представление стратегии в API.", example = StrategyOpenApiExamples.FULL_RESPONSE)
public class StrategyResponse {

    @Schema(description = "Внешний идентификатор стратегии.", example = "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7")
    private String internalId;

    @Schema(description = "Идентификатор инструмента стратегии.", example = "101")
    private Long instrumentId;

    @Schema(description = "Человекочитаемое имя стратегии.", example = "ETH SWAP Trend/Grid v3")
    private String name;

    @Schema(description = "Append-only версия стратегии.", example = "3")
    private Integer version;

    @Schema(description = "Текущий статус стратегии.", example = "ACTIVE")
    private String status;

    @Schema(description = "Набор деталей стратегии по фазам рынка.")
    private List<StrategyDetailsModel> details;
}
