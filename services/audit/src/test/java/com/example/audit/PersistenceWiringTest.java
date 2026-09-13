package com.example.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.audit.config.PersistenceConfig;
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

    private static final String JOURNAL_URL = "jdbc:postgresql://platform-postgres-rw:5432/audit";
    private static final String JOURNAL_ROLE = "audit";

    /**
     * Число пробы намеренно НЕ равно умолчанию библиотеки: при совпадающем
     * «величина доехала» было бы неотличимо от «пул взял своё умолчание».
     */
    private static final int JOURNAL_POOL = 3;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceConfig.class)
            .withPropertyValues(
                    "audit.persistence.journal.url=" + JOURNAL_URL,
                    "audit.persistence.journal.username=" + JOURNAL_ROLE,
                    "audit.persistence.journal.password=journal-password",
                    "audit.persistence.journal.max-pool-size=" + JOURNAL_POOL);

    @Test
    @DisplayName("Своя база владельца держится его собственной ролью")
    void theOwnerHoldsItsOwnDatabaseUnderItsOwnRole() {
        runner.run(context -> {
            HikariDataSource journal = pool(context, PersistenceConfig.JOURNAL_DATA_SOURCE);

            assertThat(journal.getJdbcUrl()).isEqualTo(JOURNAL_URL);
            assertThat(journal.getUsername()).isEqualTo(JOURNAL_ROLE);
        });
    }

    @Test
    @DisplayName("Назначенный размер пула доезжает до подключения")
    void theAssignedPoolSizeReachesTheConnection() {
        runner.run(context ->
                assertThat(pool(context, PersistenceConfig.JOURNAL_DATA_SOURCE).getMaximumPoolSize())
                        .isEqualTo(JOURNAL_POOL));
    }

    private HikariDataSource pool(ApplicationContext context, String beanName) {
        return context.getBean(beanName, HikariDataSource.class);
    }
}
