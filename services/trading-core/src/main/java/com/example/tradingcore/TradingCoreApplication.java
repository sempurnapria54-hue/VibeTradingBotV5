package com.example.tradingcore;

import com.example.tradingcore.config.EnvironmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса {@code trading-core}.
 *
 * <p><b>Расписание и асинхронный запуск включены с первого дня:</b> ядро
 * живёт проходами оркестратора, наблюдения фактов и реле outbox, а
 * внерасписанный запуск джобы идёт через асинхронный фасад
 * ({@code .claude/rules/codestyle.md} §Джобы).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(EnvironmentProperties.class)
public class TradingCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingCoreApplication.class, args);
    }
}
