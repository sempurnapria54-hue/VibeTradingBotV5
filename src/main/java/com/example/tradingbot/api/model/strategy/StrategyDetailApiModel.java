package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Деталь стратегии (API): торговые правила одной фазы рынка. Шаги
 * сгруппированы по статусу сделки (ключ — имя Deal.Status).
 */
@Getter
@Setter
public class StrategyDetailApiModel {

    @NotBlank
    @Schema(description = "Фаза рынка детали: BULL_TREND/BEAR_TREND/RANGE/UNKNOWN",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String marketPhaseType;

    @NotBlank
    @Schema(description = "Политика торговли: FOLLOW_PHASE/CONTRARIAN/GRID/NO_TRADE (матрица по фазе)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String phaseEntryPolicy;

    @Positive
    @DecimalMax("100")
    @Schema(description = "Риск на сделку, % от капитала (0; 100]; торгово-суждённые пороги — семантика activate")
    private BigDecimal riskPerTradePercent;

    @Positive
    @Schema(description = "High-level ориентир risk/reward")
    private BigDecimal targetRiskRewardRatio;

    @Valid
    @Schema(description = "Шаги по статусу сделки; ключ — имя Deal.Status (PRECHECK, MANAGING, ...). "
            + "Индикаторы/структуры детали объявлены на стратегии (strategy-scope), адресуются по key")
    private Map<String, List<StrategyStepApiModel>> stepsByStatus;
}
