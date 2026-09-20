package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Клетки {@code B5.1}, {@code B5.2}, {@code B5.3}, {@code B5.6} и
 * {@code B5.7} — нижняя граница полноты на штатном положении осей
 * (.claude/tests/cases/audit.md §«B5 — Полнота: нижняя граница»).
 *
 * <p><b>Класс равен КОНФИГУРАЦИИ КОНТЕКСТА</b>, как у соседних групп: все
 * пять клеток берут штатные оси у {@link SharedAuditBox} — подписку на обе
 * темы, включённый тик, чистящий профиль хранения и глубину умолчания, — и
 * расходятся только состоянием журнала и строк пар, которое каждая ставит
 * себе сама. Ставится оно ТРОПОЙ ящика: строки пар заводит тик, строки
 * журнала — приём.
 *
 * <p><b>Соотношение двух операндов границы задаётся ПОРЯДКОМ ходов, а не
 * правкой колонок.</b> Тик заводит строку моментом своего такта, приём
 * ставит момент своим: тик до подачи события даёт «приём позже
 * наблюдения», подача до первого такта — обратное. Обе стороны сравнения
 * достижимы тропой, и выдумывать им состояние незачем.
 *
 * <p><b>Клетка о пустой области квантора обходится БЕЗ своей оси.</b>
 * Строк состояния нет, пока их не завёл такт: расписание тика в прогоне
 * выражено часовой паузой, и второго повторения за прогон оно не даёт
 * ({@link AuditSubstrate}), а база опустошается перед каждой клеткой.
 * Снятый выключатель тика поставил бы то же состояние ценой своего
 * контекста — и заодно сделал бы входом ось, которой кейс не называет.
 *
 * <p><b>Возраст строки журнала ставится в ДАННЫХ</b>
 * ({@link AuditBox#recordedEarlier}): глубина чистки назначается сутками, и
 * строки, принятой раньше неё, тропа ящика не производит ни при какой
 * расстановке. Часы процесса при этом не двигаются
 * (.claude/tests/cases/audit.md §«Чем достаются выходы»).
 *
 * <p><b>Граница и момент приёма сравниваются ВЫДАЧЕЙ ПОВЕРХНОСТИ, а не
 * колонкой базы.</b> Обе величины едут одним ответом и одним
 * сериализатором, и равенство между ними есть утверждение о том, что
 * журнал объявляет читателю; сравнение с колонкой мерило бы заодно
 * точность записи момента в тексте.
 */
class CompletenessLowerBoundBoxTest extends SharedAuditBox {

    /** Тема первого производителя: в неё ходят все клетки класса. */
    private static final String CORE = AuditSubstrate.CORE_TOPIC;

    /** Имя поля момента приёма в строке выдачи. */
    private static final String RECORDED_FIELD = "recordedAt";

    /** Имя величины границы в объявленной полноте выдачи. */
    private static final String BOUND_FIELD = "lowerBound";

    /** Возраст события, с которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Окно чтения: то же, которым ящик читает объявленную полноту. */
    private static final Duration WINDOW = Duration.ofHours(1);

    /** Насколько глубже назначенной глубины лежит строка, которую уносит чистка. */
    private static final Duration BEYOND_DEPTH = Duration.ofDays(60);

    @Test
    @DisplayName("B5.1 — Граница есть позднейший из двух моментов")
    void theBoundIsTheLaterOfTheTwoMoments() {
        givenReceptionStateRows();
        Instant observedFirst = instant(pair(CORE), OBSERVED_COLUMN);
        publish(CORE, "E-RECORDED-LATER", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);

        Answer page = page();
        assertThat(instant(record(), RECORDED_COLUMN))
                .as("вход поставлен: приём случился ПОЗЖЕ позднейшего момента наблюдения")
                .isAfter(observedFirst);
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("границей служит позднейший из двух — момент приёма самой ранней строки")
                .isEqualTo(page.records().getFirst().get(RECORDED_FIELD));
        assertThat(lowerBoundMoment().toInstant())
                .as("это именно позднейший, а не минимум: момент наблюдения раньше")
                .isAfter(observedFirst);

        // Обратное соотношение: наблюдение начинается ПОСЛЕ того, как
        // событие уже принято. Состояние достижимо тропой — строк пар нет,
        // пока их не завёл такт, и приём в этом окне строки пары не ждёт.
        rows.clear();
        publish(CORE, "E-OBSERVED-LATER", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);
        givenReceptionStateRows();

        Instant observedSecond = instant(pair(CORE), OBSERVED_COLUMN);
        assertThat(observedSecond)
                .as("вход поставлен: наблюдение началось позже приёма")
                .isAfter(instant(record(), RECORDED_COLUMN));
        assertThat(lowerBoundMoment().toInstant())
                .as("границей служит позднейший из двух — теперь момент наблюдения")
                .isEqualTo(observedSecond);
        assertThat(lowerBoundMoment().toInstant())
                .as("минимумом граница не является ни в одной из двух расстановок")
                .isAfter(instant(record(), RECORDED_COLUMN));
    }

    @Test
    @DisplayName("B5.2 — Пустой журнал: границей служит позднейший момент наблюдения")
    void anEmptyJournalFallsBackToTheLatestObservation() {
        givenReceptionStateRows();

        Answer page = page();

        assertThat(rows.count(JOURNAL_TABLE)).as("вход поставлен: журнал пуст").isZero();
        assertThat(page.records()).as("страница пуста").isEmpty();
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("граница НЕ пуста: обещать по пустому журналу нечего, но наблюдение уже идёт")
                .isNotNull();
        assertThat(lowerBoundMoment().toInstant())
                .as("и равна она позднейшему моменту наблюдения")
                .isEqualTo(latestObserved());
        assertThat(page.completeness().get("continuityClaimable"))
                .as("пустой журнал предиката непрерывности не роняет")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B5.3 — Ни одной подписанной пары: границы нет, и это значение")
    void withoutASubscribedPairThereIsNoBoundAtAll() {
        publish(CORE, "E-NO-PAIRS", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(CORE);

        assertThat(rows.count(RECEPTION_TABLE))
                .as("вход поставлен: строк состояния нет ни одной — такта не было")
                .isZero();
        Answer page = page();
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("границы нет, и это значение — не ноль и не эпоха")
                .isNull();
        assertThat(page.completeness().get("continuityClaimable"))
                .as("обе величины говорят об одном состоянии одно и то же")
                .isEqualTo(Boolean.FALSE);
        assertThat(page.records())
                .as("строки журнала при этом отдаются: отсутствие границы их не прячет")
                .hasSize(1);
        assertThat(page.records().getFirst().get("eventId")).isEqualTo("E-NO-PAIRS");
    }

    @Test
    @DisplayName("B5.6 — Чистка двигает границу вперёд")
    void theCleanupMovesTheBoundForward() {
        givenReceptionStateRows();
        publish(CORE, "E-OLD", momentsAgo(EVENT_AGE), Bodies.reference());
        publish(CORE, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        recordedEarlier("E-OLD", momentsAgo(BEYOND_DEPTH));
        OffsetDateTime boundBefore = lowerBoundMoment();
        assertThat(instant(recordOf("E-OLD"), RECORDED_COLUMN))
                .as("вход поставлен: часть строк принята раньше назначенной глубины")
                .isBefore(boundBefore.toInstant());

        cleanup();

        assertThat(rows.count(JOURNAL_TABLE)).as("строка старше глубины удалена").isEqualTo(1L);
        assertThat(record().get("event_id")).isEqualTo("E-SURVIVING");
        assertThat(lowerBoundMoment().toInstant())
                .as("граница двинулась ВПЕРЁД: величина монотонна")
                .isAfter(boundBefore.toInstant());
        Answer page = page();
        assertThat(page.completeness().get(BOUND_FIELD))
                .as("и равна она моменту приёма самой ранней УЦЕЛЕВШЕЙ строки")
                .isEqualTo(page.records().getFirst().get(RECORDED_FIELD));
        assertThat(page.completeness().get("continuityClaimable"))
                .as("клейм остаётся верным без отдельной оговорки")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B5.7 — Оси времени разведены: граница на оси приёма, клейм о производстве")
    void theAxesAreKeptApartAndTheDivergenceIsConservative() {
        givenReceptionStateRows();
        Instant observed = latestObserved();
        OffsetDateTime occurredAt = momentsAgo(EVENT_AGE);
        assertThat(occurredAt.toInstant())
                .as("вход поставлен: событие произведено РАНЬШЕ позднейшего момента наблюдения")
                .isBefore(observed);

        publish(CORE, "E-EARLY-OCCURRENCE", occurredAt, Bodies.reference());
        awaitConsumed(CORE);

        Answer page = page();
        assertThat(page.records())
                .as("строка события в выборке есть, хотя произведено оно раньше границы")
                .hasSize(1);
        assertThat(page.records().getFirst().get("eventId")).isEqualTo("E-EARLY-OCCURRENCE");
        assertThat(instant(record(), OCCURRED_COLUMN))
                .as("и её момент происшествия действительно раньше границы")
                .isBefore(lowerBoundMoment().toInstant());
        assertThat(lowerBoundMoment().toInstant())
                .as("до момента происшествия граница не опускается: она стои́т на оси ПРИЁМА")
                .isEqualTo(instant(record(), RECORDED_COLUMN));
        assertThat(lowerBoundMoment().toInstant())
                .as("расхождение консервативно в одну сторону: журнал несёт не меньше обещанного")
                .isAfter(observed);
    }

    /** Страница журнала тенанта за окно, которым ящик читает полноту. */
    private Answer page() {
        return journal(TENANT, momentsAgo(WINDOW), now());
    }

    /** Строка журнала названного события; иное число строк — падение. */
    private Map<String, Object> recordOf(String eventId) {
        return rows.row(JOURNAL_TABLE, "event_id", eventId);
    }

    /** Позднейший момент наблюдения по строкам пар — второй операнд границы. */
    private Instant latestObserved() {
        return pairs().stream()
                .map(row -> instant(row, OBSERVED_COLUMN))
                .max(Instant::compareTo)
                .orElseThrow(() -> new AssertionError("Строк состояния приёма нет ни одной"));
    }
}
