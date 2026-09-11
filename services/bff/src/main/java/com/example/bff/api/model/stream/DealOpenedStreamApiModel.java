package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сделка создана — форма периметра на проводе к браузеру.
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param entryReason               причина заведения — имя значения
 *                                  перечня {@code Deal.EntryReason};
 *                                  область значений домовая
 *                                  (docs/models/domain/aggregate/Deal.md),
 *                                  и здесь она не переписывается
 * @param direction                 направление сделки — имя значения
 *                                  перечня
 *                                  {@code StrategyTradeDirection};
 *                                  область значений домовая
 *                                  (docs/models/domain/aggregate/Strategy.md),
 *                                  и здесь она не переписывается
 */
public record DealOpenedStreamApiModel(
        @Schema(description = "Идентичность сделки") String dealInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Причина заведения сделки") String entryReason,
        @Schema(description = "Направление сделки") String direction) {
}
