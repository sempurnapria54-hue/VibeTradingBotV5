package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.CandleGroupEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы единиц сбора свечей. */
public interface CandleGroupRepository extends JpaRepository<CandleGroupEntity, Long> {

    Optional<CandleGroupEntity> findByInstrumentIdAndTimeframe(Long instrumentId, String timeframe);

    List<CandleGroupEntity> findByInstrumentId(Long instrumentId);

    List<CandleGroupEntity> findByStatusIn(Collection<String> statuses);

    /**
     * Группы заданного таймфрейма, готовые отдать историю расчёту.
     * Популяция производных: идентичность вычисления заказана глобально, а
     * инструменты приносят те группы, что уже собраны
     * (docs/architecture/market-data-collection.md).
     */
    List<CandleGroupEntity> findByTimeframeAndStatusIn(String timeframe, Collection<String> statuses);

    /** Идентификаторы инструментов, у которых есть хотя бы одна группа не в ACTIVE. */
    @Query("select distinct g.instrumentId from CandleGroupEntity g where g.status <> :activeStatus")
    List<Long> findInstrumentIdsWithUnreadyGroups(@Param("activeStatus") String activeStatus);
}
