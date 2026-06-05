package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.instrument.InstrumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Long> {

    Optional<InstrumentEntity> findByInternalId(String internalId);

    List<InstrumentEntity> findByStatus(String status);

    @Query("select distinct i from InstrumentEntity i left join fetch i.candleGroups where i.id = :id")
    Optional<InstrumentEntity> findByIdWithCandleGroups(@Param("id") Long id);

    @Query("select i.internalId from InstrumentEntity i where i.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    @Query("select i.id from InstrumentEntity i where i.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);
}
