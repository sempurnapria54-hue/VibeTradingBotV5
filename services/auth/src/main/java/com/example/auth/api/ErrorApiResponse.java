package com.example.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/** Единый error-DTO внешней поверхности (docs/rules/error-handling-policy.md). */
@Getter
@Builder
public class ErrorApiResponse {

    @Schema(description = "Код ошибки — устойчивый машиночитаемый идентификатор причины")
    private final String code;

    @Schema(description = "Пояснение для человека; секретов не несёт")
    private final String message;

    @Schema(description = "Момент отказа, UTC")
    private final OffsetDateTime occurredAt;
}
