package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketSnapshotId;
import com.example.marketdata.persistence.model.TickerSnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы ряда срезов цен. */
public interface TickerSnapshotRepository extends JpaRepository<TickerSnapshotEntity, MarketSnapshotId> {

    Optional<TickerSnapshotEntity> findFirstByInstrumentIdOrderByExternalTimestampDesc(Long instrumentId);
}
