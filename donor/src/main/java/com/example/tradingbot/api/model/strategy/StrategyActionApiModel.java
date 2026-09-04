package com.example.tradingbot.api.model.strategy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовое действие шага стратегии (API). Вид определяется JSON-
 * дискриминатором actionKind: ORDER/ALGO_ORDER/POSITION (только форма
 * сериализации, не поле домена). Позиционный вид несёт ровно один тип —
 * EXIT_ACTION: выход выражается либо условием-переходом, либо явным
 * действием шага EXIT (docs/rules/no-partial-close.md §«Формы полного
 * выхода»).
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

    @Schema(description = "Ключ действия, создавшего runtime-сущность (для REPLACE_ACTION/CANCEL_ACTION); "
            + "для CREATE_ACTION и EXIT_ACTION пусто")
    private String targetActionKey;

    @NotBlank
    @Schema(description = "Тип действия: CREATE_ACTION/REPLACE_ACTION/CANCEL_ACTION/EXIT_ACTION",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String actionType;
}
