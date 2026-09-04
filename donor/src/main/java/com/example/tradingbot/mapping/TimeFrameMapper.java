package com.example.tradingbot.mapping;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import com.example.tradingbot.util.Constants;
import org.mapstruct.Mapper;

/**
 * Маппинг доменного {@link TimeFrame} → строка таймфрейма OKX
 * (docs/models/mapping/TimeFrame.md). Строгий: точное соответствие
 * строк, без lower/upper-case. Enum строк биржи не хранит — они живут
 * в {@code Constants.Okx}.
 *
 * <p>ONE_DAY → "1Dutc" (UTC-выровненное открытие дневного бара) ради
 * сквозного правила UTC (docs/rules/time-utc.md); "1D" открывается в
 * UTC+8. Sub-day таймфреймы от таймзоны не зависят.
 */
@Mapper(componentModel = "spring")
public interface TimeFrameMapper {

    default String domainToOkx(TimeFrame timeFrame) {
        if (isNull(timeFrame)) {
            return null;
        }
        return switch (timeFrame) {
            case ONE_SECOND -> Constants.Okx.BAR_ONE_SECOND;
            case ONE_MINUTE -> Constants.Okx.BAR_ONE_MINUTE;
            case THREE_MINUTES -> Constants.Okx.BAR_THREE_MINUTES;
            case FIVE_MINUTES -> Constants.Okx.BAR_FIVE_MINUTES;
            case FIFTEEN_MINUTES -> Constants.Okx.BAR_FIFTEEN_MINUTES;
            case ONE_HOUR -> Constants.Okx.BAR_ONE_HOUR;
            case TWO_HOURS -> Constants.Okx.BAR_TWO_HOURS;
            case FOUR_HOURS -> Constants.Okx.BAR_FOUR_HOURS;
            case ONE_DAY -> Constants.Okx.BAR_ONE_DAY;
        };
    }
}
