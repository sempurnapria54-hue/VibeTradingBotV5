package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B10.6} — глубина, пауза повтора и допустимый возраст суть
 * конфигурация, а не константы (.claude/tests/cases/audit.md §«B10 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Величин у клетки четыре, а классов два, и делит их ЖИВОЙ ТАКТ.</b>
 * Три величины здесь наблюдаются ходом, который подаёт сам кейс — проходом
 * чистки, чтением страницы, повтором отравленного сообщения, — и живут
 * одним контекстом. Четвёртая, такт тика, наблюдается тем, что тик бьётся
 * САМ ({@link SelfFiringTickBoxTest}); сжатый такт переписывал бы момент
 * обновления строк состояния каждым своим ударом, то есть отнимал бы
 * предмет у клетки о допустимом возрасте — она утверждает ровно о ВОЗРАСТЕ
 * строки.
 *
 * <p><b>Каждое значение СДВИНУТО так, что умолчание сервиса дало бы другой
 * исход.</b> Совпадающее с умолчанием значение не отличало бы «величина
 * доехала» от «взято умолчание»: глубина в двое суток уносит строку
 * трёхсуточного возраста, которую тридцатисуточное умолчание оставило бы;
 * возраст в минуту роняет предикат на строке, которую пятиминутное
 * умолчание держит свежей; пауза в двадцать секунд не даёт повтора в окне,
 * в котором пятисекундное умолчание дало бы четыре.
 *
 * <p><b>Двух соседних клеймов того же кейса здесь нет, и это не
 * пропуск.</b> Что числа попыток повтора рядом нет вовсе, предъявляет
 * {@code B2.12} — сотня пауз подряд без единого продвижения смещения; что
 * размер порции удаления на исход прохода не влияет, предъявляет
 * {@code B7.6}. Второго носителя ни тому, ни другому не заводится (Д1828).
 */
class ConfiguredValuesBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b10-6";

    /** Глубина хранения этого контекста: двое суток вместо тридцати. */
    private static final Integer DEPTH_DAYS = 2;

    /** Допустимый возраст строки состояния этого контекста: минута вместо пяти. */
    private static final Duration STATE_MAX_AGE = Duration.ofMinutes(1);

    /** Пауза повтора этого контекста: двадцать секунд вместо пяти. */
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(20);

    /**
     * Окно, в котором повтора не случается.
     *
     * <p>Оно ШИРЕ умолчания сервиса и у́же назначенной паузы: при
     * пятисекундном умолчании запись была бы принята внутри него, при
     * двадцатисекундной паузе — нет.
     */
    private static final Duration WITHOUT_RETRY = Duration.ofSeconds(8);

    /** Возраст строки, выходящей за назначенную глубину. */
    private static final Duration BEYOND_DEPTH = Duration.ofDays(3);

    /** Возраст строки, за назначенную глубину не выходящей. */
    private static final Duration WITHIN_DEPTH = Duration.ofDays(1);

    /** Возраст события, с которым клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        Map<String, String> axes = new LinkedHashMap<>();
        axes.put(AuditSubstrate.CLEANUP_DEPTH_KEY, String.valueOf(DEPTH_DAYS));
        axes.put(AuditSubstrate.STATE_MAX_AGE_KEY, STATE_MAX_AGE.toSeconds() + "s");
        axes.put(AuditSubstrate.RETRY_INTERVAL_KEY, RETRY_INTERVAL.toSeconds() + "s");
        AuditSubstrate.registerOwn(registry, SLUG, axes);
    }

    @Test
    @DisplayName("B10.6 — Глубина чистки приходит конфигурацией, а не константой")
    void theCleanupDepthArrivesByConfiguration() {
        publish(subject(), "E-BEYOND", momentsAgo(EVENT_AGE), Bodies.reference());
        publish(subject(), "E-WITHIN", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        recordedEarlier("E-BEYOND", momentsAgo(BEYOND_DEPTH));
        recordedEarlier("E-WITHIN", momentsAgo(WITHIN_DEPTH));

        cleanup();

        assertThat(record().get("event_id"))
                .as("проход отмерил назначенные двое суток, а не умолчание в тридцать")
                .isEqualTo("E-WITHIN");
    }

    @Test
    @DisplayName("B10.6 — Допустимый возраст строки состояния приходит конфигурацией")
    void theAllowedStateAgeArrivesByConfiguration() {
        givenReceptionStateRows();

        agePairs(STATE_MAX_AGE.plusSeconds(1));
        assertThat(continuityClaimable())
                .as("строка старше назначенной минуты: измеритель молчит, и предикат ложен")
                .isEqualTo(Boolean.FALSE);

        agePairs(STATE_MAX_AGE.minusSeconds(1));
        assertThat(continuityClaimable())
                .as("строка моложе назначенной минуты — и тот же возраст при умолчании "
                        + "в пять минут давал бы тот же ответ: граница читается с двух сторон")
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("B10.6 — Пауза между повторами приходит конфигурацией")
    void theRetryPauseArrivesByConfiguration() {
        givenReceptionStateRows();

        rows.withoutTable(JOURNAL_TABLE, () -> {
            publish(subject(), "E-RETRIED", momentsAgo(EVENT_AGE), Bodies.reference());
            awaitHalted(subject());
        });

        Awaitility.await()
                .during(WITHOUT_RETRY)
                .atMost(WITHOUT_RETRY.plusSeconds(2))
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(JOURNAL_TABLE), 0L));
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> Objects.equals(rows.count(JOURNAL_TABLE), 1L));

        assertThat(record().get("event_id"))
                .as("повтор пришёл после назначенной паузы, а не после умолчания в пять секунд")
                .isEqualTo("E-RETRIED");
    }

    /**
     * Ставит названный возраст строкам ОБЕИХ пар подписки.
     *
     * <p>Предикат непрерывности берёт все подписанные пары, и строка,
     * оставленная свежей, отвечала бы за обе.
     *
     * @param age возраст, который получают строки состояния
     */
    private void agePairs(Duration age) {
        subscription().forEach(topic -> pairUpdatedAt(topic, momentsAgo(age)));
    }

    /** Тема, в которую клетка кладёт записи. */
    private String subject() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }
}
