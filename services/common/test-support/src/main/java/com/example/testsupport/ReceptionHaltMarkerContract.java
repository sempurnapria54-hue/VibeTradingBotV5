package com.example.testsupport;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.RetryListener;

/**
 * Маркер остановки приёма: первая неудачная доставка, повторы и
 * проглоченный отказ записи — группа `U11` и клетки `U15.5`, `U16.9`
 * документа `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии</b> (`.claude/rules/carrier-levels.md`): маркер лежит двумя
 * экземплярами, и расходятся они только текстом javadoc.
 *
 * <p><b>Выход наблюдается протоколом вызовов:</b> метод возвращает
 * {@code void}, поэтому у каждой клетки две величины — что записано в
 * службу приёма и что в неё НЕ записано.
 */
public abstract class ReceptionHaltMarkerContract {

    /** Тема пары, на которой отказала обработка, и её соседка. */
    protected static final String TOPIC = "trading-core.facts";
    protected static final String NEIGHBOUR_TOPIC = "strategies.facts";

    /** Номер первой доставки: повторы приходят с бо́льшим номером. */
    private static final int FIRST_ATTEMPT = 1;

    /**
     * Что маркер записал в службу приёма.
     *
     * <p>Перечень зеркалит поверхность службы целиком, а не одну её
     * операцию: клетки `U11.6` и `U11.7` наблюдают ОТСУТСТВИЕ вызова, и
     * сток, где этого вызова нет вовсе, был бы тавтологией. Порт
     * подставляет сюда все операции своей службы.
     */
    public interface ReceptionSink {

        void noteHalt(String topic);

        /** Снятие флага идёт транзакцией приёма следствия — у величины два писателя по роли. */
        void acceptConsequence(String topic);

        void noteGap(String topic);

        void restartObservation(String topic);
    }

    // --- порты к своей копии ---------------------------------------------

    /** Маркер своего дерева, собранный на подменённой службе приёма. */
    protected abstract RetryListener haltMarker(ReceptionSink reception);

    /** Класс маркера своего дерева. */
    protected abstract Class<?> haltMarkerType();

    // --- U11: постановка флага --------------------------------------------

    @Test
    @DisplayName("U11.1 — первая неудачная доставка: отметка остановки по теме записи")
    void u11_1_theFirstFailedDeliveryMarksTheHalt() {
        Recording recording = failedDelivery(TOPIC, FIRST_ATTEMPT);

        assertThat(recording.calls).containsExactly("noteHalt");
        assertThat(recording.halted).containsExactly(TOPIC);
    }

    @Test
    @DisplayName("U11.2 — вторая неудачная доставка того же сообщения: не пишется ничего")
    void u11_2_aSecondDeliveryWritesNothing() {
        Recording recording = failedDelivery(TOPIC, 2);

        assertThat(recording.calls)
                .as("флаг уже стои́т, и запись на каждом повторе била бы в базу с частотой паузы")
                .isEmpty();
    }

    @Test
    @DisplayName("U11.3 — сотая неудачная доставка: исход тот же, что у второй")
    void u11_3_theHundredthDeliveryIsTheSameAsTheSecond() {
        assertThat(failedDelivery(TOPIC, 100).calls).isEqualTo(failedDelivery(TOPIC, 2).calls);
    }

    @Test
    @DisplayName("U11.4 — первая неудачная доставка по другой теме: своя тема, соседняя не тронута")
    void u11_4_theMarkIsPerTopic() {
        Recording recording = new Recording(null);
        RetryListener marker = haltMarker(recording);

        marker.failedDelivery(record(TOPIC), new IllegalStateException("неполный вход"), FIRST_ATTEMPT);
        marker.failedDelivery(record(NEIGHBOUR_TOPIC), new IllegalStateException("неполный вход"), FIRST_ATTEMPT);

        assertThat(recording.halted)
                .as("ключ строки состояния потемный, и отметка едет по своей половине ключа")
                .containsExactly(TOPIC, NEIGHBOUR_TOPIC);
    }

