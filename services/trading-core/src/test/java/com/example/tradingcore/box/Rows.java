package com.example.tradingcore.box;

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
 * читать через поверхность там, где поверхность есть; у ядра поверхность
 * есть ровно у четырёх предметов — сделки, транши, торговое состояние
 * счёта, числа риск-аппетита, — и кейсы читают их поверхностью. Строки
 * исполнения, ступени пары «счёт, инструмент», outbox, inbox, отчёты
 * аномалий, проекции чужих реестров и ставки комиссии объявленного
 * читателя вне кода ядра не имеют вовсе, и документ кейсов называет
 * прямой ассерт их ценой, а не умолчанием
 * (.claude/tests/cases/trading-core.md §«Чем достаются выходы»).
 *
 * <p><b>Соединение своё, а не {@code DataSource} контекста:</b> ящик
 * смотрит на субстрат снаружи и бина сервиса для этого не берёт.
 *
 * <p><b>Опустошение между клетками идёт БЕЗ сброса идентичности.</b>
 * Числовые ключи продолжают расти, и это несущее: часть состояния живёт в
 * памяти процесса ключом строки (замок прохода, счёт слепоты детекции), и
 * сброшенная последовательность выдала бы новой строке ключ выбывшей — а
 * клетка получила бы наследство соседки.
 *
 * <p><b>Перечень таблиц читается из схемы, а не выписан списком.</b>
 * Таблиц у ядра три десятка, и выписанный список расходился бы с
 * миграциями молча: новая таблица осталась бы неопустошённой, и клетка
 * получала бы наследство соседки ровно там, где предмет новый.
 */
final class Rows {

    /** Журнал самого Flyway: он описывает прогон миграций, а не состояние клетки. */
    private static final String MIGRATION_JOURNAL = "flyway_schema_history";

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
        return of(TradingCoreSubstrate.database());
    }

    /** Наблюдатель строк названного контейнера. */
    static Rows of(PostgreSQLContainer container) {
        return new Rows(container);
    }

    /** Опустошает все таблицы схемы: вход каждой клетки — своё состояние. */
    void clear() {
        List<String> tables = tableNames().stream()
                .filter(table -> !MIGRATION_JOURNAL.equals(table))
                .toList();
        if (tables.isEmpty()) {
            throw new IllegalStateException("Схема базы субстрата пуста: миграции не накатились");
        }
        execute("truncate table " + String.join(", ", tables) + " cascade");
    }

    /**
     * Ставит состояние, писателя которому сервис не имеет.
     *
     * <p><b>Прямая запись в базу — цена, а не умолчание, и область у неё
     * узкая.</b> Предусловие ставится тропой ящика всюду, где тропа есть:
     * проекции — тиком синка, сделка — тиком сканера, транш — проходом
     * сопровождения. Прямая запись остаётся у состояний, писателя которым
     * ядро не имеет вовсе — чужая позиция на бирже без нашей сделки,
     * локально терминальная сущность, счётчик серии убытков, — и у
     * моментов, которые иначе пришлось бы ждать часами.
     *
     * @param sql       запрос правки
     * @param arguments значения подстановки
     * @return сколько строк правка затронула
     */
    Integer put(String sql, Object... arguments) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("База субстрата не приняла правку: " + sql, failure);
        }
    }

    /**
     * Ставит строку и отдаёт её числовой ключ.
     *
     * @param sql       запрос вставки с {@code returning id}
     * @param arguments значения подстановки
     * @return ключ заведённой строки
     */
    Long insert(String sql, Object... arguments) {
        List<Map<String, Object>> answer = rows(sql, arguments);
        return ((Number) answer.getFirst().values().iterator().next()).longValue();
    }

    /**
     * Ставит отказ базы на названной правке таблицы — вход клеток об
     * атомарности решения и его события.
     *
     * <p><b>Отказ ставится СНАРУЖИ процесса, и это единственная его
     * законная форма здесь.</b> Требование «решение и его событие ложатся
     * одной транзакцией» проверяется только тем, что одна из двух записей
     * не проходит; сделать её непроходимой изнутри ящика нечем — писателя
     * выбирает сервис, а идентичности он назначает сам. База же лежит за
     * границей процесса, и её отказ есть такой же вход, как отказ соседа.
     *
     * @param table     таблица, правка которой отказывает
     * @param operation {@code insert} либо {@code update}
     */
    void refuse(String table, String operation) {
        execute("create or replace function box_refuse() returns trigger language plpgsql as '"
                + "begin raise exception ''box: " + operation + " on " + table + " refused''; end'");
        execute("create trigger box_refuse_" + operation + " before " + operation + " on " + table
                + " for each row execute function box_refuse()");
    }

    /** Снимает отказ, поставленный {@link #refuse}. */
    void allow(String table, String operation) {
        execute("drop trigger if exists box_refuse_" + operation + " on " + table);
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

    /** Строки таблицы по значению колонки. */
    List<Map<String, Object>> rowsWhere(String table, String column, Object value) {
        return rows("select * from " + table + " where " + column + " = ? order by id asc", value);
    }

    /** Все строки таблицы, упорядоченные ключом по возрастанию. */
    List<Map<String, Object>> all(String table) {
        return rows("select * from " + table + " order by id asc");
    }

    /** Все строки таблицы, упорядоченные названной колонкой по возрастанию. */
    List<Map<String, Object>> allOrderedBy(String table, String column) {
        return rows("select * from " + table + " order by " + column + " asc");
    }

    /** Произвольная выборка: ею читаются соединения нескольких таблиц. */
    List<Map<String, Object>> select(String sql, Object... arguments) {
        return rows(sql, arguments);
    }

    /** Имена таблиц схемы: ими наблюдается состав схемы и её отсутствия. */
    List<String> tableNames() {
        return rows("select table_name from information_schema.tables"
                + " where table_schema = 'public' and table_type = 'BASE TABLE'")
                .stream()
                .map(row -> String.valueOf(row.get("table_name")))
                .toList();
    }

    /** Имена ограничений названной таблицы: ими наблюдаются инварианты схемы. */
    List<String> constraintNames(String table) {
        return rows("select constraint_name from information_schema.table_constraints"
                + " where table_schema = 'public' and table_name = ?", table)
                .stream()
                .map(row -> String.valueOf(row.get("constraint_name")))
                .toList();
    }

    /** Имена индексов названной таблицы. */
    List<String> indexNames(String table) {
        return rows("select indexname from pg_indexes where schemaname = 'public' and tablename = ?", table)
                .stream()
                .map(row -> String.valueOf(row.get("indexname")))
                .toList();
    }

    /** Применённые версии миграций. */
    List<String> appliedMigrations() {
        return rows("select version from " + MIGRATION_JOURNAL + " where success = true"
                + " and version is not null")
                .stream()
                .map(row -> String.valueOf(row.get("version")))
                .toList();
    }

    /** Снимок числа строк всех таблиц схемы: вход отрицаний «состояние не менялось». */
    Map<String, Long> countsByTable() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tableNames()) {
            if (!MIGRATION_JOURNAL.equals(table)) {
                counts.put(table, count(table));
            }
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
