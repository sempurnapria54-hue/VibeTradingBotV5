package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.IndicatorConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы реестра идентичностей вычисления индикатора. */
public interface IndicatorConfigRepository extends JpaRepository<IndicatorConfigEntity, Long> {

    Optional<IndicatorConfigEntity> findByIndicatorTypeAndTimeframeAndParamsCanonical(
            String indicatorType, String timeframe, String paramsCanonical);

    Optional<IndicatorConfigEntity> findByInternalId(String internalId);
}
