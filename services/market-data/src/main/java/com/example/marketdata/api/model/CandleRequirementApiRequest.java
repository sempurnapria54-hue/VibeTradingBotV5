package com.example.marketdata.api.model;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Требование свечей: инструмент, таймфрейм и глубина истории.
 *
 * <p>Команда называет ровно три вещи, и из них выводится единица сбора и
 * горизонт бэкфилла (docs/architecture/market-data-collection.md §«Как
 * потребность доходит до сбора»). Повтор безопасен: то же на то же — та
 * же единица, глубже прежнего — расширение горизонта.
 */
@Getter
@Setter
public class CandleRequirementApiRequest {

    @NotBlank
    @Schema(description = "Межсервисный идентификатор инструмента")
    private String instrumentInternalId;

    @NotNull
    @Schema(description = "Таймфрейм серии")
    private TimeFrame timeframe;

    @Positive
    @Schema(description = "Сколько баров истории нужно; пусто — вся доступная история площадки")
    private Long depthBars;
}
