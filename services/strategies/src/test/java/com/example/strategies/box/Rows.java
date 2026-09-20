package com.example.strategies.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

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
 * читать через поверхность там, где поверхность есть; у владельца
 * определений она есть у самого определения и его статуса — их кейсы и
 * читают {@code GET}-точками. Строки {@code outbox_events}, колонки
 * аудита и частичный уникальный индекс объявленного читателя вне кода
 * сервиса не имеют вовсе — копию ядро заводит СОБЫТИЕМ, а не запросом к
 * чужой базе, — и документ кейсов называет прямой ассерт по ним ценой, а
 * не умолчанием (.claude/tests/cases/strategies.md §«Чем достаются
 * выходы»).
 *
 * <p><b>Соединение своё, а не {@code DataSource} контекста:</b> ящик
 * смотрит на субстрат снаружи и бина сервиса для этого не берёт.
 *
 * <p><b>Перечень таблиц читается из схемы, а не выписан списком.</b>
 * Таблиц у дерева определения дюжина, и выписанный список расходился бы с
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
        return new Rows(StrategiesSubstrate.database());
    }

    /** Опустошает все таблицы схемы: вход каждой клетки — своё состояние. */
    void clear() {
        List<String> tables = tableNames().stream()
                .filter(table -> isFalse(MIGRATION_JOURNAL.equals(table)))
                .toList();
        if (tables.isEmpty()) {
            throw new IllegalStateException("Схема базы субстрата пуста: миграции не накатились");
        }
        execute("truncate table " + String.join(", ", tables) + " cascade");
    }

    /**
     * Прямая запись в базу субстрата; отказ базы приходит исключением с
     * текстом сработавшего ограничения.
     *
     * <p><b>Ею наблюдается ВТОРОЙ носитель инварианта.</b> Проверка
     * приложения и ограничение схемы держат одно и то же утверждение, и
     * второй носитель заведён ровно на случай, когда первый обойдён —
     * то есть на запись, которая мимо приложения и идёт.
     *
     * @param sql       команда записи
     * @param arguments её позиционные аргументы
     */
    void write(String sql, Object... arguments) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new IllegalStateException("База субстрата отвергла запись: " + failure.getMessage(),
                    failure);
        }
    }

    /**
     * Ставит отказ базы на названной правке таблицы — вход клеток о
     * порядке «сперва опубликовать, потом пометить».
     *
     * <p><b>Отказ ставится СНАРУЖИ процесса, и это единственная его
     * законная форма здесь.</b> Порядок двух ходов наблюдаем ровно тогда,
     * когда второй не проходит: публикация состоялась, отметка — нет, и
     * строка осталась непомеченной. Сделать отметку непроходимой изнутри
     * ящика нечем — писателя выбирает сервис; база же лежит за границей
     * процесса, и её отказ есть такой же вход, как отказ соседа.
     *
     * <p><b>Адреса соседей по модулю это не трогает:</b> контейнер
     * остаётся живым и доступным, меняется только содержимое его схемы, и
     * клетка возвращает его тем же заходом
     * (.claude/decisions/test-contour-design-pass.md §«Кейс, разрушающий
     * субстрат, берёт свой контейнер и свой контекст»).
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

    /**
     * Затягивает названную правку таблицы на заданное число секунд —
     * вход клетки о перекрывающем запуске.
     *
     * <p><b>Проход удлиняется БАЗОЙ, а не ожиданием в тесте.</b> Чтобы
     * второй тик пришёл во время первого, первый обязан идти дольше
     * одного обмена с брокером; удлинить его изнутри ящика нечем —
     * подмена бина сделала бы предмет внутренностью. Задержка на отметке
     * публикации даёт ровно нужное окно и снимается тем же заходом.
     *
     * @param table     таблица, правка которой затягивается
     * @param operation {@code insert} либо {@code update}
     * @param seconds   на сколько секунд затягивается каждая строка
     */
    void delay(String table, String operation, Integer seconds) {
        execute("create or replace function box_delay() returns trigger language plpgsql as '"
                + "begin perform pg_sleep(" + seconds + "); return new; end'");
        execute("create trigger box_delay_" + operation + " before " + operation + " on " + table
                + " for each row execute function box_delay()");
    }

    /** Снимает задержку, поставленную {@link #delay}. */
    void undelay(String table, String operation) {
        execute("drop trigger if exists box_delay_" + operation + " on " + table);
    }

    /**
     * Уводит таблицу из-под имени, которым её зовёт отображение, —
     * вход клетки о НЕПРЕДУСМОТРЕННОМ отказе.
     *
     * <p><b>Отказ обязан быть тем, которого обработчики не называют
     * поимённо.</b> Всякий названный класс отвечает своим кодом, и клетка
     * о «всём непредусмотренном» на нём предмета не имеет; отсутствующее
     * отношение даёт ровно непредусмотренный отказ — и даёт его ВНУТРИ
     * процесса, то есть там, где его и ловит последний обработчик.
     *
     * <p><b>Соседей по модулю это адреса не лишает</b> — контейнер жив, а
     * имя возвращается {@link #show} тем же заходом.
     *
     * @param table таблица, которую клетка уводит
     */
    void hide(String table) {
        execute("alter table " + table + " rename to " + table + "_box_hidden");
    }

    /** Возвращает имя, уведённое {@link #hide}. */
    void show(String table) {
        execute("alter table if exists " + table + "_box_hidden rename to " + table);
    }

    /** Версии применённых миграций — ими наблюдается накат схемы. */
    List<String> appliedMigrations() {
        return rows("select version from " + MIGRATION_JOURNAL + " where success order by installed_rank")
                .stream()
                .map(row -> String.valueOf(row.get("version")))
                .toList();
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

    /** Все строки таблицы, упорядоченные ключом по возрастанию. */
    List<Map<String, Object>> all(String table) {
        return rows("select * from " + table + " order by id asc");
    }

    /** Имена таблиц схемы: ими наблюдается состав схемы и её отсутствия. */
    List<String> tableNames() {
        return rows("select table_name from information_schema.tables"
                + " where table_schema = 'public' and table_type = 'BASE TABLE'")
                .stream()
                .map(row -> String.valueOf(row.get("table_name")))
                .toList();
    }

    /** Имена колонок таблицы: ими наблюдается состав контекста определения. */
    List<String> columnNames(String table) {
        return rows("select column_name from information_schema.columns"
                + " where table_schema = 'public' and table_name = ?", table)
                .stream()
                .map(row -> String.valueOf(row.get("column_name")))
                .toList();
    }

    /** Снимок числа строк всех таблиц схемы: вход отрицаний «состояние не менялось». */
    Map<String, Long> countsByTable() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tableNames()) {
            if (isFalse(MIGRATION_JOURNAL.equals(table))) {
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
