package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.MarketStructureEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {

    /** Производный checkpoint: «докуда посчитано» = max(window_end_at) для (instrument, setting). */
    @Query("select max(s.windowEndAt) from MarketStructureEntity s "
            + "where s.instrumentId = :instrumentId and s.strategyMarketStructureSettingId = :settingId")
    OffsetDateTime findMaxWindowEndAt(@Param("instrumentId") Long instrumentId, @Param("settingId") Long settingId);

    Boolean existsByInstrumentIdAndStrategyMarketStructureSettingIdAndWindowEndAt(
            Long instrumentId, Long strategyMarketStructureSettingId, OffsetDateTime windowEndAt);

    Optional<MarketStructureEntity> findFirstByInstrumentIdAndStrategyMarketStructureSettingIdOrderByWindowEndAtDesc(
            Long instrumentId, Long strategyMarketStructureSettingId);
}
