package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Определение деактивировано либо удалено — форма периметра на проводе к
 * браузеру.
 *
 * @param strategyInternalId        идентичность определения
 * @param exchangeAccountInternalId биржевой счёт определения
 * @param instrumentInternalId      инструмент определения
 */
public record StrategyLifecycleStreamApiModel(
        @Schema(description = "Идентичность определения стратегии") String strategyInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId) {
}
