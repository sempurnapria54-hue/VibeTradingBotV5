package com.example.tradingcore.box;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Иные числа допуска сверки — вторая половина клетки {@code B13.7}.
 *
 * <p><b>Своя конфигурация контекста, и это ВХОД клетки:</b> пол допуска
 * {@code pnl-reconciliation.floor} есть предмет кейса — правка числа
 * обязана сдвинуть границу расхождения без правки кода. Вход у клетки тот
 * же, что у штатной половины ({@link SchemaInputBoxTest}): расхождение,
 * которое штатный пол прощает, здесь его превышает.
 *
 * <p><b>Своя группа потребителя и своя тема владельца определений</b> —
 * довод у шапки {@link TradingCoreSubstrate}.
 */
class NarrowToleranceBoxTest extends LiveDealBox {

    /** Имя одиночки: им названы и её группа потребителя, и её тема. */
    private static final String NAME = "box-narrow-tolerance";

    /**
     * Пол допуска меньше расхождения входа: относительный член этой сделки
     * ещё меньше, и допуск равен полу.
     */
    private static final String NARROW_FLOOR = "0.001";

    @DynamicPropertySource
    static void substrate(DynamicPropertyRegistry registry) {
        TradingCoreSubstrate.registerOwn(registry, NAME, Map.of(
                TradingCoreSubstrate.TOLERANCE_FLOOR_KEY, NARROW_FLOOR));
    }

    /** Своя тема владельца определений — довод у шапки субстрата. */
    @Override
    protected String strategyTopic() {
        return TradingCoreSubstrate.ownStrategyTopic(NAME);
    }

    @Test
    @DisplayName("B13.7 (иные числа) — правка чисел допуска сдвигает границу без правки кода")
    void editingTheToleranceNumbersMovesTheBoundaryWithoutCodeChanges() {
        closeLiveDealWith("S-NARROW-1", SchemaInputBoxTest.WITHIN_RECORD, SchemaInputBoxTest.BILL);

        // То же расхождение, что штатный пол прощает, при узком поле —
        // расхождение: граница задана числом конфигурации, а не кодом.
        assertThat(dealStatus()).isEqualTo("CLOSED");
        assertThat(dealRow().get("reconciliation_status")).isEqualTo("MISMATCHED");
    }
}
