package com.example.statistics.box;

import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетки {@code B12.8}, {@code B12.9} и {@code B12.10} — схема как вход:
 * накат цепочки, инварианты вторым носителем и состав колонок
 * (.claude/tests/cases/statistics.md §«B12 — Конфигурация и схема как вход»).
 *
 * <p><b>Все три стоя́т на ШТАТНОМ положении осей и живут одним классом:</b>
 * схема не зависит ни от одной оси конфигурации — её кладут миграции, — и
 * своего контекста ни одной из трёх не нужно.
 *
 * <p><b>Ассерт ПРЯМОЙ по схеме, и поверхности у этих инвариантов нет.</b>
 * Ограничения базы наблюдаются только попыткой записи мимо приложения:
 * держись гейт одним кодом, эти записи прошли бы. Форма строк при этом
 * объявлена домами и читается наружу
 * (docs/models/domain/other/StatisticsFact.md §Персистентность,
 * docs/rules/statistics-aggregates.md §Персистентность).
 *
 * <p><b>Сверки отображения со схемой на старте у этого сервиса НЕТ, и это
 * уже названный долг, а не свойство клетки</b> (.claude/work/backlog.md
 * §«Сверки отображения со схемой на старте у двух сервисов нет вовсе»).
 * Подключение и фабрика сущностей собраны своей формой, мимо
 * {@code spring.datasource} и {@code spring.jpa}, поэтому ключ
 * {@code ddl-auto: validate} — тот, что стои́т у пяти соседних сервисов, —
 * здесь не читает никто. Колонка, объявленная моделью и не заведённая
 * миграцией, уронила бы не подъём, а первую тропу, которая её коснётся.
 * Клетка поэтому мерит то, что наблюдаемо: схождение отображения со схемой
 * предъявляется ПРОХОДОМ всех троп записи и тропы чтения.
 *
 * <p><b>Обратная сторона той же формы объявления — что генерации схемы
 * вторым писателем не происходит.</b> Фабрика сущностей объявлена без
 * {@code hbm2ddl}, и состав таблиц равен тому, что положила цепочка: лишняя
 * таблица в схеме означала бы второго писателя.
 */
class SchemaInputBoxTest extends SharedStatisticsBox {

    /** Единственная миграция цепочки: она у сервиса своя и начинается с неё. */
    private static final String BASELINE = "1";

    /** Состав схемы, положенный цепочкой: шесть таблиц предмета. */
    private static final List<String> OWN_TABLES = List.of(
            DEAL_FACTS, INCIDENT_FACTS, DEAL_AGGREGATES, INCIDENT_AGGREGATES,
            RECEPTION_TABLE, DENIALS_TABLE);

    /** Таблицы, режущиеся кусками по своей оси времени: обе таблицы фактов. */
    private static final List<String> HYPERTABLES = List.of(DEAL_FACTS, INCIDENT_FACTS);

    /** Журнал накатанных миграций: он принадлежит каркасу, а не предмету. */
    private static final String MIGRATION_JOURNAL = "flyway_schema_history";

    /** Шесть колонок аудита сущности: набор бинарен — либо все, либо ни одной. */
    private static final List<String> AUDIT_COLUMNS = List.of(
            "created_at", "created_by", "modified_at", "modified_by",
            "external_created_at", "external_modified_at");

    /** Две биржевые колонки набора: у отвергнутого вызова они пусты навсегда. */
    private static final List<String> EXCHANGE_COLUMNS =
            List.of("external_created_at", "external_modified_at");

    /** Колонка момента сборки: ею агрегат отвечает вместо момента создания. */
    private static final String ASSEMBLED_COLUMN = "assembled_at";

    /** Колонка суток зерна. */
    private static final String BUCKET_COLUMN = "bucket_date";

    /** Запись сделочного факта БЕЗ тенанта: ею мерится обязательность ключа. */
    private static final String INSERT_FACT_WITHOUT_TENANT = """
            insert into deal_facts
                (event_id, tenant_id, exchange_account_internal_id, closed_at)
            values (?, null, ?, ?)""";

