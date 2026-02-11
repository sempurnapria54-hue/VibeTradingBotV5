package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.PositionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    List<PositionEntity> findAllByExchangeIdAndInstrumentId(Long exchangeId, Long instrumentId);
}
