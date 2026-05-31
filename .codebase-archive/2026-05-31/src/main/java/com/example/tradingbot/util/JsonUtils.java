package com.example.tradingbot.util;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.domain.model.core.instrument.Instrument.Status;
import com.example.tradingbot.domain.model.kill_switch.StateSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JsonUtils {

    private final ObjectMapper objectMapper;

    public String toJson(Object source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize reconcile snapshot", exception);
        }
    }

    public String buildInternalSnapshot(StateSnapshot snapshot, Instrument instrument, Status instrumentStatusBefore) {
        LinkedHashMap<String, Object> payload = buildInternalPayload(snapshot, instrument);
        payload.put("instrumentStatusBefore", instrumentStatusBefore);
        return toJson(payload);
    }

    public String buildInternalSnapshot(StateSnapshot snapshot, Instrument instrument) {
        return toJson(buildInternalPayload(snapshot, instrument));
    }

    public String buildExternalSnapshot(StateSnapshot snapshot, Instrument instrument) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instrumentExternalId", instrument.getExternalId());
        payload.put("active", buildExternalActivePayload(snapshot));
        payload.put("relatedInactive", buildExternalRelatedInactivePayload(snapshot));
        return toJson(payload);
    }

    private LinkedHashMap<String, Object> buildInternalPayload(StateSnapshot snapshot, Instrument instrument) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("instrument", instrument);
        payload.put("active", buildInternalActivePayload(snapshot));
        payload.put("relatedInactive", buildInternalRelatedInactivePayload(snapshot));
        return payload;
    }

    private Map<String, Object> buildInternalActivePayload(StateSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("positions", snapshot.getInternalPositions());
        payload.put("orders", snapshot.getInternalOrders());
        payload.put("algoOrders", snapshot.getInternalAlgoOrders());
        payload.put("deals", snapshot.getInternalDeals());
        return payload;
    }

    private Map<String, Object> buildInternalRelatedInactivePayload(StateSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("positions", snapshot.getInternalRelatedInactivePositions());
        payload.put("orders", snapshot.getInternalRelatedInactiveOrders());
        payload.put("algoOrders", snapshot.getInternalRelatedInactiveAlgoOrders());
        payload.put("deals", snapshot.getInternalRelatedInactiveDeals());
        return payload;
    }

    private Map<String, Object> buildExternalActivePayload(StateSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("positions", snapshot.getExternalPositions());
        payload.put("orders", snapshot.getExternalOrders());
        payload.put("algoOrders", snapshot.getExternalAlgoOrders());
        return payload;
    }

    private Map<String, Object> buildExternalRelatedInactivePayload(StateSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orders", snapshot.getExternalRelatedInactiveOrders());
        payload.put("algoOrders", snapshot.getExternalRelatedInactiveAlgoOrders());
        return payload;
    }
}
