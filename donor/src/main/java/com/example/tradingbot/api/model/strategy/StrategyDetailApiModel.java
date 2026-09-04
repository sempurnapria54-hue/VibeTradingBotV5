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
 * Деталь стратегии (API): торговые правила одной фазы рынка. Уровней
 * объявления два: поведение входа несут транши, на самой детали живёт
 * только узкая агрегатная поверхность — шаги уровня сделки,
 * сгруппированные статусом агрегата (ключ — имя Deal.Status).
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
    @Schema(description = "Поактный потолок риска: сколько берёт ОДНО действие, % базы риска (0; 100]")
    private BigDecimal riskPerActionPercent;

    @Positive
    @Schema(description = "Множитель кумулятивного потолка: во сколько раз сделка за жизнь вправе "
            + "превысить поактный потолок")
    private BigDecimal cumulativeRiskPerDealMultiplier;

    @Positive
    @DecimalMax("100")
    @Schema(description = "Максимум ОДНОВРЕМЕННОГО риска сделки, % базы риска; вкладывается в "
            + "конфигурационный максимум риск-аппетита")
    private BigDecimal strategySimultaneousRiskPerDealPercent;

    @Positive
    @Schema(description = "Множитель катастрофического потолка сделки; сверяется с конфигурационным "
            + "пределом на создании")
    private BigDecimal strategyCatastrophicRiskPerDealMultiplier;

    @Positive
    @Schema(description = "High-level ориентир risk/reward")
    private BigDecimal targetRiskRewardRatio;

    @Valid
    @Schema(description = "Объявленные транши: что заводится и как ведётся каждый вход. "
            + "Торгуемая деталь обязана объявить хотя бы один")
    private List<@Valid StrategyTrancheApiModel> tranches;

    @Valid
    @Schema(description = "Шаги уровня СДЕЛКИ по статусу агрегата; ключ — имя Deal.Status (ACTIVE, ...). "
            + "Поверхность узкая: только EXIT и FAIL_SAFE — всё остальное объявляется на транше. "
            + "Индикаторы/структуры детали объявлены на стратегии (strategy-scope), адресуются по key")
    private Map<String, List<@Valid StrategyStepApiModel>> stepsByStatus;
}
