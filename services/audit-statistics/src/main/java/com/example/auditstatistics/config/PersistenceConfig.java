package com.example.auditstatistics.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Три подключения к базам сервиса
 * (docs/architecture/data-ownership.md §Раскладка: «Отсюда подключений к
 * базам у процесса три, а не два»).
 *
 * <ul>
 *   <li>{@link #JOURNAL_DATA_SOURCE} — своя база модуля аудита под ролью
 *       журнала: единственный писатель журнала;</li>
 *   <li>{@link #AGGREGATES_DATA_SOURCE} — своя база модуля статистики под
 *       ролью агрегатов;</li>
 *   <li>{@link #JOURNAL_READ_DATA_SOURCE} — <b>чужая</b> база журнала под
 *       ролью агрегатов: ею читают журнал джоба пересчёта и агрегатная
 *       выборка, и запись через неё отвергает <b>база</b>, а не дисциплина
 *       автора (docs/spec/owner-role-grants.json, {@code
 *       writeConfinedToOwner}).</li>
 * </ul>
 *
 * <p><b>Ни один из трёх не помечен основным, и это не забывчивость.</b>
 * {@code @Primary} назначил бы умолчание там, где выбор подключения и есть
 * предмет инварианта: неквалифицированное внедрение получало бы чужую
 * тропу молча. Без него такое внедрение <b>роняет контекст</b> — то есть
 * названный домом остаток («какое из подключений внедрено в читателя —
 * выбор автора кода») закрыт настолько, насколько это выразимо сборкой, а
 * не чтением.
 *
 * <p><b>Размер пула у каждого — величина конфигурации, а не умолчание
 * библиотеки.</b> Подключения собираются здесь явно, минуя
 * {@code spring.datasource}, и свойства {@code spring.datasource.hikari.*}
 * этими бинами не читаются вовсе: без собственной формы перекалибровка
 * потребовала бы сборки. Форма — на подключение, потому что и нагрузка у
 * трёх разная ({@code PersistenceProperties}).
 *
 * <p><b>Названное ограничение:</b> сборка требует квалификатор, но не
 * проверяет, что он <b>верный</b>. Верность закрывает проба над
 * объявлением читателя ({@code AggregateReaderWiringTest}); почему она над
 * объявлением, а не над поднятым контекстом, — в доме раскладки
 * (docs/architecture/data-ownership.md §«Чего грант НЕ охраняет»).
 */
@Configuration
@EnableConfigurationProperties(PersistenceProperties.class)
public class PersistenceConfig {

    /** Своя база модуля аудита под ролью-владельцем журнала. */
    public static final String JOURNAL_DATA_SOURCE = "journalDataSource";

    /** Своя база модуля статистики под ролью-владельцем агрегатов. */
    public static final String AGGREGATES_DATA_SOURCE = "aggregatesDataSource";

    /** База журнала под ролью агрегатов — только чтение. */
    public static final String JOURNAL_READ_DATA_SOURCE = "journalReadDataSource";

    @Bean(JOURNAL_DATA_SOURCE)
    public DataSource journalDataSource(PersistenceProperties properties) {
        return connect(properties.getJournal());
    }

    @Bean(AGGREGATES_DATA_SOURCE)
    public DataSource aggregatesDataSource(PersistenceProperties properties) {
        return connect(properties.getAggregates());
    }

    /**
     * Кросс-подключение: <b>база журнала</b> с <b>учётными данными роли
     * агрегатов</b>.
     *
     * <p>Обе половины взяты из уже настроенных комплектов, а не из
     * третьего: тогда «читаем журнал под своей ролью» держится
     * построением, а не совпадением двух настроек. Ошибка в конфигурации
     * при этом остаётся видимой — она сдвинет и своё подключение владельца
     * тоже, а не создаст расхождение между двумя адресами одной базы.
     *
     * <p><b>Первый потребитель у подключения появился с джобой
     * пересчёта</b> — она читает журнал под ролью агрегатов и своим
     * отображением схемы ({@link JournalReadPersistenceConfig}). Второй,
     * агрегатная выборка, приедет своим компонентом шага и возьмёт то же
     * подключение: тропа к чужой базе у модуля статистики одна.
     */
    @Bean(JOURNAL_READ_DATA_SOURCE)
    public DataSource journalReadDataSource(PersistenceProperties properties) {
        return pooled(properties.getJournal().getUrl(),
                properties.getAggregates().getUsername(),
                properties.getAggregates().getPassword(),
                properties.getJournalReadMaxPoolSize());
    }

    private DataSource connect(DatabaseConnectionProperties connection) {
        return pooled(connection.getUrl(), connection.getUsername(), connection.getPassword(),
                connection.getMaxPoolSize());
    }

    /**
     * Подключение с <b>назначенной</b> верхней границей пула.
     *
     * <p><b>Тип пула назван явно, а не выведен из дерева зависимостей.</b>
     * Размер назначается только на конкретной реализации, а сборщик
     * {@code DataSourceBuilder} без указания типа отдаёт то, что нашёл в
     * classpath: величина конфигурации тогда применялась бы или нет в
     * зависимости от набора зависимостей — то есть молча.
     */
    private DataSource pooled(String url, String username, String password, Integer maxPoolSize) {
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .build();
        dataSource.setMaximumPoolSize(maxPoolSize);
        return dataSource;
    }
}
