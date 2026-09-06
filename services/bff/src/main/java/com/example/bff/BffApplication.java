package com.example.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Периметр: единственная точка входа браузера
 * (docs/architecture/services.md, ярус периметра).
 *
 * <p><b>Торговых решений не принимает</b> — он показывает и пересылает.
 * Отсюда всё остальное: проверка права живёт у владельца операции,
 * экранных контрактов периметр не заводит, а формой наружу идёт форма
 * владельца у пересылаемого и своя api-модель у порождаемого
 * (docs/architecture/contracts.md §«Периметр: что {@code bff} отдаёт и
 * чего не делает»).
 *
 * <p><b>Расписание включено ради одного предмета — пульса потока:</b>
 * молчание без пульса есть наблюдаемый отказ, и испускает его периметр
 * (docs/architecture/contracts.md §«Живые данные в браузер»).
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
