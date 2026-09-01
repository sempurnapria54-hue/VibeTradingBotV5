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
 * дискриминатором actionKind: ORDER/ALGO_ORDER (только форма
 * сериализации, не поле домена). Позиционного действия нет: выход из
 * позиции — условие-перехода MANAGING → EXIT_PENDING, не действие
 * (docs/processes/fsm-execution-layering.md).
 */
@Getter
@Setter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "actionKind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StrategyOrderActionApiModel.class, name = "ORDER"),
        @JsonSubTypes.Type(value = StrategyAlgoOrderActionApiModel.class, name = "ALGO_ORDER")
})
@Schema(description = "Действие шага; вид задаёт дискриминатор actionKind (ORDER/ALGO_ORDER)",
        discriminatorProperty = "actionKind")
public abstract class StrategyActionApiModel {

    @NotBlank
    @Schema(description = "Стабильный ключ действия в рамках одной детали",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @Schema(description = "Ключ действия, создавшего runtime-сущность (для REPLACE/CANCEL); для CREATE пусто")
    private String targetActionKey;

    @NotBlank
    @Schema(description = "Тип действия: CREATE/REPLACE/CANCEL",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String actionType;

    @Positive
    @Schema(description = "Уровень действия внутри стратегии (не переносится в runtime-сущности)")
    private Integer level;
}
