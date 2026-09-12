package com.example.tradingbot.domain.model.aggregate.strategy.action;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ожидаемое действие над позицией — ВЫХОД. Единственный тип действия
 * этого вида — {@code EXIT_ACTION}: закрытие позиции внутри сделки
 * самостоятельного смысла не имеет, осмысленное действие — выход, и
 * отмена живых входных заявок входит в его состав
 * (docs/components/ExitActionExecutor.md).
 *
 * <p><b>Область выхода задаёт уровень объявления, а не поле действия:</b>
 * шаг транша выводит свой транш, шаг узкой агрегатной поверхности —
 * всю сделку. Собственного маркера области у действия нет — он был бы
 * второй копией уровня, который уже читается носителем шага.
 *
 * <p>Своих настроек действие не несёт: закрывается ВСЯ экспозиция своей
 * области, доли у выхода не бывает (docs/rules/no-partial-close.md).
 * См. docs/models/domain/aggregate/Strategy.md (§Действия).
 */
@Getter
@Setter
@NoArgsConstructor
public class StrategyPositionAction extends Auditable implements StrategyAction {

    /** Технический ID действия. */
    private Long id;

    /** Стабильный ключ действия в рамках StrategyDetail. */
    private String key;

    /** Тип действия: только EXIT_ACTION. */
    private StrategyActionType actionType;

    /**
     * Цели у выхода нет: он адресует экспозицию своей области, а не
     * сущность, созданную соседним действием.
     */
    @Override
    public String getTargetActionKey() {
        return null;
    }

    /**
     * Уровня выход не ставит и не переносит, поэтому вопрос «чем задан
     * уровень» к нему не применяется: значение объявленного блока
     * настроек — единственное, чего у действия нет вовсе.
     */
    @Override
    public StrategyLevelSource levelSource() {
        return StrategyLevelSource.DECLARED;
    }
}
