package com.example.auth.box;

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
 * читать через поверхность там, где у таблицы есть объявленный читатель
 * вне кода владельца. У таблиц `auth` его нет: реестр читает
 * {@code trading-core} через поверхность, а тенантов и членств не читает
 * никто. Поверхности же, отдающей число тенантов, у сервиса нет вовсе —
 * поэтому отрицания «строк не прибавилось» наблюдаются прямым чтением, и
 * документ кейсов называет это ценой, а не умолчанием
 * (.claude/tests/cases/auth.md §«Чем достаются выходы»).
 *
 * <p><b>Соединение своё, а не {@code DataSource} контекста:</b> ящик
 * смотрит на субстрат снаружи, и бина сервиса для этого не берёт.
 */
final class Rows {

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
        return of(AuthSubstrate.database());
    }

    /** Наблюдатель строк названного контейнера. */
    static Rows of(PostgreSQLContainer container) {
        return new Rows(container);
    }

    /** Число строк таблицы. */
    Long count(String table) {
        return number("select count(*) from " + table);
    }

    /** Число строк таблицы, у которых колонка равна значению. */
    Long countWhere(String table, String column, String value) {
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
    Map<String, Object> row(String table, String column, String value) {
        List<Map<String, Object>> found = rows(
                "select * from " + table + " where " + column + " = ?", value);
        return found.isEmpty() ? Map.of() : found.getFirst();
    }

    /** Строки таблицы по значению колонки. */
    List<Map<String, Object>> rowsWhere(String table, String column, String value) {
        return rows("select * from " + table + " where " + column + " = ?", value);
    }

    /** Все строки таблицы. */
    List<Map<String, Object>> all(String table) {
        return rows("select * from " + table);
    }

    /** Имена таблиц схемы: ими наблюдается отсутствие таблицы outbox. */
    List<String> tableNames() {
        return rows("select table_name from information_schema.tables where table_schema = 'public'")
                .stream()
                .map(row -> String.valueOf(row.get("table_name")))
                .toList();
    }

    /** Применённые версии миграций. */
    List<String> appliedMigrations() {
        return rows("select version from flyway_schema_history where success = true").stream()
                .map(row -> String.valueOf(row.get("version")))
                .toList();
    }

    private Long number(String sql, Object... arguments) {
        List<Map<String, Object>> found = rows(sql, arguments);
        return ((Number) found.getFirst().values().iterator().next()).longValue();
    }

    /**
     * Значение колонки; момент читается со СМЕЩЕНИЕМ, а не голым
     * таймстампом: колонки аудита объявлены {@code OffsetDateTime}
     * (шкала одна — UTC, {@code docs/rules/time-utc.md}), и без смещения
     * кейс не отличил бы записанный момент от сдвинутого.
     */
    private static Object value(ResultSet answer, Integer column) throws SQLException {
        if ("timestamptz".equals(answer.getMetaData().getColumnTypeName(column))) {
            return answer.getObject(column, OffsetDateTime.class);
        }
        return answer.getObject(column);
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
