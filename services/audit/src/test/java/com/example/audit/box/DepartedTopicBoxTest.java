package com.example.audit.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B3.4} — ушедшая из подписки тема получает ложь, а строка
 * не удаляется (.claude/tests/cases/audit.md).
 *
 * <p><b>Состав подписки — ВХОД клетки</b>, поэтому контекст у неё свой:
 * подписку контейнер читает при подъёме, и сузить её на живом контексте
 * нечем.
 *
 * <p><b>Строка ушедшей темы ставится ПРЯМОЙ ЗАПИСЬЮ, и это durable-вход,
 * а не подмена выхода.</b> Завести её тиком ЭТОГО контекста невозможно по
 * построению: тик ведёт состав по объявленной подписке, а тема из неё уже
 * ушла. Форма строки при этом объявлена домом и читается наружу
 * (docs/rules/durable-consumer-reception.md §«Строка состояния приёма —
 * таблица `reception_states`»), поэтому запись по колонкам говорит о том
 * же, о чём читает ассерт. Предмет клетки — что с такой строкой делает
 * такт, а не кто её завёл.
 *
 * <p><b>Момент наблюдения ушедшей ставится в ПРОШЛОМ</b>: им видно, что
 * такт его не тронул. Сдвинутый тактом, он опустил бы ту самую нижнюю
 * границу полноты, ради которой строка и не удаляется.
 */
class DepartedTopicBoxTest extends AuditBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b3-4";

    /** Насколько раньше такта наблюдается ушедшая тема. */
    private static final Duration OBSERVED_BEFORE = Duration.ofHours(2);

    /** Вставка строки пары: колонки те же, которыми её заводит тик. */
    private static final String OPEN_PAIR = """
            insert into reception_states
                (consumer_group, topic, observed_since, subscribed, reception_halted, updated_at)
            values (?, ?, ?, true, false, ?)
            """;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        AuditSubstrate.registerOwn(registry, SLUG,
                List.of(AuditSubstrate.ownCoreTopic(SLUG), AuditSubstrate.ownStrategyTopic(SLUG)),
                List.of(AuditSubstrate.ownCoreTopic(SLUG)),
                Map.of());
    }

    @Test
    @DisplayName("B3.4 — Ушедшая из подписки тема получает ложь, а строка не удаляется")
    void aTopicThatLeftTheSubscriptionGetsFalseWhileItsRowSurvives() {
        givenReceptionStateRows();
        String subscribed = subscription().getFirst();
        String departed = AuditSubstrate.ownStrategyTopic(SLUG);
        OffsetDateTime observedSince = momentsAgo(OBSERVED_BEFORE);
        rows.write(OPEN_PAIR, consumerGroup(), departed, observedSince, observedSince);
        Map<String, Object> subscribedBefore = pair(subscribed);

        tick();

        assertThat(pairs()).as("строк по-прежнему две: ушедшая не удалена").hasSize(2);
        assertThat(pair(subscribed).get(SUBSCRIBED_COLUMN))
                .as("оставшаяся в подписке несёт истину").isEqualTo(Boolean.TRUE);
        assertThat(pair(departed).get(SUBSCRIBED_COLUMN))
                .as("ушедшая — ложь").isEqualTo(Boolean.FALSE);
        assertThat(instant(pair(departed), OBSERVED_COLUMN))
                .as("момент наблюдения ушедшей такт не трогает")
                .isEqualTo(observedSince.toInstant());
        assertThat(instant(pair(departed), UPDATED_COLUMN))
                .as("момент обновления двинулся и у ушедшей")
                .isAfter(observedSince.toInstant());
        assertThat(instant(pair(subscribed), UPDATED_COLUMN))
                .isAfter(instant(subscribedBefore, UPDATED_COLUMN));
    }
}
