package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Транш сделки, каким его видит читатель поверхности.
 *
 * <p>Экспозиция здесь — <b>производная</b> величина модели, а не колонка:
 * читатель приходит за состоянием, а пересчитывать её на своей стороне
 * значило бы завести второй носитель формулы покрытия.
 */
@Getter
@Setter
public class DealTrancheApiResponse {

    @Schema(description = "Идентичность транша")
    private String internalId;

    @Schema(description = "Статус транша")
    private String status;

    @Schema(description = "Уровень шаблона сетки; пусто — объявление нешаблонное")
    private Integer level;

    @Schema(description = "Номер эпизода объекта шага: переоткрытие ведётся тем же траншем")
    private Integer episodeSeq;

    @Schema(description = "Тип входного шага объявления; пусто — объявление входа не несёт")
    private String entryStepType;

    @Schema(description = "Причина закрытия транша; пусто — транш не закрыт")
    private String closeReason;

    @Schema(description = "Экспозиция транша в контрактах — производная его заявок и защит")
    private BigDecimal exposure;
}
