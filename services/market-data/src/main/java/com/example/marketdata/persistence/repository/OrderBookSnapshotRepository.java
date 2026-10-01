package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketSnapshotId;
import com.example.marketdata.persistence.model.OrderBookSnapshotEntity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы ряда срезов книги заявок. */
public interface OrderBookSnapshotRepository extends JpaRepository<OrderBookSnapshotEntity, MarketSnapshotId> {

    Optional<OrderBookSnapshotEntity> findFirstByInstrumentIdOrderByExternalTimestampDesc(Long instrumentId);

    /**
     * Безопасная вставка среза по ключу {@code pk_order_book_snapshot}
     * (инструмент плюс момент площадки): повтор того же момента поглощается,
     * а не роняет запись (docs/rules/idempotency-via-unique.md). Форма ключа —
     * docs/rules/persistence-representation.md §«Ключ гипертаблицы». Аудит
     * проставляется здесь же — нативная вставка слушателей JPA не проходит.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into order_book_snapshots
                (instrument_id, external_timestamp, observed_timestamp, bids, asks,
                 created_at, created_by, modified_at, modified_by,
                 external_created_at, external_modified_at)
            values
                (:instrumentId, :externalTimestamp, :observedTimestamp,
                 cast(:bids as jsonb), cast(:asks as jsonb),
                 :writtenAt, :writer, :writtenAt, :writer,
                 :externalCreatedAt, :externalModifiedAt)
            on conflict on constraint pk_order_book_snapshot do nothing
            """)
    Integer insertIfAbsent(@Param("instrumentId") Long instrumentId,
                           @Param("externalTimestamp") Long externalTimestamp,
                           @Param("observedTimestamp") Long observedTimestamp,
                           @Param("bids") String bids,
                           @Param("asks") String asks,
                           @Param("externalCreatedAt") OffsetDateTime externalCreatedAt,
                           @Param("externalModifiedAt") OffsetDateTime externalModifiedAt,
                           @Param("writtenAt") OffsetDateTime writtenAt,
                           @Param("writer") String writer);
}
