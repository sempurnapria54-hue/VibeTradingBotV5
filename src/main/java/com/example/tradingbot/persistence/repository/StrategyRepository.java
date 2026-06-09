package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.strategy.StrategyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StrategyRepository extends JpaRepository<StrategyEntity, Long> {

    Optional<StrategyEntity> findByInternalId(String internalId);

    /**
     * Стратегия со всем деревом одним запросом (join fetch по
     * Set-коллекциям — без N+1 и MultipleBagFetch); порядок шагов и
     * действий восстанавливает маппер (step_index / id ASC).
     */
    @Query("""
            select s from StrategyEntity s
            left join fetch s.marketPhaseSetting
            left join fetch s.details d
            left join fetch d.steps st
            left join fetch st.actions
            where s.internalId = :internalId
            """)
    Optional<StrategyEntity> findByInternalIdWithTree(@Param("internalId") String internalId);

    Boolean existsByInstrumentIdAndStatus(Long instrumentId, String status);

    /**
     * Стратегии всех статусов кроме переданного (для jobs рыночных
     * данных — все, кроме DELETED) с настройками рыночных данных: фаза
     * (с phaseRules) и детали с их JSONB-настройками; шаги не грузятся.
     */
    @Query("""
            select distinct s from StrategyEntity s
            left join fetch s.marketPhaseSetting
            left join fetch s.details
            where s.status <> :status
            """)
    List<StrategyEntity> findAllWithSettingsByStatusNot(@Param("status") String status);
}
