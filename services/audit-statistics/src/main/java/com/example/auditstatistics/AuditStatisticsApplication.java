package com.example.auditstatistics;

import com.example.auditstatistics.config.AggregateReadProperties;
import com.example.auditstatistics.config.AggregateRecomputeProperties;
import com.example.auditstatistics.config.EnvironmentProperties;
import com.example.auditstatistics.config.JournalCleanupProperties;
import com.example.auditstatistics.config.JournalReadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса `audit-statistics`.
 *
 * <p><b>Расписание включено ради тиков сервиса</b> — состояния приёма
 * (docs/components/ReceptionStateJob.md), чистки журнала
 * (docs/components/JournalCleanupJob.md) и пересчёта агрегатов
 * (docs/rules/statistics-aggregates.md §«Пересчёт — проекция, а не
 * накопитель»). {@code @EnableAsync} рядом нет и
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
 * конфигурации-владельца:</b> оси окружения, чистка журнала, пересчёт
 * агрегатов и ограничения обеих выборок чтения — ни одна из них
 * ничего не настраивает, и пустой класс конфигурации был бы носителем без
 * предмета. Формы подписки и подключений объявляют свои конфигурации —
 * там, где они и настраивают клиента ({@code ReceptionKafkaConfig},
 * {@code PersistenceConfig}).
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({EnvironmentProperties.class,
        JournalCleanupProperties.class,
        AggregateRecomputeProperties.class,
        JournalReadProperties.class,
        AggregateReadProperties.class})
public class AuditStatisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditStatisticsApplication.class, args);
    }
}
