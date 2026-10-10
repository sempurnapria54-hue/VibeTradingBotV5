package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.9} — пустая идентичность события сделочного класса
 * отвергается наравне с отсутствующей (.claude/tests/cases/statistics.md).
 *
 * <p><b>Ожидание взято из дома:</b> умолчание не бывает благоприятным, и
 * ошибка направляется в запретительную сторону (docs/concept.md, П1).
 * Обязательность входа мерится <b>непустым значением</b>, а не ненулевой
 * ссылкой ({@code DealFact.hasCompleteInput}): факт с пустой идентичностью
 * схлопнул бы о свою отметку обработанного всякое следующее событие того же
 * момента (находка {@code F-4}, закрыта заходом 229).
 *
 * <p><b>Почему у ПУСТОЙ формы своя клетка.</b> Отсутствующую идентичность
 * держат два охранника — предикат полноты факта и {@code not null} колонки
 * схемы, — и утрата предиката на ней не видна. Пустую строку {@code not null}
 * пропускает, и предикат у неё единственный охранник.
 *
 * <p><b>Запись у клетки ОДНА.</b> Прежняя редакция клала три записи — пустые
 * идентичность, тенант и счёт — одним контекстом и мерила из них только
 * первую: отравленная запись занимает единственный поток слушателя, и вторая с
 * третьей до обработки не доходили. Их единицы — свои клетки своими классами:
 * {@link EmptyDealTenantBoxTest} ({@code B2.16}) и
 * {@link EmptyDealAccountBoxTest} ({@code B2.17}).
 */
class EmptyMandatoryValuesBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-9");
    }

    @Test
    @DisplayName("B2.9 — Пустая идентичность события сделочного класса отвергается наравне с отсутствующей")
    void anEmptyDealEventIdentityIsRejectedLikeAnAbsentOne() {
        givenReceptionStateRows();

        poisonUntilSettled(envelopeWith(EVENT_ID, ""), TENANT, Bodies.dealClosed(ACCOUNT, "S-1"));

        assertReceptionHalted();
    }
}
