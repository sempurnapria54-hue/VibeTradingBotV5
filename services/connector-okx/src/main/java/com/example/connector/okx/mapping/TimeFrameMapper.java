package com.example.connector.okx.mapping;

import static java.util.Objects.isNull;

import com.example.connector.okx.util.OkxConstants;
import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import org.mapstruct.Mapper;

/**
 * Перевод доменного {@link TimeFrame} в словарь баров площадки
 * (docs/models/mapping/TimeFrame.md).
 *
 * <p><b>Живёт у коннектора, потому что словарь — его.</b> Читатель
 * рыночных данных называет таймфрейм доменным перечнем и строк площадки
 * не знает: иначе вторая биржа приходила бы правкой читателя, а не новым
 * коннектором (docs/architecture/services.md). Тот же ход, что уже сделан
 * со стороной заявки (.claude/rules/codestyle.md §Слои).
 *
 * <p>{@code ONE_DAY} → {@code 1Dutc} (UTC-выровненное открытие дневного
 * бара) ради сквозного правила UTC (docs/rules/time-utc.md); {@code 1D}
 * открывается в UTC+8. Sub-day бары от таймзоны не зависят.
 */
@Mapper(componentModel = "spring")
public interface TimeFrameMapper {

    /** Доменный таймфрейм → бар площадки; пусто на входе — пусто на выходе. */
    default String domainToOkx(TimeFrame timeFrame) {
        if (isNull(timeFrame)) {
            return null;
        }
        return switch (timeFrame) {
            case ONE_SECOND -> OkxConstants.BAR_ONE_SECOND;
            case ONE_MINUTE -> OkxConstants.BAR_ONE_MINUTE;
            case THREE_MINUTES -> OkxConstants.BAR_THREE_MINUTES;
            case FIVE_MINUTES -> OkxConstants.BAR_FIVE_MINUTES;
            case FIFTEEN_MINUTES -> OkxConstants.BAR_FIFTEEN_MINUTES;
            case ONE_HOUR -> OkxConstants.BAR_ONE_HOUR;
            case TWO_HOURS -> OkxConstants.BAR_TWO_HOURS;
            case FOUR_HOURS -> OkxConstants.BAR_FOUR_HOURS;
            case ONE_DAY -> OkxConstants.BAR_ONE_DAY;
        };
    }
}
