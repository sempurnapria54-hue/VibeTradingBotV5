package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Объявление транша (API): что заводится и как ведётся один вход. Шаги
 * сгруппированы по статусу ТРАНША (ключ — имя DealTranche.Status).
 * Однотипные транши объявляются шаблоном: {@code levelCount = N} даёт N
 * экземпляров, различающихся уровнем.
 */
@Getter
@Setter
public class StrategyTrancheApiModel {

    @NotBlank
    @Schema(description = "Ключ адресации объявления внутри детали; уникален в её пределах",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @Positive
    @Schema(description = "Сколько экземпляров материализовать: 1 — один транш, больше — сетка. "
            + "Умолчания нет: пустое место мажорировалось бы единицей, то есть в разрешающую сторону",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer levelCount;

    @Positive
    @Schema(description = "Смещение размещения цены входа на один уровень; обязателен при levelCount > 1, "
            + "запрещён иначе")
    private BigDecimal levelStep;

    @Schema(description = "Допустимо ли переоткрытие эпизода этого транша; пусто читается как «не допускает»")
    private Boolean positionReopenAllowed;

    @Valid
    @Schema(description = "Шаги по статусу транша; ключ — имя DealTranche.Status "
            + "(PRECHECK, ENTRY_FINALIZED, MANAGING, ...)")
    private Map<String, List<@Valid StrategyStepApiModel>> stepsByStatus;
}
