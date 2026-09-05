package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketStructureConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы реестра идентичностей вычисления структуры рынка. */
public interface MarketStructureConfigRepository extends JpaRepository<MarketStructureConfigEntity, Long> {

    Optional<MarketStructureConfigEntity> findByInternalId(String internalId);

    /**
     * Поиск по идентичности целиком, включая идентичности входов.
     * Сравнение через {@code is not distinct from}: входы необязательны, и
     * обычное равенство на пустом операнде не находило бы существующую
     * строку — реестр заводил бы её вторично.
     */
    @Query(value = "select * from market_structure_configs where timeframe = :timeframe "
            + "and params_canonical = :paramsCanonical "
            + "and efficiency_ratio_config_id is not distinct from :efficiencyRatioConfigId "
            + "and atr_config_id is not distinct from :atrConfigId", nativeQuery = true)
    Optional<MarketStructureConfigEntity> findByIdentity(@Param("timeframe") String timeframe,
                                                         @Param("paramsCanonical") String paramsCanonical,
                                                         @Param("efficiencyRatioConfigId") Long efficiencyRatioConfigId,
                                                         @Param("atrConfigId") Long atrConfigId);

    List<MarketStructureConfigEntity> findByTimeframe(String timeframe);
}
