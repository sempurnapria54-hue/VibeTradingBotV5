package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.domain.model.entity.OrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId);

    Optional<OrderEntity> findByExchangeIdAndInstrumentIdAndClientOrderId(
        Long exchangeId,
        Long instrumentId,
        String clientOrderId
    );
    List<OrderEntity> findAllByExchangeIdAndInstrumentIdAndExchangeOrderId(
        Long exchangeId,
        Long instrumentId,
        String exchangeOrderId
    );
}
