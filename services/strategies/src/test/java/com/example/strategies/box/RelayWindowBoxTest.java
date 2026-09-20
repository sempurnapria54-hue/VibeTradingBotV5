package com.example.strategies.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Окно тика реле — клетка {@code B7.6}
 * (.claude/tests/cases/strategies.md).
 *
 * <p><b>Свой контекст, и это ВХОД клетки:</b> предусловие требует
 * неопубликованных строк ВДВОЕ больше окна, а окно приезжает
 * конфигурацией. Ставить его общему ящику значило бы менять предмет всем
 * его соседкам, а копить четыре сотни строк переходами — платить
 * временем за то, что задаётся одной осью.
 *
 * <p><b>Строки копятся переходами ОДНОГО определения, а не четырьмя
 * определениями.</b> Порядок публикации клетка мерит внутри тенанта, а
 * ключ партиции — тенант: четыре определения одного тенанта дали бы то
 * же самое, но с четырьмя проходами охраны создания вместо одного.
 */
class RelayWindowBoxTest extends StrategiesBox {

    /** Окно тика: неопубликованных строк у клетки будет вдвое больше. */
    private static final Integer WINDOW = 2;

    /** Сколько строк копит клетка: ровно два окна. */
    private static final Integer ROWS = 4;

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StrategiesSubstrate.register(registry,
                Map.of(StrategiesSubstrate.RELAY_WINDOW_KEY, String.valueOf(WINDOW)));
    }

    @Test
    @DisplayName("B7.6 — Окно тика — верхняя граница прохода, а не неполнота")
    void b7_6_theWindowIsAnUpperBoundOfThePassNotItsIncompleteness() {
        peerResolvesEverything();
        String internalId = givenActive(TENANT);
        assertThat(moveTo(internalId, TENANT, "INACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "ACTIVE").status()).isEqualTo(200);
        assertThat(moveTo(internalId, TENANT, "DELETED").status()).isEqualTo(200);
        List<String> written = eventIds();
        assertThat(written).as("строк вдвое больше окна").hasSize(ROWS);

        Wire.Mark firstMark = Wire.mark();
        relayPass();
        awaitPublished(WINDOW);
        List<Wire.Published> first = Wire.publishedSince(firstMark);
        Wire.Mark secondMark = Wire.mark();
        relayPass();
        awaitPublished(ROWS);
        List<Wire.Published> second = Wire.publishedSince(secondMark);

        assertThat(first).as("первый тик публикует ровно окно").hasSize(WINDOW);
        assertThat(second).as("второй — остаток: упор в окно неполнотой не считается").hasSize(2);
        assertThat(publishedIds(first, second))
                .as("ни одна строка не опубликована дважды, и порядок тот же, что у записи")
                .isEqualTo(written);
        assertThat(marked()).as("непомеченных строк не осталось").hasSize(ROWS);
    }

    /** Идентичности событий всех строк outbox в порядке записи. */
    private List<String> eventIds() {
        return events().stream().map(row -> String.valueOf(row.get("event_id"))).toList();
    }

    /** Идентичности опубликованного двумя тиками, в порядке публикации. */
    private List<String> publishedIds(List<Wire.Published> first, List<Wire.Published> second) {
        return Stream.concat(first.stream(), second.stream())
                .map(Wire.Published::eventId)
                .toList();
    }
}
