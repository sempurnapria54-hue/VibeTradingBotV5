package com.example.bff;

import com.example.platform.security.AccessDenialHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
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
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО</b>, а не
 * взято сканированием его пакета: перечень взятого читается в одном
 * месте. Писателя следа отказа здесь нет и не будет: у периметра базы
 * нет, и след отказа там — лог и метрика (docs/rules/api-access-policy.md
 * §«След отказа пишет тот, у кого есть база»).
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
@Import(AccessDenialHandler.class)
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
