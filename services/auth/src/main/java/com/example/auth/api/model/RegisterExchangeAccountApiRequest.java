package com.example.auth.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Запрос регистрации биржевого счёта тенанта. */
@Getter
@Setter
public class RegisterExchangeAccountApiRequest {

    @NotBlank
    @Schema(description = "Идентичность тенанта-владельца счёта")
    private String tenantInternalId;

    @NotBlank
    @Schema(description = "Код площадки: OKX, BYBIT")
    private String exchangeCode;

    @NotBlank
    @Schema(description = "Метка счёта, видимая человеку: у тенанта на одной площадке счетов может быть несколько")
    private String label;

    @NotBlank
    @Schema(description = "Контур площадки: LIVE — боевая, DEMO — демо-контур. Допустимость проверяет окружение")
    private String contour;
}
