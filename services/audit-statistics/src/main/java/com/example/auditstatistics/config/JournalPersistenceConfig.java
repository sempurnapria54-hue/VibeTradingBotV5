package com.example.auditstatistics.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Отображение схемы базы журнала на классы и его менеджер транзакций.
 *
 * <p><b>Почему объявлено явно, а не автоконфигурацией.</b> Автоконфигурация
 * JPA держится на {@code @ConditionalOnSingleCandidate(DataSource.class)}, а
 * подключений у процесса три (docs/architecture/data-ownership.md
 * §Раскладка) — при трёх кандидатах и без основного она отступает целиком.
 * Это не обход умолчания, а его следствие: {@code @Primary} здесь запрещён
 * ровно потому, что выбор подключения и есть предмет инварианта
 * ({@link PersistenceConfig}).
 *
 * <p><b>Отображений схемы на классы у процесса три, и это следствие
 * раскладки, а не вкуса.</b> Здесь — своё отображение владельца журнала;
 * соседние два завёл заход джобы пересчёта: база агрегатов под своей ролью
 * ({@link AggregatesPersistenceConfig}) и <b>та же</b> схема журнала под
 * ролью агрегатов ({@link JournalReadPersistenceConfig}). Классы сущностей
 * журнала у первого и третьего общие — расходится подключение, а не
 * отображение.
 *
 * <p><b>Пакеты названы константами, а не строкой в аннотации.</b> Три
 * объявления {@code @EnableJpaRepositories} обязаны делить репозитории
 * <b>без пересечения</b>: репозиторий, попавший в область двух объявлений,
 * получил бы фабрику того, чьё объявление обработано последним, — молча и
 * с чужим подключением.
 *
 * <p><b>Менеджер транзакций тоже не помечен основным.</b> Пока он один,
 * неквалифицированный {@code @Transactional} взял бы его молча — и на
 * появлении второго сменил бы поведение существующего кода, не тронув ни
 * строки. Поэтому квалификатор стои́т у каждого транзакционного метода
 * сервиса, а не подразумевается.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = JournalPersistenceConfig.REPOSITORY_PACKAGE,
        entityManagerFactoryRef = JournalPersistenceConfig.JOURNAL_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
public class JournalPersistenceConfig {

    /** Фабрика сущностей базы журнала. */
    public static final String JOURNAL_ENTITY_MANAGER_FACTORY = "journalEntityManagerFactory";

    /** Менеджер транзакций базы журнала. */
    public static final String JOURNAL_TRANSACTION_MANAGER = "journalTransactionManager";

    /** Пакет отображаемых классов: схему журнала держит модуль аудита. */
    static final String ENTITY_PACKAGE = "com.example.auditstatistics.persistence.model.journal";

    /** Пакет репозиториев владельца журнала. */
    static final String REPOSITORY_PACKAGE = "com.example.auditstatistics.persistence.repository.journal";

    /**
     * Фабрика сущностей поверх подключения владельца журнала.
     *
     * <p>Схему ведёт цепочка миграций, а не Hibernate: генерация DDL
     * выключена умолчанием адаптера и здесь не включается — иначе у схемы
     * появился бы второй писатель.
     */
    @Bean(JOURNAL_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean journalEntityManagerFactory(
            @Qualifier(PersistenceConfig.JOURNAL_DATA_SOURCE) DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(ENTITY_PACKAGE);
        factory.setPersistenceUnitName("journal");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return factory;
    }

    @Bean(JOURNAL_TRANSACTION_MANAGER)
    public PlatformTransactionManager journalTransactionManager(
            @Qualifier(JOURNAL_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
