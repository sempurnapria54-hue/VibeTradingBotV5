package com.example.statistics.config;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.Action;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Отображение схемы базы статистики на классы и его менеджер транзакций.
 *
 * <p><b>Почему объявлено явно, а не автоконфигурацией.</b> Подключение
 * собирается своей формой ({@link PersistenceConfig}), а не
 * {@code spring.datasource}, и {@code @Primary} на нём не стои́т: выбор
 * подключения и есть предмет инварианта «один пишущий». Автоконфигурация
 * JPA при таком объявлении отступает, и отображение называется здесь.
 *
 * <p><b>Отображение у процесса ОДНО, и это следствие раскладки, а не
 * вкуса:</b> владелец данных у сервиса один — факты и агрегаты лежат в
 * одной базе, чужого подключения конструкция не оставляет ни одного
 * (.claude/decisions/audit-statistics-split.md). Прежде отображений было
 * три: база агрегатов под своей ролью и та же схема журнала под ролью
 * агрегатов; раздел сервиса снял обе конструкции вместе с их предметом.
 *
 * <p><b>Пакеты названы константами, а не строкой в аннотации.</b> Форма
 * пережила снятие соседних объявлений намеренно: второе
 * {@code @EnableJpaRepositories}, заведённое строкой, поделило бы
 * репозитории с пересечением — попавший в область двух объявлений получил
 * бы фабрику того, чьё объявление обработано последним, молча и с чужим
 * подключением.
 *
 * <p><b>Менеджер транзакций не помечен основным.</b> Пока он один,
 * неквалифицированный {@code @Transactional} взял бы его молча — и на
 * появлении второго сменил бы поведение существующего кода, не тронув ни
 * строки. Поэтому квалификатор стои́т у каждого транзакционного метода
 * сервиса, а не подразумевается.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = StatisticsPersistenceConfig.REPOSITORY_PACKAGE,
        entityManagerFactoryRef = StatisticsPersistenceConfig.STATISTICS_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = StatisticsPersistenceConfig.STATISTICS_TRANSACTION_MANAGER)
public class StatisticsPersistenceConfig {

    /** Фабрика сущностей базы статистики. */
    public static final String STATISTICS_ENTITY_MANAGER_FACTORY = "statisticsEntityManagerFactory";

    /** Менеджер транзакций базы статистики. */
    public static final String STATISTICS_TRANSACTION_MANAGER = "statisticsTransactionManager";

    /** Пакет отображаемых классов: схему статистики держит этот модуль. */
    static final String ENTITY_PACKAGE = "com.example.statistics.persistence.model";

    /**
     * Пакет общих persistence-типов: базовый тип audit-полей и строка
     * отказа доступа.
     *
     * <p><b>Назван рядом со своим, а не подразумевается.</b> Базовый тип
     * лежит в общем артефакте — состав колонок бинарен, и единственный
     * носитель его и держит (docs/models/domain/other/Auditable.md
     * §«Правило состава колонок»), а область сканирования здесь
     * объявлена явно и чужих пакетов не видит.
     *
     * <p><b>Столкновения с соседним отображением он не создаёт:</b>
     * отображение у процесса одно, а сущность в пакете одна — строка отказа
     * доступа, чья форма общая у всех сервисов с базой
     * (docs/models/domain/other/AccessDenial.md §Персистентность); её
     * репозиторий лежит в пакете репозиториев сервиса, и разделение
     * репозиториев между отображениями пакет не трогает.
     */
    static final String SHARED_ENTITY_PACKAGE = "com.example.tradingbot.persistence.model";

    /** Пакет репозиториев владельца данных сервиса. */
    static final String REPOSITORY_PACKAGE = "com.example.statistics.persistence.repository";

    /**
     * Фабрика сущностей поверх подключения владельца данных.
     *
     * <p>Схему ведёт цепочка миграций, а не Hibernate: генерация DDL
     * выключена умолчанием адаптера и здесь не включается — иначе у схемы
     * появился бы второй писатель.
     *
     * <p><b>Сверка отображения со схемой — на подъёме, и носитель её здесь,
     * а не в {@code application.yaml}.</b> Ключ {@code spring.jpa.hibernate.ddl-auto}
     * читает автоконфигурация JPA, а она при собственном объявлении фабрики
     * отступает; поэтому режим {@code validate} — тот же, что у сервисов с
     * автоконфигурацией, — назван свойством самой фабрики
     * ({@code hibernate.hbm2ddl.auto}). Он схему не пишет, а только сверяет:
     * колонка, объявленная отображением и не заведённая цепочкой, роняет
     * подъём, а не первую тропу, которая её коснётся.
     *
     * <p><b>Подъём — после наката цепочки, и порядок назван, а не
     * подразумевается.</b> Бин миграций объявлен своей формой, мимо
     * автоконфигурации Flyway, и её упорядочение фабрик сущностей после
     * миграций на него не распространяется: без {@code @DependsOn} сверка
     * на чистой базе шла бы раньше наката и роняла подъём отсутствием таблиц.
     */
    @Bean(STATISTICS_ENTITY_MANAGER_FACTORY)
    @DependsOn(SchemaMigrationConfig.STATISTICS_MIGRATION)
    public LocalContainerEntityManagerFactoryBean statisticsEntityManagerFactory(
            @Qualifier(PersistenceConfig.STATISTICS_DATA_SOURCE) DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(ENTITY_PACKAGE, SHARED_ENTITY_PACKAGE);
        factory.setPersistenceUnitName("statistics");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(AvailableSettings.HBM2DDL_AUTO, Action.VALIDATE));
        return factory;
    }

    @Bean(STATISTICS_TRANSACTION_MANAGER)
    public PlatformTransactionManager statisticsTransactionManager(
            @Qualifier(STATISTICS_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
