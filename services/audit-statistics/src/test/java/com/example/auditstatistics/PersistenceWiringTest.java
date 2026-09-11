package com.example.auditstatistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auditstatistics.config.PersistenceConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/**
 * Подключений к базам три, и каждое ходит под объявленной ролью в
 * объявленную базу (docs/architecture/data-ownership.md §Раскладка: «Отсюда
 * подключений к базам у процесса три, а не два»).
 *
 * <p><b>Что тест закрывает.</b> Дом раскладки называет остаток охраны
 * инварианта «один пишущий»: грант отвергает запись <b>через подключение
 * роли статистики</b>, но какое из подключений внедрено в читателя — выбор
 * автора кода, и грантом он не решается. Проба уровня контекста мерит
 * ровно этот выбор в той части, которая уже существует: что
 * кросс-подключение действительно ведёт <b>в базу журнала</b> и
 * действительно <b>под ролью агрегатов</b>, а неквалифицированное
 * внедрение источника данных не имеет умолчания вовсе.
 *
 * <p><b>Что он НЕ мерит, и это названо.</b> Он не соединяется с базой:
 * пул поднимается лениво, а живого прогона у компонента нет ни одного.
 * Поэтому проверяется <b>адрес и роль подключения</b>, а не выданный грант
 * — выданное сверяется исполнимой формой
 * (docs/spec/owner-role-grants.json) и самой базой в момент записи. И он
 * не мерит выбор подключения <b>у читателя</b>: читатели — джоба пересчёта
 * и агрегатная выборка — приезжают своими компонентами, и до них у этой
 * пробы нет предмета.
 */
class PersistenceWiringTest {

    private static final String JOURNAL_URL = "jdbc:postgresql://platform-postgres-rw:5432/audit";
    private static final String AGGREGATES_URL = "jdbc:postgresql://platform-postgres-rw:5432/statistics";
    private static final String JOURNAL_ROLE = "audit";
    private static final String AGGREGATES_ROLE = "statistics";

    /**
     * Числа пробы намеренно РАЗНЫЕ и не равны умолчанию библиотеки: при
     * совпадающих «величина доехала» было бы неотличимо от «пул взял своё
     * умолчание».
     */
    private static final int JOURNAL_POOL = 3;
    private static final int AGGREGATES_POOL = 4;
    private static final int JOURNAL_READ_POOL = 2;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceConfig.class)
            .withPropertyValues(
                    "audit-statistics.persistence.journal.url=" + JOURNAL_URL,
                    "audit-statistics.persistence.journal.username=" + JOURNAL_ROLE,
                    "audit-statistics.persistence.journal.password=journal-password",
                    "audit-statistics.persistence.aggregates.url=" + AGGREGATES_URL,
                    "audit-statistics.persistence.aggregates.username=" + AGGREGATES_ROLE,
                    "audit-statistics.persistence.aggregates.password=aggregates-password",
                    "audit-statistics.persistence.journal.max-pool-size=" + JOURNAL_POOL,
                    "audit-statistics.persistence.aggregates.max-pool-size=" + AGGREGATES_POOL,
                    "audit-statistics.persistence.journal-read-max-pool-size=" + JOURNAL_READ_POOL);

    @Test
    @DisplayName("Своя база каждого владельца держится его собственной ролью")
    void eachOwnerHoldsItsOwnDatabaseUnderItsOwnRole() {
        runner.run(context -> {
            HikariDataSource journal = pool(context, PersistenceConfig.JOURNAL_DATA_SOURCE);
            HikariDataSource aggregates = pool(context, PersistenceConfig.AGGREGATES_DATA_SOURCE);

            assertThat(journal.getJdbcUrl()).isEqualTo(JOURNAL_URL);
            assertThat(journal.getUsername()).isEqualTo(JOURNAL_ROLE);
            assertThat(aggregates.getJdbcUrl()).isEqualTo(AGGREGATES_URL);
            assertThat(aggregates.getUsername()).isEqualTo(AGGREGATES_ROLE);
        });
    }

    /**
     * Кросс-подключение — то самое, ради которого подключений три, а не
     * два: база журнала под ролью агрегатов.
     *
     * <p>Оба клейма проверяются вместе, потому что порознь каждый
     * выполняется и у неверного подключения: своя база агрегатов идёт под
     * той же ролью, а база журнала — под своей.
     */
    @Test
    @DisplayName("Журнал читается под ролью агрегатов, а не под ролью журнала")
    void theCrossConnectionReadsTheJournalUnderTheAggregatesRole() {
        runner.run(context -> {
            HikariDataSource journalRead = pool(context, PersistenceConfig.JOURNAL_READ_DATA_SOURCE);

            assertThat(journalRead.getJdbcUrl())
                    .as("кросс-подключение обязано вести в базу журнала")
                    .isEqualTo(JOURNAL_URL);
            assertThat(journalRead.getUsername())
                    .as("и обязано идти под ролью агрегатов: запись отвергает база, а не автор")
                    .isEqualTo(AGGREGATES_ROLE);
        });
    }

    /**
     * Умолчания у выбора подключения нет.
     *
     * <p>Без этой проверки {@code @Primary}, поставленный когда-нибудь ради
     * удобства одного внедрения, молча отдал бы чужую тропу всем
     * остальным: контекст поднялся бы, а инвариант перестал бы держаться
     * сборкой.
     */
    @Test
    @DisplayName("Неквалифицированное внедрение источника данных не разрешается")
    void anUnqualifiedDataSourceInjectionDoesNotResolve() {
        runner.run(context -> assertThatThrownBy(() -> context.getBean(DataSource.class))
                .as("умолчания у выбора подключения быть не должно")
                .isInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    /**
     * Назначенный размер пула доезжает до каждого из трёх подключений.
     *
     * <p><b>Без этой пробы тропа конфигурации есть на бумаге.</b>
     * Подключения собираются в обход {@code spring.datasource}, и снятая
     * когда-нибудь строка назначения вернула бы умолчание библиотеки
     * молча — до тридцати соединений одной репликой у кластера, чей
     * {@code max_connections} никем не назначен.
     */
    @Test
    @DisplayName("Назначенный размер пула доезжает до всех трёх подключений")
    void theAssignedPoolSizeReachesEveryConnection() {
        runner.run(context -> {
            assertThat(pool(context, PersistenceConfig.JOURNAL_DATA_SOURCE).getMaximumPoolSize())
                    .isEqualTo(JOURNAL_POOL);
            assertThat(pool(context, PersistenceConfig.AGGREGATES_DATA_SOURCE).getMaximumPoolSize())
                    .isEqualTo(AGGREGATES_POOL);
            assertThat(pool(context, PersistenceConfig.JOURNAL_READ_DATA_SOURCE).getMaximumPoolSize())
                    .as("у кросс-подключения пул свой: он не наследуется ни от одного из владельцев")
                    .isEqualTo(JOURNAL_READ_POOL);
        });
    }

    private HikariDataSource pool(ApplicationContext context, String beanName) {
        return context.getBean(beanName, HikariDataSource.class);
    }
}
