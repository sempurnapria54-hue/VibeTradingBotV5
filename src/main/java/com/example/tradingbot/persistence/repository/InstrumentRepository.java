package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.InstrumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByExchangeIdAndName(Long exchangeId, String name);

    List<InstrumentEntity> findAllByExchangeId(Long exchangeId);
}
