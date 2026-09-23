package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bff.domain.jobs.StreamPulseJob;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка группы {@code B5}, чей предмет — ВЫКЛЮЧАТЕЛЬ тика пульса
 * (.claude/tests/cases/bff.md, {@code B5.6}).
 *
 * <p><b>Свой контекст, потому что выключатель — ось конфигурации.</b>
 * Положение оси читается тиком при каждом вызове, и штатный контекст её
 * держит включённой: иначе пульса не было бы ни у одной клетки группы.
 *
 * <p><b>Клетка утверждает ДВЕ стороны, и вторая несущая:</b> пульса нет, а
 * факт, положенный в тему после тика, доезжает. Выключатель гасит
 * ИЗМЕРИТЕЛЬ, а не поток, — сторона «ничего не пришло» без второй была бы
 * зелена и у мёртвой раздачи.
 */
class PulseSwitchedOffBoxTest extends BffBox {

    private static final String TENANT_OF_CELL = "TP6";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(BffSubstrate.PULSE_ENABLED_KEY, "false"));
    }

    @Autowired
    private StreamPulseJob pulse;

    @Test
    @DisplayName("B5.6 — Выключатель тика гасит пульс целиком")
    void b5_6_theSwitchSilencesThePulseEntirely() {
        authAnswers(Bodies.memberships(TENANT_OF_CELL, ROLE));
        String ticket = issuedTicket();

        try (Subscription stream = openedStreamOf(TENANT_OF_CELL, ticket, "e-b5-6-1")) {
            pulse.beat();
            // Барьер после тика: пульс пишется синхронно внутри тика, и
            // доехавший следом факт предъявляет, что пульса перед ним нет.
            publishDealOpened(TENANT_OF_CELL, "e-b5-6-2");
            stream.awaitFrames(2);

            assertThat(stream.types()).containsExactly("DEAL_OPENED", "DEAL_OPENED");
            assertThat(stream.ids()).containsExactly("e-b5-6-1", "e-b5-6-2");
        }
    }
}
