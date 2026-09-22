package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B7.21} и {@code B7.22} — ГРАНИЦЫ ПРОХОДА: чего он не трогает
 * и чего у него нельзя отнять насовсем
 * (.claude/tests/cases/statistics.md §«B7 — Пересчёт: окно, порции и охрана
 * начала ряда»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>: обе клетки берут штатное
 * положение осей и состояния субстрата не трогают. Окно, «накрывающее весь
 * ряд фактов», сдвига оси не требует — ряд кладётся ВНУТРЬ штатного окна, и
 * расширять его значило бы объявить ось, которой клетка не ставит.
 *
 * <p><b>Две клетки складываются в одно утверждение с двух сторон.</b>
 * {@code B7.21} говорит, что источник проходу только ЧИТАЕТСЯ;
 * {@code B7.22} — что всё, что проход пишет, из этого источника восполнимо.
 * Вместе они и есть «проекция, а не накопитель»: у проекции один писатель на
 * входе (слушатель приёма) и ни одного на выходе, кроме самого прохода.
 *
 * <p><b>«Тропы записи нет ни одной» мерится счётчиком запросов базы.</b> По
 * строкам фактов номер транзакции различает «не трогал» и «переписал тем же
 * значением», но НЕ различает удаление с последующей вставкой того же:
 * строка была бы новой, а выдача прежней. Счёт исполнений команд записи
 * отвечает на это прямо, и оба наблюдаемых стоя́т рядом — они отвечают на
 * разные половины утверждения.
 *
 * <p><b>Отсутствие глубины хранения у агрегатов читается перечнем работ
 * Timescale, а не составом колонок.</b> Политика хранения живёт отдельной
 * работой планировщика, и в схеме таблицы её не видно вовсе; к тому же
 * таблицы агрегатов гипертаблицами не являются — резать их по времени
 * нечем.
 */
class RecomputeWriteScopeBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b7-scope";

    /** Ранние сутки ряда: ими он открывается, и лежат они внутри штатного окна. */
    private static final Integer EARLIER_DAY = 4;

    /** Поздние сутки того же ряда. */
    private static final Integer LATER_DAY = 1;

    /** Сколько тактов подряд подаёт клетка о чтении: один не отличил бы повтора. */
    private static final Integer TICKS = 3;

    /** Идентичность сделочного факта, по которой читается номер его версии. */
    private static final String DEAL_PROBE = "E-7-21-DEAL-LATE";

    /** Идентичность факта происшествия — та же роль у второго зерна. */
    private static final String INCIDENT_PROBE = "E-7-21-INC-LATE";

    /**
     * Команды записи, которых у прохода к таблицам фактов нет ни одной.
     *
     * <p>Перечень закрыт по РОДУ команды — вставка, обновление, удаление — и
     * берёт обе таблицы фактов: читает проход обе, и запись к любой из них
     * была бы той же тропой.
     */
    private static final List<String> WRITE_SHAPES = List.of(
            "%insert into deal_facts%", "%update deal_facts%", "%delete from deal_facts%",
            "%insert into incident_facts%", "%update incident_facts%",
            "%delete from incident_facts%");

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B7.21 — Пересчёт факты только читает")
    void theRecomputeOnlyReadsTheFacts() {
        Facts.deal("E-7-21-DEAL-EARLY", TENANT, midnightDaysAgo(EARLIER_DAY));
        Facts.deal(DEAL_PROBE, TENANT, midnightDaysAgo(LATER_DAY));
        Facts.incident("E-7-21-INC-EARLY", TENANT, DEAL_OPENED, midnightDaysAgo(EARLIER_DAY));
        Facts.incident(INCIDENT_PROBE, TENANT, HOLD_RAISED, midnightDaysAgo(LATER_DAY));
        List<Map<String, Object>> dealsBefore = rows.all(DEAL_FACTS, "event_id");
        List<Map<String, Object>> incidentsBefore = rows.all(INCIDENT_FACTS, "event_id");
        String dealVersion = rows.rowVersion(DEAL_FACTS, "event_id", DEAL_PROBE);
        String incidentVersion = rows.rowVersion(INCIDENT_FACTS, "event_id", INCIDENT_PROBE);
        assertThat(dealVersion)
                .as("вход поставлен: у пустого места номера транзакции нет вовсе, и "
                        + "сравнение с пустотой разошлось бы впустую")
                .isNotNull();
        rows.resetStatementCounters();

        for (int tick = 0; tick < TICKS; tick++) {
            recompute();
        }

        assertThat(rows.all(DEAL_FACTS, "event_id"))
                .as("строки сделочных фактов не изменились, не удалились и не прибавились")
                .isEqualTo(dealsBefore);
        assertThat(rows.all(INCIDENT_FACTS, "event_id"))
                .as("и строки фактов происшествий тоже").isEqualTo(incidentsBefore);
        assertThat(rows.rowVersion(DEAL_FACTS, "event_id", DEAL_PROBE))
                .as("строка факта не переписана даже тем же значением: номер транзакции у "
                        + "неё прежний")
                .isEqualTo(dealVersion);
        assertThat(rows.rowVersion(INCIDENT_FACTS, "event_id", INCIDENT_PROBE))
                .as("то же у второго зерна").isEqualTo(incidentVersion);
        for (String shape : WRITE_SHAPES) {
            assertThat(rows.statementCalls(shape))
                    .as("к базе не ушло ни одной команды по образцу %s: тропы записи или "
                            + "удаления факта у прохода нет ни одной", shape)
                    .isZero();
        }
        assertThat(rows.statementCalls(DEAL_GRAIN_QUERY))
                .as("при этом проход ИДЁТ: запросы группировки исполнялись — молчание "
                        + "счётчиков записи не есть молчание всего такта")
                .isPositive();
        assertThat(aggregates(DEAL_GRAIN, TENANT).dealRows())
                .as("и строки агрегатов собраны обеими сутками ряда").hasSize(2);
    }

    @Test
    @DisplayName("B7.22 — Строки агрегатов восполнимы повторным пересчётом")
    void theAggregateRowsAreRecoverableByRepeatedRecompute() {
        Facts.deal("E-7-22-DEAL-EARLY", TENANT, midnightDaysAgo(EARLIER_DAY));
        Facts.deal("E-7-22-DEAL-LATE", TENANT, midnightDaysAgo(LATER_DAY));
        Facts.incident("E-7-22-INC-EARLY", TENANT, ANOMALY_REPORTED, midnightDaysAgo(EARLIER_DAY));
        Facts.incident("E-7-22-INC-LATE", TENANT, ORDER_DECIDED, midnightDaysAgo(LATER_DAY));

        recompute();

        Map<String, Map<String, Object>> dealsBefore = byBucket(dealRows());
        Map<String, Map<String, Object>> incidentsBefore = byBucket(incidentRows());
        OffsetDateTime assembledFirst = moment(rowOf(dealRows(), LATER_DAY), ASSEMBLED_AT);
        assertThat(dealsBefore).as("вход поставлен: строки собраны обеими сутками").hasSize(2);
        assertThat(incidentsBefore).as("и у второго зерна тоже").hasSize(2);

        rows.write("delete from " + DEAL_AGGREGATES);
        rows.write("delete from " + INCIDENT_AGGREGATES);

        assertThat(dealRows()).as("строк не осталось ни одной: терять было что").isEmpty();
        assertThat(incidentRows()).as("и у второго зерна тоже").isEmpty();

        recompute();

        assertThat(byBucket(dealRows()))
                .as("строки собраны заново и несут ТЕ ЖЕ числа: источник хранит весь ряд, и "
                        + "агрегат из него восполним целиком")
                .isEqualTo(dealsBefore);
        assertThat(byBucket(incidentRows()))
                .as("у зерна происшествий то же").isEqualTo(incidentsBefore);
        assertThat(moment(rowOf(dealRows(), LATER_DAY), ASSEMBLED_AT))
                .as("а момент сборки у восполненной строки СВОЙ: она собрана заново, и "
                        + "равный момент означал бы, что строки не удаляли вовсе")
                .isAfter(assembledFirst);
        assertThat(rows.hypertableNames())
                .as("таблицы агрегатов гипертаблицами не являются: резать их по времени "
                        + "нечем, и куска, который унесла бы глубина, у них нет")
                .doesNotContain(DEAL_AGGREGATES, INCIDENT_AGGREGATES);
        assertThat(rows.retentionPolicyTables())
                .as("а политики глубины хранения не заведено ни одной — ни у агрегатов, ни "
                        + "у самих фактов: восполнять агрегат было бы не из чего, если бы "
                        + "ряд чистился")
                .isEmpty();
    }

    /** Строки сделочного зерна, как их отдаёт поверхность чтения. */
    private List<Map<String, Object>> dealRows() {
        return aggregates(DEAL_GRAIN, TENANT).dealRows();
    }

    /** Строки зерна происшествий той же поверхностью. */
    private List<Map<String, Object>> incidentRows() {
        return aggregates(INCIDENT_GRAIN, TENANT).incidentRows();
    }

    /**
     * Строки выдачи по суткам зерна, БЕЗ момента сборки.
     *
     * <p><b>Момент сборки вычтен намеренно:</b> у восполненной строки он
     * законно иной — её собрал другой проход, — и сравнение вместе с ним
     * мерило бы время, а не числа. Что момент при этом двинулся, клетка
     * утверждает отдельно: иначе «строки восполнены» не отличалось бы от
     * «строки не удалялись».
     *
     * @param handed строки выдачи
     */
    private static Map<String, Map<String, Object>> byBucket(List<Map<String, Object>> handed) {
        return handed.stream().collect(Collectors.toMap(
                row -> String.valueOf(row.get(BUCKET_DATE)),
                RecomputeWriteScopeBoxTest::withoutAssemblyMoment,
                (first, second) -> first,
                LinkedHashMap::new));
    }

    /** Та же строка без момента сборки. */
    private static Map<String, Object> withoutAssemblyMoment(Map<String, Object> row) {
        Map<String, Object> stripped = new LinkedHashMap<>(row);
        stripped.remove(ASSEMBLED_AT);
        return stripped;
    }
}
