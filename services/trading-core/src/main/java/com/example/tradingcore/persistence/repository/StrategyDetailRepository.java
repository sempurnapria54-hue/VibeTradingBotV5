package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.StrategyDetailEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы по детали копии определения стратегии. */
public interface StrategyDetailRepository extends JpaRepository<StrategyDetailEntity, Long> {

    /**
     * Закреплённая деталь со своим поддеревом по идентификатору,
     * <b>без</b> привязки к статусу родительской стратегии.
     *
     * <p>Открытая сделка ведётся по снимку, закреплённому при открытии, а
     * не по живой активной стратегии: сопровождение и аварийное закрытие
     * работают одинаково, когда стратегия деактивирована или удалена.
     * Условие по статусу родителя оставило бы такую сделку без правил
     * ровно на аварийной тропе.
     */
    @Query("""
            select distinct d from StrategyDetailEntity d
            left join fetch d.steps st
            left join fetch st.actions
            left join fetch d.tranches tr
            left join fetch tr.steps tst
            left join fetch tst.actions
            where d.id = :id
            """)
    Optional<StrategyDetailEntity> findByIdWithTree(@Param("id") Long id);
}
