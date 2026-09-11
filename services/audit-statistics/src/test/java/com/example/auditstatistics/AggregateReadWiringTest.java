package com.example.auditstatistics;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.config.AggregatesPersistenceConfig;
import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.config.JournalReadPersistenceConfig;
import com.example.auditstatistics.domain.service.AggregateReadService;
import com.example.auditstatistics.domain.service.JournalCleanupService;
import com.example.auditstatistics.domain.service.JournalCompletenessService;
import com.example.auditstatistics.domain.service.JournalCompletenessSource;
import com.example.auditstatistics.domain.service.JournalReadService;
import com.example.auditstatistics.persistence.repository.ReceptionStateCompletenessQueries;
import com.example.auditstatistics.persistence.repository.journal.JournalReceptionStateRepository;
import com.example.auditstatistics.persistence.repository.journalread.ReceptionStateSourceRepository;
import com.example.auditstatistics.persistence.service.OwnerJournalCompletenessSource;
import com.example.auditstatistics.persistence.service.StatisticsJournalCompletenessSource;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

/**
 * Агрегатная выборка читает журнал КРОСС-ПОДКЛЮЧЕНИЕМ — под ролью
 * агрегатов, а не подключением владельца журнала
 * (docs/architecture/data-ownership.md §Раскладка, «Чего грант НЕ
 * охраняет»).
 *
 * <p><b>Почему без этой пробы охраны нет вовсе.</b> Грант отвергает запись
 * в журнал через подключение роли статистики, но какое из трёх подключений
 * внедрено в читателя, грантом не решается: оба живут в одном процессе.
 * Читатель, взявший подключение владельца, поднимает контекст без единой
 * жалобы и читает те же строки — ни старт сервиса, ни живой прогон
 * запросов такой подмены не замечают.
 *
 * <p><b>Проба читает ОБЪЯВЛЕНИЕ, а не поднятый контекст</b>, и уровень этот
 * назван домом: поднять контекст с отображением схемы без живой базы нечем
 * — фабрика сущностей на старте идёт к базе за метаданными. Мерится ровно
 * тот выбор, который подменой и портится.
 *
 * <p><b>Обход идёт ТРАНЗИТИВНО по объявленным полям, а не по одному
 * знакомству.</b> Подмена возможна на любом звене: источник операндов мог
 * бы взять репозиторий владельца журнала — и выборка получила бы чужую
 * тропу, не меняя ни одной своей строки.
 *
 * <p><b>Симметричная сторона проверяется здесь же:</b> журнальная выборка и
 * чистка обязаны ходить СВОИМ подключением. Взяв кросс-подключение, они
 * читали бы свою базу под чужой ролью — то есть паритет ролей держался бы
 * случайно.
 */
class AggregateReadWiringTest {

    private static final String CROSS_REPOSITORY_PACKAGE =
            "com.example.auditstatistics.persistence.repository.journalread";
    private static final String OWNER_REPOSITORY_PACKAGE =
            "com.example.auditstatistics.persistence.repository.journal";

    @Test
    @DisplayName("Агрегатная выборка объявляет источник полноты КРОСС-подключения, а не владельца журнала")
    void theAggregateReadDeclaresTheCrossSource() {
        assertThat(declaredFieldTypes(AggregateReadService.class))
                .as("подключение читателя обязано держаться типом, а не дисциплиной автора")
                .contains(StatisticsJournalCompletenessSource.class)
                .doesNotContain(OwnerJournalCompletenessSource.class);
    }

    /**
     * Тип объявлен КОНКРЕТНЫМ, а не интерфейсом источника: объявленный
     * интерфейсом, он выбирался бы квалификатором — строкой, а не типом, —
     * и подмена перестала бы ломать сборку.
     */
    @Test
    @DisplayName("Источник объявлен конкретным типом, а не интерфейсом порта")
    void theSourceIsDeclaredByItsConcreteType() {
        assertThat(declaredFieldTypes(AggregateReadService.class))
                .doesNotContain(JournalCompletenessSource.class);
        assertThat(declaredFieldTypes(JournalReadService.class))
                .doesNotContain(JournalCompletenessSource.class);
    }

    @Test
    @DisplayName("У агрегатной выборки не достижим НИ ОДИН репозиторий владельца журнала")
    void noOwnerJournalRepositoryIsReachableFromTheAggregateRead() {
        Set<Class<?>> repositories = reachableRepositories(StatisticsJournalCompletenessSource.class);

        assertThat(repositories)
                .as("тропа к журналу у модуля статистики одна — под его собственной ролью")
                .isNotEmpty();
        assertThat(repositories)
                .allSatisfy(repository -> assertThat(repository.getPackageName())
                        .as("репозиторий владельца журнала читал бы чужую базу под ролью с правом записи")
                        .isEqualTo(CROSS_REPOSITORY_PACKAGE));
    }

