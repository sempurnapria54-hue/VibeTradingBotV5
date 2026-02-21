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
        ExchangePriceTicker ticker = snapshot.getTickersByInstId() == null ? null : snapshot.getTickersByInstId().get(instrument.getExternalName());
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
        changed |= setIfChanged(position.getPos(), external.getPositionSize(), position::setPos);
        changed |= setIfChanged(position.getAvgPx(), external.getAveragePrice(), position::setAvgPx);
        changed |= setIfChanged(position.getMarkPx(), external.getMarkPrice(), position::setMarkPx);
        changed |= setIfChanged(position.getLiqPx(), external.getLiquidationPrice(), position::setLiqPx);
        changed |= setIfChanged(position.getLever(), external.getLeverage(), position::setLever);
        changed |= setIfChanged(position.getMgnMode(), external.getMarginMode(), position::setMgnMode);
        changed |= setIfChanged(position.getUpl(), external.getUnrealizedProfit(), position::setUpl);
        changed |= setIfChanged(position.getSide(), external.getPositionSide(), position::setSide);

        Long externalUTime = parseLong(external.getUpdateTime());
        if (!Objects.equals(position.getUTime(), externalUTime)) {
            position.setUTime(externalUTime);
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
            changed |= setIfChanged(target.getExchangeOrderId(), externalOrder.getOrderId(), target::setExchangeOrderId);
            changed |= setIfChanged(target.getState(), externalOrder.getState(), target::setState);
            changed |= setIfChanged(target.getOrdType(), externalOrder.getOrderType(), target::setOrdType);
            changed |= setIfChanged(target.getPx(), externalOrder.getPrice(), target::setPx);
            changed |= setIfChanged(target.getSz(), externalOrder.getSize(), target::setSz);
            changed |= setIfChanged(target.getFillSz(), externalOrder.getAccumulatedFillSize(), target::setFillSz);
            changed |= setIfChanged(target.getAvgPx(), externalOrder.getAveragePrice(), target::setAvgPx);
            changed |= setIfChanged(target.getFee(), externalOrder.getFee(), target::setFee);

            Long cTime = parseLong(externalOrder.getCreateTime());
            if (!Objects.equals(target.getCTime(), cTime)) {
                target.setCTime(cTime);
                changed = true;
            }
            Long uTime = parseLong(externalOrder.getUpdateTime());
            if (!Objects.equals(target.getUTime(), uTime)) {
                target.setUTime(uTime);
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
            changed |= setIfChanged(target.getExchangeAlgoOrderId(), externalAlgoOrder.getAlgoOrderId(), target::setExchangeAlgoOrderId);
            changed |= setIfChanged(target.getState(), externalAlgoOrder.getState(), target::setState);
            changed |= setIfChanged(target.getAlgoType(), externalAlgoOrder.getOrderType(), target::setAlgoType);
            changed |= setIfChanged(target.getSz(), externalAlgoOrder.getSize(), target::setSz);
            changed |= setIfChanged(target.getTriggerPx(), externalAlgoOrder.getTriggerPrice(), target::setTriggerPx);
            changed |= setIfChanged(target.getOrdPx(), externalAlgoOrder.getOrderPrice(), target::setOrdPx);
            changed |= setIfChanged(target.getTpTriggerPx(), externalAlgoOrder.getTakeProfitTriggerPrice(), target::setTpTriggerPx);
            changed |= setIfChanged(target.getTpOrdPx(), externalAlgoOrder.getTakeProfitOrderPrice(), target::setTpOrdPx);
            changed |= setIfChanged(target.getSlTriggerPx(), externalAlgoOrder.getStopLossTriggerPrice(), target::setSlTriggerPx);
            changed |= setIfChanged(target.getSlOrdPx(), externalAlgoOrder.getStopLossOrderPrice(), target::setSlOrdPx);
            changed |= setIfChanged(target.getCallbackRatio(), externalAlgoOrder.getCallbackRatio(), target::setCallbackRatio);
            changed |= setIfChanged(target.getCallbackSpread(), externalAlgoOrder.getCallbackSpread(), target::setCallbackSpread);

            Long cTime = parseLong(externalAlgoOrder.getCreateTime());
            if (!Objects.equals(target.getCTime(), cTime)) {
                target.setCTime(cTime);
                changed = true;
            }
            Long uTime = parseLong(externalAlgoOrder.getUpdateTime());
            if (!Objects.equals(target.getUTime(), uTime)) {
                target.setUTime(uTime);
                changed = true;
            }

            if (changed) {
                algoOrderDataService.save(target);
            }
        }
    }

    private OrderEntity resolveOrder(Long exchangeId, Long instrumentId, ExchangeOrder externalOrder) {
        if (StringUtils.isNotBlank(externalOrder.getClientOrderId())) {
            return orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, externalOrder.getClientOrderId())
                .orElse(null);
        }
        if (StringUtils.isBlank(externalOrder.getOrderId())) {
            return null;
        }
        List<OrderEntity> matches = orderDataService.findAllByExchangeIdAndInstrumentIdAndExchangeOrderId(exchangeId, instrumentId, externalOrder.getOrderId());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private AlgoOrderEntity resolveAlgoOrder(Long exchangeId, Long instrumentId, ExchangeAlgoOrder externalAlgoOrder) {
        if (StringUtils.isNotBlank(externalAlgoOrder.getClientOrderId())) {
            return algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeId, instrumentId, externalAlgoOrder.getClientOrderId())
                .orElse(null);
        }
        if (StringUtils.isBlank(externalAlgoOrder.getAlgoOrderId())) {
            return null;
        }
        List<AlgoOrderEntity> matches = algoOrderDataService.findAllByExchangeIdAndInstrumentIdAndExchangeAlgoOrderId(exchangeId, instrumentId, externalAlgoOrder.getAlgoOrderId());
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
}
