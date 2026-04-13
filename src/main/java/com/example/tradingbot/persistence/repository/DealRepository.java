package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DealRepository extends JpaRepository<DealEntity, Long> {

    Optional<DealEntity> findByInternalId(String internalId);

    Optional<DealEntity> findTopByInstrumentIdOrderByIdDesc(Long instrumentId);

    List<DealEntity> findAllByInstrumentId(Long instrumentId);

    @Query(value = """
            select d.*
            from deals d
            where d.instrument_id = :instrumentId
            and d.status IN(:statuses)
            order by d.id desc
            """, nativeQuery = true)
    List<DealEntity> findAllByInstrumentIdAndStatuses(@Param("instrumentId") Long instrumentId,
                                                      @Param("statuses") Set<String> statuses);
}