    @Test
    @DisplayName("Свои читатели журнала ходят подключением ВЛАДЕЛЬЦА: кросс-подключения у них нет")
    void theOwnReadersUseTheOwnerConnection() {
        assertThat(declaredFieldTypes(JournalReadService.class))
                .contains(OwnerJournalCompletenessSource.class)
                .doesNotContain(StatisticsJournalCompletenessSource.class);
        assertThat(declaredFieldTypes(JournalCleanupService.class))
                .contains(OwnerJournalCompletenessSource.class)
                .doesNotContain(StatisticsJournalCompletenessSource.class);
        assertThat(reachableRepositories(OwnerJournalCompletenessSource.class))
                .isNotEmpty()
                .allSatisfy(repository -> assertThat(repository.getPackageName())
                        .isEqualTo(OWNER_REPOSITORY_PACKAGE));
    }

    @Test
    @DisplayName("У читателя чужой базы нет ни одного пишущего метода: он не наследует CRUD")
    void theCrossReaderInheritsNoWritingMethod() {
        assertThat(CrudRepository.class.isAssignableFrom(ReceptionStateSourceRepository.class))
                .as("унаследованный save завёл бы в коде тропу записи, которую отвергала бы база")
                .isFalse();
    }

    /**
     * Текст трёх запросов живёт ОДИН раз, а читателей у него два: мутация
     * запроса обязана ронять пробы обоих сразу, иначе живой прогон мерил бы
     * только ту копию, которую взял.
     */
    @Test
    @DisplayName("Оба читателя операндов полноты наследуют ОДНИ объявления запросов")
    void bothReadersInheritTheSameQueryDeclarations() {
        assertThat(ReceptionStateCompletenessQueries.class.isAssignableFrom(JournalReceptionStateRepository.class))
                .isTrue();
        assertThat(ReceptionStateCompletenessQueries.class.isAssignableFrom(ReceptionStateSourceRepository.class))
                .isTrue();
        assertThat(Repository.class.isAssignableFrom(ReceptionStateCompletenessQueries.class))
                .as("родитель, унаследовавший Repository, стал бы бином и попал бы под чужое сканирование")
                .isFalse();
    }

    /**
     * Область объявлений репозиториев родитель не расширяет: он лежит в
     * пакете-родителе трёх сканируемых, а тот не сканируется ни одним.
     */
    @Test
    @DisplayName("Пакет общих объявлений не попадает ни под одно сканирование репозиториев")
    void theSharedPackageIsScannedByNobody() {
        String shared = ReceptionStateCompletenessQueries.class.getPackageName();

        assertThat(CROSS_REPOSITORY_PACKAGE).startsWith(shared + ".");
        assertThat(OWNER_REPOSITORY_PACKAGE).startsWith(shared + ".");
        assertThat(scannedPackages())
                .allSatisfy(scanned -> assertThat(shared.startsWith(scanned))
                        .as("область, накрывшая пакет общих объявлений, сделала бы родителя бином")
                        .isFalse());
    }

    /** Свёртка полноты своих полей не держит: операнды приходят источником. */
    @Test
    @DisplayName("Свёртка полноты не внедряет ни одного источника: иначе он был бы один на всех читателей")
    void theFoldInjectsNoSource() {
        assertThat(JournalCompletenessService.class.getDeclaredFields())
                .as("внедрённый источник пришлось бы различать квалификатором, а не типом")
                .isEmpty();
    }

    private List<String> scannedPackages() {
        return Stream.of(JournalPersistenceConfig.class,
                        AggregatesPersistenceConfig.class,
                        JournalReadPersistenceConfig.class)
                .map(configuration -> configuration.getAnnotation(EnableJpaRepositories.class).basePackages())
                .flatMap(Arrays::stream)
                .collect(toList());
    }

    private Set<Class<?>> declaredFieldTypes(Class<?> owner) {
        return Arrays.stream(owner.getDeclaredFields())
                .map(Field::getType)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private Set<Class<?>> reachableRepositories(Class<?> root) {
        Set<Class<?>> repositories = new LinkedHashSet<>();
        Set<Class<?>> visited = new LinkedHashSet<>();
        collect(root, repositories, visited);
        return repositories;
    }

    private void collect(Class<?> type, Set<Class<?>> repositories, Set<Class<?>> visited) {
        if (isFalse(visited.add(type))
                || isFalse(type.getPackageName().startsWith("com.example.auditstatistics"))) {
            return;
        }
        if (Repository.class.isAssignableFrom(type)) {
            repositories.add(type);
            return;
        }
        Arrays.stream(type.getDeclaredFields())
                .map(Field::getType)
                .forEach(fieldType -> collect(fieldType, repositories, visited));
    }
}
