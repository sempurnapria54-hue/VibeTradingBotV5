package com.example.tradingbot.rest.error;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "Стандартизированная ошибка API.")
public class ApiErrorResponse {

    @Schema(description = "Машиночитаемый код ошибки.", example = "strategy.validation.failed")
    private String code;

    @Schema(description = "Понятное описание причины ошибки.", example = "Strategy internalId is required")
    private String msg;
}
