package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.MarketStructureEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {

    Boolean existsByInstrumentIdAndStrategyMarketStructureSettingIdAndWindowEndAt(
            Long instrumentId, Long strategyMarketStructureSettingId, OffsetDateTime windowEndAt);

    Optional<MarketStructureEntity> findFirstByInstrumentIdAndStrategyMarketStructureSettingIdOrderByWindowEndAtDesc(
            Long instrumentId, Long strategyMarketStructureSettingId);
}
