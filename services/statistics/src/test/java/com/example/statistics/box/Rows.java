package com.example.statistics.box;

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
 * Наблюдение строк базы СНАРУЖИ ящика: своим соединением к контейнеру, а не
 * бином сервиса.
 *
 * <p><b>Почему прямой ассерт по базе законен здесь, и почему он разведён.</b>
 * Признак решения 7 — объявленный читатель вне кода сервиса-владельца — у
 * этого предмета выполнен ДВАЖДЫ и НЕ выполнен один раз:
 *
 * <ul>
 *   <li>у <b>строки состояния приёма</b> читатель объявлен доком сквозной
 *       формы (docs/rules/durable-consumer-reception.md §«Строка состояния
 *       приёма — таблица `reception_states`»), и ассерт по колонкам законен
 *       прямо;</li>
 *   <li>у <b>агрегатов</b> читатель есть, и читает он их ПОВЕРХНОСТЬЮ:
 *       ассерт идёт через выборку чтения, а не отсюда;</li>
 *   <li>у <b>фактов</b> поверхности нет вовсе и наружу они не отдаются
 *       (docs/models/domain/other/StatisticsFact.md §«Почему это не второй
 *       журнал»), поэтому читаются они колонками — и кейс называет это в
 *       поле «чем подтверждается», чтобы рефакторинг видел цену ассерта, а
 *       не спотыкался о неё.</li>
 * </ul>
 *
 * <p><b>Соединение своё, а не {@code DataSource} контекста:</b> ящик смотрит
 * на субстрат снаружи и бина сервиса для этого не берёт.
 *
 * <p><b>Перечень таблиц читается из схемы, а не выписан списком.</b>
 * Выписанный список расходился бы с миграциями молча: новая таблица осталась
 * бы неопустошённой, и клетка получала бы наследство соседки ровно там, где
 * предмет новый.
 *
 * <p><b>Куски гипертаблиц в перечень не попадают, и это верно по
 * построению:</b> они живут в служебной схеме Timescale, а опустошение
 * гипертаблицы опустошает их вместе с ней.
 */
final class Rows {

    /** Журнал самого Flyway: он описывает прогон миграций, а не состояние клетки. */
    private static final String MIGRATION_JOURNAL = "flyway_schema_history";

    /** Хвост имени, под которым таблица уезжает на время клетки об отказе. */
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
        return new Rows(StatisticsSubstrate.database());
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
     * Прямая запись в базу субстрата: ею ставится СОСТОЯНИЕ, которого тропа
     * ящика не производит.
     *
     * <p><b>Она законна ровно там, где состояние есть durable-вход клетки, а
     * не её выход.</b> Флаг остановки приёма ставит отказ обработки, и
     * поставить его сообщением значило бы занять единственный поток
     * слушателя бесконечными повторами — то есть отнять у клетки её
     * собственный вход, повтор. Форма строки при этом объявлена домом и
     * читается наружу (docs/rules/durable-consumer-reception.md §«Строка
     * состояния приёма — таблица `reception_states`»), поэтому запись по
     * колонкам говорит о том же, о чём читает ассерт.
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
     * <p><b>Так выражается «база отказывает» — вход клетки об отказе посреди
     * такта.</b> Предмет там — тропа, на которой такт не измерил: ряды
     * уносятся все, отказ уходит наружу, следующий успешный такт ряды
     * возвращает. Остановка контейнера базы дала бы ту же тропу ценой
     * ожидания пула соединений и не изменила бы ни одного наблюдаемого, а
     * поднять его обратно с теми же данными нечем — контейнер при остановке
     * исчезает.
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
     * {@code xmin} несёт номер транзакции, положившей нынешнюю версию строки,
     * и меняется при КАЖДОЙ записи, что бы та ни писала.
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

    /**
     * Все строки таблицы, упорядоченные названной колонкой по возрастанию.
     *
     * <p><b>Колонку порядка называет вызывающий, и это не гибкость ради
     * гибкости.</b> У гипертаблиц фактов суррогатного ключа нет вовсе — его
     * запрещает Timescale, — и общего «порядка записи» у их строк не
     * существует: клетка, читающая ряд, упорядочивает его тем, что сама и
     * подала (идентичностью события).
     *
     * @param table       таблица
     * @param orderColumn колонка порядка
     */
    List<Map<String, Object>> all(String table, String orderColumn) {
        return rows("select * from " + table + " order by " + orderColumn + " asc");
    }

