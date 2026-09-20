package com.example.audit.box;

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
 * <p><b>Почему прямой ассерт по базе законен здесь.</b> Признак решения 7
 * — объявленный читатель вне кода сервиса-владельца — у этого предмета
 * выполнен у обеих таблиц: форма строки журнала и форма строки состояния
 * приёма объявлены доками владельца и читаются наружу
 * (docs/models/domain/other/AuditRecord.md §Персистентность,
 * docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»). Через поверхность идёт то, у чего
 * поверхность есть, — журнальная выборка; строка состояния приёма
 * поверхности не имеет вовсе (.claude/tests/cases/audit.md §«Чем
 * достаются выходы»).
 *
 * <p><b>Соединение своё, а не {@code DataSource} контекста:</b> ящик
 * смотрит на субстрат снаружи и бина сервиса для этого не берёт.
 *
 * <p><b>Перечень таблиц читается из схемы, а не выписан списком.</b>
 * Выписанный список расходился бы с миграциями молча: новая таблица
 * осталась бы неопустошённой, и клетка получала бы наследство соседки
 * ровно там, где предмет новый.
 */
final class Rows {

    /** Журнал самого Flyway: он описывает прогон миграций, а не состояние клетки. */
    private static final String MIGRATION_JOURNAL = "flyway_schema_history";

    /** Хвост имени, под которым таблица пережидает клетку об отказе базы. */
    private static final String PARKED_SUFFIX = "_parked";

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
        return new Rows(AuditSubstrate.database());
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
     * Прямая запись в базу субстрата: ею ставится СОСТОЯНИЕ, которого
     * тропа ящика не производит.
     *
     * <p><b>Она законна ровно там, где состояние есть durable-вход
     * клетки, а не её выход.</b> Флаг остановки приёма ставит отказ
     * обработки, и поставить его сообщением значило бы занять
     * единственный поток слушателя бесконечными повторами — то есть
     * отнять у клетки её собственный вход. Форма строки при этом
     * объявлена домом и читается наружу
     * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма
     * — таблица `reception_states`»), поэтому запись по колонкам говорит
     * о том же, о чём читает ассерт.
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
     * Прогоняет тело при ОТСУТСТВУЮЩЕЙ таблице и возвращает её на место.
     *
     * <p><b>Так выражается «база отказывает» — вход клетки об отказе
     * посреди такта.</b> Предмет там — тропа, на которой такт не измерил:
     * ряды уносятся все, отказ уходит наружу, следующий успешный такт
     * ряды возвращает. Остановка контейнера базы дала бы ту же тропу
     * ценой тридцати секунд ожидания пула соединений и не изменила бы ни
     * одного наблюдаемого, а поднять его обратно с теми же данными
     * нечем — контейнер при остановке исчезает.
     *
     * <p><b>Возврат стои́т в {@code finally}</b>: таблица общая всем
     * контекстам прогона, и оставленная снятой она уронила бы соседние
     * клетки по причине, которой те не ставили.
     *
     * @param table таблица, которой на время тела не будет
     * @param body  тело клетки
     */
    void withoutTable(String table, Runnable body) {
        execute("alter table " + table + " rename to " + table + PARKED_SUFFIX);
        try {
            body.run();
        } finally {
            execute("alter table " + table + PARKED_SUFFIX + " rename to " + table);
        }
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

    /**
     * Версия строки в базе; пусто — строки нет.
     *
     * <p><b>Ею наблюдается ФАКТ повторной записи, которого не видно по
     * колонкам.</b> Обновление, кладущее то же значение, оставляет строку
     * неотличимой от нетронутой: чтобы отличить «записали один раз» от
     * «записывали сто раз», нужен счётчик самой базы. Системная колонка
     * {@code xmin} несёт номер транзакции, положившей нынешнюю версию
     * строки, и меняется при КАЖДОЙ записи, что бы та ни писала.
     *
     * @param table  таблица
     * @param column колонка отбора
     * @param value  значение отбора
     */
    String rowVersion(String table, String column, Object value) {
        List<Map<String, Object>> found = rows(
                "select xmin::text as row_version from " + table + " where " + column + " = ?", value);
        return found.isEmpty() ? null : String.valueOf(found.getFirst().get("row_version"));
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
     * таймстампом: колонки времени объявлены {@code OffsetDateTime}
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
