package com.example.audit;

import com.example.audit.config.EnvironmentProperties;
import com.example.audit.config.JournalCleanupProperties;
import com.example.audit.config.JournalReadProperties;
import com.example.platform.jobs.JobExecutionGuard;
import com.example.platform.security.AccessDenialHandler;
import com.example.platform.security.ActorProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса `audit`.
 *
 * <p><b>Расписание включено ради тиков сервиса</b> — состояния приёма
 * (docs/components/ReceptionStateJob.md) и чистки журнала
 * (docs/components/JournalCleanupJob.md). {@code @EnableAsync} рядом нет и
 * не будет: асинхронный фасад нужен ручному триггеру джобы, а поверхность
 * сервиса объявлена только читающей — входящей точки записи инвентарь ему
 * не даёт (docs/architecture/services.md).
 *
 * <p><b>Размер пула планировщика при этом объявлен, а не оставлен
 * умолчанию</b> ({@code application.yaml}): умолчание каркаса — один поток
 * на все джобы, а среди тиков сервиса есть ИЗМЕРИТЕЛЬ — тик состояния
 * приёма. Его голодание задерживает не работу, а её наблюдаемость, и
 * наблюдателю неотличимо от мёртвого приёма (.claude/rules/codestyle.md
 * §Джобы, клауза «Операнда живости мало: такт обязан ДОСТАТЬСЯ тику»).
 * Число выведено из числа {@code @Scheduled}-методов дерева и сверяется
 * пробой {@code SchedulerCapacityTest}.
 *
 * <p><b>Здесь объявлены те формы конфигурации, у которых нет собственной
 * конфигурации-владельца:</b> оси окружения, чистка журнала и ограничения
 * журнальной выборки чтения — ни одна из них ничего не настраивает, и
 * пустой класс конфигурации был бы носителем без предмета. Формы подписки и подключений объявляют свои конфигурации —
 * там, где они и настраивают клиента ({@code ReceptionKafkaConfig},
 * {@code PersistenceConfig}).
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО, а не
 * взято сканированием его пакета.</b> Перечень взятого читается в
 * одном месте, а то, чего сервису не нужно, не приезжает к нему вместе
 * с пакетом: конфигурация исходящей идентичности требует
 * oauth2-client, который есть не у всех потребителей артефакта.
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({EnvironmentProperties.class,
        JournalCleanupProperties.class,
        JournalReadProperties.class})
@Import({AccessDenialHandler.class, ActorProvider.class, JobExecutionGuard.class})
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}
