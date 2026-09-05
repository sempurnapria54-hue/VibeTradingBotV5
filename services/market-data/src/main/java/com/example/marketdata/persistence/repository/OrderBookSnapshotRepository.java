package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketSnapshotId;
import com.example.marketdata.persistence.model.OrderBookSnapshotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы ряда срезов книги заявок. */
public interface OrderBookSnapshotRepository extends JpaRepository<OrderBookSnapshotEntity, MarketSnapshotId> {

    Optional<OrderBookSnapshotEntity> findFirstByInstrumentIdOrderByExternalTimestampDesc(Long instrumentId);
}
