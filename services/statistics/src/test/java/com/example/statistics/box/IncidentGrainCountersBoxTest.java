package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.statistics.domain.jobs.AggregateRecomputeJob;
import com.example.statistics.domain.jobs.ReceptionStateJob;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B9.1} — {@code B9.8}: счётчики происшествий и их зерно
 * (.claude/tests/cases/statistics.md §«B9 — Счётчики происшествий: своё
 * зерно»).
 *
 * <p><b>Класс равен ГРУППЕ по тому же признаку, что у {@code B8}</b>
 * ({@link DealGrainArithmeticBoxTest}): оси конфигурации не сдвигает ни одна
 * клетка — окно пересчёта величиной субстрата, выключатель снят не у одной, —
 * и расходятся они только тем, какие факты каждая себе кладёт. Второй контекст
 * объявил бы ось, которой группа не ставит.
 *
 * <p><b>ДВЕ клетки берут тропу приёма, и это не разнобой, а предусловие.</b>
 * Определение стратегии и предмет события (сделка, заявка) в строке факта
 * происшествия не хранятся вовсе — колонок под них нет, — поэтому
 * предусловия «события несли РАЗНЫЕ определения» ({@code B9.7}, целиком) и
 * «два ребра ОДНОЙ сделки» ({@code B9.6}, наполовину: ребра подаются
 * сообщением, а факт соседнего класса — записью) прямой записью невыразимы:
 * положенные колонками, они стали бы предусловием, которого клетка не
 * ставила. Прочие шесть кладут факты прямой записью
 * ({@link IncidentDraft}) — вход у них durable, а подача сообщением сделала
 * бы их красными по живости приёма.
 *
 * <p><b>«Считает СОБЫТИЯ, а не их предметы» мерится СОСТАВОМ КОЛОНОК.</b>
 * Колонки предмета — сделки, заявки, инструмента — у таблицы фактов нет ни
 * одной, и отбор по классу события поэтому равен числу строк класса <b>по
 * построению схемы</b>, а не по совпадению. Тем же наблюдаемым стои́т вторая
 * половина {@code B9.3}: колонки актора у отбора нет вовсе, и различать тропу
 * ему нечем, кроме кода операции.
 *
 * <p><b>Факты лежат в ПОЛНОЧЬ своих суток по общему доводу группы
 * {@code B8}</b>: проход не пишет суток, начавшихся раньше первого факта ряда
 * ({@code dayRecomputable}), и факт в середине суток оставил бы их покрытыми
 * частично — клетка краснела бы по охране отбора, а не по своему предмету.
 *
 * <p><b>Умолчание разрезов у заготовки — ПУСТО</b> ({@link IncidentDraft}), и
 * клетка называет ровно те разрезы, которые её класс несёт. Отсюда ожидание
 * «разрез стои́т на нуле» читается как «разрез подан здоровым», а не как
 * «разрез не подан»: мягкая ступень и отсутствие ступени — разные входы.
 *
 * <p><b>{@code B9.8} единственности МОМЕНТА не утверждает, а называет
 * носителя.</b> Контрольный прогон уронил одной осью — зерно происшествий
 * получило свой момент сборки — разом её и {@code B7.14}
 * ({@link RecomputePassShapeBoxTest}), то есть обе утверждали одно; сильнее
 * соседняя: она мерит единственность момента по ЧЕТЫРЁМ строкам ДВУХ таблиц
 * за двое суток, а здешняя мерила по двум строкам одних суток. Слабейший
 * носитель снят (.claude/rules/carrier-levels.md); здесь остаётся то, чего у
 * соседней нет: один поданный такт собирает ОБА зерна, а реестр планировщика
 * знает ровно один такт пересчёта.
 *
 * <p><b>«Второго ПИСАТЕЛЯ нет» здесь тоже не утверждается</b> — у него свои
 * носители: {@code B7.21} (проход факты только читает) и {@code B3.13}
 * (входящей точки записи у поверхности нет ни одной). Третья запись того же
 * утверждения разошлась бы с первыми при первой же правке.
 *
 * <p><b>Своя группа и своя тема взяты по общему доводу класса кейсов:</b>
 * контексты прогона не закрываются, а группа есть состояние на брокере.
 */
class IncidentGrainCountersBoxTest extends StatisticsBox {

    /** Краткое имя класса: из него строятся его группа и его тема. */
    private static final String SLUG = "b9-incidents";

    /** Сутки, в которых лежат факты клеток группы. */
    private static final Integer DAY = 1;

