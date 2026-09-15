package com.example.statistics.config;

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
     * Пакет общего базового типа audit-полей.
     *
     * <p><b>Назван рядом со своим, а не подразумевается.</b> Базовый тип
     * лежит в общем артефакте — состав колонок бинарен, и единственный
     * носитель его и держит (docs/models/domain/other/Auditable.md
     * §«Правило состава колонок»), а область сканирования здесь
     * объявлена явно и чужих пакетов не видит.
     *
     * <p><b>Столкновения с соседним отображением он не создаёт:</b>
     * сущностей в нём нет вовсе — только {@code @MappedSuperclass}, и разделение
     * репозиториев между отображениями он не трогает.
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
     */
    @Bean(STATISTICS_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean statisticsEntityManagerFactory(
            @Qualifier(PersistenceConfig.STATISTICS_DATA_SOURCE) DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(ENTITY_PACKAGE, SHARED_ENTITY_PACKAGE);
        factory.setPersistenceUnitName("statistics");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return factory;
    }

    @Bean(STATISTICS_TRANSACTION_MANAGER)
    public PlatformTransactionManager statisticsTransactionManager(
            @Qualifier(STATISTICS_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
