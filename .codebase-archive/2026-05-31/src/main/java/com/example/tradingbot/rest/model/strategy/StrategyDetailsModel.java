package com.example.tradingbot.rest.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Schema(description = "Стратегические правила для конкретной фазы рынка.")
public class StrategyDetailsModel {

    @NotBlank(message = "Strategy detail marketPhaseType is required")
    @Schema(description = "Фаза рынка, для которой действует detail.", example = "BULL_TREND")
    private String marketPhaseType;

    @NotBlank(message = "Strategy detail phaseEntryPolicy is required")
    @Schema(description = "Политика входа для выбранной рыночной фазы.", example = "FOLLOW_PHASE")
    private String phaseEntryPolicy;

    @NotNull(message = "Strategy detail riskPerTradePercent is required")
    @Schema(description = "Риск на сделку в процентах от доступного капитала. По проектному инварианту не более 1.0.", example = "1.0")
    private BigDecimal riskPerTradePercent;

    @NotNull(message = "Strategy detail maxLeverage is required")
    @Schema(description = "Максимально допустимое плечо. По проектному инварианту не выше 10.", example = "10")
    private Integer maxLeverage;

    @NotNull(message = "Strategy detail targetRiskRewardRatio is required")
    @Schema(description = "High-level ориентир reward/risk.", example = "3.0")
    private BigDecimal targetRiskRewardRatio;

    @Valid
    @Schema(
            description = "Шаги стратегии, сгруппированные по `Deal.Status`. В JSON ключами выступают строковые значения доменного enum.",
            example = "{\"PRECHECK\":[{\"stepType\":\"ENTRY\",\"condition\":{\"rules\":[{\"level\":1,\"ruleType\":\"NO_OPEN_POSITION\"}]},\"actions\":[]}],\"MANAGING\":[{\"stepType\":\"EXIT\",\"condition\":{\"rules\":[{\"level\":1,\"ruleType\":\"TREND_CHANGED\"}]},\"actions\":[]}]}"
    )
    private Map<String, List<StrategyStepModel>> stepsByStatus;
}
