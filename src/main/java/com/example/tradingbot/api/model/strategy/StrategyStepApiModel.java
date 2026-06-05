package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Шаг стратегии (API): условие + пакет действий. */
@Getter
@Setter
public class StrategyStepApiModel {

    @NotBlank
    @Schema(description = "Тип шага: ENTRY/MAIN_PROTECTION/PROTECTION_ADJUSTMENT/PARTIAL_EXIT/"
            + "GRID_ENTRY/GRID_MANAGEMENT/EXIT/FAIL_SAFE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String stepType;

    @Valid
    @NotNull
    @Schema(description = "Общее условие применимости пакета действий", requiredMode = Schema.RequiredMode.REQUIRED)
    private StrategyConditionApiModel condition;

    @Valid
    @NotEmpty
    @Schema(description = "Пакет действий; выполняется целиком при истинном условии",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<StrategyActionApiModel> actions;

    @Valid
    @NotNull
    @Schema(description = "Политика на устаревание данных шага (обязательна для каждого шага)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private StrategyMarketDataExpiredSettingApiModel marketDataExpiredSetting;
}
