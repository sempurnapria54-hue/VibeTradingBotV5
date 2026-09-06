package com.example.bff.api.model.stream;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Определение активировано — форма периметра на проводе к браузеру.
 *
 * <p><b>Дерева определения здесь нет намеренно.</b> Содержимое события
 * несёт снимок дерева целиком — он нужен ядру, которое дочитать его
 * синхронно не может по построению. Браузер может: определение лежит за
 * проксируемой поверхностью владельца. Отдать дерево в поток значило бы
 * сделать доменную модель внешним контрактом
 * (docs/architecture/contracts.md §«Форма на проводе к браузеру — своя,
 * а не доменный класс»).
 *
 * @param strategyInternalId        идентичность определения
 * @param exchangeAccountInternalId биржевой счёт определения
 * @param instrumentInternalId      инструмент определения
 * @param name                      имя определения, видимое человеку
 */
public record StrategyActivatedStreamApiModel(
        @Schema(description = "Идентичность определения стратегии") String strategyInternalId,
        @Schema(description = "Идентичность биржевого счёта") String exchangeAccountInternalId,
        @Schema(description = "Идентичность инструмента") String instrumentInternalId,
        @Schema(description = "Имя определения, видимое человеку") String name) {
}
