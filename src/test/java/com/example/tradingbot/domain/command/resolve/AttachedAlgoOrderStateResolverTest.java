package com.example.tradingbot.domain.command.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Связывает резолвер встроенной защиты с его исполнимой спецификацией
 * (docs/spec/order-lifecycle.json §attachedParentClass,
 * §attachedOutcomeByParent, §attachedBecomesActive,
 * §searchExhaustedOutcome, §attachedHistoryStatus,
 * §attachedHistoryCloseReason) и с домом матриц —
 * docs/lifecycles/Order.md.
 */
class AttachedAlgoOrderStateResolverTest {

    private final AttachedAlgoOrderStateResolver resolver = new AttachedAlgoOrderStateResolver();

    @Test
    @DisplayName("Присутствие в теле живого родителя без налива живости НЕ доказывает")
    void presenceInLiveParentDoesNotProveLiveness() {
        AttachedProtectionResolution live = resolver.resolve(facts(Order.Status.ACTIVE, BigDecimal.ZERO)
                .snapshot(snapshot(null, null))
                .build());

        assertEquals(AttachedAlgoOrder.Status.PENDING, live.getStatus());
        assertNull(live.getCloseReason());

        // Налив есть — защита материализована, и предикат живости срабатывает.
        AttachedProtectionResolution filled = resolver.resolve(facts(Order.Status.PARTIALLY_COMPLETED, BigDecimal.ONE)
                .snapshot(snapshot(null, null))
                .build());
        assertEquals(AttachedAlgoOrder.Status.ACTIVE, filled.getStatus());
    }

    @Test
    @DisplayName("Отменённый родитель: терминал даёт НАЛИВ, а недобытый налив нулём не подменяется")
    void terminalOutcomeIsDiscriminatedByFill() {
        AttachedProtectionResolution empty = resolver.resolve(facts(Order.Status.CANCELED, BigDecimal.ZERO)
                .snapshot(snapshot(null, null))
                .build());

        assertEquals(AttachedAlgoOrder.Status.CANCELED, empty.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED, empty.getCloseReason());

        // Пустой налив — «факт не получен», а не «налива нет»: в CANCEL_BY_PARENT
        // не уводим, иначе пометили бы снятой возможно живую защиту.
        AttachedProtectionResolution unknown = resolver.resolve(facts(Order.Status.CANCELED, null)
                .snapshot(snapshot(null, null))
                .trancheExposure(BigDecimal.ZERO)
                .build());
        assertFalse(AttachedAlgoOrder.Status.CANCELED.equals(unknown.getStatus()));
    }

