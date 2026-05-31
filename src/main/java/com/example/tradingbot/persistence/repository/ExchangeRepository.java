package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRepository extends JpaRepository<ExchangeEntity, Long> {

    Optional<ExchangeEntity> findByInternalId(String internalId);

    @Query("select e.internalId from ExchangeEntity e where e.id = :id")
    Optional<String> findInternalIdById(@Param("id") Long id);

    @Query("select e.id from ExchangeEntity e where e.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);
}
