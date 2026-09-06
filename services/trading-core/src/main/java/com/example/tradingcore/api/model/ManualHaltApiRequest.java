package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Вызов ручной остановки: класс вмешательства и объект радиуса
 * (docs/rules/manual-halt.md §«Параметры вызова — существующие поля
 * сигнала»).
 *
 * <p><b>Радиус отдельным полем не едет:</b> его задаёт присутствие
 * инструмента. Второе поле позволило бы прислать пару «радиус счёта плюс
 * инструмент», у которой нет ни одного осмысленного прочтения.
 *
 * <p>Идентичности — {@code internalId}: числовой ключ границу сервиса не
 * пересекает (.claude/rules/codestyle.md §«Идентичность наружу»).
 *
 * <p><b>Класс вмешательства едет строкой, а не перечнем:</b> перечни
 * объявляются только в доменном слое, и на границе api они строки
 * (.claude/rules/codestyle.md §«Слои моделей и enum'ы»). Побочный
 * выигрыш — единая форма отказа: неизвестное значение доходит до резолва
 * и уходит в общий {@code 400} нашим error-DTO, а не в разбор тела
 * мимо {@code @RestControllerAdvice}.
 */
@Getter
@Setter
public class ManualHaltApiRequest {

    @NotBlank
    @Schema(description = "Класс вмешательства: SOFT — мягкий по паре «счёт, инструмент»,"
            + " FREEZE — мягкий по счёту, FULL — жёсткий на обоих радиусах")
    private String haltClass;

    @NotBlank
    @Schema(description = "Идентичность биржевого счёта — объект счётного радиуса и часть ключа пары")
    private String exchangeAccountInternalId;

    @Schema(description = "Идентичность инструмента; присутствие поля и задаёт радиус пары."
            + " Пусто — радиус всего счёта")
    private String instrumentInternalId;
}
