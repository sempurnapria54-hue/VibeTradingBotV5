package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.IndicatorValueEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IndicatorValueRepository extends JpaRepository<IndicatorValueEntity, Long> {

    /** Уже сохранённые candle_timestamp в окне (дедуп при идемпотентном пересчёте). */
    @Query("select v.candleTimestamp from IndicatorValueEntity v "
            + "where v.instrumentId = :instrumentId and v.strategyIndicatorSettingId = :settingId "
            + "and v.candleTimestamp between :from and :to")
    List<OffsetDateTime> findCandleTimestampsInRange(@Param("instrumentId") Long instrumentId,
                                                     @Param("settingId") Long settingId,
                                                     @Param("from") OffsetDateTime from,
                                                     @Param("to") OffsetDateTime to);

    Optional<IndicatorValueEntity> findFirstByInstrumentIdAndStrategyIndicatorSettingIdOrderByCandleTimestampDesc(
            Long instrumentId, Long strategyIndicatorSettingId);

    /** Два последних значения (по убыванию candle_timestamp) — для slope/crossover. */
    List<IndicatorValueEntity> findFirst2ByInstrumentIdAndStrategyIndicatorSettingIdOrderByCandleTimestampDesc(
            Long instrumentId, Long strategyIndicatorSettingId);
}
