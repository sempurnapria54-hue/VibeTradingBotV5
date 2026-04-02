package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long>,
        JpaSpecificationExecutor<InstrumentEntity> {

    Optional<InstrumentEntity> findByExchangeIdAndName(Long exchangeId, String name);

    Optional<InstrumentEntity> findByExchangeIdAndInstId(Long exchangeId, String instId);

    Optional<InstrumentEntity> findByExchangeIdAndInternalId(Long exchangeId, String internalId);

    Optional<InstrumentEntity> findByInternalId(String internalId);

    @Query(value = """
            select i.*
            from instruments i
            join deals d on d.instrument_id = i.id
            where d.id = :dealId
            """, nativeQuery = true)
    Optional<InstrumentEntity> findByDealId(@Param("dealId") Long dealId);
}
