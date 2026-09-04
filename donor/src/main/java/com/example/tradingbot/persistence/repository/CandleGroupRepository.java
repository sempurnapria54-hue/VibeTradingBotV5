package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.candle.CandleGroupEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleGroupRepository extends JpaRepository<CandleGroupEntity, Long> {

    List<CandleGroupEntity> findByInstrument_Id(Long instrumentId);

    Optional<CandleGroupEntity> findByInstrument_IdAndTimeframe(Long instrumentId, String timeframe);

    List<CandleGroupEntity> findByStatusIn(Collection<String> statuses);
}
