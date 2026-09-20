package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетки {@code B4.1} и {@code B4.6} — штатный исход сравнения смещений и
 * первая запись после назначения (.claude/tests/cases/audit.md §«B4 —
 * Обнаружение разрыва: три исхода сравнения смещений»).
 *
 * <p><b>Обе стоя́т на ОДНОМ положении осей и на одном состоянии группы</b>,
 * поэтому живут одним классом: штатная конфигурация плюс своя пара «группа
 * × темы». Своя группа здесь обязательна не ради порядка: клетка кладёт
 * запись и смотрит ЗАФИКСИРОВАННОЕ группой смещение, а оно есть состояние
 * на брокере, общее всем контекстам одного имени.
 *
 * <p><b>Назначение подаётся вступлением чужого участника в группу</b>
 * ({@link AuditBox#reassignPartitions()}): у поднятого контекста своё
 * назначение случается раньше, чем тик заведёт строки пар, то есть тогда,
 * когда писать ещё некуда.
 *
 * <p><b>Возврат назначения предъявляется СОСЕДНЕЙ темой, а не своей.</b>
 * Исход обеих клеток — «не пишется ничего», и отрицание сошлось бы и у
 * контейнера, который назначения обратно не получил вовсе. Принятая после
 * ребалансировки запись соседней темы предъявляет возврат: чужой участник
 * закрыт, и принять её больше некому. Своя тема при этом остаётся
 * нетронутой — иначе приём двигал бы ровно те величины, о неподвижности
 * которых клетка и утверждает.
 */
class AssignmentOffsetsBoxTest extends AuditBox {

    /** Краткое имя клеток: из него строятся их группа и их темы. */
    private static final String SLUG = "b4-1";

    /** Возраст события, которым клетки ходят в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG, Map.of());
    }

    @Test
    @DisplayName("B4.1 — Смещение есть и не ниже наименьшего доступного: не пишется ничего")
    void anOffsetAtOrAboveTheEarliestAvailableWritesNothingAtAll() {
        givenReceptionStateRows();
        publish(subject(), "E-1", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(subject());
        Object observedBefore = pair(subject()).get(OBSERVED_COLUMN);
        String versionBefore = pairVersion(subject());
        Long committedBefore = Wire.committedOffset(consumerGroup(), subject());
        assertThat(committedBefore)
                .as("вход поставлен: группа зафиксировала смещение, и оно не ниже наименьшего доступного")
                .isNotNull()
                .isGreaterThanOrEqualTo(Wire.earliestOffset(subject()));

        reassignPartitions();
        givenPartitionsBack();

        assertThat(pair(subject()).get(GAP_COLUMN)).as("момент разрыва пуст").isNull();
        assertThat(pair(subject()).get(OBSERVED_COLUMN))
                .as("момент наблюдения не переписан").isEqualTo(observedBefore);
        assertThat(continuityClaimable()).as("непрерывность утверждаема").isEqualTo(Boolean.TRUE);
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("чтение продолжается с зафиксированного смещения").isEqualTo(committedBefore);
        assertThat(pairVersion(subject()))
                .as("уже принятое событие повторно не принималось: строка пары не переписана")
                .isEqualTo(versionBefore);
        assertThat(records()).as("строк ровно две: по одной на событие").hasSize(2);
    }

    @Test
    @DisplayName("B4.6 — Первая запись после назначения разрывом не объявляется")
    void theFirstRecordAfterAnAssignmentIsNotDeclaredAGap() {
        givenReceptionStateRows();

        reassignPartitions();
        publish(subject(), "E-FIRST", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("ожидание посажено самим назначением, и первая запись приходит ровно с него")
                .isNull();
        assertThat(records()).as("строка журнала записана").hasSize(1);
        assertThat(continuityClaimable()).isEqualTo(Boolean.TRUE);

        publish(subject(), "E-SECOND", momentsAgo(Duration.ofMinutes(1)), Bodies.reference());
        awaitConsumed(subject());

        assertThat(pair(subject()).get(GAP_COLUMN))
                .as("ожидание сдвинуто на следующее смещение: вторая запись разрывом тоже не является")
                .isNull();
        assertThat(records()).hasSize(2);
    }

    /** Тема, о которой утверждают обе клетки. */
    private String subject() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }

    /** Соседняя тема: ею предъявляется возврат назначения контейнеру. */
    private String witness() {
        return AuditSubstrate.ownStrategyTopic(SLUG);
    }

    /**
     * Ждёт, пока контейнер получит партиции обратно: принятая запись
     * соседней темы есть предъявление назначения — чужой участник закрыт,
     * и принять её больше некому.
     */
    private void givenPartitionsBack() {
        publish(witness(), "E-BACK", momentsAgo(Duration.ofMinutes(1)), Bodies.reference());
        awaitConsumed(witness());
    }
}
