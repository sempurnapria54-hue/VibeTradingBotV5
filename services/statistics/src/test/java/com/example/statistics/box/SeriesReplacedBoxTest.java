package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B3.8} — ряды заменяются целиком, а не накапливаются
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Подписка у этого контекста объявлена ДВУМЯ темами, и без второй
 * клетка не выразима вовсе.</b> Снятие единственной темы делает приём
 * неживым, то есть подменяет предмет соседней клеткой {@code B3.6}: ряды
 * пропали бы не потому, что состав заменён, а потому, что такт промолчал.
 * Вторая тема здесь — ось конфигурации, а не второй производитель
 * ({@link StatisticsSubstrate#ownSecondTopic}).
 *
 * <p><b>Сужение наблюдается на ряде ПОРОГА, а не на ряде возраста, и это
 * вынужденная замена оси.</b> Состав пар внутри одного контекста неподвижен
 * по построению: тик приводит строки к объявленной подписке тем же тактом,
 * которым считает ряды, а подписку контейнер читает при подъёме. Значит
 * набор рядов возраста в живом контексте сузиться не может ни при каком
 * входе, и клетка, стоящая на нём, мерила бы недостижимое. Ряд порога
 * сужается на достижимом входе: тема, снятая у брокера, срока хранения
 * больше не отдаёт.
 *
 * <p><b>Предмет от замены оси не меняется.</b> Утверждается ровно то, что
 * названо домом: ряд живёт один такт, и ряд прошлого такта не остаётся ни
 * прежним значением, ни нулём (docs/components/ReceptionStateJob.md §«Ряды
 * экспорта пишет тот же тик»). Ряд, который такт измерил, при этом стои́т со
 * своей прежней меткой.
 *
 * <p><b>Ожидание держит ОБА условия, и второе несущее.</b> Снятие темы
 * роняет назначение на время ребалансировки группы, и такт этой минуты
 * уносит ряды ВСЕ — как всякий такт неживого приёма. Ожидание одного лишь
 * исчезновения ряда ушедшей темы кончилось бы именно там, и клетка сошлась
 * бы по причине соседней клетки {@code B3.6}. Поэтому условием стои́т пара:
 * ряда ушедшей нет, а ряд оставшейся ЕСТЬ, — то есть такт измерил и заменил
 * состав, а не промолчал.
 *
 * <p><b>Контекст закрывается вместе с классом</b>: одной темы его подписки у
 * брокера больше нет.
 */
@DirtiesContext
class SeriesReplacedBoxTest extends StatisticsBox {

    /** Краткое имя клетки: из него строятся её группа и её темы. */
    private static final String SLUG = "b3-8";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        List<String> both = List.of(
                StatisticsSubstrate.ownTopic(SLUG), StatisticsSubstrate.ownSecondTopic(SLUG));
        StatisticsSubstrate.registerOwn(registry, SLUG, both, both, Map.of());
    }

    @Test
    @DisplayName("B3.8 — Ряды заменяются целиком, а не накапливаются")
    void theSeriesAreReplacedWholesaleRatherThanAccumulated() {
        givenReceptionStateRows();
        tick();
        String staying = subscription().getFirst();
        String leaving = subscription().getLast();
        assertThat(thresholdRowOf(staying)).isNotNull();
        assertThat(thresholdRowOf(leaving)).isNotNull();
        Long thresholdBefore = thresholdRowOf(staying);

        Wire.deleteTopics(List.of(leaving));

        Awaitility.await()
                .atMost(RECEPTION_WAIT)
                .pollInterval(POLL)
                .until(() -> {
                    tick();
                    return Objects.isNull(thresholdRowOf(leaving))
                            && Objects.nonNull(thresholdRowOf(staying));
                });

        assertThat(thresholdRowOf(leaving))
                .as("ряд ушедшей темы пропал, а не застыл на прежнем значении").isNull();
        assertThat(thresholdRowOf(staying))
                .as("метка и значение оставшегося ряда прежние").isEqualTo(thresholdBefore);
        assertThat(ageRowOf(staying)).as("измеренное тактом на месте").isNotNull();
    }
}
