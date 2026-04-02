package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.ExchangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExchangeRepository extends JpaRepository<ExchangeEntity, Long> {

    Optional<ExchangeEntity> findByName(String name);

    Optional<ExchangeEntity> findByInternalId(String internalId);

    @Query("select e.id from ExchangeEntity e where e.internalId = :internalId")
    Optional<Long> findIdByInternalId(@Param("internalId") String internalId);

    boolean existsByName(String name);
}
