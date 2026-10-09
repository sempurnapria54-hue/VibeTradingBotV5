package com.example.tradingcore.util;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

import com.example.tradingbot.domain.model.core.position.Position;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Режим маржи записи среза позиций против режима контура
 * (docs/models/domain/core/Position.md §«Режим маржи записи — атрибут
 * границы той же формы»; docs/components/AnomalyJob.md, запись позиции
 * режима контура).
 *
 * <p><b>Один предикат на обоих читателей среза</b> — проактивную детекцию
 * и снятие риска вне графа сделок: копия у каждого разошлась бы первой же
 * правкой. Живёт здесь, а не на модели позиции, потому что режим контура —
 * величина ядра (adapter-константа писателя заявок), а не свойство записи.
 */
@UtilityClass
public class PositionMarginMode {

    /**
     * Запись иного режима маржи, чем режим контура: её открыла заявка,
     * которой мы не отправляли, и нашей позицией она не читается.
     *
     * <p><b>Пустой режим чужим не делает.</b> Граница отдаёт режим у каждой
     * записи среза и на значении вне формы отказывает чтением; пусто бывает
     * только у строки из нашей базы, которая и есть наша позиция. Объявить
     * чужой запись, режима которой никто не видел, значило бы поднять
     * жёсткую ступень счёта по отсутствию факта.
     */
    public static Boolean isForeign(Position position) {
        return nonNull(position.getMarginMode())
                && isFalse(Objects.equals(Constants.Contour.MARGIN_MODE, position.getMarginMode()));
    }
}
