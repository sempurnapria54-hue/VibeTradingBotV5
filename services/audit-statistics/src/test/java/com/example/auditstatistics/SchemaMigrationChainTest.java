package com.example.auditstatistics;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.auditstatistics.config.DatabaseConnectionProperties;
import com.example.auditstatistics.config.PersistenceProperties;
import com.example.auditstatistics.config.SchemaMigrationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Цепочек миграций две — по одной на владельца данных, — и каждая идёт в
 * свою базу своим подключением
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Контекст здесь не поднимается намеренно.</b> Бины цепочек
 * запускаются {@code initMethod}, то есть в контексте они бы соединились с
 * базой; предмет проверки — <b>настройка</b> цепочки, и она читается с
 * объекта, который вернул сам фабричный метод.
 *
 * <p><b>Подстановка сверяется с телом миграции, а не с копией её имени.</b>
 * Третий тест читает SQL цепочки журнала и требует, чтобы у <b>каждой</b>
 * подстановки, встреченной в теле, было настроенное значение. Второго
 * носителя это не заводит: обе стороны — источники истины, а тест
 * утверждает их отношение.
 */
class SchemaMigrationChainTest {

    /**
     * Имя роли в проверке <b>намеренно не совпадает</b> с именем из
     * раскладки: литерал {@code "statistics"}, поставленный в код вместо
     * чтения настройки, прошёл бы проверку с совпадающим именем и остался
     * бы незамеченным до первой смены имени роли.
     */
    private static final String CONFIGURED_AGGREGATES_ROLE = "role-taken-from-configuration";

    /** Тело цепочки журнала; путь — от каталога модуля, как у соседнего теста. */
    private static final Path JOURNAL_CHAIN =
            Path.of("src", "main", "resources", "db", "migration", "audit");

    /** Подстановка Flyway в теле миграции: префикс `${`, суффикс `}`. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    private final SchemaMigrationConfig config = new SchemaMigrationConfig();

    @Test
    @DisplayName("Каждая цепочка ведёт свою базу своим подключением")
    void eachChainRunsItsOwnDatabaseOverItsOwnConnection() {
        DataSource journalDataSource = mock(DataSource.class);
        DataSource aggregatesDataSource = mock(DataSource.class);

        Flyway journal = config.journalMigration(journalDataSource, properties());
        Flyway aggregates = config.aggregatesMigration(aggregatesDataSource);

        assertThat(descriptors(journal)).containsExactly(SchemaMigrationConfig.JOURNAL_LOCATION);
        assertThat(descriptors(aggregates)).containsExactly(SchemaMigrationConfig.AGGREGATES_LOCATION);
        assertThat(journal.getConfiguration().getDataSource()).isSameAs(journalDataSource);
        assertThat(aggregates.getConfiguration().getDataSource()).isSameAs(aggregatesDataSource);
    }

    /**
     * Грант достаётся той роли, которой читатель и ходит.
     *
     * <p>Литерал в миграции выполнил бы любой отдельный тест «грант выдан»,
     * но разошёлся бы с настроенным подключением молча — и пересчёт остался
     * бы без чтения при зелёной миграции.
     */
    @Test
    @DisplayName("Роль-читатель в подстановке — имя настроенного подключения агрегатов")
    void theReaderRoleComesFromTheConfiguredAggregatesConnection() {
        Flyway journal = config.journalMigration(mock(DataSource.class), properties());

        assertThat(journal.getConfiguration().getPlaceholders())
                .as("грант обязан достаться роли настроенного подключения, а не литералу")
                .containsEntry(SchemaMigrationConfig.STATISTICS_ROLE_PLACEHOLDER, CONFIGURED_AGGREGATES_ROLE);
    }

    /**
     * Всякая подстановка тела цепочки имеет значение.
     *
     * <p>Без этого расхождение имён обнаруживалось бы только первым живым
     * прогоном миграции — то есть при постановке окружения, а не сборкой.
     * Конъюнкт непустоты обязателен: без него тест был бы зелен и на
     * цепочке вовсе без подстановок.
     */
    @Test
    @DisplayName("Каждая подстановка, встреченная в теле цепочки журнала, настроена")
    void everyPlaceholderUsedByTheJournalChainHasAValue() throws IOException {
        Set<String> used = placeholdersUsedBy(JOURNAL_CHAIN);
        Flyway journal = config.journalMigration(mock(DataSource.class), properties());

        assertThat(used).as("цепочка журнала обязана называть роль-читателя подстановкой").isNotEmpty();
        assertThat(journal.getConfiguration().getPlaceholders().keySet())
                .as("подстановка без значения роняет миграцию на живом прогоне, а не на сборке")
                .containsAll(used);
    }

    private PersistenceProperties properties() {
        DatabaseConnectionProperties aggregates = new DatabaseConnectionProperties();
        aggregates.setUsername(CONFIGURED_AGGREGATES_ROLE);

        PersistenceProperties properties = new PersistenceProperties();
        properties.setAggregates(aggregates);
        return properties;
    }

    private Set<String> descriptors(Flyway flyway) {
        return Stream.of(flyway.getConfiguration().getLocations())
                .map(Location::getDescriptor)
                .collect(toCollection(LinkedHashSet::new));
    }

    private Set<String> placeholdersUsedBy(Path chain) throws IOException {
        Set<String> used = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(chain)) {
            for (Path file : files.toList()) {
                Matcher matcher = PLACEHOLDER.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }
            }
        }
        return used;
    }
}
