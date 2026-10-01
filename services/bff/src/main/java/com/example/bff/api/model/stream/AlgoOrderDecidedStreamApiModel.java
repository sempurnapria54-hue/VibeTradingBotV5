package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Решение об отдельной условной заявке — форма периметра на проводе к
 * браузеру.
 *
 * @param algoOrderInternalId       идентичность условной заявки
 * @param dealInternalId            сделка, к которой заявка относится
 * @param exchangeAccountInternalId биржевой счёт решения
 * @param instrumentInternalId      инструмент решения
 * @param replacesInternalId        предшественник в цепочке замещений;
 *                                  пусто — первичная постановка
 * @param conditionType             тип условия — имя значения перечня
 *                                  {@code AlgoOrder.ConditionType};
 *                                  область значений домовая
 *                                  (docs/models/domain/core/AlgoOrder.md),
 *                                  и здесь она не переписывается
 * @param direction                 сторона — имя значения перечня
 *                                  {@code AlgoOrder.Direction}; область
 *                                  значений домовая (там же)
 * @param sizeContracts             размер в контрактах
 * @param stopLossTriggerPrice      уровень триггера остановки убытка;
 *                                  пусто — ноги нет
 * @param takeProfitTriggerPrice    уровень триггера фиксации прибыли;
 *                                  пусто — ноги нет
 */
public record AlgoOrderDecidedStreamApiModel(
        @Schema(description = "Идентичность условной заявки") String algoOrderInternalId,
        @Schema(description = "Идентичность сделки, к которой относится заявка") String dealInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Идентичность предшественника в цепочке замещений; пусто — первичная постановка")
        String replacesInternalId,
        @Schema(description = "Тип условия") String conditionType,
        @Schema(description = "Сторона условной заявки") String direction,
        @Schema(description = "Размер в контрактах") String sizeContracts,
        @Schema(description = "Уровень триггера остановки убытка") String stopLossTriggerPrice,
        @Schema(description = "Уровень триггера фиксации прибыли") String takeProfitTriggerPrice) {
}
