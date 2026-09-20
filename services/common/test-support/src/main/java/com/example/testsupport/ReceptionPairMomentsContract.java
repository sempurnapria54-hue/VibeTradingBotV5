package com.example.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Моменты пары состояния приёма: основание возраста последнего принятого
 * события — группа `U5` и клетка `U15.6` документа
 * `.claude/tests/cases/durable-reception.md`.
 *
 * <p><b>Ожидание объявлено один раз и прогоняется каждым деревом своей
 * копии.</b> Модель лежит двумя экземплярами — `audit` и `statistics`, —
 * и совпадают они дословно; сличить их на одном classpath нечем, поэтому
 * форма ожидания общая, а порт подставляет свою копию
 * (`.claude/rules/carrier-levels.md`).
 *
 * <p><b>Часов процесса модель не читает:</b> оба момента приезжают
 * полями строки, и ожидание выражается равенством, а не допуском.
 */
public abstract class ReceptionPairMomentsContract {

    /** Тема пары — вторая половина её ключа. */
    protected static final String TOPIC = "trading-core.facts";

    private static final OffsetDateTime ACCEPTED =
            OffsetDateTime.of(2026, 9, 12, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime OBSERVED =
            OffsetDateTime.of(2026, 9, 12, 9, 0, 0, 0, ZoneOffset.UTC);

    // --- порты к своей копии ---------------------------------------------

    /** Основание возраста, как его выводит копия своего дерева. */
    protected abstract OffsetDateTime lastEventMoment(String topic, OffsetDateTime lastAccepted,
                                                      OffsetDateTime observedSince);

    /** Тема, как её отдаёт та же копия. */
    protected abstract String topicOf(String topic, OffsetDateTime lastAccepted, OffsetDateTime observedSince);

    /** Класс модели своего дерева — вход клетки тождества. */
    protected abstract Class<?> pairMomentsType();

    // --- U5: основание возраста -------------------------------------------

    @Test
    @DisplayName("U5.1 — момент принятого задан, наблюдение раньше: основание — принятое")
    void u5_1_anAcceptedMomentWinsOverAnEarlierObservation() {
        assertThat(lastEventMoment(TOPIC, ACCEPTED, OBSERVED)).isEqualTo(ACCEPTED);
    }

    @Test
    @DisplayName("U5.2 — принятого нет: основанием служит момент наблюдения")
    void u5_2_beforeTheFirstAcceptanceTheObservationIsTheBase() {
        assertThat(lastEventMoment(TOPIC, null, OBSERVED))
                .as("иначе у здоровой пары без входов ряда возраста не было бы вовсе")
                .isEqualTo(OBSERVED);
    }

    @Test
    @DisplayName("U5.3 — принятое РАНЬШЕ наблюдения: выбор идёт по пустоте, а не по сравнению")
    void u5_3_theChoiceIsByPresenceAndNotByOrder() {
        OffsetDateTime earlierAccepted = OBSERVED.minusHours(5L);

        assertThat(lastEventMoment(TOPIC, earlierAccepted, OBSERVED))
                .as("ветвление объявлено по наличию, и сравнения моментов в нём нет")
                .isEqualTo(earlierAccepted);
    }

    @Test
    @DisplayName("U5.4 — оба момента равны: развилки нет")
    void u5_4_equalMomentsLeaveNoFork() {
        assertThat(lastEventMoment(TOPIC, ACCEPTED, ACCEPTED)).isEqualTo(ACCEPTED);
    }

    @Test
    @DisplayName("U5.5 — оба момента пусты: пустота, а не отказ разыменования")
    void u5_5_bothMomentsAbsentYieldAbsenceAndNotAFailure() {
        assertThat(lastEventMoment(TOPIC, null, null))
                .as("состояние недостижимо — момент наблюдения пишет заведение строки, — и кейс стои́т охраной")
                .isNull();
    }

    @Test
    @DisplayName("U5.6 — тема отдаётся как получена: производная величина её не трогает")
    void u5_6_theTopicTravelsUnchanged() {
        assertThat(topicOf(TOPIC, ACCEPTED, OBSERVED)).isEqualTo(TOPIC);
    }

    @Test
    @DisplayName("U5.7 — копии дословны: имя класса и состав полей совпадают у обоих деревьев")
    void u5_7_theCopiesAreLiteral() {
        assertThat(pairMomentsType().getSimpleName())
                .as("форма объявлена сквозной, а кода два — по дереву на потребителя")
                .isEqualTo("ReceptionPairMoments");
        assertThat(fieldNames()).containsExactly("topic", "lastAcceptedOccurredAt", "observedSince");
    }

    private List<String> fieldNames() {
        List<String> names = new ArrayList<>();
        for (Field field : pairMomentsType().getDeclaredFields()) {
            if (!field.isSynthetic()) {
                names.add(field.getName());
            }
        }
        return names;
    }
}
