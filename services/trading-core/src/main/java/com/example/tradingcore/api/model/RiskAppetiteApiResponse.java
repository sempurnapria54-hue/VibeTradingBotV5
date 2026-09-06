package com.example.tradingcore.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Числа риск-аппетита тенанта, какими их держит ядро.
 *
 * <p>Пустое поле означает «число не назначено» и читается как отказ
 * risk-creating действия (docs/rules/absent-value-semantics.md).
 */
@Getter
@Setter
public class RiskAppetiteApiResponse {

    @Schema(description = "Идентичность тенанта-владельца")
    private String tenantInternalId;

    @Schema(description = "Потолок одновременного риска на сделку, процент базы риска")
    private BigDecimal globalSimultaneousRiskPerDealPercent;

    @Schema(description = "Множитель катастрофического потолка над потолком одновременного риска")
    private BigDecimal globalCatastrophicRiskPerDealMultiplier;

    @Schema(description = "Предел подряд идущих убыточных сделок")
    private Integer globalConsecutiveLossLimit;
}
