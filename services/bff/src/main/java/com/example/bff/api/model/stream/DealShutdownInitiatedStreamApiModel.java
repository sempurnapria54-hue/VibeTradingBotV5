package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Сделка перестала вестись штатно — форма периметра на проводе к браузеру.
 *
 * <p><b>Клиент дедуплицирует по идентичности события, а не по сделке:</b> у
 * одной сделки таких событий бывает два, и второе описывает другое
 * происшествие (docs/architecture/contracts.md §«У одной сделки бывает
 * БОЛЬШЕ ОДНОГО `DealShutdownInitiated`, и это свойство класса, а не
 * дефект»).
 *
 * @param dealInternalId            идентичность сделки
 * @param exchangeAccountInternalId биржевой счёт сделки
 * @param instrumentInternalId      инструмент сделки
 * @param status                    состояние, в которое сделка ушла —
 *                                  имя значения перечня
 *                                  {@code Deal.Status}; область значений
 *                                  домовая
 *                                  (docs/models/domain/aggregate/Deal.md),
 *                                  и здесь она не переписывается
 * @param shutdownReason            причина выхода из штатного ведения —
 *                                  имя значения перечня
 *                                  {@code Deal.ShutdownReason}; область
 *                                  значений домовая (там же), и здесь
 *                                  она не переписывается
 */
public record DealShutdownInitiatedStreamApiModel(
        @Schema(description = "Идентичность сделки") String dealInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Состояние, в которое сделка ушла") String status,
        @Schema(description = "Причина выхода из штатного ведения") String shutdownReason) {
}
