package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statistics.config.PersistenceConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/**
 * Подключение к базе одно, и ходит оно под объявленной ролью в объявленную
 * базу (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Что тест закрывает.</b> Подключение собирается в обход
 * {@code spring.datasource}, поэтому ни адрес, ни роль, ни размер пула не
 * подтверждаются автоконфигурацией: снятая когда-нибудь строка назначения
 * вернула бы умолчание библиотеки молча — до тридцати соединений одной
 * репликой у кластера, чей {@code max_connections} никем не назначен.
 *
 * <p><b>Что он НЕ мерит, и это названо.</b> Он не соединяется с базой: пул
 * поднимается лениво, а живого прогона у компонента нет ни одного. Поэтому
 * проверяется <b>адрес, роль и пул</b>, а не права роли — их сверяет сама
 * база в момент записи.
 *
 * <p><b>Прежняя редакция пробы мерила три подключения</b> — своё, чужое и
 * кросс-подключение к журналу под ролью агрегатов. Раздел сервиса снял
 * второго владельца данных вместе с обоими
 * (.claude/decisions/audit-statistics-split.md), и предмета у тех клеймов
 * не осталось.
 */
class PersistenceWiringTest {

    private static final String STATISTICS_URL = "jdbc:postgresql://platform-postgres-rw:5432/statistics";
    private static final String STATISTICS_ROLE = "statistics";

    /**
     * Число пробы намеренно НЕ равно умолчанию библиотеки: при совпадающем
     * «величина доехала» было бы неотличимо от «пул взял своё умолчание».
     */
    private static final int STATISTICS_POOL = 3;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceConfig.class)
            .withPropertyValues(
                    "statistics.persistence.url=" + STATISTICS_URL,
                    "statistics.persistence.username=" + STATISTICS_ROLE,
                    "statistics.persistence.password=statistics-password",
                    "statistics.persistence.max-pool-size=" + STATISTICS_POOL);

    @Test
    @DisplayName("Своя база владельца держится его собственной ролью")
    void theOwnerHoldsItsOwnDatabaseUnderItsOwnRole() {
        runner.run(context -> {
            HikariDataSource connection = pool(context, PersistenceConfig.STATISTICS_DATA_SOURCE);

            assertThat(connection.getJdbcUrl()).isEqualTo(STATISTICS_URL);
            assertThat(connection.getUsername()).isEqualTo(STATISTICS_ROLE);
        });
    }

    @Test
    @DisplayName("Назначенный размер пула доезжает до подключения")
    void theAssignedPoolSizeReachesTheConnection() {
        runner.run(context ->
                assertThat(pool(context, PersistenceConfig.STATISTICS_DATA_SOURCE).getMaximumPoolSize())
                        .isEqualTo(STATISTICS_POOL));
    }

    private HikariDataSource pool(ApplicationContext context, String beanName) {
        return context.getBean(beanName, HikariDataSource.class);
    }
}
