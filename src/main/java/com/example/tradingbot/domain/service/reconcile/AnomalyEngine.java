package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.DbInstrumentState;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class AnomalyEngine {

    private static final String SEVERITY_CRITICAL = "CRITICAL";
    private static final String SEVERITY_NON_CRITICAL = "NON_CRITICAL";

    public Optional<AnomalyDecision> evaluate(InstrumentBucket bucket) {
        DbInstrumentState dbState = bucket.getDbState();

        boolean instrumentUnknown = Objects.isNull(dbState) || Objects.isNull(dbState.getInstrument());
        boolean instrumentOnHold = Objects.nonNull(dbState)
            && Objects.nonNull(dbState.getInstrument())
            && "HOLD".equalsIgnoreCase(dbState.getInstrument().getStatus());

        int exPositions = bucket.getPositionsCount();
        int exOrders = bucket.getOrdersCount();
        int exAlgoOrders = bucket.getAlgoOrdersCount();

        int dbPositions = Objects.isNull(dbState) ? 0 : dbState.getPositionsCount();
        int dbOrders = Objects.isNull(dbState) ? 0 : dbState.getOrdersCount();
        int dbAlgoOrders = Objects.isNull(dbState) ? 0 : dbState.getAlgoOrdersCount();

        if (instrumentUnknown && (exPositions + exOrders + exAlgoOrders > 0)) {
            return Optional.of(build("B1", SEVERITY_CRITICAL, true, true, "Unknown instrument has exchange entities", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if (instrumentOnHold && (exPositions + exOrders + exAlgoOrders > 0)) {
            return Optional.of(build("B2", SEVERITY_CRITICAL, true, true, "Instrument is HOLD but exchange has active entities", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if (exPositions > 1) {
            return Optional.of(build("B3", SEVERITY_CRITICAL, true, true, "More than one exchange position detected", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if (exPositions > 0 && dbPositions == 0) {
            return Optional.of(build("B4", SEVERITY_CRITICAL, true, true, "Exchange position exists while DB has none", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if (exPositions == 1 && exAlgoOrders == 0) {
            return Optional.of(build("B5", SEVERITY_CRITICAL, true, true, "Protective SL is missing for open position", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if ((dbPositions + dbOrders + dbAlgoOrders > 0)
            && (exPositions + exOrders + exAlgoOrders == 0)
            && BooleanUtils.isFalse(instrumentOnHold)) {
            return Optional.of(build("B6", SEVERITY_NON_CRITICAL, false, false, "DB has active entities while exchange is empty", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if (instrumentOnHold
            && exPositions == 0
            && exOrders == 0
            && exAlgoOrders == 0
            && (dbPositions + dbOrders + dbAlgoOrders > 0)) {
            return Optional.of(build("B7", SEVERITY_NON_CRITICAL, false, false, "Instrument HOLD with empty exchange but stale DB entities", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        if (exPositions == 0 && (exOrders > 0 || exAlgoOrders > 0)) {
            return Optional.of(build("B8", SEVERITY_NON_CRITICAL, true, true, "Orders/algoOrders exist without position", bucket,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders));
        }

        return Optional.empty();
    }

    private AnomalyDecision build(
        String type,
        String severity,
        boolean shouldHold,
        boolean shouldCancelFlow,
        String summary,
        InstrumentBucket bucket,
        int exPositions,
        int exOrders,
        int exAlgoOrders,
        int dbPositions,
        int dbOrders,
        int dbAlgoOrders
    ) {
        String details = "{" +
            "\"instrument\":\"" + escape(bucket.getInstrumentName()) + "\"," +
            "\"exchangeCounts\":{\"positions\":" + exPositions + ",\"orders\":" + exOrders + ",\"algoOrders\":" + exAlgoOrders + "}," +
            "\"dbCounts\":{\"positions\":" + dbPositions + ",\"orders\":" + dbOrders + ",\"algoOrders\":" + dbAlgoOrders + "}" +
            "}";
        return AnomalyDecision.builder()
            .type(type)
            .severity(severity)
            .shouldHold(shouldHold)
            .shouldCancelFlow(shouldCancelFlow)
            .summary(summary)
            .detailsJson(details)
            .build();
    }

    private String escape(String value) {
        if (Objects.isNull(value)) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
