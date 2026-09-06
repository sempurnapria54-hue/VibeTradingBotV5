package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сделка создана — форма периметра на проводе к браузеру.
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param entryReason               причина заведения
 * @param direction                 направление сделки
 */
public record DealOpenedStreamApiModel(
        @Schema(description = "Идентичность сделки") String dealInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Причина заведения сделки") String entryReason,
        @Schema(description = "Направление сделки") String direction) {
}
