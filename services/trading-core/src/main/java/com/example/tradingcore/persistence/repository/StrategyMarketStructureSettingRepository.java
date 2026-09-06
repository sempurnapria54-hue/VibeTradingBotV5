package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.StrategyMarketStructureSettingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Запросы по объявлению структуры рынка в копии определения. Форма та же,
 * что у объявления индикатора: точечная write-once привязка идентичности
 * вычисления плюс перечень непривязанных.
 */
public interface StrategyMarketStructureSettingRepository
        extends JpaRepository<StrategyMarketStructureSettingEntity, Long> {

    /** Стратегии, у которых есть объявление структуры без идентичности вычисления. */
    @Query("""
            select distinct s.strategy.id from StrategyMarketStructureSettingEntity s
            where s.computationConfigInternalId is null""")
    List<Long> findStrategyIdsWithUnboundComputation();

    /** Write-once привязки идентичности вычисления к объявлению структуры. */
    @Modifying
    @Query("""
            update StrategyMarketStructureSettingEntity s set s.computationConfigInternalId = :configInternalId
            where s.id = :id and s.computationConfigInternalId is null""")
    int bindComputation(@Param("id") Long id, @Param("configInternalId") String configInternalId);
}
