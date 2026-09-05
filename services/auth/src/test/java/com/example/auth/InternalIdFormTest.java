package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tradingbot.domain.util.InternalIdFactory;
import org.junit.jupiter.api.Test;

/**
 * Формы идентификатора различаются тем, уезжает ли сущность на площадку
 * (docs/architecture/data-ownership.md §Идентификаторы).
 *
 * <p>Тест стои́т здесь, у первого потребителя фабрики, и охраняет
 * арифметику, из-за которой формы и разведены: потолок `clOrdId` у
 * площадки — 32 символа, а строка UUID — 36.
 */
class InternalIdFormTest {

    /** Потолок поля клиентского идентификатора у источника. */
    private static final int EXCHANGE_ID_LIMIT = 32;

    @Test
    void internalEntityGetsUuid() {
        String id = InternalIdFactory.forInternalEntity();

        assertThat(id).hasSize(36);
        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void exchangeBoundEntityFitsSourceLimitAndCarriesMarker() {
        String id = InternalIdFactory.forExchangeBoundEntity();

        assertThat(id).hasSize(EXCHANGE_ID_LIMIT);
        assertThat(id).startsWith("vtb");
        assertThat(id).matches("[a-z0-9]+");
    }

    /** Идентификатор уезжающей сущности не мог бы быть UUID — он длиннее потолка. */
    @Test
    void uuidWouldNotFitTheSourceLimit() {
        assertThat(InternalIdFactory.forInternalEntity().length()).isGreaterThan(EXCHANGE_ID_LIMIT);
    }

    @Test
    void identifiersDoNotRepeat() {
        assertThat(InternalIdFactory.forExchangeBoundEntity())
                .isNotEqualTo(InternalIdFactory.forExchangeBoundEntity());
    }
}
