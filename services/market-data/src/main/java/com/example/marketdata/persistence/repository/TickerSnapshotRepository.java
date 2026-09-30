package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketSnapshotId;
import com.example.marketdata.persistence.model.TickerSnapshotEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы ряда срезов цен. */
public interface TickerSnapshotRepository extends JpaRepository<TickerSnapshotEntity, MarketSnapshotId> {

    Optional<TickerSnapshotEntity> findFirstByInstrumentIdOrderByExternalTimestampDesc(Long instrumentId);

    /**
     * Безопасная вставка среза по ключу {@code pk_ticker_snapshot}
     * (инструмент плюс момент площадки): повтор того же момента поглощается,
     * а не роняет запись (docs/rules/idempotency-via-unique.md). Ключ
     * гипертаблицы включает колонку разбиения, поэтому спецификация
     * конфликта его находит. Аудит проставляется здесь же — нативная
     * вставка слушателей JPA не проходит.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into ticker_snapshots
                (instrument_id, external_timestamp, observed_timestamp, last_price, volume,
                 mark_price, index_price, created_at, created_by, modified_at, modified_by,
                 external_created_at, external_modified_at)
            values
                (:instrumentId, :externalTimestamp, :observedTimestamp, :lastPrice, :volume,
                 :markPrice, :indexPrice, :writtenAt, :writer, :writtenAt, :writer,
                 :externalCreatedAt, :externalModifiedAt)
            on conflict on constraint pk_ticker_snapshot do nothing
            """)
    Integer insertIfAbsent(@Param("instrumentId") Long instrumentId,
                           @Param("externalTimestamp") Long externalTimestamp,
                           @Param("observedTimestamp") Long observedTimestamp,
                           @Param("lastPrice") BigDecimal lastPrice,
                           @Param("volume") BigDecimal volume,
                           @Param("markPrice") BigDecimal markPrice,
                           @Param("indexPrice") BigDecimal indexPrice,
                           @Param("externalCreatedAt") OffsetDateTime externalCreatedAt,
                           @Param("externalModifiedAt") OffsetDateTime externalModifiedAt,
                           @Param("writtenAt") OffsetDateTime writtenAt,
                           @Param("writer") String writer);
}
