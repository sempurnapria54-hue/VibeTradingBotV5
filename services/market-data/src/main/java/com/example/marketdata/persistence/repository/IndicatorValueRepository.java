package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.IndicatorValueEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы ряда значений индикаторов. */
public interface IndicatorValueRepository extends JpaRepository<IndicatorValueEntity, Long> {

    @Query("select v.candleTimestamp from IndicatorValueEntity v where v.instrumentId = :instrumentId "
            + "and v.indicatorConfigId = :configId and v.candleTimestamp between :from and :to")
    List<OffsetDateTime> findCandleTimestampsInRange(@Param("instrumentId") Long instrumentId,
                                                     @Param("configId") Long configId,
                                                     @Param("from") OffsetDateTime from,
                                                     @Param("to") OffsetDateTime to);

    Optional<IndicatorValueEntity> findFirstByInstrumentIdAndIndicatorConfigIdOrderByCandleTimestampDesc(
            Long instrumentId, Long indicatorConfigId);

    List<IndicatorValueEntity> findFirst2ByInstrumentIdAndIndicatorConfigIdOrderByCandleTimestampDesc(
            Long instrumentId, Long indicatorConfigId);
}
