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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Встроенная защита: класс родителя, отказ постановки и гейт цикла —
 * группа {@code U10} документа `.claude/tests/cases/trading-core-calc.md`
 * (дом — docs/spec/order-lifecycle.json §{@code attachedParentClass},
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
 * <p><b>Один кейс группы красен по построению</b> и помечен
 * {@code @Tag("debt")}: {@code U10.13} предъявляет находку {@code F4} —
 * публичный гейт цикла пустой статус родителя охраняет явным возвратом
 * лжи, а резолв на том же значении роняет разыменование.
 */
class AttachedParentClassTest {

    private final AttachedAlgoOrderStateResolver resolver = new AttachedAlgoOrderStateResolver();

    private static BigDecimal fill(String value) {
        return CalcFixture.decimal(value);
    }

    @Test
    @DisplayName("U10.1 — гейт цикла: пустой статус родителя цикла не запускает, исключения нет")
    void u10_1_anEmptyParentStatusDoesNotRunTheSearchCycle() {
        assertThatCode(() -> assertThat(resolver.runsSearchCycle(null, fill("1")))
                .as("охрана пустоты стои́т явно (Z25)")
                .isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("U10.2 — гейт цикла: родитель не подтверждён — цикл не запускается")
    void u10_2_anUnconfirmedParentDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.CREATED, fill("1"))).isFalse();
        assertThat(resolver.runsSearchCycle(Order.Status.PENDING, fill("1"))).isFalse();
    }

    @Test
    @DisplayName("U10.3 — гейт цикла: живой родитель — «искать дальше» значит «наблюдаем дальше»")
    void u10_3_aLiveParentDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.ACTIVE, fill("1"))).isFalse();
        assertThat(resolver.runsSearchCycle(Order.Status.PARTIALLY_COMPLETED, fill("1"))).isFalse();
    }

    @Test
    @DisplayName("U10.4 — гейт цикла: проблемный терминал родителя цикла не запускает")
    void u10_4_aProblemParentDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.ERROR, fill("1"))).isFalse();
    }

    @Test
    @DisplayName("U10.5 — гейт цикла: родитель исполнен с положительным наливом — цикл запускается")
    void u10_5_aFilledTerminalParentRunsTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.COMPLETED, fill("1"))).isTrue();
    }

    @Test
    @DisplayName("U10.6 — гейт цикла: дискриминатор — налив, а не статус")
    void u10_6_aCancelledParentWithAFillRunsTheSearchCycleToo() {
        assertThat(resolver.runsSearchCycle(Order.Status.CANCELED, fill("1"))).isTrue();
    }

    @Test
    @DisplayName("U10.7 — гейт цикла: терминал с наливом ровно ноль — цикл не запускается")
    void u10_7_aTerminalParentWithAZeroFillDoesNotRunTheSearchCycle() {
        assertThat(resolver.runsSearchCycle(Order.Status.CANCELED, fill("0"))).isFalse();
    }

    @Test
    @DisplayName("U10.8 — гейт цикла: пустой налив нулём не подменяется — цикл запускается")
    void u10_8_anEmptyFillIsNotSubstitutedByZero() {
        assertThat(resolver.runsSearchCycle(Order.Status.COMPLETED, fill("")))
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

    @Test
    @DisplayName("U10.11 — проблемный родитель без кода отказа: ошибка с неизвестной причиной")
    void u10_11_aProblemParentGivesAnUnknownReason() {
        AttachedProtectionResolution resolution = resolver.resolve(facts()
                .observed(observed())
                .parentStatus(Order.Status.ERROR)
                .build());

        assertThat(resolution.getStatus()).isEqualTo(AttachedAlgoOrder.Status.ERROR);
        assertThat(resolution.getCloseReason())
                .as("проблемный родитель своей причины защите не даёт")
                .isEqualTo(AttachedAlgoOrder.CloseReason.UNKNOWN);
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
    @Tag("debt")
    @DisplayName("U10.13 — пустой статус родителя у резолва: пустота есть отсутствие факта, а не отказ")
    void u10_13_anEmptyParentStatusDoesNotBreakTheResolve() {
        assertThatCode(() -> resolver.resolve(facts()
                .observed(observed())
                .parentAccumulatedFillSize(fill("1"))
                .build()))
                .as("ожидание дома — тот же отказ, что у публичного гейта (Z25): пустота есть "
                        + "отсутствие факта. Сегодня выбор по пустому статусу роняет разыменование, "
                        + "хотя публичный гейт ту же пустоту охраняет явно — находка F4 (Z24). "
                        + "Состояние недостижимо: факты собирает исполнитель добычи по уже "
                        + "прочитанной заявке")
                .doesNotThrowAnyException();
    }
}
