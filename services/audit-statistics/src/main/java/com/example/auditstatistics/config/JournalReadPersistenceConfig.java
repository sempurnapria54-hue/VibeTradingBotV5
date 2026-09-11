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
 * Отображение схемы базы ЖУРНАЛА на классы — под ролью АГРЕГАТОВ
 * (docs/architecture/data-ownership.md §Раскладка: «Отсюда подключений к
 * базам у процесса три, а не два»).
 *
 * <p><b>Классы сущностей — те же, что у владельца журнала</b>
 * ({@link JournalPersistenceConfig#ENTITY_PACKAGE}): схема одна, и второе
 * её описание разошлось бы с первым молча. Расходится не отображение, а
 * <b>подключение</b> — и вместе с ним права: через него база отвергает
 * запись сама, а не дисциплина автора
 * (docs/spec/owner-role-grants.json, {@code writeConfinedToOwner}).
 *
 * <p><b>Репозитории у этой фабрики лежат в СВОЁМ пакете</b> и наследуют
 * {@code Repository}, а не {@code JpaRepository}: у читателя чужой базы
 * пишущего метода не бывает вовсе.
 *
 * <p><b>Почему отображение, а не второй набор нативных запросов у
 * владельца.</b> Тропа модуля статистики к журналу объявлена читающей
 * грантом, и ходить по ней обязано именно его подключение; запросы,
 * положенные к владельцу журнала, шли бы под ролью журнала — то есть
 * грант перестал бы что-либо держать, оставшись выданным.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = JournalReadPersistenceConfig.REPOSITORY_PACKAGE,
        entityManagerFactoryRef = JournalReadPersistenceConfig.JOURNAL_READ_ENTITY_MANAGER_FACTORY,
        transactionManagerRef = JournalReadPersistenceConfig.JOURNAL_READ_TRANSACTION_MANAGER)
public class JournalReadPersistenceConfig {

    /** Фабрика сущностей журнала на кросс-подключении. */
    public static final String JOURNAL_READ_ENTITY_MANAGER_FACTORY = "journalReadEntityManagerFactory";

    /** Менеджер транзакций кросс-подключения. */
    public static final String JOURNAL_READ_TRANSACTION_MANAGER = "journalReadTransactionManager";

    /** Пакет репозиториев-читателей журнала под ролью агрегатов. */
    static final String REPOSITORY_PACKAGE = "com.example.auditstatistics.persistence.repository.journalread";

    @Bean(JOURNAL_READ_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean journalReadEntityManagerFactory(
            @Qualifier(PersistenceConfig.JOURNAL_READ_DATA_SOURCE) DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(JournalPersistenceConfig.ENTITY_PACKAGE);
        factory.setPersistenceUnitName("journal-read");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return factory;
    }

    @Bean(JOURNAL_READ_TRANSACTION_MANAGER)
    public PlatformTransactionManager journalReadTransactionManager(
            @Qualifier(JOURNAL_READ_ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
