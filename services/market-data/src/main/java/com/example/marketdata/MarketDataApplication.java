package com.example.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Сервис рыночных данных: сбор свечей по требованию потребителя, сбор
 * невосполнимых срезов по всему листингу, расчёт производных и раздача
 * готовых значений (docs/architecture/services.md).
 *
 * <p>Сканируется и общий артефакт {@code strategy-engine}: интерпретатор
 * условий — коллаборатор классификации фазы.
 */
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = {"com.example.marketdata", "com.example.strategy.engine"})
public class MarketDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataApplication.class, args);
    }
}
