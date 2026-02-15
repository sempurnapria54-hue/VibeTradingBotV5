package com.example.tradingbot.util;

public final class TimeframeMillis {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 86_400_000L;

    private TimeframeMillis() {
    }

    public static long toMillis(String timeframe) {
        return switch (timeframe) {
            case OkxTimeframes.ONE_MINUTE -> MINUTE;
            case OkxTimeframes.THREE_MINUTES -> 3 * MINUTE;
            case OkxTimeframes.FIVE_MINUTES -> 5 * MINUTE;
            case OkxTimeframes.FIFTEEN_MINUTES -> 15 * MINUTE;
            case OkxTimeframes.THIRTY_MINUTES -> 30 * MINUTE;
            case OkxTimeframes.ONE_HOUR -> HOUR;
            case OkxTimeframes.TWO_HOURS -> 2 * HOUR;
            case OkxTimeframes.FOUR_HOURS -> 4 * HOUR;
            case OkxTimeframes.SIX_HOURS_UTC -> 6 * HOUR;
            case OkxTimeframes.TWELVE_HOURS_UTC -> 12 * HOUR;
            case OkxTimeframes.ONE_DAY_UTC -> DAY;
            case OkxTimeframes.TWO_DAYS_UTC -> 2 * DAY;
            case OkxTimeframes.THREE_DAYS_UTC -> 3 * DAY;
            case OkxTimeframes.ONE_WEEK_UTC -> 7 * DAY;
            case OkxTimeframes.ONE_MONTH_UTC -> 30 * DAY;
            case OkxTimeframes.THREE_MONTHS_UTC -> 90 * DAY;
            default -> throw new IllegalArgumentException("Unsupported OKX timeframe: " + timeframe);
        };
    }
}
