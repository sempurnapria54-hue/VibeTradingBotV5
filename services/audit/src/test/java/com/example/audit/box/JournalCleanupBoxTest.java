package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Группа {@code B7} документа кейсов — та её часть, которой штатное
 * положение осей конфигурации является входом
 * (.claude/tests/cases/audit.md §«B7 — Чистка журнала: применимость из
 * оси, глубина из конфигурации»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у трёх соседних
 * групп: десять клеток берут штатные оси у {@link SharedAuditBox} —
 * чистящий профиль хранения, поднятый выключатель, глубину умолчания, — и
 * расходятся только состоянием журнала и строк пар. Три клетки, у которых
 * входом служит САМА ось (неограниченный профиль, недоехавшая ось, снятый
 * выключатель), живут своими классами и платят за это своим подъёмом.
 *
 * <p><b>Такт чистки подаётся прямым вызовом метода джобы</b>
 * ({@link AuditBox#cleanup}): ручного фасада у неё нет намеренно —
 * поверхность сервиса объявлена только читающей
 * (docs/components/JournalCleanupJob.md §«Форма — джоба без ручного
 * фасада, и это объявлено»), — а расписание выражено CRON, до которого
 * прогон не доживает.
 *
 * <p><b>ВОЗРАСТ строк ставится в данных, и тропы у него нет ни при какой
 * расстановке:</b> глубина назначается сутками, а прогон живёт секунды
 * ({@link AuditBox#recordedEarlier}, {@link #givenAgedRecords}). Часы
 * процесса при этом не двигаются
 * (.claude/tests/cases/audit.md §«Чем достаются выходы»).
 *
 * <p><b>Состаренная строка стареет ОБЕИМИ осями, кроме той клетки, чей
 * предмет — их различение.</b> Иначе мутация «отсчёт по происшествию»
 * роняла бы весь класс разом и не различала бы ничего: краснота читалась
 * бы как «клетки что-то мерят», а не как «клетка мерит ось отсчёта».
 * Различает их {@code B7.2}, и только она кладёт строки с расходящимися
 * осями.
 *
 * <p><b>Момент наблюдения и момент разрыва ставятся прямой записью</b>
 * ({@link AuditBox#givenPair}), и довод у обоих свой: первый тик ставит
 * своим тактом — то есть «сейчас», — а клетке о ДВИЖЕНИИ границы нужен
 * момент позади моментов приёма; второй пишет обнаружение по смещениям,
 * то есть состояние группы на брокере, принадлежащее своему классу
 * (§«B4 — Обнаружение разрыва: три исхода сравнения смещений»).
 */
class JournalCleanupBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходят все клетки класса. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Тема второго производителя: ею наблюдается радиус снятия разрыва. */
    private static final String STRATEGY = AuditSubstrate.STRATEGY_TOPIC;

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Насколько глубже назначенной глубины лежит строка, которую уносит проход. */
    private static final Duration BEYOND_DEPTH = Duration.ofDays(60);

    /** Возраст уцелевающей строки: за глубину он не выходит. */
    private static final Duration WITHIN_DEPTH = Duration.ofHours(1);

    /** Насколько раньше «сейчас» пары наблюдают свои темы. */
    private static final Duration OBSERVED_AGO = Duration.ofHours(2);

    /**
     * Возраст разрыва, который проход выносит ЗА новую границу.
     *
     * <p>Лежит между двумя границами: позже той, что была до прохода
     * (момент наблюдения, {@link #OBSERVED_AGO}), и раньше той, которую
     * проход создаст (момент приёма уцелевшей строки,
     * {@link #WITHIN_DEPTH}).
     */
    private static final Duration GAP_OUTSIDE_NEW_BOUND = Duration.ofMinutes(90);

    /** Возраст разрыва, остающегося ВНУТРИ новой границы. */
    private static final Duration GAP_INSIDE_NEW_BOUND = Duration.ofMinutes(30);

    /**
     * Сколько строк за глубиной кладёт клетка о порциях.
     *
     * <p><b>Число взято заведомо больше порции удаления, и цена этого
     * названа.</b> Порция объявлена локальной технической константой
     * исполнителя — у неё нет ни владельца, ни якоря калибровки, ни
     * влияния на исход прохода
     * (docs/components/JournalCleanupJob.md §«Чем ограничен проход:
 * применимость из оси, глубина из конфигурации»), —
     * поэтому ящику она не видна и видна быть не должна. Цена: порция,
     * поднятая выше этого числа, оставит клетку зелёной и холостой —
     * проход уложится в один ход, и цикла она мерить перестанет.
     * Доказывает клетку мутационная ось, снимающая цикл, а не само число.
     */
    private static final Integer BEYOND_ONE_BATCH = 2_500;

    /** Сколько строк одного возраста накопил каждый пропущенный такт. */
    private static final Integer MISSED_PER_TICK = 3;

    /** Возрасты строк, накопленных за пропущенные такты. */
    private static final List<Duration> MISSED_AGES = List.of(
            Duration.ofDays(60), Duration.ofDays(45), Duration.ofDays(31));

    /** Сколько строк кладёт клетка о перекрывающем такте. */
    private static final Integer OVERLAPPING_ROWS = 200;

    /**
     * Потолок ожидания перекрывающего такта.
     *
     * <p>Ограничен, потому что предмет клетки и есть ВОЗВРАТ: такт,
     * дождавшийся первого прохода, охраной не пропущен, и различает их
     * ровно срок.
     */
    private static final Duration SKIP_WINDOW = Duration.ofSeconds(15);

    /** Запись охраны о пропущенном такте — единственный её наблюдаемый след. */
    private static final String OVERLAP_SKIPPED = "journalCleanupJob is already running";

    /** Пути ручного триггера джоб по конвенции соседних сервисов. */
    private static final List<String> TRIGGER_PATHS = List.of(
            "/api/v1/audit/jobs",
            "/api/v1/audit/jobs/journal-cleanup",
            "/api/v1/audit/journal/cleanup");

    /** Методы, которыми перебираются пути ручного триггера. */
    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    /** Колонки строки пары, которых проход не касается ни одной. */
    private static final List<String> UNTOUCHED_COLUMNS = List.of(
            GROUP_COLUMN, TOPIC_COLUMN, OBSERVED_COLUMN, SUBSCRIBED_COLUMN,
            HALTED_COLUMN, LAST_ACCEPTED_COLUMN, UPDATED_COLUMN);

    /** Вставка накопленных строк журнала: обе оси времени у них одинаковы. */
    private static final String INSERT_AGED_RECORDS = """
            insert into audit_records
                (event_id, tenant_id, event_type, occurred_at, recorded_at, version, content)
            select cast(? as varchar) || cast(series as varchar), ?, ?, ?, ?, 1, '{}'
              from generate_series(1, cast(? as integer)) as series
            """;

    /** Вставка строки следа отказа доступа: глубины хранения у неё нет. */
    private static final String INSERT_DENIAL = """
            insert into access_denials (internal_id, surface, outcome, created_at)
            values (?, ?, ?, ?)
            """;

    @Test
    @DisplayName("B7.1 — Чистящий профиль удаляет вышедшее за глубину")
    void aCleaningProfileRemovesWhatFellOutOfTheDepth() {
        givenReceptionStateRows();
        publish(CORE, "E-AGED", momentsAgo(BEYOND_DEPTH), Bodies.reference());
        publish(CORE, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        awaitConsumed(CORE);
        recordedEarlier("E-AGED", momentsAgo(BEYOND_DEPTH));
        givenAccessDenial("AD-1", now());
        Map<String, Object> pairBefore = pair(CORE);
        Long coreEnd = Wire.endOffset(CORE);
        Long strategyEnd = Wire.endOffset(STRATEGY);

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("строк за глубиной не осталось ни одной").isEqualTo(1L);
        assertThat(record().get("event_id"))
                .as("а строки внутри глубины целы все").isEqualTo("E-SURVIVING");
        assertThat(rows.count(RECEPTION_TABLE))
                .as("строк состояния приёма проход не удалил").isEqualTo(2L);
        assertThat(pair(CORE))
                .as("и ни одной их колонки не тронул: разрыва у пары не было")
                .isEqualTo(pairBefore);
        assertThat(rows.count(DENIALS_TABLE))
                .as("следа отказа доступа проход не тронул").isEqualTo(1L);
        assertThat(Wire.endOffset(CORE))
                .as("наружу проход не публикует ничего: конец темы не двинулся")
                .isEqualTo(coreEnd);
        assertThat(Wire.endOffset(STRATEGY)).isEqualTo(strategyEnd);
    }

    @Test
    @DisplayName("B7.2 — Отсчёт идёт по моменту ПРИЁМА, а не происшествия")
    void theDepthIsCountedFromReceptionRatherThanOccurrence() {
        givenReceptionStateRows();
        publish(CORE, "E-OCCURRED-BEYOND", momentsAgo(BEYOND_DEPTH), Bodies.reference());
        publish(CORE, "E-RECORDED-BEYOND", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        awaitConsumed(CORE);
        recordedEarlier("E-RECORDED-BEYOND", momentsAgo(BEYOND_DEPTH));

        assertThat(instant(recordOf("E-OCCURRED-BEYOND"), OCCURRED_COLUMN))
                .as("вход поставлен: у первой строки за глубиной момент ПРОИСШЕСТВИЯ")
                .isBefore(instant(recordOf("E-RECORDED-BEYOND"), OCCURRED_COLUMN));
        assertThat(instant(recordOf("E-RECORDED-BEYOND"), RECORDED_COLUMN))
                .as("а у второй — момент ПРИЁМА, и оси у них разведены")
                .isBefore(instant(recordOf("E-OCCURRED-BEYOND"), RECORDED_COLUMN));

        cleanup();

        assertThat(records()).as("удалена ровно одна строка").hasSize(1);
        assertThat(record().get("event_id"))
                .as("удалена та, чей момент ПРИЁМА вышел за глубину")
                .isEqualTo("E-OCCURRED-BEYOND");
        assertThat(lowerBoundMoment().toInstant())
                .as("граница после прохода считается по тому же моменту, по которому шло удаление")
                .isEqualTo(instant(record(), RECORDED_COLUMN));
        assertThat(lowerBoundMoment().toInstant())
                .as("и на ось происшествия она не опускается")
                .isAfter(instant(record(), OCCURRED_COLUMN));
    }

    @Test
    @DisplayName("B7.6 — Удаление идёт порциями до исчерпания отбора")
    void theDeletionRunsInBatchesUntilTheSelectionIsExhausted() {
        givenReceptionStateRows();
        publish(CORE, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(1L);
        givenAgedRecords("E-BULK-", BEYOND_ONE_BATCH, momentsAgo(BEYOND_DEPTH));
        assertThat(rows.count(JOURNAL_TABLE))
                .as("вход поставлен: строк за глубиной заведомо больше одной порции")
                .isEqualTo(BEYOND_ONE_BATCH + 1L);

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("по окончании прохода строк за глубиной не осталось ни одной: "
                        + "он не оборвался на первой порции")
                .isEqualTo(1L);
        assertThat(record().get("event_id"))
                .as("а строка внутри глубины цела").isEqualTo("E-SURVIVING");
    }

    @Test
    @DisplayName("B7.7 — Момент разрыва гаснет по НОВОЙ границе, и порядок ходов несущий")
    void theGapMomentIsClearedAgainstTheBoundThisPassItselfCreated() {
        OffsetDateTime gapAt = givenAgedJournalAndPairs(momentsAgo(GAP_OUTSIDE_NEW_BOUND));
        OffsetDateTime boundBefore = lowerBoundMoment();

        assertThat(boundBefore.toInstant())
                .as("вход поставлен: разрыв лежит ПОЗЖЕ границы до прохода")
                .isBefore(gapAt.toInstant());
        assertThat(continuityClaimable())
                .as("и предикат по паре до прохода ложен").isEqualTo(Boolean.FALSE);

        cleanup();

        assertThat(pair(CORE).get(GAP_COLUMN))
                .as("разрыв погашен: снятие мерит границу, подвинутую ЭТИМ же проходом")
                .isNull();
        assertThat(lowerBoundMoment().toInstant())
                .as("и граница эта — момент приёма самой ранней уцелевшей строки")
                .isEqualTo(instant(record(), RECORDED_COLUMN));
        assertThat(lowerBoundMoment().toInstant())
                .as("то есть та, которой до прохода не существовало")
                .isAfter(gapAt.toInstant());
        assertThat(continuityClaimable())
                .as("предикат снова утверждаем ТЕМ ЖЕ ходом, а не следующим тактом")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B7.8 — Разрыв внутри новой границы не гаснет")
    void aGapInsideTheNewBoundSurvivesThePass() {
        OffsetDateTime gapAt = givenAgedJournalAndPairs(momentsAgo(GAP_INSIDE_NEW_BOUND));
        OffsetDateTime boundBefore = lowerBoundMoment();

        cleanup();

        assertThat(lowerBoundMoment().toInstant())
                .as("граница двинулась вперёд: проход своё дело сделал")
                .isAfter(boundBefore.toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("а разрыв лежит ПОЗЖЕ неё — внутри обещаемого ряда")
                .isBefore(gapAt.toInstant());
        assertThat(instant(pair(CORE), GAP_COLUMN))
                .as("поэтому момент разрыва цел").isEqualTo(gapAt.toInstant());
        assertThat(continuityClaimable())
                .as("и предикат непрерывности остаётся ложным").isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B7.9 — Подписанных пар нет: гасить нечем, и проход это переживает")
    void withoutSubscribedPairsThereIsNothingToClearAndThePassSurvivesIt() {
        publish(CORE, "E-AGED", momentsAgo(BEYOND_DEPTH), Bodies.reference());
        awaitRecordCount(1L);
        recordedEarlier("E-AGED", momentsAgo(BEYOND_DEPTH));

        assertThat(rows.count(RECEPTION_TABLE))
                .as("вход поставлен: строк состояния нет ни одной — такта не было").isZero();
        assertThat(lowerBound()).as("и границы поэтому нет").isNull();

        assertThatCode(this::cleanup)
                .as("проход завершился штатно: отсутствие границы отказом не является")
                .doesNotThrowAnyException();

        assertThat(rows.count(JOURNAL_TABLE)).as("удаление при этом прошло").isZero();
        assertThat(rows.count(RECEPTION_TABLE))
                .as("строк состояния проход не завёл").isZero();
        assertThat(lowerBound()).as("границы по-прежнему нет").isNull();
        assertThat(continuityClaimable())
                .as("и непрерывность не утверждаема — область квантора пуста")
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("B7.10 — Проход не трогает следа отказа доступа")
    void thePassLeavesTheAccessDenialTrailAlone() {
        givenReceptionStateRows();
        publish(CORE, "E-AGED", momentsAgo(BEYOND_DEPTH), Bodies.reference());
        awaitRecordCount(1L);
        recordedEarlier("E-AGED", momentsAgo(BEYOND_DEPTH));
        givenAccessDenial("AD-BEYOND-DEPTH", momentsAgo(BEYOND_DEPTH));
        givenAccessDenial("AD-WITHIN-DEPTH", momentsAgo(EVENT_AGE));

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("удалены только строки журнала").isZero();
        assertThat(rows.count(DENIALS_TABLE))
                .as("строки отказов целы все: журнальной глубины им не приписывают")
                .isEqualTo(2L);
        assertThat(rows.row(DENIALS_TABLE, "internal_id", "AD-BEYOND-DEPTH"))
                .as("включая ту, что старше журнальной глубины").isNotEmpty();
    }

    @Test
    @DisplayName("B7.11 — Проход не трогает строк состояния, кроме момента разрыва")
    void thePassTouchesNoReceptionColumnOtherThanTheGapMoment() {
        givenAgedJournal();
        givenPair(CORE, momentsAgo(OBSERVED_AGO), Boolean.TRUE, Boolean.TRUE,
                momentsAgo(GAP_OUTSIDE_NEW_BOUND), momentsAgo(EVENT_AGE));
        givenPair(STRATEGY, momentsAgo(OBSERVED_AGO.plusMinutes(1)), Boolean.FALSE, Boolean.FALSE,
                null, null);
        Map<String, Object> coreBefore = pair(CORE);
        Map<String, Object> strategyBefore = pair(STRATEGY);
        String strategyVersion = pairVersion(STRATEGY);

        assertThat(coreBefore.get(GAP_COLUMN))
                .as("вход поставлен: гасить было что").isNotNull();

        cleanup();

        assertThat(pair(CORE).get(GAP_COLUMN))
                .as("изменилась колонка момента разрыва").isNull();
        for (String column : UNTOUCHED_COLUMNS) {
            assertThat(pair(CORE).get(column))
                    .as("и только она: колонка " + column + " не тронута")
                    .isEqualTo(coreBefore.get(column));
        }
        assertThat(pair(STRATEGY))
                .as("у пары без разрыва не тронуто вовсе ничего").isEqualTo(strategyBefore);
        assertThat(pairVersion(STRATEGY))
                .as("её строка не переписана даже тем же значением").isEqualTo(strategyVersion);
        assertThat(rows.count(RECEPTION_TABLE))
                .as("строк состояния не удалено и не заведено").isEqualTo(2L);
    }

    @Test
    @DisplayName("B7.12 — Перекрывающий запуск чистки гасится")
    void anOverlappingCleanupRunIsSkipped() {
        givenReceptionStateRows();
        publish(CORE, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(1L);
        givenAgedRecords("E-BULK-", OVERLAPPING_ROWS, momentsAgo(BEYOND_DEPTH));
        AtomicReference<Future<?>> held = new AtomicReference<>();
        ExecutorService passes = Executors.newFixedThreadPool(2);
        Integer logMark = AppLog.mark();

        try {
            rows.withTableLocked(JOURNAL_TABLE, () -> {
                held.set(passes.submit(this::cleanup));
                awaitBlockedOnJournal();
                Future<?> overlapping = passes.submit(this::cleanup);

                assertThatCode(() -> overlapping.get(SKIP_WINDOW.toMillis(), TimeUnit.MILLISECONDS))
                        .as("перекрывающий такт вернулся, не дождавшись идущего прохода")
                        .doesNotThrowAnyException();
                assertThat(AppLog.since(logMark))
                        .as("и вернулся он ПРОПУСКОМ: база обоих исходов не различает — "
                                + "удаление идемпотентно")
                        .contains(OVERLAP_SKIPPED);
            });
            Awaitility.await().atMost(RECEPTION_WAIT).pollInterval(POLL).until(held.get()::isDone);
            assertThatCode(() -> held.get().get())
                    .as("исход первого прохода перекрывающий такт не изменил")
                    .doesNotThrowAnyException();
        } finally {
            passes.shutdownNow();
        }

        assertThat(rows.count(JOURNAL_TABLE))
                .as("удалено ровно положенное: не больше и не меньше").isEqualTo(1L);
        assertThat(record().get("event_id")).isEqualTo("E-SURVIVING");
    }

    @Test
    @DisplayName("B7.13 — Пропущенный проход невыполненной работы не оставляет")
    void aMissedPassLeavesNoUndoneWorkBehind() {
        givenReceptionStateRows();
        publish(CORE, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(1L);
        for (Duration age : MISSED_AGES) {
            givenAgedRecords("E-MISSED-" + age.toDays() + "-", MISSED_PER_TICK, momentsAgo(age));
        }
        assertThat(rows.count(JOURNAL_TABLE))
                .as("вход поставлен: за пропуск накопились строки разного возраста, все за глубиной")
                .isEqualTo(MISSED_AGES.size() * MISSED_PER_TICK + 1L);

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("один проход унёс всё накопленное, включая старейшее").isEqualTo(1L);
        assertThat(record().get("event_id")).isEqualTo("E-SURVIVING");
        String survivorVersion = rows.rowVersion(JOURNAL_TABLE, "event_id", "E-SURVIVING");

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE))
                .as("догоняющего прохода не требуется: второй не нашёл работы").isEqualTo(1L);
        assertThat(rows.rowVersion(JOURNAL_TABLE, "event_id", "E-SURVIVING"))
                .as("и уцелевшей строки он не переписал").isEqualTo(survivorVersion);
        for (String path : TRIGGER_PATHS) {
            for (String method : METHODS) {
                assertThat(call(method, path).status())
                        .as("ручного фасада для догона нет: " + method + " " + path)
                        .isEqualTo(404);
            }
        }
    }

    /**
     * Ставит общий вход трёх клеток о границе: журнал с одной строкой за
     * глубиной и одной внутри неё плюс две строки пар, из которых первая
     * несёт названный момент разрыва.
     *
     * <p><b>Момент наблюдения кладётся ПОЗАДИ обоих моментов приёма</b> —
     * иначе граница равнялась бы ему в обеих расстановках, и проход не
     * двигал бы её вовсе: она есть позднейший из двух операндов
     * (docs/spec/durable-reception.json, {@code receptionLowerBound}).
     *
     * @param gapAt момент разрыва первой пары
     * @return он же — им клетки читают обе стороны сравнения
     */
    private OffsetDateTime givenAgedJournalAndPairs(OffsetDateTime gapAt) {
        givenAgedJournal();
        givenPair(CORE, momentsAgo(OBSERVED_AGO), Boolean.TRUE, Boolean.FALSE, gapAt, null);
        givenPair(STRATEGY, momentsAgo(OBSERVED_AGO), Boolean.TRUE, Boolean.FALSE, null, null);
        return gapAt;
    }

    /**
     * Кладёт журнал из двух строк: одна за глубиной, другая внутри неё и
     * позади «сейчас» на час.
     *
     * <p><b>Возраст уцелевающей строки ставится тоже, и это несущее.</b>
     * Момент приёма, оставленный «сейчас», лежал бы позже момента
     * наблюдения пары при любой его расстановке, и обе границы — до
     * прохода и после — оказались бы у одной стороны сравнения.
     */
    private void givenAgedJournal() {
        publish(CORE, "E-AGED", momentsAgo(BEYOND_DEPTH), Bodies.reference());
        publish(CORE, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        awaitConsumed(CORE);
        recordedEarlier("E-AGED", momentsAgo(BEYOND_DEPTH));
        recordedEarlier("E-SURVIVING", momentsAgo(WITHIN_DEPTH));
    }

    /**
     * Кладёт названное число строк журнала одного возраста.
     *
     * <p><b>Обе оси времени у них одинаковы намеренно:</b> предмет этих
     * клеток — объём и накопление, а не различение осей, и расходящиеся
     * моменты сделали бы их вторым носителем клетки {@code B7.2}.
     *
     * <p><b>Кладутся они прямой записью, и тропы у такого состояния нет:</b>
     * возраст за глубиной ставится только данными, а несколько тысяч строк
     * приезжают через единственный поток слушателя по одной.
     *
     * @param prefix     начало идентичности: им различаются пачки
     * @param count      сколько строк класть
     * @param recordedAt момент приёма и происшествия у всей пачки
     */
    private void givenAgedRecords(String prefix, Integer count, OffsetDateTime recordedAt) {
        rows.write(INSERT_AGED_RECORDS, prefix, TENANT, DEAL_OPENED, recordedAt, recordedAt, count);
    }

    /**
     * Кладёт строку следа отказа доступа названного возраста.
     *
     * <p>Тропой её ставит отвергнутый по правам вызов ({@code B9}), но
     * возраст за журнальной глубиной у неё той же тропой не получается —
     * по тому же доводу, что у строки журнала.
     *
     * @param internalId идентичность строки
     * @param createdAt  момент её создания
     */
    private void givenAccessDenial(String internalId, OffsetDateTime createdAt) {
        rows.write(INSERT_DENIAL, internalId, "GET " + JOURNAL_RECORDS, "PRINCIPAL_ABSENT",
                createdAt);
    }

    /** Ждёт, пока идущий проход встанет в очередь за замком журнала. */
    private void awaitBlockedOnJournal() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> rows.waitingLocksOn(JOURNAL_TABLE) > 0L);
    }

    /** Строка журнала названного события; иное число строк — падение. */
    private Map<String, Object> recordOf(String eventId) {
        return rows.row(JOURNAL_TABLE, "event_id", eventId);
    }
}
