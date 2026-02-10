package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.CandleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    boolean existsByInstrumentIdAndTimeframe(Long instrumentId, String timeframe);

    @Query("""
        select min(c.timestamp)
        from CandleEntity c
        where c.instrumentId = :instrumentId and c.timeframe = :timeframe
        """)
    Optional<Long> findOldestTimestampByInstrumentIdAndTimeframe(
        @Param("instrumentId") Long instrumentId,
        @Param("timeframe") String timeframe
    );

    @Query("""
        select max(c.timestamp)
        from CandleEntity c
        where c.instrumentId = :instrumentId and c.timeframe = :timeframe
        """)
    Optional<Long> findNewestTimestampByInstrumentIdAndTimeframe(
        @Param("instrumentId") Long instrumentId,
        @Param("timeframe") String timeframe
    );
}
