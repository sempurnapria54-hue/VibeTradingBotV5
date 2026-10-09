package com.example.tradingcore.unit.calc;

import static com.example.tradingcore.unit.calc.AttachedFacts.facts;
import static com.example.tradingcore.unit.calc.AttachedFacts.observed;
import static com.example.tradingcore.unit.calc.AttachedFacts.observedFailingToPlace;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingcore.domain.command.resolve.AttachedAlgoOrderStateResolver;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionResolution;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Встроенная защита: класс родителя, отказ постановки и гейт цикла —
 * группа {@code U10} документа `.claude/tests/cases/trading-core-calc.md`
 * (дом — docs/spec/order-lifecycle.json §{@code attachedParentStatus},
 * §{@code attachedParentClass},
 * §{@code attachedOutcomeByParent}, §{@code attachedFailsToPlace};
 * docs/components/AttachedAlgoOrderStateResolver.md §Границы; звенья
 * Z23-Z25).
 *
 * <p><b>Различает не статус сам по себе, а ПАРА «терминален ли
 * родитель» + «каков налив»:</b> до терминала налив исхода не меняет, на
 * терминале он его и определяет. Пустой налив нулём НЕ подменяется —
 * подмена увела бы недобытый факт в «снята вместе с родителем».
 *
 * <p><b>Базовая сборка:</b> {@code new AttachedAlgoOrderStateResolver()};
 * набор фактов собирается строителем. Конфигурации у единицы нет вовсе.
 *
 * <p><b>Пустой статус родителя оба входа читают одинаково</b> —
 * отсутствием факта: гейт цикла не запускает ({@code U10.1}), резолв
 * исхода не определяет ({@code U10.13}, находка {@code F4} закрыта).
 */
class AttachedParentClassTest {

    private final AttachedAlgoOrderStateResolver resolver = new AttachedAlgoOrderStateResolver();

    private static BigDecimal fill(String value) {
        return CalcFixture.decimal(value);
    }

