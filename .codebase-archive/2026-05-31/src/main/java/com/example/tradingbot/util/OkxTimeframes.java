package com.example.tradingbot.util;

import java.util.Set;

public final class OkxTimeframes {

    public static final String ONE_MINUTE = "1m";
    public static final String THREE_MINUTES = "3m";
    public static final String FIVE_MINUTES = "5m";
    public static final String FIFTEEN_MINUTES = "15m";
    public static final String THIRTY_MINUTES = "30m";
    public static final String ONE_HOUR = "1H";
    public static final String TWO_HOURS = "2H";
    public static final String FOUR_HOURS = "4H";
    public static final String SIX_HOURS_UTC = "6Hutc";
    public static final String TWELVE_HOURS_UTC = "12Hutc";
    public static final String ONE_DAY_UTC = "1Dutc";
    public static final String TWO_DAYS_UTC = "2Dutc";
    public static final String THREE_DAYS_UTC = "3Dutc";
    public static final String ONE_WEEK_UTC = "1Wutc";
    public static final String ONE_MONTH_UTC = "1Mutc";
    public static final String THREE_MONTHS_UTC = "3Mutc";

    public static final Set<String> ALL_TIMEFRAMES = Set.of(
        ONE_MINUTE,
        THREE_MINUTES,
        FIVE_MINUTES,
        FIFTEEN_MINUTES,
        THIRTY_MINUTES,
        ONE_HOUR,
        TWO_HOURS,
        FOUR_HOURS,
        SIX_HOURS_UTC,
        TWELVE_HOURS_UTC,
        ONE_DAY_UTC,
        TWO_DAYS_UTC,
        THREE_DAYS_UTC,
        ONE_WEEK_UTC,
        ONE_MONTH_UTC,
        THREE_MONTHS_UTC
    );

    public static final Set<String> RECOMMENDED_TIMEFRAMES = Set.of(
        ONE_MINUTE,
        THREE_MINUTES,
        FIFTEEN_MINUTES,
        ONE_HOUR,
        FOUR_HOURS,
        ONE_DAY_UTC
    );

    private OkxTimeframes() {
    }

    public static boolean isSupported(String timeframe) {
        return ALL_TIMEFRAMES.contains(timeframe);
    }
}
