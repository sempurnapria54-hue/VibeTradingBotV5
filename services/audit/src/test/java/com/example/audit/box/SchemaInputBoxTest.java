package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетки {@code B10.7}, {@code B10.8} и {@code B10.9} — схема как вход:
 * накат цепочки, инварианты вторым носителем и состав колонок
 * (.claude/tests/cases/audit.md §«B10 — Конфигурация и схема как вход»).
 *
 * <p><b>Все три стоя́т на ШТАТНОМ положении осей и живут одним классом:</b>
 * схема не зависит ни от одной оси конфигурации — её кладут миграции, — и
 * своего контекста ни одной из трёх не нужно.
 *
 * <p><b>Ассерт ПРЯМОЙ по схеме, и поверхности у этих инвариантов нет.</b>
 * Ограничения базы наблюдаются только попыткой записи мимо приложения:
 * держись гейт одним кодом, эти записи прошли бы. Форма строк при этом
 * объявлена домами и читается наружу
 * (docs/models/domain/other/AuditRecord.md §Персистентность,
 * docs/models/domain/other/AccessDenial.md §Персистентность).
 *
 * <p><b>Сверки отображения со схемой на старте у этого сервиса НЕТ, и это
 * находка, а не свойство клетки</b> ({@code F-9}). Подключение и фабрика
 * сущностей собраны своей формой, мимо {@code spring.datasource} и
 * {@code spring.jpa}, поэтому ключ {@code ddl-auto: validate} — тот, что
 * стои́т у пяти соседних сервисов, — здесь не читает никто. Колонка,
 * объявленная моделью и не заведённая миграцией, уронила бы не подъём, а
 * первую тропу, которая её коснётся. Клетка поэтому мерит то, что
 * наблюдаемо: схождение отображения со схемой предъявляется ПРОХОДОМ всех
 * трёх троп записи и тропы чтения.
 */
class SchemaInputBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходит тропа записи журнала. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Единственная миграция цепочки: она у сервиса своя и начинается с неё. */
    private static final String BASELINE = "1";

    /** Идентичность события, которой клетки ставят строку журнала. */
    private static final String EVENT = "E-B10-SCHEMA";

    /** Шесть колонок аудита сущности: набор бинарен — либо все, либо ни одной. */
    private static final List<String> AUDIT_COLUMNS = List.of(
            "created_at", "created_by", "modified_at", "modified_by",
            "external_created_at", "external_modified_at");

    /** Две биржевые колонки набора: у отвергнутого вызова они пусты навсегда. */
    private static final List<String> EXCHANGE_COLUMNS =
            List.of("external_created_at", "external_modified_at");

    /** Запись строки журнала мимо приложения: содержимое кладётся навесом. */
    private static final String INSERT_RECORD = """
            insert into audit_records
                (event_id, tenant_id, event_type, occurred_at, recorded_at, version, content)
            values (?, ?, ?, ?, ?, ?, ?::jsonb)""";

    /** Запись строки журнала БЕЗ содержимого: им мерится обязательность навеса. */
    private static final String INSERT_RECORD_WITHOUT_CONTENT = """
            insert into audit_records
                (event_id, tenant_id, event_type, occurred_at, recorded_at, version, content)
            values (?, ?, ?, ?, ?, ?, null)""";

    /** Запись строки следа отказа мимо приложения. */
    private static final String INSERT_DENIAL = """
            insert into access_denials (internal_id, surface, outcome)
            values (?, ?, ?)""";

    @Test
    @DisplayName("B10.7 — Схема накатывается миграциями и сходится с отображением")
    void theSchemaIsMigratedAndTheMappingMeetsIt() {
        assertThat(rows.appliedMigrations())
                .as("цепочка у сервиса одна и накатана со своего начала")
                .containsExactly(BASELINE);
        assertThat(rows.tableNames())
                .contains(JOURNAL_TABLE, RECEPTION_TABLE, DENIALS_TABLE);
        assertThat(List.of(JOURNAL_TABLE, RECEPTION_TABLE, DENIALS_TABLE))
                .as("имена таблиц — во множественном числе")
                .allMatch(table -> table.endsWith("s"));

        publish(CORE, EVENT, momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(1L);
        givenReceptionStateRows();
        Integer status = getAnonymously(JOURNAL_RECORDS).status();
        Answer page = journal(TENANT, momentsAgo(Duration.ofHours(1)), now());

        assertThat(status).as("тропа записи следа отказа пройдена").isEqualTo(401);
        assertThat(rows.count(DENIALS_TABLE)).isEqualTo(1L);
        assertThat(pairs()).as("тропа записи строк состояния пройдена").isNotEmpty();
        assertThat(page.status())
                .as("тропа чтения пройдена без отказа отображения")
                .isEqualTo(200);
        assertThat(record().get("event_id")).isEqualTo(EVENT);
    }

    @Test
    @DisplayName("B10.8 — Инварианты держатся вторым носителем — схемой")
    void theInvariantsAreHeldBySchemaAsTheSecondCarrier() {
        publish(CORE, EVENT, momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(1L);
        givenReceptionStateRows();
        getAnonymously(JOURNAL_RECORDS);
        String denialId = String.valueOf(rows.all(DENIALS_TABLE).getFirst().get("internal_id"));

        assertThatThrownBy(() -> rows.write(INSERT_RECORD, EVENT, TENANT, "DEAL_OPENED",
                momentsAgo(EVENT_AGE), now(), 1, Bodies.reference()))
                .as("вторая строка журнала с той же идентичностью события")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> givenPair(CORE, now(), Boolean.TRUE, Boolean.FALSE, null, null))
                .as("вторая строка состояния с той же парой «группа × тема»")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rows.write(INSERT_RECORD_WITHOUT_CONTENT, "E-EMPTY", TENANT,
                "DEAL_OPENED", momentsAgo(EVENT_AGE), now(), 1))
                .as("строка журнала с пустым содержимым")
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> rows.write(INSERT_DENIAL, denialId, "GET /x", "PRINCIPAL_ABSENT"))
                .as("вторая строка отказа с той же идентичностью")
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows.count(JOURNAL_TABLE)).as("не прошла ни одна из записей").isEqualTo(1L);
        assertThat(rows.count(DENIALS_TABLE)).isEqualTo(1L);
        assertThat(radiusIndexes())
                .as("частичные индексы по двум радиусам существуют и строк с пустым "
                        + "значением не несут по построению отбора")
                .hasSize(2)
                .allMatch(definition -> definition.contains("IS NOT NULL"));
        assertThat(rows.foreignKeyNames())
                .as("внешних ключей к чужим реестрам нет ни одного: идентичности "
                        + "принадлежат чужим сервисам")
                .isEmpty();
    }

    @Test
    @DisplayName("B10.9 — Поля аудита сущности у строки журнала не наследуются")
    void theJournalRowInheritsNoEntityAuditColumns() {
        getAnonymously(JOURNAL_RECORDS);
        Map<String, Object> denial = rows.all(DENIALS_TABLE).getFirst();

        assertThat(rows.columnNames(JOURNAL_TABLE))
                .as("шести колонок аудита у строки журнала нет ни одной")
                .doesNotContainAnyElementsOf(AUDIT_COLUMNS)
                .as("момент создания несёт момент приёма своим именем")
                .contains(RECORDED_COLUMN);
        assertThat(rows.columnNames(DENIALS_TABLE))
                .as("у строки отказа набор из шести есть целиком: он бинарен")
                .containsAll(AUDIT_COLUMNS);
        assertThat(denial.get("created_at"))
                .as("системные колонки набора заполняет персистентность")
                .isNotNull();
        EXCHANGE_COLUMNS.forEach(column -> assertThat(denial.get(column))
                .as("биржевая колонка %s остаётся пустой навсегда: биржевого домена "
                        + "у отвергнутого вызова нет вовсе", column)
                .isNull());
    }

    /** Объявления частичных индексов по колонкам радиуса. */
    private List<String> radiusIndexes() {
        return rows.indexDefinitions(JOURNAL_TABLE).stream()
                .filter(definition -> definition.contains("WHERE"))
                .toList();
    }
}