    /**
     * Поля строки зерна происшествий — ВСЕ, которые отдаёт поверхность.
     *
     * <p>Перечнем этим стоя́т три отрицания группы сразу: определения
     * стратегии и валюты в ключе нет ({@code B9.1}, {@code B9.7}), счётчика
     * остановленных сделок нет ({@code B9.6}), третьего разреза по
     * направлению ручной операции нет ({@code B9.4}). Сличается он
     * <b>дословно</b>: перечень «содержит» пропустил бы лишний счётчик, а
     * отрицания группы утверждают именно о лишнем.
     */
    private static final List<String> ROW_FIELDS = List.of(
            "exchangeAccountInternalId", "bucketDate",
            "openedDeals", "orderDecisions",
            "raisedHolds", "hardRaisedHolds", "manuallyRaisedHolds",
            "anomalyReports", "criticalAnomalyReports", "manualOperationReports",
            "assembledAt");

    /**
     * Колонки таблицы фактов происшествий — ВСЕ.
     *
     * <p>Ими мерится, что предмета события у факта нет ни одной колонкой
     * (отбор поэтому считает события) и что актора у него нет тоже (тропу
     * различает только код операции).
     */
    private static final List<String> FACT_COLUMNS = List.of(
            "event_id", "tenant_id", "exchange_account_internal_id", "occurred_at",
            "event_type", "hold_rung", "anomaly_severity", "operation_code");

    /** Поле счётчика заведённых сделок. */
    private static final String OPENED_DEALS = "openedDeals";

    /** Поле счётчика решений о заявке. */
    private static final String ORDER_DECISIONS = "orderDecisions";

    /** Поле счётчика поднятых ступеней — обе тропы вместе. */
    private static final String RAISED_HOLDS = "raisedHolds";

    /** Поле разреза жёстких ступеней. */
    private static final String HARD_RAISED_HOLDS = "hardRaisedHolds";

    /** Поле разреза ступеней, поднятых ручной тропой. */
    private static final String MANUALLY_RAISED_HOLDS = "manuallyRaisedHolds";

    /** Поле счётчика отчётов о происшествиях — обе тропы вместе. */
    private static final String ANOMALY_REPORTS = "anomalyReports";

    /** Поле разреза критичных отчётов. */
    private static final String CRITICAL_ANOMALY_REPORTS = "criticalAnomalyReports";

    /** Поле разреза отчётов, заведённых ручной тропой. */
    private static final String MANUAL_OPERATION_REPORTS = "manualOperationReports";

    /** Поле определения стратегии: есть у сделочного зерна и нет у этого. */
    private static final String STRATEGY_FIELD = "strategyInternalId";

    /** Поле расчётной валюты: то же различие ключей двух зёрен. */
    private static final String CURRENCY_FIELD = "resultCurrency";

    /** Сколько сделок заведено в сутках клетки о составе счётчиков. */
    private static final Integer OPENED_COUNT = 2;

    /** Сколько решений о заявке там же: числа у классов РАЗНЫЕ намеренно. */
    private static final Integer DECIDED_COUNT = 3;

    /** Сколько ступеней поднято там же. */
    private static final Integer HOLD_COUNT = 4;

    /** Сколько отчётов заведено там же. */
    private static final Integer REPORT_COUNT = 5;

    /** Второе определение стратегии: им мерится, что зерно им не делится. */
    private static final String SECOND_STRATEGY = "S-2";

    /** Насколько позже первой ступени поднята вторая при эскалации. */
    private static final Duration ESCALATION_DELAY = Duration.ofMinutes(5);

