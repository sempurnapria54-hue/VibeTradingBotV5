package com.example.bff.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка группы {@code B4}, чей предмет — ШИРИНА окна переигрывания
 * (.claude/tests/cases/bff.md, {@code B4.4}; третий вход — пробел
 * {@code G5}).
 *
 * <p><b>Свой контекст, потому что сдвинута ось.</b> Штатное окно — двести
 * записей тенанта, и вытеснение при нём стоило бы двухсот публикаций ради
 * одного ожидания; здесь ширина задана тремя, и вытеснение становится
 * входом, а не нагрузкой.
 *
 * <p><b>«Окно держит ровно объявленную ширину» мерится ПАРОЙ позиций на
 * границе.</b> Первая невытесненная запись находится — окно не у́же
 * ширины; последняя вытесненная не находится — окно не шире. Каждая
 * сторона по отдельности была бы зелена и у окна другой ширины.
 */
class NarrowReplayWindowBoxTest extends BffBox {

    /** Ширина окна этого контекста: вытеснение обязано случиться внутри клетки. */
    private static final Integer WIDTH = 3;

    /** Тенант клетки. */
    private static final String TENANT_OF_CELL = "TR4";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        BffSubstrate.register(registry, Map.of(
                BffSubstrate.REPLAY_WINDOW_KEY, String.valueOf(WIDTH)));
    }

    @Test
    @DisplayName("B4.4 — Позиция, вытесненная из окна, даёт разрыв")
    void b4_4_aPositionEvictedFromTheWindowGivesAGap() {
        authAnswers(Bodies.memberships(TENANT_OF_CELL, ROLE));
        String ticket = issuedTicket();
        // Пять записей при ширине три: первые две вытеснены. Наполнившая
        // окно подписка остаётся открытой до конца клетки — брошенная, она
        // ловила бы гонку F-14 на следующей записи тенанта.
        try (Subscription filling = openedStreamOf(TENANT_OF_CELL, ticket, "e-b4-4-1")) {
            publishDealOpened(TENANT_OF_CELL, "e-b4-4-2");
            publishDealOpened(TENANT_OF_CELL, "e-b4-4-3");
            publishDealOpened(TENANT_OF_CELL, "e-b4-4-4");
            publishDealOpened(TENANT_OF_CELL, "e-b4-4-5");
            filling.awaitFrames(5);

            // Позиция первой — давно вытесненной — записи: разрыв, и
            // вытесненные не восстанавливаются ничем.
            try (Subscription fromFirst = subscribe(ticket, "e-b4-4-1")) {
                fromFirst.awaitFrames(1);
                assertThat(fromFirst.types()).containsExactly("PERIMETER_GAP");
                assertThat(fromFirst.ids()).doesNotContain("e-b4-4-2", "e-b4-4-3", "e-b4-4-4", "e-b4-4-5");
            }

            // Последняя вытесненная: окно не шире объявленного.
            try (Subscription fromLastEvicted = subscribe(ticket, "e-b4-4-2")) {
                fromLastEvicted.awaitFrames(1);
                assertThat(fromLastEvicted.types()).containsExactly("PERIMETER_GAP");
            }

            // Первая невытесненная (пробел G5): окно не у́же объявленного, и
            // поток продолжается с неё без разрыва.
            try (Subscription fromFirstKept = subscribe(ticket, "e-b4-4-3")) {
                fromFirstKept.awaitFrames(2);
                assertThat(fromFirstKept.ids()).containsExactly("e-b4-4-4", "e-b4-4-5");
                assertThat(fromFirstKept.types()).doesNotContain("PERIMETER_GAP");
            }
        }
    }
}
