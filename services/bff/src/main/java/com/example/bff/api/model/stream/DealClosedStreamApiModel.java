package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сделка закрыта — форма периметра на проводе к браузеру.
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param status                    терминальное состояние сделки
 * @param closeReason               причина закрытия
 * @param result                    финансовый исход сделки
 * @param resultCurrency            валюта исхода
 */
public record DealClosedStreamApiModel(
        @Schema(description = "Идентичность сделки") String dealInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Терминальное состояние сделки") String status,
        @Schema(description = "Причина закрытия") String closeReason,
        @Schema(description = "Финансовый исход сделки") String result,
        @Schema(description = "Валюта исхода") String resultCurrency) {
}
