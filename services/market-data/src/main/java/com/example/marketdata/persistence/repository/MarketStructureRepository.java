package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketStructureEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы ряда структур рынка. */
public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {

    Boolean existsByInstrumentIdAndMarketStructureConfigIdAndWindowEndAt(
            Long instrumentId, Long marketStructureConfigId, OffsetDateTime windowEndAt);

    Optional<MarketStructureEntity> findFirstByInstrumentIdAndMarketStructureConfigIdOrderByWindowEndAtDesc(
            Long instrumentId, Long marketStructureConfigId);
}
