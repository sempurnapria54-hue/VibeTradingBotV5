package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ступень блокировки поднята — форма периметра на проводе к браузеру.
 *
 * @param exchangeAccountInternalId биржевой счёт радиуса
 * @param instrumentInternalId      инструмент радиуса, если он назван
 * @param scope                     радиус блокировки — имя значения
 *                                  перечня {@code HoldScope}; область
 *                                  значений домовая
 *                                  (docs/components/models/HoldSignal.md),
 *                                  и здесь она не переписывается
 * @param rung                      поднятая ступень — имя значения перечня
 *                                  ступеней реакции; область значений
 *                                  домовая
 *                                  (docs/components/models/HoldSignal.md
 *                                  §«Енум HoldRung»), и здесь она не
 *                                  переписывается
 * @param code                      машинный код причины
 */
public record HoldRaisedStreamApiModel(
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента, если радиус его называет") String instrumentInternalId,
        @Schema(description = "Радиус блокировки") String scope,
        @Schema(description = "Поднятая ступень") String rung,
        @Schema(description = "Машинный код причины") String code) {
}
