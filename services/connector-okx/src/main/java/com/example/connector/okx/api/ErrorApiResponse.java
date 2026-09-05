package com.example.connector.okx.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Единый error-DTO поверхности коннектора
 * ({@code docs/rules/error-handling-policy.md}).
 *
 * <p><b>Поле {@code code} здесь несёт больше, чем пояснение.</b> Реакция
 * на контролируемый отказ площадки — перевод сущности в ошибку, холд
 * инструмента, холд биржи — живёт у ЯДРА
 * ({@code docs/rules/controlled-exchange-exceptions.md}), а не у
 * коннектора. Значит по границе обязан переезжать КЛАСС отказа, а не одно
 * лишь «не получилось»: свернув три разных отказа в общий статус, мы
 * лишили бы ядро возможности отличить их и выбрать реакцию.
 */
@Getter
@Builder
public class ErrorApiResponse {

    @Schema(description = "Класс отказа — устойчивый машиночитаемый идентификатор причины; по нему ядро выбирает реакцию")
    private final String code;

    @Schema(description = "Причина внутри класса, когда класс её различает: у EXTERNAL_STATUS — код проблемного статуса, из которого ядро выводит причину закрытия сущности. Пусто, если класс причин не различает")
    private final String reason;

    @Schema(description = "Пояснение для человека; секретов не несёт")
    private final String message;

    @Schema(description = "Момент отказа, UTC")
    private final OffsetDateTime occurredAt;
}
