package com.example.statistics;

import com.example.platform.jobs.JobExecutionGuard;
import com.example.platform.security.AccessDenialHandler;
import com.example.platform.security.ActorProvider;
import com.example.statistics.config.AggregateReadProperties;
import com.example.statistics.config.AggregateRecomputeProperties;
import com.example.statistics.config.EnvironmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа сервиса `statistics`.
 *
 * <p><b>Расписание включено ради тиков сервиса</b> — состояния приёма
 * (docs/components/ReceptionStateJob.md) и пересчёта агрегатов
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
 * конфигурации-владельца:</b> оси окружения, пересчёт агрегатов и
 * ограничения агрегатной выборки чтения — ни одна из них ничего не
 * настраивает, и пустой класс конфигурации был бы носителем без предмета. Формы подписки и подключений объявляют свои конфигурации —
 * там, где они и настраивают клиента ({@code ReceptionKafkaConfig},
 * {@code PersistenceConfig}).
 *
 * <p><b>Взятое из общего артефакта периметра названо ИМЕНОВАННО</b>, а не
 * взято сканированием его пакета: перечень взятого читается в одном
 * месте, а то, чего сервису не нужно, не приезжает к нему вместе с
 * пакетом.
 *
 * <p><b>{@code @EntityScan} рядом нет, и это не пропуск:</b> подключений
 * у процесса несколько, отображение объявлено явно, и аннотация
 * умолчания на него не действует. Общий пакет базового типа
 * audit-полей назван в области сканирования там
 * ({@code StatisticsPersistenceConfig}).
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({EnvironmentProperties.class,
        AggregateRecomputeProperties.class,
        AggregateReadProperties.class})
@Import({AccessDenialHandler.class, ActorProvider.class, JobExecutionGuard.class})
public class StatisticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatisticsApplication.class, args);
    }
}