    @Test
    @DisplayName("U10.1 — гейт цикла: пустой статус родителя цикла не запускает, исключения нет")
    void u10_1_anEmptyParentStatusDoesNotRunTheSearchCycle() {
        assertThatCode(() -> assertThat(resolver.runsSearchCycle(null, null, null, fill("1")))
                .as("охрана пустоты стои́т явно (Z25)")
                .isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U10.2 — гейт цикла: родитель не подтверждён — цикл не запускается")
    void u10_2_anUnconfirmedParentDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.CREATED, null, null, fill("1"))).isFalse();
        assertThat(resolver.runsSearchCycle(Order.Status.PENDING, null, null, fill("1"))).isFalse();
    }

    @Test
    @DisplayName("U10.3 — гейт цикла: живой родитель — «искать дальше» значит «наблюдаем дальше»")
    void u10_3_aLiveParentDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.ACTIVE, null, null, fill("1"))).isFalse();
        assertThat(resolver.runsSearchCycle(Order.Status.PARTIALLY_COMPLETED, null, null, fill("1"))).isFalse();
    }

    /**
     * Проблемный — родитель, о котором площадка не показала ничего:
     * наблюдённая живость пуста либо истинна, наблюдённого статуса нет.
     * Пустота нежилостью не читается.
     */
    @Test
    @DisplayName("U10.4 — гейт цикла: родитель в ERROR без наблюдения нежилости цикла не запускает")
    void u10_4_aProblemParentDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, null, null, fill("1"))).isFalse();
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, null, Boolean.TRUE, fill("1"))).isFalse();
    }

    @Test
    @DisplayName("U10.5 — гейт цикла: родитель исполнен с положительным наливом — цикл запускается")
    void u10_5_aFilledTerminalParentRunsTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.COMPLETED, null, null, fill("1"))).isTrue();
    }

    @Test
    @DisplayName("U10.6 — гейт цикла: дискриминатор — налив, а не статус")
    void u10_6_aCancelledParentWithAFillRunsTheSearchCycleToo() {
        assertThat(resolver.runsSearchCycle(Order.Status.CANCELED, null, null, fill("1"))).isTrue();
    }

    @Test
    @DisplayName("U10.7 — гейт цикла: терминал с наливом ровно ноль — цикл не запускается")
    void u10_7_aTerminalParentWithAZeroFillDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.CANCELED, null, null, fill("0"))).isFalse();
    }

    @Test
    @DisplayName("U10.8 — гейт цикла: пустой налив нулём не подменяется — цикл запускается")
    void u10_8_anEmptyFillIsNotSubstitutedByZero() {
        assertThat(resolver.runsSearchCycle(Order.Status.COMPLETED, null, null, fill("")))
                .as("иначе недобытый факт ушёл бы в «снята вместе с родителем»")
                .isTrue();
    }

    @Test
    @DisplayName("U10.9 — непустой код отказа постановки при живом родителе: ошибка постановки защиты")
    void u10_9_aFailCodeGivesThePlacementFailure() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observedFailingToPlace("51008"))
                .parentStatus(Order.Status.ACTIVE)
                .parentAccumulatedFillSize(fill("1"))
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ERROR);
        assertThat(resolution.getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED);
    }

    @Test
    @DisplayName("U10.10 — тот же код отказа при проблемном родителе: охрана отказа стои́т раньше")
    void u10_10_theFailCodeGuardOutranksTheParentClass() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observedFailingToPlace("51008"))
                .parentStatus(Order.Status.ERROR)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ERROR);
        assertThat(resolution.getCloseReason())
                .as("отказ постановки — свой факт, и состояние родителя его не отменяет (Z23)")
                .isEqualTo(AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED);
    }

    /**
     * Родитель без наблюдения защиту не двигает: её живость не исключена, и
     * ошибочный исход увёл бы в неживые то, что снятие риска обязано снять.
     */
    @Test
    @DisplayName("U10.11 — проблемный родитель без кода отказа: исход «ждать», защита не двигается")
    void u10_11_aProblemParentLeavesTheProtectionWaiting() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ERROR)
                .parentAccumulatedFillSize(fill("1"))
                .build());

        assertThat(resolution.getStatus()).as("статус защиты не двигается").isNull();
        assertThat(resolution.getCloseReason()).isNull();
        assertThat(resolution.getOutcomeUndetermined())
                .as("факта не искали: сигнала пустого разбора нет")
                .isFalse();
    }

    @Test
    @DisplayName("U10.12 — родитель отменён с наливом ровно ноль: снята вместе с родителем")
    void u10_12_aCancelledEmptyParentCancelsTheProtection() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.CANCELED)
                .parentAccumulatedFillSize(fill("0"))
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(resolution.getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
    }

    @Test
    @DisplayName("U10.13 — пустой статус родителя у резолва: пустота есть отсутствие факта, исход не определён")
    void u10_13_anEmptyParentStatusLeavesTheOutcomeUndetermined() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentAccumulatedFillSize(fill("1"))
                .build());

        assertThat(resolution.getOutcomeUndetermined())
                .as("та же трактовка, что у публичного гейта (Z25): класс родителя не выводится")
                .isTrue();
        assertThat(resolution.getStatus()).as("статус защиты не двигается").isNull();
        assertThat(resolution.getCloseReason()).isNull();
    }

    /**
     * Наблюдённый живой статус у родителя в проблемном терминале читается
     * тем же правилом, что наблюдённый терминал: пометка ошибки — наше
     * safety-состояние, а не факт площадки. Защита родителя с наливом
     * наблюдается дальше живой, а не уходит в ошибочные, где снятие риска её
     * бы не сняло.
     */
    @ParameterizedTest
    @EnumSource(value = Order.Status.class, names = {"ACTIVE", "PARTIALLY_COMPLETED"})
    @DisplayName("U10.14 — проблемный родитель, наблюдённый живым: класс живого, цикл не запускается")
    void u10_14_aProblemParentObservedLiveTakesTheLiveClass(Order.Status observedStatus) {
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, observedStatus, null, fill("2")))
                .as("у живого родителя «искать дальше» значит «наблюдаем дальше»")
                .isFalse();

        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ERROR)
                .parentObservedStatus(observedStatus)
                .parentAccumulatedFillSize(fill("2"))
                .build());

        assertThat(resolution.getStatus())
                .as("класс живого держит защиту в постановке — записи нет; класс проблемного увёл бы её в ошибочные")
                .isEqualTo(AttachedAlgoOrder.Status.PENDING);
        assertThat(resolution.getCloseReason()).isNull();
        assertThat(resolution.getOutcomeUndetermined()).isFalse();
    }

    /**
     * Ненайденность полным циклом — тоже наблюдение: на площадке родителя
     * нет, и налива он больше не наберёт. Непустой налив мог материализовать
     * защиту живой записью, и её ищет цикл добычи.
     */
    @Test
    @DisplayName("U10.15 — родитель в ERROR, не найденный полным циклом, налив больше нуля: цикл запускается, найденная запись живёт")
    void u10_15_anUnfoundErrorParentWithAFillRunsTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, null, Boolean.FALSE, fill("2")))
                .as("класс — терминальный с наливом, а не проблемный")
                .isTrue();

        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ERROR)
                .parentExternalLive(Boolean.FALSE)
                .parentAccumulatedFillSize(fill("2"))
                .standaloneRecordFound(Boolean.TRUE)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(resolution.getCloseReason()).isNull();
    }

    /** Недобытый налив нулём не подменяется: поиск, а не уход с родителем. */
    @Test
    @DisplayName("U10.16 — родитель в ERROR, не найденный полным циклом, налив пуст: цикл запускается")
    void u10_16_anUnfoundErrorParentWithAnEmptyFillRunsTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, null, Boolean.FALSE, fill("")))
                .as("класс — терминальный с недобытым наливом")
                .isTrue();

        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ERROR)
                .parentExternalLive(Boolean.FALSE)
                .standaloneRecordFound(Boolean.TRUE)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ACTIVE);
        assertThat(resolution.getCloseReason()).isNull();
    }

    /** Материализоваться защите было нечем — она уходит с родителем. */
    @Test
    @DisplayName("U10.17 — родитель в ERROR, не найденный полным циклом, налив ровно ноль: снята вместе с родителем")
    void u10_17_anUnfoundErrorParentWithAZeroFillCancelsTheProtection() {
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, null, Boolean.FALSE, fill("0"))).isFalse();

        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ERROR)
                .parentExternalLive(Boolean.FALSE)
                .parentAccumulatedFillSize(fill("0"))
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.CANCELED);
        assertThat(resolution.getCloseReason())
                .isEqualTo(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
    }
}
