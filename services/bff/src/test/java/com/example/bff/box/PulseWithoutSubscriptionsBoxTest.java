package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.bff.domain.jobs.StreamPulseJob;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка группы {@code B5}, чей предмет — тик на реплике БЕЗ подписок
 * (.claude/tests/cases/bff.md, {@code B5.5}).
 *
 * <p><b>Свой контекст и без зонда назначения</b> ({@link #awaitsDelivery()}),
 * и это следствие предмета: зонд открывает подписку, а закрытая клиентом
 * подписка выбывает из набора лишь на следующей записи в неё — контекст,
 * через который прошёл зонд или соседняя клетка, «подписок нет ни одной»
 * не гарантирует. Здесь подписок не открывал никто.
 *
 * <p><b>Что без подписок пульс не пишется, снаружи не наблюдается:</b>
 * провода нет, и писать его некуда по построению. Наблюдаемы две стороны,
 * и обе утверждаются: тик без подписок не бросает, а следующий тик после
 * открытия подписки пульс даёт — пустой набор тик не выключает.
 *
 * <p><b>Назначение партиций здесь не ждётся зондом, а наблюдается самим
 * пульсом:</b> тик подаётся, пока пульс не придёт, — живость потребителя
 * и есть условие, при котором он приходит.
 */
class PulseWithoutSubscriptionsBoxTest extends BffBox {

    /** Потолок ожидания назначения партиций свежему слушателю. */
    private static final Duration ASSIGNMENT = Duration.ofSeconds(30);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of());
    }

    @Autowired
    private StreamPulseJob pulse;

    @Override
    protected Boolean awaitsDelivery() {
        return Boolean.FALSE;
    }

    @Test
    @DisplayName("B5.5 — Без подписок пульса нет")
    void b5_5_withoutSubscriptionsThereIsNoPulse() {
        assertThatCode(() -> pulse.beat()).doesNotThrowAnyException();

        authAnswers(Bodies.memberships("TP5", ROLE));
        String ticket = issuedTicket();
        try (Subscription stream = subscribe(ticket, "e-b5-5-unknown")) {
            stream.awaitType("PERIMETER_GAP");
            Awaitility.await("пульс после открытия подписки")
                    .atMost(ASSIGNMENT).pollInterval(Duration.ofMillis(200))
                    .until(() -> {
                        pulse.beat();
                        return stream.types().contains("PERIMETER_PULSE");
                    });

            assertThat(stream.types().getFirst()).isEqualTo("PERIMETER_GAP");
            assertThat(stream.types()).contains("PERIMETER_PULSE");
        }
    }
}
