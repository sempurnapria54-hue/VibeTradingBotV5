package com.example.statistics;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.statistics.config.SchemaMigrationConfig;
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
 * Цепочка миграций одна — владелец данных у сервиса один, — и ведёт она
 * свою базу своим подключением
 * (docs/architecture/data-ownership.md §Раскладка).
 *
 * <p><b>Контекст здесь не поднимается намеренно.</b> Бин цепочки
 * запускается {@code initMethod}, то есть в контексте он бы соединился с
 * базой; предмет проверки — <b>настройка</b> цепочки, и она читается с
 * объекта, который вернул сам фабричный метод.
 *
 * <p><b>Прежняя редакция пробы мерила две цепочки и подстановку роли
 * статистики.</b> Раздел сервиса увёз вторую цепочку в свой сервис, а
 * грант чтения снял вместе с его единственным читателем
 * (.claude/decisions/audit-statistics-split.md), и подстановок в теле
 * цепочки не осталось ни одной.
 */
class SchemaMigrationChainTest {

    /** Тело цепочки журнала; путь — от каталога модуля, как у соседнего теста. */
    private static final Path JOURNAL_CHAIN =
            Path.of("src", "main", "resources", "db", "migration", "statistics");

    /** Подстановка Flyway в теле миграции: префикс `${`, суффикс `}`. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    private final SchemaMigrationConfig config = new SchemaMigrationConfig();

    @Test
    @DisplayName("Цепочка ведёт свою базу своим подключением")
    void theChainRunsItsOwnDatabaseOverItsOwnConnection() {
        DataSource journalDataSource = mock(DataSource.class);

        Flyway journal = config.statisticsMigration(journalDataSource);

        assertThat(descriptors(journal)).containsExactly(SchemaMigrationConfig.STATISTICS_LOCATION);
        assertThat(journal.getConfiguration().getDataSource()).isSameAs(journalDataSource);
    }

    /**
     * Всякая подстановка тела цепочки имеет значение.
     *
     * <p>Сегодня подстановок в теле нет ни одной, и проверка стои́т ради
     * <b>будущей</b>: заведённая в миграции и не настроенная, она роняла бы
     * старт сервиса на живом прогоне, а не на сборке. Клейма «подстановка
     * обязана быть» здесь поэтому нет — оно было бы ложным.
     */
    @Test
    @DisplayName("Каждая подстановка, встреченная в теле цепочки, настроена")
    void everyPlaceholderUsedByTheJournalChainHasAValue() throws IOException {
        Set<String> used = placeholdersUsedBy(JOURNAL_CHAIN);
        Flyway journal = config.statisticsMigration(mock(DataSource.class));

        assertThat(journal.getConfiguration().getPlaceholders().keySet())
                .as("подстановка без значения роняет миграцию на живом прогоне, а не на сборке")
                .containsAll(used);
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
