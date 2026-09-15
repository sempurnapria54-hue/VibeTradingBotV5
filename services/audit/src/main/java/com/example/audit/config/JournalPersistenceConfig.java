package com.example.audit.config;

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
 * <p><b>Почему объявлено явно, а не автоконфигурацией.</b> Подключение
 * собирается своей формой ({@link PersistenceConfig}), а не
 * {@code spring.datasource}, и {@code @Primary} на нём не стои́т: выбор
 * подключения и есть предмет инварианта «один пишущий». Автоконфигурация
 * JPA при таком объявлении отступает, и отображение называется здесь.
 *
 * <p><b>Отображение у процесса ОДНО, и это следствие раскладки, а не
 * вкуса:</b> владелец данных у сервиса один. Прежде отображений было три —
 * своё, база агрегатов под своей ролью и та же схема журнала под ролью
 * агрегатов; раздел сервиса снял обе соседние конструкции вместе с их
 * предметом (.claude/decisions/audit-statistics-split.md).
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
        basePackages = JournalPersistenceConfig.REPOSITORY_PACKAGE,
        entityManagerFactoryRef = JournalPersistenceConfig.JOURNAL_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = JournalPersistenceConfig.JOURNAL_TRANSACTION_MANAGER)
public class JournalPersistenceConfig {

    /** Фабрика сущностей базы журнала. */
    public static final String JOURNAL_ENTITY_MANAGER_FACTORY = "journalEntityManagerFactory";

    /** Менеджер транзакций базы журнала. */
    public static final String JOURNAL_TRANSACTION_MANAGER = "journalTransactionManager";

    /** Пакет отображаемых классов: схему журнала держит модуль аудита. */
    static final String ENTITY_PACKAGE = "com.example.audit.persistence.model.journal";

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

    /** Пакет репозиториев владельца журнала. */
    static final String REPOSITORY_PACKAGE = "com.example.audit.persistence.repository.journal";

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
        factory.setPackagesToScan(ENTITY_PACKAGE, SHARED_ENTITY_PACKAGE);
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
