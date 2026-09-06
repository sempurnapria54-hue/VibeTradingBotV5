package com.example.strategies;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class StrategiesApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategiesApplication.class, args);
    }
}
