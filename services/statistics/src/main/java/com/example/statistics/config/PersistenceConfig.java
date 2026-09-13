package com.example.statistics.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Подключение к базе сервиса — одно
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Владелец данных у процесса один, и отсюда одно подключение.</b>
 * Прежде их было три: своё, чужое подключение к базе статистики и
 * кросс-подключение к журналу под ролью агрегатов. Раздел сервиса снял оба
 * последних вместе с их предметом — статистика ведёт свои факты в своей
 * базе и журнал источником не читает
 * (.claude/decisions/audit-statistics-split.md).
 *
 * <p><b>Подключение собирается здесь явно, минуя {@code spring.datasource}</b>,
 * и это названный остаток, а не необходимость: с одним владельцем автоконфиг
 * Boot справился бы сам. Форма оставлена потому, что на ней стои́т <b>имя
 * менеджера транзакций</b>, которое проверяют пробы границ записи
 * ({@code ReceptionTransactionBoundariesTest} и соседние): писатель обязан
 * идти через менеджер своего владельца, и проба мерит именно это имя.
 *
 * <p><b>Размер пула — величина конфигурации, а не умолчание библиотеки:</b>
 * свойства {@code spring.datasource.hikari.*} этим бином не читаются вовсе,
 * и без собственной формы перекалибровка потребовала бы сборки.
 */
@Configuration
@EnableConfigurationProperties(PersistenceProperties.class)
public class PersistenceConfig {

    /** Своя база под ролью владельца: единственный писатель фактов и агрегатов. */
    public static final String STATISTICS_DATA_SOURCE = "statisticsDataSource";

    @Bean(STATISTICS_DATA_SOURCE)
    public DataSource statisticsDataSource(PersistenceProperties properties) {
        return connect(properties);
    }

    private DataSource connect(DatabaseConnectionProperties connection) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(connection.getUrl())
                .username(connection.getUsername())
                .password(connection.getPassword())
                .build();
        dataSource.setMaximumPoolSize(connection.getMaxPoolSize());
        return dataSource;
    }
}
