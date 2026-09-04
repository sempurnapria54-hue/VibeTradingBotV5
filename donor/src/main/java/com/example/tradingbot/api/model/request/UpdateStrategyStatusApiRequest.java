package com.example.tradingbot.api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос смены административного статуса стратегии: activate /
 * inactivate / delete (логическое удаление, не физическое).
 * Допустимые переходы — docs/lifecycles/Strategy.md.
 */
@Getter
@Setter
public class UpdateStrategyStatusApiRequest {

    @NotBlank
    @Schema(description = "Целевой статус: ACTIVE/INACTIVE/DELETED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
}
