package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.MarketStructureEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы ряда структур рынка. */
public interface MarketStructureRepository extends JpaRepository<MarketStructureEntity, Long> {

    /**
     * Безопасная вставка каркаса по ключу {@code uk_market_structure_identity}:
     * строка заводится, если результата этого окна у пары (инструмент,
     * идентичность) ещё нет, а второй писатель того же окна не получает ни
     * дубля, ни отказа (docs/rules/idempotency-via-unique.md). Возвращает
     * число вставленных строк: {@code 0} — окно уже записано.
     *
     * <p>Уровни каркаса этой вставкой не пишутся: их строки ссылаются на
     * каркас, и заводит их вызывающий, только когда вставка состоялась.
     * Аудит проставляется здесь же — нативная вставка слушателей JPA не
     * проходит.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into market_structures
                (instrument_id, market_structure_config_id, type, window_start_at, window_end_at,
                 confirmed_at, breakout_broken_level_type, breakout_direction, breakout_level_price,
                 breakout_confirmed_at, created_at, created_by, modified_at, modified_by,
                 external_created_at, external_modified_at)
            values
                (:instrumentId, :marketStructureConfigId, :type, :windowStartAt, :windowEndAt,
                 :confirmedAt, :breakoutBrokenLevelType, :breakoutDirection, :breakoutLevelPrice,
                 :breakoutConfirmedAt, :writtenAt, :writer, :writtenAt, :writer,
                 :externalCreatedAt, :externalModifiedAt)
            on conflict on constraint uk_market_structure_identity do nothing
            """)
    Integer insertIfAbsent(@Param("instrumentId") Long instrumentId,
                           @Param("marketStructureConfigId") Long marketStructureConfigId,
                           @Param("type") String type,
                           @Param("windowStartAt") OffsetDateTime windowStartAt,
                           @Param("windowEndAt") OffsetDateTime windowEndAt,
                           @Param("confirmedAt") OffsetDateTime confirmedAt,
                           @Param("breakoutBrokenLevelType") String breakoutBrokenLevelType,
                           @Param("breakoutDirection") String breakoutDirection,
                           @Param("breakoutLevelPrice") BigDecimal breakoutLevelPrice,
                           @Param("breakoutConfirmedAt") OffsetDateTime breakoutConfirmedAt,
                           @Param("externalCreatedAt") OffsetDateTime externalCreatedAt,
                           @Param("externalModifiedAt") OffsetDateTime externalModifiedAt,
                           @Param("writtenAt") OffsetDateTime writtenAt,
                           @Param("writer") String writer);

    /** Каркас окна по ключу уникальности — тот, что только что завела безопасная вставка. */
    Optional<MarketStructureEntity> findByInstrumentIdAndMarketStructureConfigIdAndWindowEndAt(
            Long instrumentId, Long marketStructureConfigId, OffsetDateTime windowEndAt);

    Optional<MarketStructureEntity> findFirstByInstrumentIdAndMarketStructureConfigIdOrderByWindowEndAtDesc(
            Long instrumentId, Long marketStructureConfigId);
}
