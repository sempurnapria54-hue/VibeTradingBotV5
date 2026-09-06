package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отчёт о происшествии заведён — форма периметра на проводе к браузеру.
 *
 * @param anomalyReportInternalId   идентичность отчёта
 * @param exchangeAccountInternalId биржевой счёт радиуса
 * @param instrumentInternalId      инструмент радиуса, если он назван
 * @param scope                     радиус происшествия
 * @param severity                  тяжесть
 * @param code                      машинный код класса
 */
public record AnomalyReportedStreamApiModel(
        @Schema(description = "Идентичность отчёта о происшествии") String anomalyReportInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента, если радиус его называет") String instrumentInternalId,
        @Schema(description = "Радиус происшествия") String scope,
        @Schema(description = "Тяжесть происшествия") String severity,
        @Schema(description = "Машинный код класса происшествия") String code) {
}
