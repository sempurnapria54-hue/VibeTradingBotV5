package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.model.entity.AlgoOrderEntity;
import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import com.example.tradingbot.domain.model.entity.OrderEntity;
import com.example.tradingbot.domain.model.entity.PositionEntity;
import com.example.tradingbot.domain.model.exchange.ExchangeAlgoOrder;
import com.example.tradingbot.domain.model.exchange.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.model.exchange.ExchangeOrder;
import com.example.tradingbot.domain.model.exchange.ExchangePosition;
import com.example.tradingbot.domain.model.exchange.ExchangePriceTicker;
import com.example.tradingbot.domain.model.exchange.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.InstrumentDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeToDbExtendedTransferService {

    private final InstrumentDataService instrumentDataService;
    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public void transfer(
        Long exchangeId,
        InstrumentBucket bucket,
        ExchangeInstrumentSnapshot currentExchangeState,
        ExchangeSnapshot exchangeBeforeOrCurrent
    ) {
        InstrumentEntity instrument = bucket.getDbState() == null ? null : bucket.getDbState().getInstrument();
        if (instrument == null) {
            return;
        }

        transferInstrumentPrices(instrument, exchangeBeforeOrCurrent);
        transferPositionFields(exchangeId, instrument.getId(), currentExchangeState);
        transferOrderFields(exchangeId, instrument.getId(), currentExchangeState.getOrders());
        transferAlgoOrderFields(exchangeId, instrument.getId(), currentExchangeState.getAlgoOrders());
    }

    private void transferInstrumentPrices(InstrumentEntity instrument, ExchangeSnapshot snapshot) {
        ExchangePriceTicker ticker = snapshot.getTickersByInstId() == null ? null : snapshot.getTickersByInstId().get(instrument.getExternalId());
        if (ticker == null) {
            return;
        }

        boolean changed = false;
        changed |= setIfChanged(instrument.getLastPrice(), ticker.getLastPrice(), instrument::setLastPrice);
        changed |= setIfChanged(instrument.getMarkPrice(), ticker.getMarkPrice(), instrument::setMarkPrice);
        changed |= setIfChanged(instrument.getIndexPrice(), ticker.getIndexPrice(), instrument::setIndexPrice);

        Instant parsedTs = parseInstantMillis(ticker.getTimestamp());
        if (!Objects.equals(instrument.getPriceUpdatedAt(), parsedTs)) {
            instrument.setPriceUpdatedAt(parsedTs);
            changed = true;
        }

        if (changed) {
            instrumentDataService.save(instrument);
        }
    }

    private void transferPositionFields(Long exchangeId, Long instrumentId, ExchangeInstrumentSnapshot currentExchangeState) {
        if (currentExchangeState.getPositions().isEmpty()) {
            return;
        }
        ExchangePosition external = currentExchangeState.getPositions().get(0);
        List<PositionEntity> positions = positionDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId).stream()
            .filter(entity -> !"CLOSED".equalsIgnoreCase(entity.getStatus()))
            .toList();
        if (positions.isEmpty()) {
            return;
        }
        PositionEntity position = positions.get(0);
        boolean changed = false;
        changed |= setIfChanged(position.getPositionSize(), external.getPositionSize(), position::setPositionSize);
        changed |= setIfChanged(position.getAveragePrice(), external.getAveragePrice(), position::setAveragePrice);
        changed |= setIfChanged(position.getMarkPrice(), external.getMarkPrice(), position::setMarkPrice);
        changed |= setIfChanged(position.getLiquidationPrice(), external.getLiquidationPrice(), position::setLiquidationPrice);
        changed |= setIfChanged(position.getLeverage(), external.getLeverage(), position::setLeverage);
        changed |= setIfChanged(position.getMarginMode(), external.getMarginMode(), position::setMarginMode);
        changed |= setIfChanged(position.getUnrealizedProfit(), external.getUnrealizedProfit(), position::setUnrealizedProfit);
        changed |= setIfChanged(position.getPositionSide(), external.getPositionSide(), position::setPositionSide);

        OffsetDateTime externalUTime = parseOffsetDateTimeMillis(external.getUpdateTime());
        if (!Objects.equals(position.getExchangeModifiedAt(), externalUTime)) {
            position.setExchangeModifiedAt(externalUTime);
            changed = true;
        }

        if (changed) {
            positionDataService.save(position);
        }
    }

    private void transferOrderFields(Long exchangeId, Long instrumentId, List<ExchangeOrder> externalOrders) {
        for (ExchangeOrder externalOrder : externalOrders) {
            OrderEntity target = resolveOrder(exchangeId, instrumentId, externalOrder);
            if (target == null) {
                continue;
            }
            boolean changed = false;
            changed |= setIfChanged(target.getExternalId(), externalOrder.getExternalId(), target::setExternalId);
            changed |= setIfChanged(target.getExternalStatus(), externalOrder.getStatus(), target::setExternalStatus);
            changed |= setIfChanged(target.getType(), externalOrder.getType(), target::setType);
            changed |= setIfChanged(target.getPrice(), externalOrder.getPrice(), target::setPrice);
            changed |= setIfChanged(target.getSize(), externalOrder.getSize(), target::setSize);
            changed |= setIfChanged(target.getAccumulatedFillSize(), externalOrder.getAccumulatedFillSize(), target::setAccumulatedFillSize);
            changed |= setIfChanged(target.getAveragePrice(), externalOrder.getAveragePrice(), target::setAveragePrice);
            changed |= setIfChanged(target.getFee(), externalOrder.getFee(), target::setFee);

            OffsetDateTime cTime = parseOffsetDateTimeMillis(externalOrder.getCreateTime());
            if (!Objects.equals(target.getExchangeCreatedAt(), cTime)) {
                target.setExchangeCreatedAt(cTime);
                changed = true;
            }
            OffsetDateTime uTime = parseOffsetDateTimeMillis(externalOrder.getUpdateTime());
            if (!Objects.equals(target.getExchangeModifiedAt(), uTime)) {
                target.setExchangeModifiedAt(uTime);
                changed = true;
            }

            if (changed) {
                orderDataService.save(target);
            }
        }
    }

    private void transferAlgoOrderFields(Long exchangeId, Long instrumentId, List<ExchangeAlgoOrder> externalAlgoOrders) {
        for (ExchangeAlgoOrder externalAlgoOrder : externalAlgoOrders) {
            AlgoOrderEntity target = resolveAlgoOrder(exchangeId, instrumentId, externalAlgoOrder);
            if (target == null) {
                continue;
            }

            boolean changed = false;
            changed |= setIfChanged(target.getExternalId(), externalAlgoOrder.getExternalId(), target::setExternalId);
            changed |= setIfChanged(target.getExternalStatus(), externalAlgoOrder.getStatus(), target::setExternalStatus);
            changed |= setIfChanged(target.getType(), externalAlgoOrder.getType(), target::setType);
            changed |= setIfChanged(target.getSize(), externalAlgoOrder.getSize(), target::setSize);
            changed |= setIfChanged(target.getTriggerPrice(), externalAlgoOrder.getTriggerPrice(), target::setTriggerPrice);
            changed |= setIfChanged(target.getOrderPrice(), externalAlgoOrder.getOrderPrice(), target::setOrderPrice);
            changed |= setIfChanged(target.getTakeProfitTriggerPrice(), externalAlgoOrder.getTakeProfitTriggerPrice(), target::setTakeProfitTriggerPrice);
            changed |= setIfChanged(target.getTakeProfitOrderPrice(), externalAlgoOrder.getTakeProfitOrderPrice(), target::setTakeProfitOrderPrice);
            changed |= setIfChanged(target.getStopLossTriggerPrice(), externalAlgoOrder.getStopLossTriggerPrice(), target::setStopLossTriggerPrice);
            changed |= setIfChanged(target.getStopLossOrderPrice(), externalAlgoOrder.getStopLossOrderPrice(), target::setStopLossOrderPrice);
            changed |= setIfChanged(target.getCallbackRatio(), externalAlgoOrder.getCallbackRatio(), target::setCallbackRatio);
            changed |= setIfChanged(target.getCallbackStep(), externalAlgoOrder.getCallbackStep(), target::setCallbackStep);

            OffsetDateTime cTime = parseOffsetDateTimeMillis(externalAlgoOrder.getCreateTime());
            if (!Objects.equals(target.getExchangeCreatedAt(), cTime)) {
                target.setExchangeCreatedAt(cTime);
                changed = true;
            }
            OffsetDateTime uTime = parseOffsetDateTimeMillis(externalAlgoOrder.getUpdateTime());
            if (!Objects.equals(target.getExchangeModifiedAt(), uTime)) {
                target.setExchangeModifiedAt(uTime);
                changed = true;
            }

            if (changed) {
                algoOrderDataService.save(target);
            }
        }
    }

    private OrderEntity resolveOrder(Long exchangeId, Long instrumentId, ExchangeOrder externalOrder) {
        if (StringUtils.isNotBlank(externalOrder.getInternalId())) {
            return orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, externalOrder.getInternalId())
                .orElse(null);
        }
        if (StringUtils.isBlank(externalOrder.getExternalId())) {
            return null;
        }
        List<OrderEntity> matches = orderDataService.findAllByExchangeIdAndInstrumentIdAndExchangeOrderId(exchangeId, instrumentId, externalOrder.getExternalId());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private AlgoOrderEntity resolveAlgoOrder(Long exchangeId, Long instrumentId, ExchangeAlgoOrder externalAlgoOrder) {
        if (StringUtils.isNotBlank(externalAlgoOrder.getInternalOrderId())) {
            return algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeId, instrumentId, externalAlgoOrder.getInternalOrderId())
                .orElse(null);
        }
        if (StringUtils.isBlank(externalAlgoOrder.getExternalId())) {
            return null;
        }
        List<AlgoOrderEntity> matches = algoOrderDataService.findAllByExchangeIdAndInstrumentIdAndExchangeAlgoOrderId(exchangeId, instrumentId, externalAlgoOrder.getExternalId());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private boolean setIfChanged(String previous, String next, java.util.function.Consumer<String> setter) {
        if (Objects.equals(previous, next)) {
            return false;
        }
        setter.accept(next);
        return true;
    }

    private Long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Instant parseInstantMillis(String value) {
        Long epochMillis = parseLong(value);
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    private OffsetDateTime parseOffsetDateTimeMillis(String value) {
        Long epochMillis = parseLong(value);
        return epochMillis == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }
}
