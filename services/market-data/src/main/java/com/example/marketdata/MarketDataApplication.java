package com.example.marketdata;

import com.example.platform.client.ServiceClientConfig;
import com.example.platform.exception.handler.AccessDenialHandler;
import com.example.platform.jobs.JobExecutionGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Сервис рыночных данных: сбор свечей по требованию потребителя, сбор
 * невосполнимых срезов по всему листингу, расчёт производных и раздача
 * готовых значений (docs/architecture/services.md).
 *
 * <p>Сканируется и общий артефакт {@code strategy-engine}: интерпретатор
 * условий — коллаборатор классификации фазы.
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО</b>, а не
 * взято сканированием его пакета: перечень взятого читается в одном
 * месте, а то, чего сервису не нужно, не приезжает к нему вместе с
 * пакетом. Разница со {@code strategy-engine} не в форме, а в предмете:
 * там сканируется слой расчёта целиком, здесь берутся поимённые
 * исполнители.
 *
 * <p><b>Область отображаемых классов названа явно.</b> Базовый тип
 * audit-полей лежит в общем артефакте, то есть вне пакета сервиса, и
 * умолчание сканирования его не видит. Свой пакет перечисляется рядом:
 * {@code @EntityScan} умолчание ЗАМЕЩАЕТ, а не дополняет.
 */
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
@Import({AccessDenialHandler.class, JobExecutionGuard.class, ServiceClientConfig.class})
@EntityScan({"com.example.marketdata", "com.example.tradingbot.persistence.model"})
@SpringBootApplication(scanBasePackages = {"com.example.marketdata", "com.example.strategy.engine"})
public class MarketDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataApplication.class, args);
    }
}
