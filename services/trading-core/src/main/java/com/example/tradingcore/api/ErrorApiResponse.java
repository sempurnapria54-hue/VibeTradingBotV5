package com.example.tradingcore.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Единый error-DTO поверхности торгового ядра
 * (docs/rules/error-handling-policy.md §«Внешняя поверхность»).
 */
@Getter
@Builder
public class ErrorApiResponse {

    @Schema(description = "Класс отказа — устойчивый машиночитаемый идентификатор причины")
    private final String code;

    @Schema(description = "Пояснение для человека; секретов не несёт")
    private final String message;

    @Schema(description = "Момент отказа, UTC")
    private final OffsetDateTime occurredAt;
}
