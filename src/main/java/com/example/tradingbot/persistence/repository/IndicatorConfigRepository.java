package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.marketdata.IndicatorConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndicatorConfigRepository extends JpaRepository<IndicatorConfigEntity, Long> {

    Optional<IndicatorConfigEntity> findByIndicatorTypeAndTimeframeAndParamsCanonical(
            String indicatorType, String timeframe, String paramsCanonical);
}
