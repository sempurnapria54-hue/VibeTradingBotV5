package com.example.auth.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** Биржевой счёт тенанта в ответе API. */
@Getter
@Builder
public class ExchangeAccountApiResponse {

    @Schema(description = "Идентичность счёта; из неё выводится путь ключей в Vault")
    private final String internalId;

    @Schema(description = "Идентичность тенанта-владельца")
    private final String tenantInternalId;

    @Schema(description = "Код площадки")
    private final String exchangeCode;

    @Schema(description = "Метка счёта, видимая человеку")
    private final String label;

    @Schema(description = "Контур площадки: LIVE либо DEMO. Показывается везде, где показывается счёт")
    private final String contour;

    @Schema(description = "Реестровое состояние счёта: ACTIVE либо CLOSED. Ступени безопасности счёта ведёт trading-core, и здесь их нет")
    private final String status;
}
