package com.example.tradingbot.api.model.request;

import com.example.tradingbot.api.model.strategy.StrategyDetailApiModel;
import com.example.tradingbot.api.model.strategy.StrategyIndicatorSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketPhaseSettingApiModel;
import com.example.tradingbot.api.model.strategy.StrategyMarketStructureSettingApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на создание стратегии (полное immutable-дерево). Статус
 * (CREATED) проставляется системой; инструмент задаётся межсервисным
 * идентификатором. Create-валидация — структурно-ссылочная (400);
 * торгово-суждённые диапазоны — семантика activate
 * (docs/decisions/strategy-materialization-and-validation.md).
 */
@Getter
@Setter
public class CreateStrategyApiRequest {

    @NotBlank
    @Schema(description = "Межсервисный идентификатор стратегии", requiredMode = Schema.RequiredMode.REQUIRED)
    private String internalId;

    @NotBlank
    @Schema(description = "Межсервисный идентификатор инструмента стратегии",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String instrumentInternalId;

    @NotBlank
    @Schema(description = "Человекочитаемое имя стратегии", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Valid
    @NotNull
    @Schema(description = "Настройка расчёта фазы рынка (одна на стратегию)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private StrategyMarketPhaseSettingApiModel marketPhaseSetting;

    @Valid
    @NotEmpty
    @Schema(description = "Детали по фазам рынка: ровно одна на каждый MarketPhase.Type",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyDetailApiModel> details;

    @Valid
    @Schema(description = "Настройки индикаторов стратегии (strategy-scope, объявлены раз; "
            + "фаза/детали/действия ссылаются по key)")
    private List<StrategyIndicatorSettingApiModel> indicatorSettings;

    @Valid
    @Schema(description = "Настройки структуры рынка стратегии (strategy-scope, адресуются по key)")
    private List<StrategyMarketStructureSettingApiModel> marketStructureSettings;
}
