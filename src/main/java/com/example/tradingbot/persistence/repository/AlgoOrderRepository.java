package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.AlgoOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlgoOrderRepository extends JpaRepository<AlgoOrderEntity, Long> {

    List<AlgoOrderEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId);

    Optional<AlgoOrderEntity> findByExchangeIdAndInstrumentIdAndClientAlgoOrderId(
        Long exchangeId,
        Long instrumentId,
        String clientAlgoOrderId
    );
}
