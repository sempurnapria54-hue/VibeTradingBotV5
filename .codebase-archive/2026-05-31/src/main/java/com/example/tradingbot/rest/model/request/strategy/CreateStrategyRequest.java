package com.example.tradingbot.rest.model.request.strategy;

import com.example.tradingbot.rest.model.strategy.StrategyDetailsModel;
import com.example.tradingbot.rest.model.strategy.StrategyOpenApiExamples;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(
        description = "Полный объект стратегии на создание. internalId и статус генерируются сервисом.",
        example = StrategyOpenApiExamples.CREATE_REQUEST
)
public class CreateStrategyRequest {

    @NotNull(message = "Strategy instrumentId is required")
    @Schema(description = "Идентификатор инструмента, для которого создаётся стратегия.", example = "101")
    private Long instrumentId;

    @NotBlank(message = "Strategy name is required")
    @Schema(description = "Человекочитаемое имя стратегии.", example = "ETH SWAP Trend/Grid v3")
    private String name;

    @NotNull(message = "Strategy version is required")
    @Schema(description = "Append-only версия стратегии. Для изменения создаётся новая стратегия новой версии.", example = "3")
    private Integer version;

    @Valid
    @NotEmpty(message = "Strategy details must not be empty")
    @Schema(description = "Набор деталей стратегии по рыночным фазам.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyDetailsModel> details;
}
