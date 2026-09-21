package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B10.3} — подписка приходит конфигурацией, и форма значения
 * есть скаляр через запятую (.claude/tests/cases/audit.md §«B10 —
 * Конфигурация и схема как вход»).
 *
 * <p><b>Подъёмов у клетки два, и второй уже стои́т в прогоне.</b> Штатное
 * положение осей — подписка на ДВЕ темы, записанные через запятую, — есть
 * положение всякого класса на {@link SharedAuditBox}, и строки обеих пар
 * там заводит тик ({@code B3.1}). Второго носителя того же положения здесь
 * не заводится (Д1828); свой контекст нужен ровно суженной стороне.
 *
 * <p><b>Сужение ставится РАЗВЕДЕНИЕМ двух составов:</b> у брокера темы
 * заводятся обе, а подпиской объявляется одна
 * ({@link AuditSubstrate#registerOwn(DynamicPropertyRegistry, String, List,
 * List, Map)}). Иначе «строки второй пары нет» сошлось бы и оттого, что
 * второй темы не существует вовсе, — то есть клетка мерила бы состав
 * брокера, а не состав объявленной подписки.
 *
 * <p><b>Что предъявляет одиночное значение.</b> Скаляр без запятой
 * разбирается в перечень из ОДНОГО имени: строка состояния заводится одна,
 * и заведённая у брокера, но не объявленная тема остаётся непрочитанной —
 * её смещение группа не фиксирует, и строки журнала по ней не появляется.
 * Перечнем разметки то же значение не подаётся: одиночный плейсхолдер
 * выражения {@code @KafkaListener} перечнем YAML не разрешается вовсе
 * ({@code ReceptionProperties}, javadoc о темах подписки).
 */
class NarrowedSubscriptionBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b10-3";

    /** Возраст события, с которым клетка ходит в тему. */
    private static final Duration EVENT_AGE = Duration.ofMinutes(5);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                List.of(declared(), undeclared()), List.of(declared()), Map.of());
    }

    @Test
    @DisplayName("B10.3 — Подписка приходит конфигурацией, и форма значения — скаляр через запятую")
    void theSubscriptionArrivesByConfigurationAsACommaSeparatedScalar() {
        givenReceptionStateRows();

        assertThat(pairs())
                .as("состав пар равен ОБЪЯВЛЕННОЙ подписке, а не составу тем у брокера")
                .hasSize(1);
        assertThat(pair(declared()).get(TOPIC_COLUMN)).isEqualTo(declared());
        assertThat(pair(undeclared()))
                .as("заведённая у брокера, но не объявленная тема пары не получает")
                .isEmpty();

        publish(declared(), "E-B10-3-FIRST", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(declared());
        publish(undeclared(), "E-B10-3-OUTSIDE", momentsAgo(EVENT_AGE), Bodies.reference());
        publish(declared(), "E-B10-3-SECOND", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitConsumed(declared());

        assertThat(records())
                .as("прочитана только объявленная тема: обе её записи легли строками")
                .hasSize(2)
                .extracting(row -> row.get("event_id"))
                .containsExactly("E-B10-3-FIRST", "E-B10-3-SECOND");
        assertThat(Wire.endOffset(undeclared()))
                .as("вход в необъявленную тему положен: отрицание не сходится само")
                .isEqualTo(1L);
        assertThat(Wire.committedOffset(consumerGroup(), undeclared()))
                .as("необъявленную тему группа не читает: смещения по ней нет")
                .isNull();
    }

    /** Тема, объявленная подпиской этого контекста. */
    private static String declared() {
        return AuditSubstrate.ownCoreTopic(SLUG);
    }

    /** Тема, заведённая у брокера и подпиской НЕ объявленная. */
    private static String undeclared() {
        return AuditSubstrate.ownStrategyTopic(SLUG);
    }
}