    @Test
    @DisplayName("U11.5 — служба приёма бросает отказ: наружу он НЕ уходит")
    void u11_5_aFailingWriteDoesNotReplaceTheCause() {
        Recording recording = new Recording(new IllegalStateException("строки состояния нет"));
        RetryListener marker = haltMarker(recording);

        assertThatCode(() -> marker.failedDelivery(
                record(TOPIC), new IllegalStateException("неполный вход"), FIRST_ATTEMPT))
                .as("проход и так идёт по тропе отказа, и подмена причины запрещена")
                .doesNotThrowAnyException();
        assertThat(recording.calls)
                .as("попытка записи была — проглочен её отказ, а не пропущен вызов")
                .containsExactly("noteHalt");
    }

    @Test
    @DisplayName("U11.6 — снятия флага маркер не производит")
    void u11_6_theMarkerNeverClearsTheFlag() {
        Recording recording = failedDelivery(TOPIC, FIRST_ATTEMPT);

        assertThat(recording.calls)
                .as("снимает флаг транзакция приёма — у величины два писателя по роли")
                .doesNotContain("acceptConsequence");
    }

    @Test
    @DisplayName("U11.7 — момента разрыва маркер не пишет: у него другой писатель")
    void u11_7_theMarkerNeverWritesAGapMoment() {
        Recording recording = failedDelivery(TOPIC, FIRST_ATTEMPT);

        assertThat(recording.calls).doesNotContain("noteGap");
    }

    @Test
    @DisplayName("U11.8 — тема берётся из записи, а не из конфигурации подписки")
    void u11_8_theTopicComesFromTheRecord() {
        Recording recording = failedDelivery(NEIGHBOUR_TOPIC, FIRST_ATTEMPT);

        assertThat(recording.halted).containsExactly(NEIGHBOUR_TOPIC);
    }

    @Test
    @DisplayName("U11.9 — исходы совпадают у обеих копий маркера")
    void u11_9_bothCopiesOfTheMarkerBehaveAlike() {
        assertThat(haltMarkerType().getSimpleName())
                .as("расхождение копий здесь только в тексте javadoc")
                .isEqualTo("ReceptionHaltMarker");
        assertThat(RetryListener.class)
                .as("обе копии — слушатель повторов библиотеки, а не свой механизм")
                .isAssignableFrom(haltMarkerType());
    }

    // --- U16.9: чего маркер не делает -------------------------------------

    @Test
    @DisplayName("U16.9 — маркер не снимает флага и не трогает соседних пар")
    void u16_9_theMarkerTouchesOnlyItsOwnPair() {
        Recording recording = failedDelivery(TOPIC, FIRST_ATTEMPT);

        assertThat(recording.calls).containsExactly("noteHalt");
        assertThat(recording.halted).containsExactly(TOPIC);
    }

    // --- оснастка ---------------------------------------------------------

    private Recording failedDelivery(String topic, int deliveryAttempt) {
        Recording recording = new Recording(null);
        haltMarker(recording).failedDelivery(
                record(topic), new IllegalStateException("неполный вход"), deliveryAttempt);
        return recording;
    }

    private static ConsumerRecord<String, String> record(String topic) {
        return new ConsumerRecord<>(topic, 0, 12L, "tenant-1", "{}");
    }

    /** Сток вызовов службы приёма, умеющий отказать на записи. */
    protected static final class Recording implements ReceptionSink {

        private final RuntimeException failure;
        private final List<String> calls = new ArrayList<>();
        private final List<String> halted = new ArrayList<>();

        private Recording(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void noteHalt(String topic) {
            calls.add("noteHalt");
            halted.add(topic);
            if (nonNull(failure)) {
                throw failure;
            }
        }

        @Override
        public void acceptConsequence(String topic) {
            calls.add("acceptConsequence");
        }

        @Override
        public void noteGap(String topic) {
            calls.add("noteGap");
        }

        @Override
        public void restartObservation(String topic) {
            calls.add("restartObservation");
        }
    }
}
