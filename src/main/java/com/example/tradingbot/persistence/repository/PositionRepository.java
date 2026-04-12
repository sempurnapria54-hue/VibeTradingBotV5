package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    Optional<PositionEntity> findByDealId(Long dealId);

    Optional<PositionEntity> findByExternalId(String externalId);

    @Query(value = """
            select p.*
            from positions p
            join deals d on d.id = p.deal_id
            where d.instrument_id = :instrumentId
            order by p.id desc
            """, nativeQuery = true)
    List<PositionEntity> findAllByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query(value = """
            select p.*
            from positions p
            join deals d on d.id = p.deal_id
            where d.instrument_id = :instrumentId
            and p.status IN(:statuses)
            order by p.id desc
            """, nativeQuery = true)
    List<PositionEntity> findAllByInstrumentIdAndStatuses(@Param("instrumentId") Long instrumentId,
                                                          @Param("status") Set<String> statuses);
}
