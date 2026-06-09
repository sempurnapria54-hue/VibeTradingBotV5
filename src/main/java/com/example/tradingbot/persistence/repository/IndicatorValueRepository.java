package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.IndicatorValueEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IndicatorValueRepository extends JpaRepository<IndicatorValueEntity, Long> {

    /** Производный checkpoint: «докуда посчитано» = max(candle_timestamp) для (instrument, config). */
    @Query("select max(v.candleTimestamp) from IndicatorValueEntity v "
            + "where v.instrumentId = :instrumentId and v.configId = :configId")
    OffsetDateTime findMaxCandleTimestamp(@Param("instrumentId") Long instrumentId,
                                          @Param("configId") Long configId);

    /** Уже сохранённые candle_timestamp в окне (дедуп при идемпотентном пересчёте). */
    @Query("select v.candleTimestamp from IndicatorValueEntity v "
            + "where v.instrumentId = :instrumentId and v.configId = :configId "
            + "and v.candleTimestamp between :from and :to")
    List<OffsetDateTime> findCandleTimestampsInRange(@Param("instrumentId") Long instrumentId,
                                                     @Param("configId") Long configId,
                                                     @Param("from") OffsetDateTime from,
                                                     @Param("to") OffsetDateTime to);

    Optional<IndicatorValueEntity> findFirstByInstrumentIdAndConfigIdOrderByCandleTimestampDesc(
            Long instrumentId, Long configId);

    /** Два последних значения (по убыванию candle_timestamp) — для slope/crossover. */
    List<IndicatorValueEntity> findFirst2ByInstrumentIdAndConfigIdOrderByCandleTimestampDesc(
            Long instrumentId, Long configId);
}
