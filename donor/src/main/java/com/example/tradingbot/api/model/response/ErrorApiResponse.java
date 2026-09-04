package com.example.tradingbot.api.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Единый error-DTO внешней поверхности приложения: все ошибки контроллеров
 * маппятся в него глобальным обработчиком (docs/rules/error-handling-policy.md).
 * FSM/оркестрация наружу не торчат — контракт тонкий.
 */
@Value
@Builder
public class ErrorApiResponse {

    @Schema(description = "Момент возникновения ошибки (UTC).")
    OffsetDateTime timestamp;

    @Schema(description = "HTTP-статус код ответа.")
    Integer status;

    @Schema(description = "Краткое имя ошибки (reason phrase статуса).")
    String error;

    @Schema(description = "Человекочитаемое сообщение об ошибке.")
    String message;

    @Schema(description = "Путь запроса, на котором возникла ошибка.")
    String path;
}
