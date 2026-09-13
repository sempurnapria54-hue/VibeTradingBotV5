package com.example.audit.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Запуск миграций базы журнала.
 *
 * <p><b>Цепочка одна — у сервиса один владелец данных.</b> Вторая, ведшая
 * базу статистики, уехала вместе с модулем статистики в свой сервис
 * (.claude/decisions/audit-statistics-split.md); плейсхолдер роли статистики
 * ушёл вместе с грантом, у которого не осталось читателей
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Бин объявлен явно, а не стартером:</b> автоконфиг Flyway отступает
 * при собственном бине ({@code @ConditionalOnMissingBean(Flyway.class)}), а
 * подключение здесь собирается своей формой ({@link PersistenceConfig}) и
 * свойствами {@code spring.datasource.*} не настраивается.
 */
@Configuration
public class SchemaMigrationConfig {

    /** Цепочка базы журнала. */
    public static final String JOURNAL_LOCATION = "classpath:db/migration/audit";

    @Bean(initMethod = "migrate")
    public Flyway journalMigration(
            @Qualifier(PersistenceConfig.JOURNAL_DATA_SOURCE) DataSource journalDataSource) {
        return Flyway.configure()
                .dataSource(journalDataSource)
                .locations(JOURNAL_LOCATION)
                .load();
    }
}
