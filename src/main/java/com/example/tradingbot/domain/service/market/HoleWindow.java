package com.example.tradingbot.domain.service.market;

/**
 * Окно поиска дыры в свечном ряду {@code [fromMillis, toMillis]} (UTC
 * мс) — результат бинарного поиска по count в {@link CandleLoader}.
 * Вынесен отдельным типом: в сервисах внутренние классы/рекорды не
 * заводим (codestyle).
 */
public record HoleWindow(long fromMillis, long toMillis) {
}
