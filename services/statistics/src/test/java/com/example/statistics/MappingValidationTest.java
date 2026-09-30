package com.example.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.statistics.config.SchemaMigrationConfig;
import com.example.statistics.config.StatisticsPersistenceConfig;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * Отображение сверяется со схемой на подъёме, и сверка идёт после наката
 * цепочки (.claude/tests/cases/statistics.md §«B12.8 — Схема накатывается
 * миграциями и сходится с отображением»).
 *
 * <p><b>Что тест закрывает.</b> Фабрика сущностей объявлена явно, и ключ
 * {@code ddl-auto} автоконфигурации её не достигает: режим сверки назван
 * свойством самой фабрики. Снятое когда-нибудь, оно вернуло бы умолчание
 * Hibernate — «ничего не делать» — молча: ящик поднимался бы так же, и его
 * клетка о схождении отображения со схемой потеряла бы носитель, оставаясь
 * зелёной.
 *
 * <p><b>Порядок подъёма пинится здесь же, а не только ящиком.</b> Ящик
 * поднимается на базе, уже накатанной предыдущим контекстом прогона, и
 * гонку «сверка раньше наката» наблюдал бы только первый контекст —
 * то есть порядком классов, а не предметом.
 *
 * <p><b>Что он НЕ мерит, и это названо.</b> Он не соединяется с базой:
 * фабрика не инициализируется, и самой сверки здесь нет. Её исход — подъём
 * контекста ящика на схеме, положенной цепочкой.
 */
class MappingValidationTest {

    private final StatisticsPersistenceConfig config = new StatisticsPersistenceConfig();

    @Test
    @DisplayName("Фабрика сущностей сверяет отображение со схемой и схему не пишет")
    void theEntityManagerFactoryValidatesTheMappingAndWritesNoSchema() {
        Object action = config.statisticsEntityManagerFactory(mock(DataSource.class))
                .getJpaPropertyMap()
                .get(AvailableSettings.HBM2DDL_AUTO);

        assertThat(action)
                .as("режим сверки — свойство самой фабрики: ключ автоконфигурации её не достигает")
                .isEqualTo(Action.VALIDATE);
    }

    @Test
    @DisplayName("Фабрика сущностей поднимается после наката цепочки")
    void theEntityManagerFactoryIsRaisedAfterTheChainIsMigrated() throws NoSuchMethodException {
        DependsOn order = StatisticsPersistenceConfig.class
                .getMethod("statisticsEntityManagerFactory", DataSource.class)
                .getAnnotation(DependsOn.class);
        Bean migration = SchemaMigrationConfig.class
                .getMethod("statisticsMigration", DataSource.class)
                .getAnnotation(Bean.class);

        assertThat(migration.name())
                .as("бин миграций носит имя, на которое ссылается порядок")
                .containsExactly(SchemaMigrationConfig.STATISTICS_MIGRATION);
        assertThat(order.value())
                .as("сверка на чистой базе раньше наката роняла бы подъём отсутствием таблиц")
                .containsExactly(SchemaMigrationConfig.STATISTICS_MIGRATION);
    }
}
