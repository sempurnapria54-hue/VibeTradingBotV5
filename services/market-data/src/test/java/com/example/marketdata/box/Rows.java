package com.example.marketdata.box;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Наблюдение строк базы СНАРУЖИ ящика: своим соединением к контейнеру, а
 * не бином сервиса.
 *
 * <p><b>Почему прямой ассерт по базе законен здесь.</b> Решение 7 требует
 * читать через поверхность там, где поверхность есть, и у рядов
 * market-data она есть — каталог, единицы сбора, история, срезы, фичи. Но
 * часть предмета поверхности не имеет вовсе: статус единицы сбора,
 * границы загруженного, сдвиг горизонта, инварианты схемы, отрицания
 * «строк не прибавилось». Документ кейсов называет это ценой, а не
 * умолчанием (.claude/tests/cases/market-data.md §«Чем достаются выходы»).
 *
 * <p><b>Соединение своё, а не DataSource контекста:</b> ящик смотрит на
 * субстрат снаружи, и бина сервиса для этого не берёт.
 *
 * <p><b>Опустошение между клетками идёт БЕЗ сброса идентичности.</b>
 * Числовые ключи продолжают расти, и это несущее: часть состояния тиков
 * живёт в памяти процесса ключом группы (счётчик попыток докачки,
 * курсор обхода правил), а сброшенная последовательность выдала бы новой
 * группе ключ выбывшей — и клетка получила бы наследство соседки.
 */
final class Rows {

    /** Таблицы схемы в порядке, безопасном для опустошения одним ходом. */
    private static final String ALL_TABLES = String.join(", ",
            "candles", "candle_groups", "indicator_values", "market_price_levels", "market_structures",
            "market_structure_configs", "indicator_configs", "order_book_snapshots", "ticker_snapshots",
            "instruments");

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private Rows(PostgreSQLContainer container) {
        this.jdbcUrl = container.getJdbcUrl();
        this.username = container.getUsername();
        this.password = container.getPassword();
    }

    /** Наблюдатель строк общего субстрата. */
    static Rows shared() {
        return of(MarketDataSubstrate.database());
    }

    /** Наблюдатель строк названного контейнера. */
    static Rows of(PostgreSQLContainer container) {
        return new Rows(container);
    }

    /** Опустошает все таблицы схемы: вход каждой клетки — своё состояние. */
    void clear() {
        execute("truncate table " + ALL_TABLES + " cascade");
    }

    /**
     * Ставит состояние, писателя которому сервис не имеет.
     *
     * <p><b>Прямая запись в базу — цена, а не умолчание, и область у неё
     * узкая.</b> Предусловие ставится тропой ящика всюду, где тропа есть;
     * статусы {@code Instrument.CREATED} и {@code CandleGroup.DELETED}
     * тропы не имеют вовсе — их не пишет ни один исполнитель сервиса
     * (находки {@code F-3} и {@code F-7} документа кейсов), — и кейс о
     * них иначе не поставить.
     *
     * @param sql       запрос правки
     * @param arguments значения подстановки
     */
    void put(String sql, Object... arguments) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("База субстрата не приняла правку: " + sql, failure);
        }
    }

    /** Число строк таблицы. */
    Long count(String table) {
        return number("select count(*) from " + table);
    }

    /** Число строк таблицы, у которых колонка равна значению. */
    Long countWhere(String table, String column, Object value) {
        return number("select count(*) from " + table + " where " + column + " = ?", value);
    }

    /**
     * Одна строка по значению колонки; пустая карта, когда строки нет.
     *
     * @param table  таблица
     * @param column колонка отбора
     * @param value  значение отбора
     * @return колонки строки по именам
     */
    Map<String, Object> row(String table, String column, Object value) {
        List<Map<String, Object>> found = rows(
                "select * from " + table + " where " + column + " = ?", value);
        return found.isEmpty() ? Map.of() : found.getFirst();
    }

    /** Строки таблицы по значению колонки, упорядоченные по ключу. */
    List<Map<String, Object>> rowsWhere(String table, String column, Object value) {
        return rows("select * from " + table + " where " + column + " = ?", value);
    }

    /** Все строки таблицы. */
    List<Map<String, Object>> all(String table) {
        return rows("select * from " + table);
    }

    /** Все строки таблицы, упорядоченные названной колонкой по возрастанию. */
    List<Map<String, Object>> allOrderedBy(String table, String column) {
        return rows("select * from " + table + " order by " + column + " asc");
    }

    /** Имена таблиц схемы: ими наблюдается отсутствие таблиц outbox и фаз. */
    List<String> tableNames() {
        return rows("select table_name from information_schema.tables where table_schema = 'public'")
                .stream()
                .map(row -> String.valueOf(row.get("table_name")))
                .toList();
    }

    /** Имена гипертаблиц схемы: ими наблюдается форма хранения рядов. */
    List<String> hypertableNames() {
        return rows("select hypertable_name from timescaledb_information.hypertables").stream()
                .map(row -> String.valueOf(row.get("hypertable_name")))
                .toList();
    }

    /** Применённые версии миграций. */
    List<String> appliedMigrations() {
        return rows("select version from flyway_schema_history where success = true").stream()
                .map(row -> String.valueOf(row.get("version")))
                .toList();
    }

    /** Снимок числа строк всех таблиц схемы: вход отрицаний «состояние не менялось». */
    Map<String, Long> countsByTable() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : ALL_TABLES.split(", ")) {
            counts.put(table, count(table));
        }
        return counts;
    }

    private Long number(String sql, Object... arguments) {
        List<Map<String, Object>> found = rows(sql, arguments);
        return ((Number) found.getFirst().values().iterator().next()).longValue();
    }

    /**
     * Значение колонки; момент читается со СМЕЩЕНИЕМ, а не голым
     * таймстампом: колонки аудита объявлены {@code OffsetDateTime}
     * (шкала одна — UTC, docs/rules/time-utc.md), и без смещения кейс не
     * отличил бы записанный момент от сдвинутого.
     */
    private static Object value(ResultSet answer, Integer column) throws SQLException {
        if ("timestamptz".equals(answer.getMetaData().getColumnTypeName(column))) {
            return answer.getObject(column, OffsetDateTime.class);
        }
        return answer.getObject(column);
    }

    private void execute(String sql) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException failure) {
            throw new IllegalStateException("База субстрата не приняла команду: " + sql, failure);
        }
    }

    private List<Map<String, Object>> rows(String sql, Object... arguments) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            try (ResultSet answer = statement.executeQuery()) {
                List<Map<String, Object>> collected = new ArrayList<>();
                while (answer.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= answer.getMetaData().getColumnCount(); column++) {
                        row.put(answer.getMetaData().getColumnLabel(column), value(answer, column));
                    }
                    collected.add(row);
                }
                return collected;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("База субстрата не ответила: " + sql, failure);
        }
    }
}
