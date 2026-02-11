package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.AnomalyDecision;
import com.example.tradingbot.domain.service.reconcile.model.DbInstrumentState;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class AnomalyEngine {

    public Optional<AnomalyDecision> evaluate(ExchangeSnapshot snapshot, InstrumentBucket bucket, DbInstrumentState dbState) {
        String instrumentName = bucket.getInstrumentName();
        boolean instrumentUnknown = Objects.isNull(dbState.getInstrument());
        boolean instrumentOnHold = Objects.nonNull(dbState.getInstrument()) && "HOLD".equalsIgnoreCase(dbState.getInstrument().getStatus());

        int exPositions = bucket.getPositionsCount();
        int exOrders = bucket.getOrdersCount();
        int exAlgoOrders = bucket.getAlgoOrdersCount();
        int dbPositions = dbState.getPositionsCount();
        int dbOrders = dbState.getOrdersCount();
        int dbAlgoOrders = dbState.getAlgoOrdersCount();

        if (instrumentUnknown) {
            return Optional.of(build("B1", "CRITICAL", true, true, "Unknown instrument has exchange entities", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if (instrumentOnHold && (exPositions + exOrders + exAlgoOrders > 0)) {
            return Optional.of(build("B2", "CRITICAL", true, true, "Instrument is HOLD but exchange has active entities", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if (exPositions > 1) {
            return Optional.of(build("B3", "CRITICAL", true, true, "More than one exchange position detected", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if (exPositions > 0 && dbPositions == 0) {
            return Optional.of(build("B4", "CRITICAL", true, true, "Exchange position exists while DB has none", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if (exPositions == 1 && exAlgoOrders == 0) {
            return Optional.of(build("B5", "CRITICAL", true, true, "Protective SL is missing for open position", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if ((dbPositions + dbOrders + dbAlgoOrders > 0) && (exPositions + exOrders + exAlgoOrders == 0) && BooleanUtils.isFalse(instrumentOnHold)) {
            return Optional.of(build("B6", "WARN", false, false, "DB has active entities while exchange is empty", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if (instrumentOnHold && exPositions == 0 && exOrders == 0 && exAlgoOrders == 0 && (dbPositions + dbOrders + dbAlgoOrders > 0)) {
            return Optional.of(build("B7", "INFO", false, false, "Instrument HOLD with empty exchange but stale DB entities", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        if (exPositions == 0 && (exOrders > 0 || exAlgoOrders > 0)) {
            return Optional.of(build("B8", "WARN", true, true, "Orders/algoOrders exist without position", instrumentName,
                exPositions, exOrders, exAlgoOrders, dbPositions, dbOrders, dbAlgoOrders, snapshot.getExchangeName()));
        }

        return Optional.empty();
    }

    private AnomalyDecision build(
        String category,
        String severity,
        boolean shouldHold,
        boolean shouldCancelFlow,
        String summary,
        String instrumentName,
        int exPositions,
        int exOrders,
        int exAlgoOrders,
        int dbPositions,
        int dbOrders,
        int dbAlgoOrders,
        String exchangeName
    ) {
        String details = "{" +
            "\"exchange\":\"" + escape(exchangeName) + "\"," +
            "\"instrument\":\"" + escape(instrumentName) + "\"," +
            "\"exchangeCounts\":{\"positions\":" + exPositions + ",\"orders\":" + exOrders + ",\"algoOrders\":" + exAlgoOrders + "}," +
            "\"dbCounts\":{\"positions\":" + dbPositions + ",\"orders\":" + dbOrders + ",\"algoOrders\":" + dbAlgoOrders + "}" +
            "}";
        return AnomalyDecision.builder()
            .category(category)
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
