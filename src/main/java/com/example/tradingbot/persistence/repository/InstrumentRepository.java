package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.domain.model.core.instrument.Instrument;
import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);

    Optional<InstrumentEntity> findByExchangeIdAndExternalId(Long exchangeId, String externalId);

    List<InstrumentEntity> findByStatus(Instrument.Status status);
}
