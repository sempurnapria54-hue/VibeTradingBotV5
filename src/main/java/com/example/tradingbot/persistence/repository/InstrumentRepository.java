package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.domain.model.entity.InstrumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByExchangeIdAndName(Long exchangeId, String name);

    Optional<InstrumentEntity> findByExchangeIdAndInstId(Long exchangeId, String instId);

    Optional<InstrumentEntity> findByExchangeIdAndInternalId(Long exchangeId, String internalId);

    List<InstrumentEntity> findAllByExchangeId(Long exchangeId);
}
