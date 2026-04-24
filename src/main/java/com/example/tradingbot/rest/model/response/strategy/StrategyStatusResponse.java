package com.example.tradingbot.rest.model.response.strategy;

import com.example.tradingbot.rest.model.strategy.StrategyOpenApiExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Короткий ответ с внешним идентификатором стратегии и её текущим статусом.", example = StrategyOpenApiExamples.STATUS_RESPONSE)
public class StrategyStatusResponse {

    @Schema(description = "Внешний идентификатор стратегии.", example = "4f5d2c90-1b8c-4d63-9cfd-7d0ce4f6a9b7")
    private String internalId;

    @Schema(description = "Текущий статус стратегии.", example = "ACTIVE")
    private String status;
}
