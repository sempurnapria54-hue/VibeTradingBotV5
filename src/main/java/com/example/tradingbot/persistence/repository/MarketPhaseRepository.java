package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.MarketPhaseEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketPhaseRepository extends JpaRepository<MarketPhaseEntity, Long> {

    /** Производный checkpoint: «докуда посчитано» = max(candle_timestamp) для (instrument, setting). */
    @Query("select max(p.candleTimestamp) from MarketPhaseEntity p "
            + "where p.instrumentId = :instrumentId and p.strategyMarketPhaseSettingId = :settingId")
    OffsetDateTime findMaxCandleTimestamp(@Param("instrumentId") Long instrumentId,
                                          @Param("settingId") Long strategyMarketPhaseSettingId);

    Boolean existsByInstrumentIdAndStrategyMarketPhaseSettingIdAndCandleTimestamp(
            Long instrumentId, Long strategyMarketPhaseSettingId, OffsetDateTime candleTimestamp);

    Optional<MarketPhaseEntity> findFirstByInstrumentIdAndStrategyMarketPhaseSettingIdOrderByCandleTimestampDesc(
            Long instrumentId, Long strategyMarketPhaseSettingId);
}
