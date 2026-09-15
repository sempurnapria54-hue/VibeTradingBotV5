package com.example.strategies;

import com.example.platform.client.ServiceClientConfig;
import com.example.platform.exception.handler.AccessDenialHandler;
import com.example.platform.jobs.JobExecutionGuard;
import com.example.platform.security.ActorProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса `strategies` — владельца определений стратегий и
 * их жизненного цикла (docs/architecture/services.md).
 *
 * <p>Расписание включено ради одного прохода — реле outbox: решение и
 * его событие пишутся одной транзакцией, публикует их отдельная джоба
 * (docs/architecture/data-ownership.md §«Outbox и доставка»).
 *
 * <p>Асинхронное исполнение — ради ручного триггера того же прохода:
 * внерасписанный запуск идёт через фасад и не блокирует HTTP-ответ
 * (.claude/rules/codestyle.md §Джобы).
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО</b>, а не
 * взято сканированием его пакета: перечень взятого читается в одном
 * месте, а то, чего сервису не нужно, не приезжает к нему вместе с
 * пакетом.
 *
 * <p><b>Область отображаемых классов названа явно.</b> Базовый тип
 * audit-полей лежит в общем артефакте, то есть вне пакета сервиса, и
 * умолчание сканирования его не видит. Свой пакет перечисляется рядом:
 * {@code @EntityScan} умолчание ЗАМЕЩАЕТ, а не дополняет.
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@EntityScan({"com.example.strategies", "com.example.tradingbot.persistence.model"})
@Import({AccessDenialHandler.class, ActorProvider.class, JobExecutionGuard.class, ServiceClientConfig.class})
public class StrategiesApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategiesApplication.class, args);
    }
}
