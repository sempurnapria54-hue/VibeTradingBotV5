package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Параметры проверки ссылок определения стратегии.
 *
 * <p>Объект параметров, а не три {@code @RequestParam}: их больше двух
 * (.claude/rules/codestyle.md §«Контроллеры / API»).
 */
@Getter
@Setter
public class PairCheckApiRequest {

    @NotBlank
    @Schema(description = "Идентичность тенанта, в контексте которого идёт проверка",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantInternalId;

    @NotBlank
    @Schema(description = "Идентичность биржевого счёта", requiredMode = Schema.RequiredMode.REQUIRED)
    private String exchangeAccountInternalId;

    @NotBlank
    @Schema(description = "Идентичность инструмента", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instrumentInternalId;
}
