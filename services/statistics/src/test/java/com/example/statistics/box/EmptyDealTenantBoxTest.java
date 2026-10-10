package com.example.statistics.box;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Клетка {@code B2.16} — пустой ключ записи у сделочного класса отвергается
 * наравне с отсутствующим (.claude/tests/cases/statistics.md).
 *
 * <p>Тенант приезжает КЛЮЧОМ записи и ничем иным
 * (docs/architecture/data-ownership.md §«Разделение по тенанту»); строка с
 * пустым тенантом была бы невидима выборке чтения и дала бы зерно агрегата,
 * которого не прочитает никто (docs/concept.md, П1).
 *
 * <p><b>Единица — пустая форма, а не отсутствующая.</b> Отсутствующий ключ
 * держат два охранника — предикат полноты сделочного факта и {@code not null}
 * колонки, — пустую строку только первый
 * (.claude/tests/cases/statistics.md, пояснение у {@code B2.9}). Своим
 * классом клетка живёт потому, что травит приём
 * ({@link PoisonedReceptionBox}).
 */
class EmptyDealTenantBoxTest extends PoisonedReceptionBox {

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        StatisticsSubstrate.registerOwn(registry, "b2-16");
    }

    @Test
    @DisplayName("B2.16 — Пустой ключ записи у сделочного класса отвергается наравне с отсутствующим")
    void anEmptyRecordKeyOfADealClassIsRejectedLikeAnAbsentOne() {
        givenReceptionStateRows();

        poisonUntilSettled(envelope(POISON_EVENT, DEAL_CLOSED, occurredAt), "",
                Bodies.dealClosed(ACCOUNT, "S-1"));

        assertReceptionHalted();
    }
}
