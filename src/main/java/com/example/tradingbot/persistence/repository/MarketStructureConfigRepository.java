package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.MarketStructureConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketStructureConfigRepository extends JpaRepository<MarketStructureConfigEntity, Long> {

    Optional<MarketStructureConfigEntity> findByTimeframeAndParamsCanonical(
            String timeframe, String paramsCanonical);
}
