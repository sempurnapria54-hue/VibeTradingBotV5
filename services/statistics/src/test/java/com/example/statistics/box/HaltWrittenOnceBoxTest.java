package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.12} — флаг ставится один раз, а не на каждой попытке
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Повторов у группы статистики не ограничено ничем</b>, и запись на
 * каждом из них била бы в базу с частотой паузы, ничего не меняя: флаг уже
 * стои́т ({@code ReceptionHaltMarker}). Клетка наблюдает сотню попыток и
 * утверждает, что строка состояния за это время не менялась.
 *
 * <p><b>Наблюдателей у «не писали» ДВА, и первого мало.</b> Момент обновления
 * строки ведёт только тик (docs/rules/writer-named-for-every-value.md), и
 * запись величин приёма его не трогает — но ровно поэтому неподвижный момент
 * не отличает «записали один раз» от «записывали сто раз»: обе тропы оставляют
 * его прежним. Отличает их ВЕРСИЯ строки в базе ({@link Rows#rowVersion}) —
 * номер транзакции, положившей нынешнюю версию, меняется при каждой записи,
 * что бы та ни писала. Клетка утверждает оба наблюдения разом.
 *
 * <p><b>Третьим идёт ключ пары:</b> второй строки для той же пары не
 * появляется — ключ уникален, истории состояний не хранится
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»).
 *
 * <p><b>Пауза повтора у контекста своя</b> по тому же доводу, что у
 * {@link UnlimitedRetryBoxTest}: входом клетки является число попыток, а не их
 * частота.
 */
class HaltWrittenOnceBoxTest extends PoisonedReceptionBox {

    /** Пауза повтора этого контекста. */
    private static final String RETRY_INTERVAL = "200ms";

    /** Окно наблюдения: сотня пауз повтора подряд. */
    private static final Duration OBSERVATION = Duration.ofSeconds(20);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-12",
                Map.of(StatisticsSubstrate.RETRY_INTERVAL_KEY, RETRY_INTERVAL));
    }

    @Test
    @DisplayName("B2.12 — Флаг ставится один раз, а не на каждой попытке")
    void theHaltFlagIsWrittenOnceRatherThanOnEveryAttempt() {
        givenReceptionStateRows();
        Object updatedBeforeFailure = pair(topic()).get(UPDATED_COLUMN);
        Long pairsBeforeFailure = rows.count(RECEPTION_TABLE);

        poisonWithout(EVENT_ID);

        String versionAfterHalt = pairVersion(topic());
        Awaitility.await()
                .during(OBSERVATION)
                .atMost(OBSERVATION.plus(RECEPTION_WAIT))
                .pollInterval(POLL)
                .until(() -> stateRowUnchanged(versionAfterHalt, pairsBeforeFailure));
        assertThat(pair(topic()).get(HALTED_COLUMN)).isEqualTo(Boolean.TRUE);
        assertThat(pair(topic()).get(UPDATED_COLUMN))
                .as("момент обновления строки двигает только тик").isEqualTo(updatedBeforeFailure);
        assertThat(dealFacts()).as("строки факта нет").isEmpty();
    }

    /** Строка пары за сотню попыток не переписана и не задвоена. */
    private Boolean stateRowUnchanged(String versionAfterHalt, Long pairsBeforeFailure) {
        return Objects.equals(versionAfterHalt, pairVersion(topic()))
                && Objects.equals(pairsBeforeFailure, rows.count(RECEPTION_TABLE));
    }
}
