package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ступень блокировки снята — форма периметра на проводе к браузеру.
 *
 * @param exchangeAccountInternalId биржевой счёт радиуса
 * @param instrumentInternalId      инструмент радиуса, если он назван
 * @param scope                     радиус снятия — имя значения перечня
 *                                  {@code HoldScope}; область значений
 *                                  домовая
 *                                  (docs/components/models/HoldSignal.md),
 *                                  и здесь она не переписывается
 * @param rung                      снятая ступень — имя значения перечня
 *                                  ступеней реакции; область значений
 *                                  домовая
 *                                  (docs/components/models/HoldSignal.md
 *                                  §«Енум HoldRung»), и здесь она не
 *                                  переписывается
 */
public record HoldReleasedStreamApiModel(
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента, если радиус его называет") String instrumentInternalId,
        @Schema(description = "Радиус снятия") String scope,
        @Schema(description = "Снятая ступень") String rung) {
}
