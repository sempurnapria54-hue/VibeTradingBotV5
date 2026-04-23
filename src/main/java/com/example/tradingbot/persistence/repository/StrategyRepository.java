package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StrategyRepository extends JpaRepository<StrategyEntity, Long> {

    @EntityGraph(attributePaths = {"detailEntities", "detailEntities.steps"})
    Optional<StrategyEntity> findFirstByInstrumentIdAndStatusOrderByVersionDesc(Long instrumentId, String status);

    @EntityGraph(attributePaths = {"detailEntities", "detailEntities.steps"})
    @Query("select strategy from StrategyEntity strategy where strategy.id = :id")
    Optional<StrategyEntity> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"detailEntities", "detailEntities.steps"})
    Optional<StrategyEntity> findByInternalId(String internalId);
}
