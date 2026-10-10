package com.example.statistics.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.21} — пустой класс события отвергается наравне с
 * отсутствующим, а не принимается неизвестным
 * (.claude/tests/cases/statistics.md).
 *
 * <p><b>Пустая строка — отсутствующий класс, а не неизвестный</b>
 * (docs/models/domain/other/StatisticsFact.md §«Признак несомого класса»):
 * неизвестный класс назван и уходит в ненесомые ({@code B1.8}), а пустая
 * строка не называет никакого, и та же ветвь на ней была бы благоприятным
 * умолчанием (docs/rules/absent-value-semantics.md).
 *
 * <p><b>Содержимое — штатный терминал сделки, и это несущее.</b> Клетка мерит
 * ровно ту потерю, против которой заведена: несомое событие, потерявшее
 * класс, ушло бы без факта со сдвинутым смещением. Исход охраны, мерящей
 * ссылку, а не значение, — продвинутое смещение без строки, и его ловит
 * общий ассерт группы.
 *
 * <p>Своим классом клетка живёт потому, что травит приём
 * ({@link PoisonedReceptionBox}).
 */
class EmptyEventTypeBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-21");
    }

    @Test
    @DisplayName("B2.21 — Пустой класс события отвергается наравне с отсутствующим, а не принимается неизвестным")
    void anEmptyEventClassIsRejectedLikeAnAbsentOneRatherThanAcceptedAsUnknown() {
        givenReceptionStateRows();

        poisonUntilSettled(envelopeWith(EVENT_TYPE, ""), TENANT, Bodies.dealClosed(ACCOUNT, "S-1"));

        assertReceptionHalted();
        assertThat(pair(topic()).get(LAST_ACCEPTED_COLUMN))
                .as("B2.21: исход неизвестного класса (B1.8) двинул бы момент последнего "
                        + "принятого; у отсутствующего класса он не двигается")
                .isNull();
    }
}
