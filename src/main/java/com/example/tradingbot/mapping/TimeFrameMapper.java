package com.example.tradingbot.mapping;

import com.example.tradingbot.domain.model.trade.candle.TimeFrame;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;

/**
 * Маппинг доменного {@link TimeFrame} ↔ строка таймфрейма OKX
 * (docs/models/mapping/TimeFrame.md). Строгий: точное соответствие
 * строк, без lower/upper-case. Enum строк биржи не хранит — они живут
 * только здесь.
 *
 * <p>ONE_DAY → "1Dutc" (UTC-выровненное открытие дневного бара) ради
 * сквозного правила UTC (docs/rules/time-utc.md); "1D" открывается в
 * UTC+8. Sub-day таймфреймы от таймзоны не зависят.
 */
@Mapper(componentModel = "spring")
public interface TimeFrameMapper {

    String OKX_ONE_MINUTE = "1m";
    String OKX_THREE_MINUTES = "3m";
    String OKX_FIVE_MINUTES = "5m";
    String OKX_FIFTEEN_MINUTES = "15m";
    String OKX_ONE_HOUR = "1H";
    String OKX_TWO_HOURS = "2H";
    String OKX_FOUR_HOURS = "4H";
    String OKX_ONE_DAY = "1Dutc";

    default String domainToOkxClient(TimeFrame timeFrame) {
        if (Objects.isNull(timeFrame)) {
            return null;
        }
        return switch (timeFrame) {
            case ONE_MINUTE -> OKX_ONE_MINUTE;
            case THREE_MINUTES -> OKX_THREE_MINUTES;
            case FIVE_MINUTES -> OKX_FIVE_MINUTES;
            case FIFTEEN_MINUTES -> OKX_FIFTEEN_MINUTES;
            case ONE_HOUR -> OKX_ONE_HOUR;
            case TWO_HOURS -> OKX_TWO_HOURS;
            case FOUR_HOURS -> OKX_FOUR_HOURS;
            case ONE_DAY -> OKX_ONE_DAY;
        };
    }

    default TimeFrame okxClientToDomain(String bar) {
        if (StringUtils.isBlank(bar)) {
            return null;
        }
        return switch (bar) {
            case OKX_ONE_MINUTE -> TimeFrame.ONE_MINUTE;
            case OKX_THREE_MINUTES -> TimeFrame.THREE_MINUTES;
            case OKX_FIVE_MINUTES -> TimeFrame.FIVE_MINUTES;
            case OKX_FIFTEEN_MINUTES -> TimeFrame.FIFTEEN_MINUTES;
            case OKX_ONE_HOUR -> TimeFrame.ONE_HOUR;
            case OKX_TWO_HOURS -> TimeFrame.TWO_HOURS;
            case OKX_FOUR_HOURS -> TimeFrame.FOUR_HOURS;
            case OKX_ONE_DAY -> TimeFrame.ONE_DAY;
            default -> throw new IllegalArgumentException("Unsupported OKX bar: " + bar);
        };
    }
}
