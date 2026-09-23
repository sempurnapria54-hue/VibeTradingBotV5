package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Клетка группы {@code B5}, чей предмет — слушатель, ЗАПУЩЕННЫЙ без
 * назначения партиций (.claude/tests/cases/bff.md, {@code B5.3}).
 *
 * <p><b>Предусловие ставится адресом брокера, а не касанием бина.</b>
 * Контекст поднимается с адресом, по которому брокера нет: контейнер
 * слушателя стартует, как стартует при потерянной связи, и назначения не
 * получает никогда. Общий контейнер брокера при этом не трогается, и адреса
 * соседи не лишаются.
 *
 * <p><b>Зонда назначения у контекста нет</b> ({@link #awaitsDelivery()}):
 * он ждал бы доехавшей записи, которой без брокера не бывает.
 *
 * <p><b>Регистрация подписки предъявляется записью РАЗРЫВА.</b> Факт в этот
 * провод не доезжает по построению, а отрицание «пульса нет» без
 * зарегистрированной подписки было бы тавтологией — пульсу некуда было бы
 * идти и у живого потребителя. Разрыв пишется при открытии, после
 * регистрации, и открывает ответ провода.
 */
class ConsumerWithoutAssignmentBoxTest extends BffBox {

    /** Адрес, по которому брокера нет: порт один мёртв на любой машине. */
    private static final String DEAD_BROKER = "127.0.0.1:1";

    /** Сколько клетка слушает молчание после тика. */
    private static final Duration SILENCE = Duration.ofSeconds(2);

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(BffSubstrate.BROKER_ADDRESS_KEY, DEAD_BROKER));
    }

    @Autowired
    private StreamPulseJob pulse;

    @Override
    protected Boolean awaitsDelivery() {
        return Boolean.FALSE;
    }

    @Test
    @DisplayName("B5.3 — Слушатель без назначенных партиций пульс гасит")
    void b5_3_aListenerWithoutAssignedPartitionsSilencesThePulse() {
        authAnswers(Bodies.memberships("TP3", ROLE));
        String ticket = issuedTicket();

        try (Subscription stream = subscribe(ticket, "e-b5-3-unknown")) {
            stream.awaitType("PERIMETER_GAP");
            pulse.beat();

            // Пульс пишется синхронно внутри тика, и молчание слушается
            // ограниченно: барьерной записи факта в этом контексте нет.
            Awaitility.await("провод молчит после тика")
                    .during(SILENCE).atMost(SILENCE.plusSeconds(3))
                    .until(() -> stream.frames().size() == 1);
            assertThat(stream.types()).containsExactly("PERIMETER_GAP");
            assertThat(stream.isOpen()).isTrue();
        }
    }
}
