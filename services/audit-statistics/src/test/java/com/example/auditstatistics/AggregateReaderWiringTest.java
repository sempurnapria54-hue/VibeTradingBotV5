package com.example.auditstatistics;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auditstatistics.config.AggregatesPersistenceConfig;
import com.example.auditstatistics.config.JournalPersistenceConfig;
import com.example.auditstatistics.config.JournalReadPersistenceConfig;
import com.example.auditstatistics.config.PersistenceConfig;
import com.example.auditstatistics.persistence.repository.journalread.JournalAggregateSourceRepository;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.CrudRepository;

/**
 * Читатель журнала ходит КРОСС-ПОДКЛЮЧЕНИЕМ — под ролью агрегатов, а не
 * подключением владельца журнала (docs/architecture/data-ownership.md
 * §Раскладка, «Чего грант НЕ охраняет»).
 *
 * <p><b>Почему без этой пробы охраны нет вовсе.</b> Грант отвергает запись
 * через подключение роли статистики, но какое из трёх подключений внедрено
 * в читателя, грантом не решается. Фабрика, объявленная на подключении
 * владельца журнала, поднимает контекст без единой жалобы и читает журнал
 * под ролью, у которой на нём есть и запись, — то есть инвариант «один
 * пишущий» держался бы дисциплиной автора, а не построением. Ни старт
 * сервиса, ни живой прогон запросов такой подмены не замечают: читается
 * та же база и те же строки.
 *
 * <p><b>Что проба НЕ мерит, и это названо.</b> Она читает ОБЪЯВЛЕНИЕ, а не
 * поднятый контекст: фабрика сущностей на старте лезет к базе за
 * метаданными, и контекстной пробы у неё без живой базы не бывает. Мерится
 * ровно тот выбор, который подменой и портится, — квалификатор источника
 * данных у каждой из трёх фабрик.
 *
 * <p><b>Непересечение областей репозиториев мерится здесь же.</b> Три
 * объявления {@code @EnableJpaRepositories} делят репозитории по пакетам, а
 * область пакета включает вложенные: пакет, оказавшийся префиксом чужого,
 * отдал бы чужие репозитории своей фабрике — молча и с чужим подключением.
 */
class AggregateReaderWiringTest {

    @Test
    @DisplayName("Фабрика читателя журнала объявлена на КРОСС-подключении, а не на подключении владельца")
    void theJournalReaderIsDeclaredOnTheCrossConnection() {
        assertThat(dataSourceQualifier(JournalReadPersistenceConfig.class, "journalReadEntityManagerFactory"))
                .as("читатель обязан ходить под ролью агрегатов: запись тогда отвергает база, а не автор")
                .isEqualTo(PersistenceConfig.JOURNAL_READ_DATA_SOURCE);
    }

    @Test
    @DisplayName("Каждая из трёх фабрик объявлена на СВОЁМ подключении")
    void everyFactoryIsDeclaredOnItsOwnConnection() {
        assertThat(dataSourceQualifier(JournalPersistenceConfig.class, "journalEntityManagerFactory"))
                .isEqualTo(PersistenceConfig.JOURNAL_DATA_SOURCE);
        assertThat(dataSourceQualifier(AggregatesPersistenceConfig.class, "aggregatesEntityManagerFactory"))
                .isEqualTo(PersistenceConfig.AGGREGATES_DATA_SOURCE);
        assertThat(dataSourceQualifier(JournalReadPersistenceConfig.class, "journalReadEntityManagerFactory"))
                .isEqualTo(PersistenceConfig.JOURNAL_READ_DATA_SOURCE);
    }

    @Test
    @DisplayName("Области трёх объявлений репозиториев не пересекаются: ни один пакет не префикс другого")
    void theRepositoryScopesDoNotOverlap() {
        List<String> packages = List.of(repositoryPackage(JournalPersistenceConfig.class),
                repositoryPackage(AggregatesPersistenceConfig.class),
                repositoryPackage(JournalReadPersistenceConfig.class));

        assertThat(packages).doesNotHaveDuplicates();
        packages.forEach(scope -> assertThat(packages.stream()
                .filter(other -> isFalse(Objects.equals(other, scope)))
                .filter(other -> other.startsWith(scope + ".")))
                .as("пакет-префикс отдал бы чужие репозитории своей фабрике")
                .isEmpty());
    }

    @Test
    @DisplayName("У читателя чужой базы нет ни одного пишущего метода: он не наследует CRUD")
    void theCrossReaderInheritsNoWritingMethod() {
        assertThat(CrudRepository.class.isAssignableFrom(JournalAggregateSourceRepository.class))
                .as("унаследованный save завёл бы в коде тропу записи, которую отвергала бы база")
                .isFalse();
    }

    private String dataSourceQualifier(Class<?> configuration, String beanMethod) {
        Method method = Arrays.stream(configuration.getDeclaredMethods())
                .filter(candidate -> Objects.equals(candidate.getName(), beanMethod))
                .findFirst()
                .orElseThrow();
        return method.getParameters()[0].getAnnotation(Qualifier.class).value();
    }

    private String repositoryPackage(Class<?> configuration) {
        return configuration.getAnnotation(EnableJpaRepositories.class).basePackages()[0];
    }
}
