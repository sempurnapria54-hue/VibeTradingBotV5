package com.example.tradingbot.api.model.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Действие над позицией (API): выход из сделки либо транша. Собственных
 * полей у вида нет — область выхода задаёт уровень объявления шага, а
 * доли у выхода не бывает (docs/rules/no-partial-close.md).
 */
@Getter
@Setter
@Schema(description = "Действие выхода: снять живые входные ноги своей области и закрыть её экспозицию")
public class StrategyPositionActionApiModel extends StrategyActionApiModel {
}
