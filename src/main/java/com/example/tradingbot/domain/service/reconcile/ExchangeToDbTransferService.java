package com.example.tradingbot.domain.service.reconcile;

import com.example.tradingbot.domain.service.reconcile.model.InstrumentBucket;
import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.model.PositionEntity;
import com.example.tradingbot.persistence.service.AlgoOrderDataService;
import com.example.tradingbot.persistence.service.OrderDataService;
import com.example.tradingbot.persistence.service.PositionDataService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExchangeToDbTransferService {

    private final PositionDataService positionDataService;
    private final OrderDataService orderDataService;
    private final AlgoOrderDataService algoOrderDataService;

    @Transactional
    public void transfer(Long exchangeId, Long instrumentId, InstrumentBucket bucket) {
        List<OrderEntity> dbOrders = orderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
        List<AlgoOrderEntity> dbAlgoOrders = algoOrderDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
        List<PositionEntity> dbPositions = positionDataService.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);

        bucket.getOrders().forEach(externalOrder -> {
            dbOrders.stream()
                .filter(order -> Objects.equals(order.getClientOrderId(), resolveOrderClientId(externalOrder.getClOrdId(), externalOrder.getOrdId())))
                .findFirst()
                .ifPresent(order -> {
                    if (BooleanUtils.isFalse(Objects.equals(order.getExchangeOrderId(), externalOrder.getOrdId()))) {
                        order.setExchangeOrderId(externalOrder.getOrdId());
                        orderDataService.save(order);
                    }
                });
        });

        bucket.getAlgoOrders().forEach(externalAlgoOrder -> {
            dbAlgoOrders.stream()
                .filter(algoOrder -> Objects.equals(algoOrder.getClientAlgoOrderId(), resolveAlgoClientId(externalAlgoOrder.getAlgoClOrdId(), externalAlgoOrder.getAlgoId())))
                .findFirst()
                .ifPresent(algoOrder -> {
                    if (BooleanUtils.isFalse(Objects.equals(algoOrder.getExchangeAlgoOrderId(), externalAlgoOrder.getAlgoId()))) {
                        algoOrder.setExchangeAlgoOrderId(externalAlgoOrder.getAlgoId());
                        algoOrderDataService.save(algoOrder);
                    }
                });
        });

        if (!bucket.getPositions().isEmpty()) {
            String side = bucket.getPositions().get(0).getSide();
            if (StringUtils.isNotBlank(side)) {
                dbPositions.stream().findFirst().ifPresent(position -> {
                    if (BooleanUtils.isFalse(Objects.equals(position.getSide(), side))) {
                        position.setSide(side);
                        positionDataService.save(position);
                    }
                });
            }
        }
    }

    private String resolveOrderClientId(String clOrdId, String ordId) {
        if (StringUtils.isNotBlank(clOrdId)) {
            return clOrdId;
        }
        if (StringUtils.isNotBlank(ordId)) {
            return "unknown-ord-" + ordId;
        }
        return "unknown-ord-no-id";
    }

    private String resolveAlgoClientId(String algoClOrdId, String algoId) {
        if (StringUtils.isNotBlank(algoClOrdId)) {
            return algoClOrdId;
        }
        if (StringUtils.isNotBlank(algoId)) {
            return "unknown-algo-" + algoId;
        }
        return "unknown-algo-no-id";
    }
}
