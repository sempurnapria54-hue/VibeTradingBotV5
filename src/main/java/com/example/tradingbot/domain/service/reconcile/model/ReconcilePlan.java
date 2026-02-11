package com.example.tradingbot.domain.service.reconcile.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReconcilePlan {

    private final int targetPositionsCount;
    private final int targetOrdersCount;
    private final int targetAlgoOrdersCount;
    private final List<ReconcileAction> createMissing;
    private final List<ReconcileAction> markUnknown;
    private final List<ReconcileAction> markClosed;

    @Getter
    @Builder
    public static class ReconcileAction {

        private final EntityType entityType;
        private final Long entityId;
        private final String externalId;
        private final String clientId;

        public static ReconcileAction create(EntityType entityType, String externalId, String clientId) {
            return ReconcileAction.builder()
                .entityType(entityType)
                .externalId(externalId)
                .clientId(clientId)
                .build();
        }

        public static ReconcileAction mark(EntityType entityType, Long entityId) {
            return ReconcileAction.builder()
                .entityType(entityType)
                .entityId(entityId)
                .build();
        }
    }

    public enum EntityType {
        POSITION,
        ORDER,
        ALGO_ORDER
    }
}
