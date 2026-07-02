package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.strategy.StrategyDetailEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StrategyDetailRepository extends JpaRepository<StrategyDetailEntity, Long> {

    /**
     * Деталь стратегии со своим поддеревом (шаги + действия) по id, без
     * привязки к статусу родительской стратегии — для сборки запиненной
     * {@code StrategyDetail} уже открытой сделки. Сопровождение и аварийное
     * закрытие опираются на снимок настроек сделки, а не на живую активную
     * стратегию, и работают одинаково при {@code Strategy.INACTIVE} и
     * {@code DELETED}. Join fetch по Set-коллекциям (без N+1 и MultipleBagFetch);
     * порядок шагов и действий восстанавливает маппер.
     */
    @Query("""
            select distinct d from StrategyDetailEntity d
            left join fetch d.steps st
            left join fetch st.actions
            where d.id = :id
            """)
    Optional<StrategyDetailEntity> findByIdWithTree(@Param("id") Long id);
}
