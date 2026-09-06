package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Решение о заявке — форма периметра на проводе к браузеру.
 *
 * @param orderInternalId           идентичность заявки
 * @param dealInternalId            сделка, к которой заявка относится
 * @param exchangeAccountInternalId биржевой счёт решения
 * @param instrumentInternalId      инструмент решения
 * @param orderType                 тип заявки площадки
 * @param direction                 сторона заявки
 * @param plannedSizeContracts      запланированный объём в контрактах
 * @param plannedEntryPrice         запланированная цена размещения
 */
public record OrderDecidedStreamApiModel(
        @Schema(description = "Идентичность заявки") String orderInternalId,
        @Schema(description = "Идентичность сделки, к которой относится заявка") String dealInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Тип заявки площадки") String orderType,
        @Schema(description = "Сторона заявки") String direction,
        @Schema(description = "Запланированный объём в контрактах") String plannedSizeContracts,
        @Schema(description = "Запланированная цена размещения") String plannedEntryPrice) {
}
