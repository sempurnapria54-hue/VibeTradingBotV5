package com.example.strategies.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Действие над ordinary order (API, actionKind = ORDER). */
@Getter
@Setter
public class StrategyOrderActionApiModel extends StrategyActionApiModel {

    @NotBlank
    @Schema(description = "Бизнес-тип ордера: ENTRY/ENTRY_ATTACHED_STOP_LOSS",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderType;

    @NotBlank
    @Schema(description = "Торговое направление: LONG/SHORT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String direction;

    /**
     * Доля расчётного объёма входа.
     *
     * <p>Диапазон {@code (0; 100]} держит не аннотация, а
     * {@code StrategyDefinitionValidator} кодом
     * {@code STRATEGY_ACTION_ALLOCATION_NOT_POSITIVE}: реджект создания
     * объявлен доком (docs/rules/strategy-validation.md), а Bean Validation
     * отвечает до тела обработчика — с аннотацией именованный код был бы
     * недостижим, и потребитель ветвился бы на сообщении вместо кода.
     */
    @Schema(description = "Доля расчётного объёма, % (0; 100]")
    private BigDecimal allocationPercents;

    @Schema(description = "Намерение reduce-only: ордер только уменьшает позицию")
    private Boolean positionReducingOnly;

    @Valid
    @Schema(description = "Правило расчёта цены размещения; пусто — market-like вход")
    private StrategyPricePlacementApiModel placement;

    @Valid
    @Schema(description = "Attached-защита (обязательна для ENTRY_ATTACHED_STOP_LOSS)")
    private StrategyAttachedProtectionSettingsApiModel attachedProtection;
}