    /**
     * Контекст прогона: им читается реестр задач планировщика.
     *
     * <p><b>Бина сервиса это не подменяет</b> — контекст читается, а не
     * правится, — и признак ящика остаётся выполненным.
     */
    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, SLUG);
    }

    @Test
    @DisplayName("B9.1 — Каждый счётчик отбирает строки фактов по классу события")
    void everyCounterSelectsItsOwnFactRowsByEventClass() {
        put(OPENED_COUNT, "E-9-1-O", DEAL_OPENED);
        put(DECIDED_COUNT, "E-9-1-D", ORDER_DECIDED);
        IntStream.range(0, HOLD_COUNT).forEach(index ->
                hold("E-9-1-H" + index, Bodies.SOFT, null, midnightDaysAgo(DAY)));
        IntStream.range(0, REPORT_COUNT).forEach(index ->
                report("E-9-1-A" + index, Bodies.NON_CRITICAL, null));

        recompute();

        Map<String, Object> row = incidentRow();
        assertThat(row.get(OPENED_DEALS))
                .as("числа у четырёх классов РАЗНЫЕ намеренно: при совпадении перепутанный "
                        + "отбор сошёлся бы зелёным").isEqualTo(OPENED_COUNT);
        assertThat(row.get(ORDER_DECISIONS)).isEqualTo(DECIDED_COUNT);
        assertThat(row.get(RAISED_HOLDS)).isEqualTo(HOLD_COUNT);
        assertThat(row.get(ANOMALY_REPORTS)).isEqualTo(REPORT_COUNT);
        assertThat(row.get(HARD_RAISED_HOLDS))
                .as("жёстких нет: ступени поданы МЯГКИМИ, а не без разреза").isEqualTo(0);
        assertThat(row.get(CRITICAL_ANOMALY_REPORTS))
                .as("критичных нет: отчёты поданы некритичными").isEqualTo(0);
        assertThat(row.get(MANUALLY_RAISED_HOLDS)).isEqualTo(0);
        assertThat(row.get(MANUAL_OPERATION_REPORTS)).isEqualTo(0);
        assertThat(rows.columnNames(INCIDENT_FACTS))
                .as("колонки ПРЕДМЕТА — сделки, заявки, инструмента — у факта нет ни одной: "
                        + "оттого отбор по классу равен числу событий по построению схемы")
                .containsExactlyInAnyOrderElementsOf(FACT_COLUMNS);
        assertThat(row.keySet())
                .as("ключ строки — счёт и сутки: определения стратегии и валюты в нём нет")
                .containsExactlyInAnyOrderElementsOf(ROW_FIELDS);
    }

    @Test
    @DisplayName("B9.2 — Разрез по ступени: жёсткие выделены из общего числа")
    void theHardRungCutIsSeparatedFromTheTotalNumberOfRaisedHolds() {
        hold("E-9-2-SOFT-1", Bodies.SOFT, null, midnightDaysAgo(DAY));
        hold("E-9-2-SOFT-2", Bodies.SOFT, null, midnightDaysAgo(DAY));
        hold("E-9-2-HARD", Bodies.HARD, null, midnightDaysAgo(DAY));

        recompute();

        Map<String, Object> row = incidentRow();
        assertThat(row.get(RAISED_HOLDS))
                .as("обе тропы вместе: поднятых ступеней три").isEqualTo(3);
        assertThat(row.get(HARD_RAISED_HOLDS))
                .as("без этого разреза три мягких инструментных холда и два мягких плюс "
                        + "сворачивание счёта читались бы одинаково").isEqualTo(1);
        assertThat(row.get(MANUALLY_RAISED_HOLDS))
                .as("кодов операции ни у одной ступени нет: ручная тропа пуста").isEqualTo(0);
    }

    @Test
    @DisplayName("B9.3 — Разрез по тропе различает КОД операции, а не актора")
    void theManualPathCutTellsTheOperationCodeApartRatherThanTheActor() {
        hold("E-9-3-MANUAL", Bodies.SOFT, Bodies.MANUAL_HALT_REQUESTED, midnightDaysAgo(DAY));
        // У второй ступени код пуст, а актор строки — принципал ручного
        // запуска: у джобы, запущенной ручным триггером, он именно таков.
        hold("E-9-3-AUTOMATIC", Bodies.SOFT, null, midnightDaysAgo(DAY));

        recompute();

        Map<String, Object> row = incidentRow();
        assertThat(row.get(RAISED_HOLDS)).as("подъёмов два").isEqualTo(2);
        assertThat(row.get(MANUALLY_RAISED_HOLDS))
                .as("ручной считается ПЕРВАЯ — та, у которой код принадлежит поверхности; "
                        + "вторая в ручные не попала, хотя актор у неё принципал").isEqualTo(1);
        assertThat(rows.columnNames(INCIDENT_FACTS))
                .as("колонки актора у отбора нет вовсе — различать тропу ему нечем, кроме кода")
                .containsExactlyInAnyOrderElementsOf(FACT_COLUMNS);
    }

    @Test
    @DisplayName("B9.4 — Разрезы отчёта: критичность и ручная тропа")
    void theReportCutsAreSeverityAndTheManualPath() {
        report("E-9-4-CRITICAL", Bodies.CRITICAL, null);
        report("E-9-4-AUTOMATIC", Bodies.NON_CRITICAL, null);
        report("E-9-4-MANUAL", Bodies.NON_CRITICAL, Bodies.MANUAL_HALT_CLEARED);

        recompute();

        Map<String, Object> row = incidentRow();
        assertThat(row.get(ANOMALY_REPORTS)).as("обе тропы вместе: отчётов три").isEqualTo(3);
        assertThat(row.get(CRITICAL_ANOMALY_REPORTS))
                .as("«kill-switch гонялся» и «не гонялся» в одном числе неразличимы").isEqualTo(1);
        assertThat(row.get(MANUAL_OPERATION_REPORTS))
                .as("ручной тропой заведён один — тот, у которого код принадлежит поверхности")
                .isEqualTo(1);
        assertThat(row.keySet())
                .as("направление ручной операции третьим разрезом не разводится: счётчика "
                        + "под постановку и под снятие по отдельности в строке нет")
                .containsExactlyInAnyOrderElementsOf(ROW_FIELDS);
    }

    @Test
    @DisplayName("B9.5 — Эскалация мягкой ступени в жёсткую даёт ВТОРОЙ подъём")
    void escalatingASoftRungIntoAHardOneGivesASecondRaise() {
        // Объект под ступенью — биржевой счёт: обе строки несут его, то есть
        // вторая ступень поднята на ТОМ ЖЕ объекте, что и первая.
        hold("E-9-5-SOFT", Bodies.SOFT, null, midnightDaysAgo(DAY));
        hold("E-9-5-HARD", Bodies.HARD, null, midnightDaysAgo(DAY).plus(ESCALATION_DELAY));

        recompute();

        Map<String, Object> row = incidentRow();
        assertThat(row.get(RAISED_HOLDS))
                .as("счётчик считает ПОДЪЁМЫ, а не объекты под ступенью: вторая ступень на том "
                        + "же объекте — второе решение, а не дубль первого").isEqualTo(2);
        assertThat(row.get(HARD_RAISED_HOLDS)).as("жёсткой из них стала одна").isEqualTo(1);
    }

    @Test
    @DisplayName("B9.6 — Счётчика остановленных сделок в перечне нет")
    void thereIsNoCounterOfShutDownDeals() {
        givenReceptionStateRows();
        OffsetDateTime earlier = momentsAgo(Duration.ofHours(2));
        OffsetDateTime later = momentsAgo(Duration.ofMinutes(5));
        givenReceptionOf(topic(), earlier, Boolean.FALSE);
        Facts.incident("E-9-6-OPENED", TENANT, DEAL_OPENED, midnightDaysAgo(DAY));

        // Два ребра присвоения причины у ОДНОЙ сделки: счётчик, заведённый
        // общей формой, дал бы ей вес два.
        publish("E-9-6-SHUTDOWN-1", NOT_CARRIED, later, Bodies.incident(Facts.ACCOUNT));
        publish("E-9-6-SHUTDOWN-2", NOT_CARRIED, later, Bodies.incident(Facts.ACCOUNT));
        awaitConsumed();
        recompute();

        assertThat(incidentFacts())
                .as("класс несомым не является: фактов по двум рёбрам не появилось, и лежит "
                        + "один — положенный клеткой").hasSize(1);
        Map<String, Object> row = incidentRow();
        assertThat(row.get(OPENED_DEALS)).as("собралось заведение сделки, и только оно")
                .isEqualTo(1);
        assertThat(row.keySet())
                .as("счётчика под остановки в строке нет ни одного")
                .containsExactlyInAnyOrderElementsOf(ROW_FIELDS);
        assertThat(instant(pair(topic()), LAST_ACCEPTED_COLUMN))
                .as("момент последнего принятого при этом двигается: записи обработаны")
                .isEqualTo(later.toInstant());
    }

    @Test
    @DisplayName("B9.7 — Зерно происшествий не делится определением и валютой")
    void theIncidentGrainIsSplitNeitherByStrategyNorByCurrency() {
        publish("E-9-7-FIRST", DEAL_OPENED, midnightDaysAgo(DAY),
                Bodies.incidentOfStrategy(Facts.ACCOUNT, Facts.STRATEGY));
        publish("E-9-7-SECOND", DEAL_OPENED, midnightDaysAgo(DAY),
                Bodies.incidentOfStrategy(Facts.ACCOUNT, SECOND_STRATEGY));
        awaitConsumed();

        recompute();

        List<Map<String, Object>> handed = incidentRows();
        assertThat(handed)
                .as("определения у событий разные, а строка ОДНА: зерно ими не делится")
                .hasSize(1);
        Map<String, Object> row = handed.getFirst();
        assertThat(row.get(OPENED_DEALS))
                .as("счётчики сложены по счёту и суткам").isEqualTo(2);
        assertThat(row.keySet())
                .as("колонок определения и валюты у строки нет вовсе")
                .containsExactlyInAnyOrderElementsOf(ROW_FIELDS);
        assertThat(row).doesNotContainKeys(STRATEGY_FIELD, CURRENCY_FIELD);
    }

    @Test
    @DisplayName("B9.8 — Оба зерна складываются тем же проходом и той же джобой")
    void bothGrainsAreAssembledByTheSamePassAndTheSameJob() {
        Facts.deal("E-9-8-DEAL", TENANT, midnightDaysAgo(DAY));
        Facts.incident("E-9-8-INCIDENT", TENANT, DEAL_OPENED, midnightDaysAgo(DAY));

        recompute();

        Map<String, Object> deal = rowOf(aggregates(DEAL_GRAIN, TENANT).dealRows(), DAY);
        Map<String, Object> incident = incidentRow();
        assertThat(deal).as("один поданный такт собрал сделочное зерно").isNotEmpty();
        assertThat(incident).as("и тем же тактом — зерно происшествий").isNotEmpty();
        assertThat(deal.keySet())
                .as("расходятся зёрна ключами строки: у сделочного определение и валюта есть")
                .contains(STRATEGY_FIELD, CURRENCY_FIELD);
        assertThat(incident.keySet())
                .as("а у зерна происшествий их нет")
                .containsExactlyInAnyOrderElementsOf(ROW_FIELDS);

        Set<String> ticks = scheduledTicks();
        assertThat(ticks)
                .as("тиков у сервиса ровно два — состояние приёма и пересчёт; третьего "
                        + "расписания нет ни одного")
                .hasSize(2);
        assertThat(ticks.stream().filter(this::recomputesAggregates).toList())
                .as("такт пересчёта ОДИН: второй джобы под второе зерно не существует")
                .hasSize(1);
        assertThat(ticks.stream().filter(tick -> tick.contains(ReceptionStateJob.class.getName())))
                .as("второй — тик состояния приёма, и агрегатов он не собирает").hasSize(1);
    }

    /**
     * Кладёт названное число фактов одного класса без разрезов.
     *
     * @param count     сколько фактов положить
     * @param prefix    начало идентичности: к нему приписывается номер
     * @param eventType класс события
     */
    private void put(Integer count, String prefix, String eventType) {
        IntStream.range(0, count).forEach(index ->
                Facts.incident(prefix + index, TENANT, eventType, midnightDaysAgo(DAY)));
    }

    /**
     * Кладёт факт подъёма ступени с названными разрезами.
     *
     * @param eventId    идентичность события
     * @param rung       жёсткость поднятой ступени
     * @param code       код операции; пусто означает «тропа не ручная»
     * @param occurredAt момент происшествия
     */
    private void hold(String eventId, String rung, String code, OffsetDateTime occurredAt) {
        IncidentDraft.of(eventId, TENANT, HOLD_RAISED, occurredAt)
                .holdRung(rung)
                .operationCode(code)
                .build()
                .put();
    }

    /**
     * Кладёт факт отчёта о происшествии с названными разрезами.
     *
     * @param eventId  идентичность события
     * @param severity критичность отчёта
     * @param code     код операции; пусто означает «тропа не ручная»
     */
    private void report(String eventId, String severity, String code) {
        IncidentDraft.of(eventId, TENANT, ANOMALY_REPORTED, midnightDaysAgo(DAY))
                .anomalySeverity(severity)
                .operationCode(code)
                .build()
                .put();
    }

    /** Строки зерна происшествий, отданные поверхностью за окно пересчёта. */
    private List<Map<String, Object>> incidentRows() {
        return aggregates(INCIDENT_GRAIN, TENANT).incidentRows();
    }

    /** Строка зерна происшествий тех суток, в которых лежат факты клетки. */
    private Map<String, Object> incidentRow() {
        return rowOf(incidentRows(), DAY);
    }

    /**
     * Такты, которые планировщик ПОДНЯТОГО контекста ведёт по расписанию.
     *
     * <p><b>Читается реестр контекста, а не дерево исходников.</b> Счёт
     * объявлений в дереве уже мерит своя проба ({@code SchedulerCapacityTest}),
     * и вторая её запись сверяла бы тот же текст; ящику же нужен состав
     * поднятого контекста — то есть то, что действительно бьёт.
     */
    private Set<String> scheduledTicks() {
        return context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(ScheduledTask::toString)
                .collect(Collectors.toSet());
    }

    /**
     * Такт принадлежит джобе пересчёта агрегатов.
     *
     * @param tick запись такта из реестра планировщика
     */
    private Boolean recomputesAggregates(String tick) {
        return tick.contains(AggregateRecomputeJob.class.getName());
    }
}
