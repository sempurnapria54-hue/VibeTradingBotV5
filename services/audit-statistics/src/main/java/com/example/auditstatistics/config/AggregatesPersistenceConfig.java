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
 * Отображение схемы базы агрегатов на классы и его менеджер транзакций.
 *
 * <p><b>Второе отображение у процесса, и приехало оно с первым писателем
 * агрегатов</b> — джобой пересчёта. До неё фабрика, которой нечего
 * отображать, была бы настройкой без предмета; довод и его условие
 * возврата стоя́ли в {@link JournalPersistenceConfig} с захода слушателя
 * приёма.
 *
 * <p><b>Подключение — своё, под ролью-владельцем агрегатов</b>
 * ({@link PersistenceConfig#AGGREGATES_DATA_SOURCE}). Тропа к базе журнала
 * здесь не участвует вовсе: она отдельное подключение и отдельное
 * отображение ({@link JournalReadPersistenceConfig}), потому что
 * кросс-базового запроса не бывает (docs/architecture/data-ownership.md
 * §Раскладка).
 *
 * <p><b>Схему ведёт цепочка миграций, а не Hibernate:</b> генерация DDL
 * выключена умолчанием адаптера и здесь не включается — иначе у схемы
 * появился бы второй писатель.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = AggregatesPersistenceConfig.REPOSITORY_PACKAGE,
        entityManagerFactoryRef = AggregatesPersistenceConfig.AGGREGATES_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = AggregatesPersistenceConfig.AGGREGATES_TRANSACTION_MANAGER)
public class AggregatesPersistenceConfig {

    /** Фабрика сущностей базы агрегатов. */
    public static final String AGGREGATES_ENTITY_MANAGER_FACTORY = "aggregatesEntityManagerFactory";

    /** Менеджер транзакций базы агрегатов. */
    public static final String AGGREGATES_TRANSACTION_MANAGER = "aggregatesTransactionManager";

    /** Пакет отображаемых классов: схему агрегатов держит модуль статистики. */
    static final String ENTITY_PACKAGE = "com.example.auditstatistics.persistence.model.aggregates";

    /** Пакет репозиториев владельца агрегатов. */
    static final String REPOSITORY_PACKAGE = "com.example.auditstatistics.persistence.repository.aggregates";

    @Bean(AGGREGATES_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean aggregatesEntityManagerFactory(
            @Qualifier(PersistenceConfig.AGGREGATES_DATA_SOURCE) DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(ENTITY_PACKAGE);
        factory.setPersistenceUnitName("aggregates");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return factory;
    }

    @Bean(AGGREGATES_TRANSACTION_MANAGER)
    public PlatformTransactionManager aggregatesTransactionManager(
            @Qualifier(AGGREGATES_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
