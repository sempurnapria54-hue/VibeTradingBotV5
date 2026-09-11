package com.example.auditstatistics.config;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Две цепочки миграций — по одной на владельца данных
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Почему цепочки объявлены здесь, а не настройкой
 * {@code spring.flyway.*}.</b> Автоконфигурация Boot ведёт <b>одну</b>
 * цепочку на один источник данных и отказывает при собственном бине
 * {@code Flyway}; источников здесь три, а баз — две, и выразить это одной
 * настройкой нечем. Отсюда явные бины и явный запуск {@code initMethod}.
 *
 * <p><b>Каждая цепочка идёт подключением СВОЕГО владельца.</b> Это не
 * оформление: права по умолчанию на будущие таблицы, которые выдаёт
 * миграция журнала, действуют на объекты, создаваемые <b>текущей</b> ролью
 * — то есть на объекты следующих миграций журнала. Пройди цепочка
 * кросс-подключением, таблицы завёл бы не владелец, и грант достался бы не
 * тем объектам.
 */
@Configuration
public class SchemaMigrationConfig {

    /** Цепочка базы журнала: она же выдаёт грант чтения роли агрегатов. */
    public static final String JOURNAL_LOCATION = "classpath:db/migration/audit";

    /** Цепочка базы агрегатов: грантов не выдаёт — журнал её не читает. */
    public static final String AGGREGATES_LOCATION = "classpath:db/migration/statistics";

    /**
     * Имя подстановки, которой миграция журнала называет роль-читателя.
     *
     * <p>Значение приезжает <b>из настроенного подключения</b> модуля
     * статистики, а не литералом: грант достаётся ровно той роли, которой
     * читатель и ходит. Литерал разошёлся бы с настройкой молча — миграция
     * осталась бы зелёной, а пересчёт остался бы без чтения.
     *
     * <p><b>Отказ на незаданном значении громкий, и он назван.</b> Пустое
     * имя роняет миграцию отказом самой базы — {@code role "" does not
     * exist}, — то есть отказ приходит от того, кто грант и выдаёт;
     * отсутствие ключа целиком роняет старт процесса. Второе достижимо
     * только подменой формы конфигурации: {@code application.yaml} ключ
     * задаёт всегда, а пустым его делает незаданная переменная окружения.
     */
    public static final String STATISTICS_ROLE_PLACEHOLDER = "statisticsRole";

    @Bean(initMethod = "migrate")
    public Flyway journalMigration(
            @Qualifier(PersistenceConfig.JOURNAL_DATA_SOURCE) DataSource journalDataSource,
            PersistenceProperties properties) {
        return Flyway.configure()
                .dataSource(journalDataSource)
                .locations(JOURNAL_LOCATION)
                .placeholders(Map.of(
                        STATISTICS_ROLE_PLACEHOLDER, properties.getAggregates().getUsername()))
                .load();
    }

    @Bean(initMethod = "migrate")
    public Flyway aggregatesMigration(
            @Qualifier(PersistenceConfig.AGGREGATES_DATA_SOURCE) DataSource aggregatesDataSource) {
        return Flyway.configure()
                .dataSource(aggregatesDataSource)
                .locations(AGGREGATES_LOCATION)
                .load();
    }
}
