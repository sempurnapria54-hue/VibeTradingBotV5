package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B10.4} — позиция чтения с самой ранней доступной
 * (.claude/tests/cases/audit.md §«B10 — Конфигурация и схема как вход»).
 *
 * <p><b>Вход кладётся ДО ПОДЪЁМА контекста, и иначе его не поставить.</b>
 * Предмет клетки — откуда группа начинает читать тему, в которой уже
 * что-то лежит; запись, положенная при живом контейнере, приезжает
 * ДОСТАВКОЙ и о начальной позиции не говорит ничего. Единственное место
 * прогона, которое случается раньше первого бина, — регистрация свойств
 * контекста, и вход кладётся там.
 *
 * <p><b>Своя группа обязательна ровно по этому же признаку.</b>
 * Зафиксированное смещение есть состояние НА БРОКЕРЕ, общее всем
 * контекстам одного имени: группа штатного прогона своё начало давно
 * прошла, и «прочитано с самой ранней» на ней не выразимо ничем.
 *
 * <p><b>Опустошение базы перед клеткой ОТМЕНЕНО, и это не послабление
 * дисциплины, а следствие того же признака.</b> Вход положен до подъёма,
 * значит и выход — строки журнала — записан до первого хода кейса;
 * штатное {@code truncate} стёрло бы ровно то, что клетка предъявляет.
 * Цена названа: база достаётся клетке в том состоянии, в каком её оставил
 * предыдущий класс, поэтому ассерты идут ПО СВОИМ идентичностям событий, а
 * не по числу строк таблицы.
 *
 * <p><b>Смещения при этом читаются у своей группы</b>, и на них чужое
 * наследство не влияет вовсе.
 */
class EarliestPositionBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b10-4";

    /** Идентичности событий, положенных в тему до первого старта группы. */
    private static final List<String> LYING = List.of("E-B10-4-A", "E-B10-4-B", "E-B10-4-C");

    /** Возраст событий, лежавших в теме до подписки. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG, Map.of());
        LYING.forEach(eventId -> Wire.publish(subject(), TENANT,
                envelope(eventId, momentsAgo(EVENT_AGE)), Bodies.reference()));
    }

    /**
     * Базу клетка не опустошает: её вход положен до подъёма контекста, и
     * опустошение стёрло бы её же выход. Довод целиком — в шапке класса.
     */
    @BeforeEach
    @Override
    void resetSubstrate() {
        // Намеренно пусто: состояние клетки поставлено раньше её самой.
    }

    @Test
    @DisplayName("B10.4 — Позиция чтения — с самой ранней доступной")
    void theGroupStartsReadingFromTheEarliestAvailableOffset() {
        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> LYING.stream().noneMatch(eventId -> record(eventId).isEmpty()));
        awaitConsumed(subject());

        assertThat(Wire.earliestOffset(subject()))
                .as("записи лежат с самого начала темы: удалено из неё ничего не было")
                .isZero();
        assertThat(Wire.committedOffset(consumerGroup(), subject()))
                .as("приняты ВСЕ лежавшие записи, а не только пришедшие после подписки")
                .isEqualTo((long) LYING.size());
        assertThat(Wire.endOffset(subject())).isEqualTo((long) LYING.size());
        LYING.forEach(eventId -> assertThat(record(eventId))
                .as("строка журнала заведена на каждую лежавшую запись: %s", eventId)
                .isNotEmpty());
    }

    /** Строка журнала названного события; пустая карта — строки нет. */
    private Map<String, Object> record(String eventId) {
        return rows.row(JOURNAL_TABLE, "event_id", eventId);
    }

    /** Тема, в которую вход положен до подъёма контекста. */
    private static String subject() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }
}
