package com.example.strategies.api.model.strategy;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Параметры стохастического осциллятора в API (indicatorType = STOCHASTIC).
 *
 * <p><b>Имена {@code kPeriod} и {@code dPeriod} на проводе объявлены ЯВНО, и
 * без этого объявление не принималось бы вовсе.</b> Капитализация аксессоров
 * в репозитории — {@code beanspec} (lombok.config в корне), и у поля, чей
 * второй знак заглавный, Lombok печатает {@code getkPeriod}/{@code setkPeriod};
 * сериализатор провода (Jackson 3) считает аксессором только префикс плюс
 * ЗАГЛАВНУЮ, поэтому свойство не опознаётся — поле остаётся пустым при любом
 * теле, и {@code @NotNull} отвергает даже полное объявление (находка
 * {@code F-11}). Аннотация — из {@code com.fasterxml.jackson.annotation},
 * общего пакета обеих линий Jackson. {@code smoothPeriod} под класс не
 * попадает: второй знак у него строчный.
 */
@Getter
@Setter
public class StochasticParamsApiModel extends IndicatorParamsApiModel {

    @NotNull
    @Positive
    @JsonProperty("kPeriod")
    @Schema(description = "Период линии %K, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer kPeriod;

    @NotNull
    @Positive
    @JsonProperty("dPeriod")
    @Schema(description = "Период линии %D, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer dPeriod;

    @NotNull
    @Positive
    @Schema(description = "Период дополнительного сглаживания, баров", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer smoothPeriod;
}
