package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.MarketStructureEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {

    /** Производный checkpoint: «докуда посчитано» = max(window_end_at) для (instrument, config). */
    @Query("select max(s.windowEndAt) from MarketStructureEntity s "
            + "where s.instrumentId = :instrumentId and s.configId = :configId")
    OffsetDateTime findMaxWindowEndAt(@Param("instrumentId") Long instrumentId, @Param("configId") Long configId);

    Boolean existsByInstrumentIdAndConfigIdAndWindowEndAt(Long instrumentId, Long configId, OffsetDateTime windowEndAt);

    Optional<MarketStructureEntity> findFirstByInstrumentIdAndConfigIdOrderByWindowEndAtDesc(
            Long instrumentId, Long configId);
}
