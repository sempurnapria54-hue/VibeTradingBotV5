package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.MarketStructureEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {

    Boolean existsByInstrumentIdAndMarketStructureConfigIdAndWindowEndAt(
            Long instrumentId, Long marketStructureConfigId, OffsetDateTime windowEndAt);

    Optional<MarketStructureEntity> findFirstByInstrumentIdAndMarketStructureConfigIdOrderByWindowEndAtDesc(
            Long instrumentId, Long marketStructureConfigId);
}