    /** Имена таблиц схемы: ими наблюдается состав схемы и её отсутствия. */
    List<String> tableNames() {
        return rows("select table_name from information_schema.tables"
                + " where table_schema = 'public' and table_type = 'BASE TABLE'")
                .stream()
                .map(row -> String.valueOf(row.get("table_name")))
                .toList();
    }

    /**
     * Имена колонок названной таблицы в порядке объявления.
     *
     * <p><b>Ими читается СОСТАВ строки, а не её содержимое.</b> Утверждение
     * «колонки под это у факта нет вовсе» есть утверждение о схеме: строка,
     * которой колонки нет, от строки с пустой колонкой по выдаче неотличима.
     *
     * @param table таблица, чей состав читается
     */
    List<String> columnNames(String table) {
        return rows("select column_name from information_schema.columns"
                + " where table_schema = 'public' and table_name = ?"
                + " order by ordinal_position", table)
                .stream()
                .map(row -> String.valueOf(row.get("column_name")))
                .toList();
    }

    /**
     * Имена колонок названной таблицы, чей тип — момент со смещением.
     *
     * <p><b>Ими выражается «второй оси времени у факта нет».</b> Клейм этот о
     * СОСТАВЕ схемы, а не о значениях: колонка приёма, заведённая рядом с
     * осью зерна, ни одной выдачей от её отсутствия не отличалась бы, пока
     * никто не заглянул в неё.
     *
     * @param table таблица, чьи оси времени читаются
     */
    List<String> momentColumnNames(String table) {
        return rows("select column_name from information_schema.columns"
                + " where table_schema = 'public' and table_name = ?"
                + " and data_type = 'timestamp with time zone'"
                + " order by ordinal_position", table)
                .stream()
                .map(row -> String.valueOf(row.get("column_name")))
                .toList();
    }

    /**
     * Имена колонок названной таблицы, чей тип — документ.
     *
     * <p><b>Ими выражается «колонки, хранящей тело целиком, у факта нет ни в
     * одной из двух таблиц».</b> Клейм этот о СОСТАВЕ схемы и о ТИПЕ, а не об
     * именах: колонка, названная иначе, но объявленная документом, несла бы
     * содержимое как доставлено ровно так же, и перечень по именам её бы
     * пропустил.
     *
     * @param table таблица, чьи документные колонки читаются
     */
    List<String> documentColumnNames(String table) {
        return rows("select column_name from information_schema.columns"
                + " where table_schema = 'public' and table_name = ?"
                + " and data_type in ('json', 'jsonb', 'xml')"
                + " order by ordinal_position", table)
                .stream()
                .map(row -> String.valueOf(row.get("column_name")))
                .toList();
    }

    /**
     * Границы куска гипертаблицы, в который попала строка с названным
     * моментом; пустая карта — куска нет.
     *
     * <p><b>Ею читается, что строка легла в кусок ПО СВОЕЙ оси.</b> Ось
     * разбиения объявлена миграцией и снаружи не видна ни одной колонкой
     * строки: единственное наблюдаемое — что границы куска накрывают именно
     * тот момент, который клетка подала.
     *
     * @param hypertable имя гипертаблицы
     * @param moment     момент, по которому ищется кусок
     */
    Map<String, Object> chunkCovering(String hypertable, OffsetDateTime moment) {
        List<Map<String, Object>> found = rows(
                "select range_start, range_end from timescaledb_information.chunks"
                        + " where hypertable_name = ? and range_start <= ? and range_end > ?",
                hypertable, moment, moment);
        return found.isEmpty() ? Map.of() : found.getFirst();
    }

    private Long number(String sql, Object... arguments) {
        List<Map<String, Object>> found = rows(sql, arguments);
        return ((Number) found.getFirst().values().iterator().next()).longValue();
    }

    /**
     * Значение колонки; момент читается со СМЕЩЕНИЕМ, а не голым
     * таймстампом: колонки времени объявлены {@code OffsetDateTime} (шкала
     * одна — UTC, docs/rules/time-utc.md), и без смещения кейс не отличил бы
     * записанный момент от сдвинутого.
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