    /** Запись строки состояния приёма мимо приложения. */
    private static final String INSERT_RECEPTION_STATE = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, false, ?)""";

    /** Сутки, в которых лежат факты клеток. */
    private static final Integer DAY = 1;

    /** Сколько фактов кладёт проход по тропам записи: по одному на зерно. */
    private static final Long ONE = 1L;

    @Test
    @DisplayName("B12.8 — Схема накатывается миграциями и сходится с отображением")
    void theSchemaIsMigratedAndTheMappingMeetsIt() {
        assertThat(rows.appliedMigrations())
                .as("цепочка у сервиса одна и накатана со своего начала")
                .containsExactly(BASELINE);
        assertThat(rows.tableNames())
                .as("генерации схемы вторым писателем не происходит: состав равен тому, "
                        + "что положила цепочка")
                .containsExactlyInAnyOrderElementsOf(withJournal());
        assertThat(subjectTables())
                .as("имена таблиц предмета — во множественном числе")
                .isNotEmpty()
                .allMatch(table -> table.endsWith("s"));
        assertThat(rows.hypertableNames())
                .as("обе таблицы фактов режутся кусками по своей оси времени, а таблицы "
                        + "агрегатов — нет: у них зерно суток, а не ряд")
                .containsExactlyInAnyOrderElementsOf(HYPERTABLES);

        givenReceptionStateRows();
        publish("E-B12-8-DEAL", DEAL_CLOSED, midnightDaysAgo(DAY),
                Bodies.dealClosed(Facts.ACCOUNT, Facts.STRATEGY));
        publish("E-B12-8-INCIDENT", DEAL_OPENED, midnightDaysAgo(DAY),
                Bodies.incident(Facts.ACCOUNT));
        awaitConsumed();
        recompute();
        Integer denied = getAnonymously(AGGREGATE_ROWS).status();
        Answer page = aggregates(DEAL_GRAIN, TENANT);

        assertThat(rows.count(DEAL_FACTS))
                .as("тропа записи сделочного факта пройдена").isEqualTo(ONE);
        assertThat(rows.count(INCIDENT_FACTS))
                .as("тропа записи факта происшествия пройдена").isEqualTo(ONE);
        assertThat(rows.count(DEAL_AGGREGATES))
                .as("тропа записи сделочного агрегата пройдена").isEqualTo(ONE);
        assertThat(rows.count(INCIDENT_AGGREGATES))
                .as("тропа записи агрегата происшествий пройдена").isEqualTo(ONE);
        assertThat(pairs()).as("тропа записи строк состояния пройдена").isNotEmpty();
        assertThat(denied).as("тропа записи следа отказа пройдена").isEqualTo(401);
        assertThat(rows.count(DENIALS_TABLE)).isEqualTo(ONE);
        assertThat(page.status())
                .as("тропа чтения пройдена без отказа отображения")
                .isEqualTo(200);
        assertThat(page.dealRows()).as("строка выдачи собрана").hasSize(1);
    }

    @Test
    @DisplayName("B12.9 — Инварианты держатся вторым носителем — схемой")
    void theInvariantsAreHeldBySchemaAsTheSecondCarrier() {
        givenReceptionStateRows();
        OffsetDateTime assembledAt = now();
        Aggregates.dealRowWithEmptyKeys(TENANT, bucket(DAY), assembledAt);

        assertThatThrownBy(() -> rows.write(INSERT_FACT_WITHOUT_TENANT,
                "E-NO-TENANT", Facts.ACCOUNT, midnightDaysAgo(DAY)))
                .as("строка факта с пустым тенантом: обязательность ключа объявлена "
                        + "ограничением, а не только кодом")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Aggregates.dealRowWithEmptyKeys(TENANT, bucket(DAY), assembledAt))
                .as("вторая строка того же зерна с теми же ПУСТЫМИ компонентами ключа: "
                        + "уникальность считает пустые значения одинаковыми")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rows.write(INSERT_RECEPTION_STATE,
                consumerGroup(), topic(), now(), now()))
                .as("вторая строка той же пары «группа × тема»")
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows.count(DEAL_FACTS)).as("не прошла ни одна из записей").isZero();
        assertThat(rows.count(DEAL_AGGREGATES)).isEqualTo(ONE);
        assertThat(pairs()).hasSize(subscription().size());
        assertThat(rows.foreignKeyNames())
                .as("внешних ключей к чужим реестрам нет ни одного: идентичности тенанта, "
                        + "счёта и определения принадлежат чужим сервисам")
                .isEmpty();
    }

    @Test
    @DisplayName("B12.10 — Поля аудита сущности у строк фактов и агрегатов не наследуются")
    void factAndAggregateRowsInheritNoEntityAuditColumns() {
        getAnonymously(AGGREGATE_ROWS);
        Map<String, Object> denial = rows.all(DENIALS_TABLE, "id").getFirst();

        assertThat(rows.columnNames(DEAL_FACTS))
                .as("у сделочного факта шести колонок аудита нет ни одной")
                .doesNotContainAnyElementsOf(AUDIT_COLUMNS)
                .as("момент несёт ось времени своего зерна")
                .contains(CLOSED_COLUMN);
        assertThat(rows.columnNames(INCIDENT_FACTS))
                .doesNotContainAnyElementsOf(AUDIT_COLUMNS)
                .contains(OCCURRED_COLUMN);
        assertThat(rows.columnNames(DEAL_AGGREGATES))
                .as("у сделочного агрегата их тоже нет: момент у него — момент СБОРКИ")
                .doesNotContainAnyElementsOf(AUDIT_COLUMNS)
                .contains(ASSEMBLED_COLUMN, BUCKET_COLUMN);
        assertThat(rows.columnNames(INCIDENT_AGGREGATES))
                .doesNotContainAnyElementsOf(AUDIT_COLUMNS)
                .contains(ASSEMBLED_COLUMN, BUCKET_COLUMN);
        assertThat(rows.columnNames(DENIALS_TABLE))
                .as("у строки отказа набор из шести есть целиком: он бинарен")
                .containsAll(AUDIT_COLUMNS);
        assertThat(denial.get("created_at"))
                .as("системные колонки набора заполняет персистентность")
                .isNotNull();
        EXCHANGE_COLUMNS.forEach(column -> assertThat(denial.get(column))
                .as("биржевая колонка %s остаётся пустой навсегда: биржевого домена у "
                        + "отвергнутого вызова нет вовсе", column)
                .isNull());
    }

    /** Таблицы предмета в схеме: журнал миграций принадлежит каркасу. */
    private List<String> subjectTables() {
        return rows.tableNames().stream()
                .filter(table -> isFalse(MIGRATION_JOURNAL.equals(table)))
                .toList();
    }

    /** Состав схемы вместе с журналом миграций каркаса. */
    private static List<String> withJournal() {
        return Stream.concat(OWN_TABLES.stream(), Stream.of(MIGRATION_JOURNAL)).toList();
    }
}
