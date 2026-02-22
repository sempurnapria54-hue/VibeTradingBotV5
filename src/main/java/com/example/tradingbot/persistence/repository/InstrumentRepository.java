package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.InstrumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByExchangeIdAndName(Long exchangeId, String name);

    Optional<InstrumentEntity> findByExchangeIdAndInstId(Long exchangeId, String instId);

    Optional<InstrumentEntity> findByExchangeIdAndInternalId(Long exchangeId, String internalId);

    @Query("""
        select i.externalId
        from InstrumentEntity i
        where i.exchangeId = :exchangeId
          and i.internalId = :internalId
        """)
    Optional<String> findExternalIdByExchangeIdAndInternalId(@Param("exchangeId") Long exchangeId, @Param("internalId") String internalId);

    @Query("""
        select i.id
        from InstrumentEntity i
        where i.internalId = :instrumentInternalId
          and i.exchangeId = (
              select e.id
              from ExchangeEntity e
              where e.internalId = :exchangeInternalId
          )
        """)
    Optional<Long> findIdByExchangeInternalIdAndInstrumentInternalId(
        @Param("exchangeInternalId") String exchangeInternalId,
        @Param("instrumentInternalId") String instrumentInternalId
    );

    List<InstrumentEntity> findAllByExchangeId(Long exchangeId);

    boolean existsByExchangeIdAndExternalId(Long exchangeId, String externalId);
}
