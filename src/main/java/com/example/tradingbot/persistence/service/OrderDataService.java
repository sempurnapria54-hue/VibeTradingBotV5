package com.example.tradingbot.persistence.service;

import com.example.tradingbot.persistence.model.OrderEntity;
import com.example.tradingbot.persistence.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.tradingbot.util.Constant.ErrorCode.ORDER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class OrderDataService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderEntity save(OrderEntity orderEntity) {
        return orderRepository.save(orderEntity);
    }

    @Transactional
    public List<OrderEntity> saveAll(List<OrderEntity> orderEntities) {
        return orderRepository.saveAll(orderEntities);
    }

    public List<OrderEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId) {
        return orderRepository.findAllByExchangeIdAndInstrumentId(exchangeId, instrumentId);
    }


    public List<OrderEntity> findAllByExchangeIdAndInstrumentIdAndExchangeOrderId(
            Long exchangeId,
            Long instrumentId,
            String exchangeOrderId
    ) {
        return orderRepository.findAllByExchangeIdAndInstrumentIdAndExchangeOrderId(exchangeId, instrumentId, exchangeOrderId);
    }

    public Optional<OrderEntity> findByExchangeIdAndInstrumentIdAndClientOrderId(
            Long exchangeId,
            Long instrumentId,
            String clientOrderId
    ) {
        return orderRepository.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, clientOrderId);
    }

    public OrderEntity findRequiredByExchangeIdAndInstrumentIdAndClientOrderId(Long exchangeId,
                                                                               Long instrumentId,
                                                                               String clientOrderId) {
        return orderRepository.findByExchangeIdAndInstrumentIdAndClientOrderId(exchangeId, instrumentId, clientOrderId)
                .orElseThrow(() -> new RuntimeException(ORDER_NOT_FOUND));
    }
}
