package com.example.tradingbot.api.model.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовое действие шага стратегии (API). Вид определяется JSON-
 * дискриминатором actionKind: ORDER/ALGO_ORDER/POSITION (только форма
 * сериализации, не поле домена).
 */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "actionKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StrategyOrderActionApiModel.class, name = "ORDER"),
        @JsonSubTypes.Type(value = StrategyAlgoOrderActionApiModel.class, name = "ALGO_ORDER"),
        @JsonSubTypes.Type(value = StrategyPositionActionApiModel.class, name = "POSITION")
})
@Schema(description = "Действие шага; вид задаёт дискриминатор actionKind (ORDER/ALGO_ORDER/POSITION)",
        discriminatorProperty = "actionKind")
public abstract class StrategyActionApiModel {

    @NotBlank
    @Schema(description = "Стабильный ключ действия в рамках одной детали",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @Schema(description = "Ключ действия, создавшего runtime-сущность (для AMEND/CANCEL); для CREATE пусто")
    private String targetActionKey;

    @NotBlank
    @Schema(description = "Тип действия: CREATE/AMEND/CANCEL (POSITION — CLOSE_FULL)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String actionType;

    @Positive
    @Schema(description = "Уровень действия внутри стратегии (не переносится в runtime-сущности)")
    private Integer level;
}
