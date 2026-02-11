package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.ExchangeInstrumentSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExchangeSnapshot;
import com.example.tradingbot.domain.service.reconcile.model.ExternalAlgoOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalOrder;
import com.example.tradingbot.domain.service.reconcile.model.ExternalPosition;
import com.example.tradingbot.domain.service.reconcile.model.ExternalTicker;
import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.InstrumentEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
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
        ExternalTicker ticker = snapshot.getTickersByInstId() == null ? null : snapshot.getTickersByInstId().get(instrument.getName());
        if (ticker == null) {
            return;
        }

        boolean changed = false;
        changed |= setIfChanged(instrument.getLastPrice(), ticker.getLast(), instrument::setLastPrice);
        changed |= setIfChanged(instrument.getMarkPrice(), ticker.getMarkPx(), instrument::setMarkPrice);
        changed |= setIfChanged(instrument.getIndexPrice(), ticker.getIdxPx(), instrument::setIndexPrice);

        Instant parsedTs = parseInstantMillis(ticker.getTs());
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
        ExternalPosition external = currentExchangeState.getPositions().get(0);
        List<PositionEntity> positions = positionDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId).stream()
            .filter(entity -> !"CLOSED".equalsIgnoreCase(entity.getStatus()))
            .toList();
        if (positions.isEmpty()) {
            return;
        }
        PositionEntity position = positions.get(0);
        boolean changed = false;
        changed |= setIfChanged(position.getPos(), external.getPos(), position::setPos);
        changed |= setIfChanged(position.getAvgPx(), external.getAvgPx(), position::setAvgPx);
        changed |= setIfChanged(position.getMarkPx(), external.getMarkPx(), position::setMarkPx);
        changed |= setIfChanged(position.getLiqPx(), external.getLiqPx(), position::setLiqPx);
        changed |= setIfChanged(position.getLever(), external.getLever(), position::setLever);
        changed |= setIfChanged(position.getMgnMode(), external.getMgnMode(), position::setMgnMode);
        changed |= setIfChanged(position.getUpl(), external.getUpl(), position::setUpl);
        changed |= setIfChanged(position.getSide(), external.getSide(), position::setSide);

        Long externalUTime = parseLong(external.getUTime());
        if (!Objects.equals(position.getUTime(), externalUTime)) {
            position.setUTime(externalUTime);
            changed = true;
        }

        if (changed) {
            positionDataService.save(position);
        }
    }

    private void transferOrderFields(Long exchangeId, Long instrumentId, List<ExternalOrder> externalOrders) {
        for (ExternalOrder externalOrder : externalOrders) {
            OrderEntity target = resolveOrder(exchangeId, instrumentId, externalOrder);
            if (target == null) {
                continue;
            }
            boolean changed = false;
            changed |= setIfChanged(target.getExchangeOrderId(), externalOrder.getOrdId(), target::setExchangeOrderId);
            changed |= setIfChanged(target.getState(), externalOrder.getState(), target::setState);
            changed |= setIfChanged(target.getOrdType(), externalOrder.getOrdType(), target::setOrdType);
            changed |= setIfChanged(target.getPx(), externalOrder.getPx(), target::setPx);
            changed |= setIfChanged(target.getSz(), externalOrder.getSz(), target::setSz);
            changed |= setIfChanged(target.getFillSz(), externalOrder.getFillSz(), target::setFillSz);
            changed |= setIfChanged(target.getAvgPx(), externalOrder.getAvgPx(), target::setAvgPx);
            changed |= setIfChanged(target.getFee(), externalOrder.getFee(), target::setFee);

            Long cTime = parseLong(externalOrder.getCTime());
            if (!Objects.equals(target.getCTime(), cTime)) {
                target.setCTime(cTime);
                changed = true;
            }
            Long uTime = parseLong(externalOrder.getUTime());
            if (!Objects.equals(target.getUTime(), uTime)) {
                target.setUTime(uTime);
                changed = true;
            }

            if (changed) {
                orderDataService.save(target);
            }
        }
    }

    private void transferAlgoOrderFields(Long exchangeId, Long instrumentId, List<ExternalAlgoOrder> externalAlgoOrders) {
        for (ExternalAlgoOrder externalAlgoOrder : externalAlgoOrders) {
            AlgoOrderEntity target = resolveAlgoOrder(exchangeId, instrumentId, externalAlgoOrder);
            if (target == null) {
                continue;
            }

            boolean changed = false;
            changed |= setIfChanged(target.getExchangeAlgoOrderId(), externalAlgoOrder.getAlgoId(), target::setExchangeAlgoOrderId);
            changed |= setIfChanged(target.getState(), externalAlgoOrder.getState(), target::setState);
            changed |= setIfChanged(target.getAlgoType(), externalAlgoOrder.getAlgoType(), target::setAlgoType);
            changed |= setIfChanged(target.getSz(), externalAlgoOrder.getSz(), target::setSz);
            changed |= setIfChanged(target.getTriggerPx(), externalAlgoOrder.getTriggerPx(), target::setTriggerPx);
            changed |= setIfChanged(target.getOrdPx(), externalAlgoOrder.getOrdPx(), target::setOrdPx);
            changed |= setIfChanged(target.getTpTriggerPx(), externalAlgoOrder.getTpTriggerPx(), target::setTpTriggerPx);
            changed |= setIfChanged(target.getTpOrdPx(), externalAlgoOrder.getTpOrdPx(), target::setTpOrdPx);
            changed |= setIfChanged(target.getSlTriggerPx(), externalAlgoOrder.getSlTriggerPx(), target::setSlTriggerPx);
            changed |= setIfChanged(target.getSlOrdPx(), externalAlgoOrder.getSlOrdPx(), target::setSlOrdPx);
            changed |= setIfChanged(target.getCallbackRatio(), externalAlgoOrder.getCallbackRatio(), target::setCallbackRatio);
            changed |= setIfChanged(target.getCallbackSpread(), externalAlgoOrder.getCallbackSpread(), target::setCallbackSpread);

            Long cTime = parseLong(externalAlgoOrder.getCTime());
            if (!Objects.equals(target.getCTime(), cTime)) {
                target.setCTime(cTime);
                changed = true;
            }
            Long uTime = parseLong(externalAlgoOrder.getUTime());
            if (!Objects.equals(target.getUTime(), uTime)) {
                target.setUTime(uTime);
                changed = true;
            }

            if (changed) {
                algoOrderDataService.save(target);
            }
        }
    }

    private OrderEntity resolveOrder(Long exchangeId, Long instrumentId, ExternalOrder externalOrder) {
        if (StringUtils.isNotBlank(externalOrder.getClOrdId())) {
            return orderDataService.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, externalOrder.getClOrdId())
                .orElse(null);
        }
        if (StringUtils.isBlank(externalOrder.getOrdId())) {
            return null;
        }
        List<OrderEntity> matches = orderDataService.findAllByExchangeIdAndInstrumentIdAndExchangeOrderId(
            exchangeId,
            instrumentId,
            externalOrder.getOrdId()
        );
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private AlgoOrderEntity resolveAlgoOrder(Long exchangeId, Long instrumentId, ExternalAlgoOrder externalAlgoOrder) {
        if (StringUtils.isNotBlank(externalAlgoOrder.getAlgoClOrdId())) {
            return algoOrderDataService.findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(exchangeId, instrumentId, externalAlgoOrder.getAlgoClOrdId())
                .orElse(null);
        }
        if (StringUtils.isBlank(externalAlgoOrder.getAlgoId())) {
            return null;
        }
        List<AlgoOrderEntity> matches = algoOrderDataService.findAllByExchangeIdAndInstrumentIdAndExchangeAlgoOrderId(
            exchangeId,
            instrumentId,
            externalAlgoOrder.getAlgoId()
        );
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
