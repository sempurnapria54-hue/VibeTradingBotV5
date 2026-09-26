package com.example.tests.e2e;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;

/**
 * База одной стороны тропы и чтение её колонками.
 *
 * <p><b>Только чтение.</b> Состояние сторон ставится ходами тропы, а не
 * записью в базу: состояние, поставленное рукой, проверяло бы не стык, а
 * собственную расстановку (.claude/tests/cases/e2e-strategy-to-deal.md
 * §«Новая ось формы — СТОРОНА ТРОПЫ И ЕЁ СЛЕД»). Ассерт колонками законен
 * у таблицы, у которой есть внешний читатель, и у той, у которой
 * поверхности нет вовсе (решение 7 контура).
 *
 * <p><b>Исключение одно — неисправность субстрата, а не состояние:</b>
 * {@link #refuseInserts} ставит отказ вставки в таблицу стороны. Строк он
 * не пишет и хода тропы не подменяет — он делает базу неисправной ровно так,
 * как её сделал бы отказ носителя в проде, и кейс проверяет, что сторона
 * делает с отказом.
 *
 * @param name     имя базы
 * @param url      адрес JDBC
 * @param username пользователь
 * @param password пароль
 */
public record Database(String name, String url, String username, String password) {

    /** Число строк таблицы; таблицы, которой ещё нет, — отказ, а не ноль. */
    public Long count(String table) {
        return ((Number) query("select count(*) as rows from " + table).getFirst().get("rows")).longValue();
    }

    /** Есть ли у стороны такая таблица вовсе. */
    public Boolean hasTable(String table) {
        return isFalse(query("select 1 as present from information_schema.tables where table_name = ?", table)
                .isEmpty());
    }

    /** Все строки таблицы в порядке первичного ключа. */
    public List<Map<String, Object>> all(String table) {
        return query("select * from " + table + " order by 1");
    }

    /**
     * База отвергает вставку в таблицу строк, отвечающих условию: триггер
     * перед вставкой бросает исключение. Пустое условие — отказ любой строки.
     *
     * @param table     таблица стороны
     * @param condition условие над {@code new} либо пусто
     */
    public void refuseInserts(String table, String condition) {
        String when = isNull(condition) ? "" : " when (" + condition + ")";
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("create or replace function trail_refuse_insert() returns trigger language plpgsql "
                    + "as $$ begin raise exception 'insert refused by the trail'; end $$");
            statement.execute("create trigger trail_refuse_insert before insert on " + table + " for each row"
                    + when + " execute function trail_refuse_insert()");
        } catch (SQLException failure) {
            throw new IllegalStateException("Отказ вставки в " + name + "." + table + " не поставлен", failure);
        }
    }

    /**
     * Строки выборки колонками.
     *
     * @param sql        запрос
     * @param parameters позиционные параметры
     * @return строки: имя колонки → значение
     */
    public List<Map<String, Object>> query(String sql, Object... parameters) {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData columns = result.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= columns.getColumnCount(); column++) {
                        row.put(columns.getColumnLabel(column), result.getObject(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Чтение базы " + name + " не удалось: " + sql, failure);
        }
    }
}