    @Test
    @DisplayName("Вторая ступень траншевая: экспозицию соседа покрытием этого транша не считаем")
    void secondStageReadsTrancheExposureOnly() {
        AttachedProtectionResolution lost = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ONE)
                .standaloneProtectionExists(false)
                .build());

        assertEquals(AttachedAlgoOrder.Status.ERROR, lost.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.PROTECTION_LOST, lost.getCloseReason());

        // Своей экспозиции нет — живую позицию держит сосед; покрытие НЕ потеряно,
        // терминал не ставится, ветвь уходит в разбор истории.
        AttachedProtectionResolution covered = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ZERO)
                .standaloneProtectionExists(false)
                .build());
        assertTrue(covered.getOutcomeUndetermined());
        assertFalse(covered.hasStatus());
    }

    @Test
    @DisplayName("Живой родитель цикла не запускает: исчерпания на нём не бывает")
    void liveParentNeverYieldsProtectionLost() {
        AttachedProtectionResolution resolution = resolver.resolve(facts(Order.Status.ACTIVE, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ONE)
                .standaloneProtectionExists(false)
                .snapshot(snapshot(null, null))
                .build());

        assertEquals(AttachedAlgoOrder.Status.ACTIVE, resolution.getStatus());
        assertNull(resolution.getCloseReason());
    }

    @Test
    @DisplayName("Три тропы потери покрытия разведены значением, а не одной причиной на всех")
    void threeLossTropesCarryDistinctReasons() {
        AttachedProtectionResolution placement = resolver.resolve(facts(Order.Status.ACTIVE, BigDecimal.ONE)
                .snapshot(snapshot("51000", null))
                .build());
        assertEquals(AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED, placement.getCloseReason());

        AttachedProtectionResolution lost = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ONE)
                .standaloneProtectionExists(false)
                .build());
        assertEquals(AttachedAlgoOrder.CloseReason.PROTECTION_LOST, lost.getCloseReason());

        AttachedProtectionResolution triggerFailed = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ZERO)
                .historyLegFound(ProtectionHistoryLeg.ORDER_FAILED)
                .build());
        assertEquals(AttachedAlgoOrder.CloseReason.PROTECTION_TRIGGER_FAILED, triggerFailed.getCloseReason());

        assertEquals(3, java.util.Set.of(placement.getCloseReason(), lost.getCloseReason(),
                triggerFailed.getCloseReason()).size());
    }

    @Test
    @DisplayName("Причина снятой записи берётся из НАШЕГО намерения, иначе замыкается перечнем")
    void canceledLegTakesReasonFromOurIntent() {
        AttachedProtectionResolution switched = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ZERO)
                .historyLegFound(ProtectionHistoryLeg.CANCELED)
                .cancelIntentStanding(true)
                .build());
        assertEquals(AttachedAlgoOrder.Status.CANCELED, switched.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.SWITCHED_BY_STRATEGY, switched.getCloseReason());

        AttachedProtectionResolution unknown = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ZERO)
                .historyLegFound(ProtectionHistoryLeg.CANCELED)
                .cancelIntentStanding(false)
                .build());
        assertEquals(AttachedAlgoOrder.CloseReason.UNKNOWN, unknown.getCloseReason());
    }

    @Test
    @DisplayName("Пустой разбор истории терминала НЕ ставит — это отсутствие факта, а не факт")
    void emptyHistoryLeavesOutcomeUndetermined() {
        AttachedProtectionResolution resolution = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ZERO)
                .standaloneProtectionExists(true)
                .historyLegFound(null)
                .build());

        assertTrue(resolution.getOutcomeUndetermined());
        assertNull(resolution.getStatus());
        assertNull(resolution.getCloseReason());
    }

    @Test
    @DisplayName("Найденный терминал применяется через активацию тем же наблюдением")
    void observedTerminalGoesThroughActivation() {
        AttachedProtectionResolution effective = resolver.resolve(facts(Order.Status.COMPLETED, BigDecimal.ONE)
                .trancheExposure(BigDecimal.ZERO)
                .historyLegFound(ProtectionHistoryLeg.EFFECTIVE)
                .build());
        assertEquals(AttachedAlgoOrder.Status.COMPLETED, effective.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.TRIGGERED, effective.getCloseReason());

        // Ребра PENDING -> COMPLETED в матрице нет: без активации применить нечем.
        AttachedAlgoOrder attached = new AttachedAlgoOrder();
        attached.setStatus(AttachedAlgoOrder.Status.PENDING);
        assertFalse(attached.canTransitionTo(AttachedAlgoOrder.Status.COMPLETED));

        attached.applyObservedTerminal(effective.getStatus(), effective.getCloseReason());
        assertEquals(AttachedAlgoOrder.Status.COMPLETED, attached.getStatus());
        assertEquals(AttachedAlgoOrder.CloseReason.TRIGGERED, attached.getCloseReason());
    }

    @Test
    @DisplayName("Предъявленная самостоятельная запись гасит недобытость налива родителя")
    void standaloneRecordProvesMaterialization() {
        AttachedProtectionResolution resolution = resolver.resolve(facts(Order.Status.CANCELED, null)
                .snapshot(snapshot(null, null))
                .standaloneRecordFound(true)
                .build());

        assertEquals(AttachedAlgoOrder.Status.ACTIVE, resolution.getStatus());
        assertFalse(resolution.getOutcomeUndetermined());
    }

    private AttachedProtectionFacts.AttachedProtectionFactsBuilder facts(Order.Status parentStatus,
                                                                        BigDecimal parentFill) {
        return AttachedProtectionFacts.builder()
                .parentStatus(parentStatus)
                .parentAccumulatedFillSize(parentFill)
                .standaloneRecordFound(false)
                .standaloneProtectionExists(false)
                .cancelIntentStanding(false);
    }

    private AttachedAlgoOrderExternalSnapshot snapshot(String failCode, String externalStatus) {
        return AttachedAlgoOrderExternalSnapshot.builder()
                .internalId("attached-1")
                .failCode(failCode)
                .externalStatus(externalStatus)
                .build();
    }
}
