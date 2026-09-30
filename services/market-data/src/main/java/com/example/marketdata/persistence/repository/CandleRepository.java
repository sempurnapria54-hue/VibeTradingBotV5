package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.CandleEntity;
import com.example.marketdata.persistence.model.CandleId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Запросы свечного ряда. */
public interface CandleRepository extends JpaRepository<CandleEntity, CandleId> {

    /**
     * Недавнее окно свечей группы по убыванию открытия. Окно ограничено
     * намеренно: минутные свечи за годы кладут БД
     * (.claude/rules/codestyle.md).
     */
    List<CandleEntity> findByCandleGroupIdOrderByOpenTimestampDesc(Long candleGroupId, Pageable pageable);

    /** Окно свечей группы от границы по возрастанию — пакетное чтение истории. */
    List<CandleEntity> findByCandleGroupIdAndOpenTimestampGreaterThanEqualOrderByOpenTimestampAsc(
            Long candleGroupId, Long fromMillis, Pageable pageable);

    Long countByCandleGroupId(Long candleGroupId);

    @Query("select c.openTimestamp from CandleEntity c where c.candleGroupId = :groupId "
            + "and c.openTimestamp between :fromMillis and :toMillis")
    List<Long> findOpenTimestampsInRange(@Param("groupId") Long groupId,
                                         @Param("fromMillis") Long fromMillis,
                                         @Param("toMillis") Long toMillis);

    @Query("select count(c) from CandleEntity c where c.candleGroupId = :groupId "
            + "and c.openTimestamp between :fromMillis and :toMillis")
    Long countInRange(@Param("groupId") Long groupId,
                      @Param("fromMillis") Long fromMillis,
                      @Param("toMillis") Long toMillis);

    /**
     * Безопасная вставка свечи по ключу {@code pk_candle} (группа плюс
     * открытие бара): свеча, уже лежащая в ряду, поглощается, а не роняет
     * запись страницы (docs/rules/idempotency-via-unique.md). Ключ
     * гипертаблицы включает колонку разбиения, поэтому спецификация
     * конфликта его находит. Возвращает число вставленных строк: {@code 0}
     * — свеча уже была. Аудит проставляется здесь же — нативная вставка
     * слушателей JPA не проходит.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into candles
                (candle_group_id, open_timestamp, open, high, low, close, volume,
                 created_at, created_by, modified_at, modified_by,
                 external_created_at, external_modified_at)
            values
                (:candleGroupId, :openTimestamp, :open, :high, :low, :close, :volume,
                 :writtenAt, :writer, :writtenAt, :writer,
                 :externalCreatedAt, :externalModifiedAt)
            on conflict on constraint pk_candle do nothing
            """)
    Integer insertIfAbsent(@Param("candleGroupId") Long candleGroupId,
                           @Param("openTimestamp") Long openTimestamp,
                           @Param("open") BigDecimal open,
                           @Param("high") BigDecimal high,
                           @Param("low") BigDecimal low,
                           @Param("close") BigDecimal close,
                           @Param("volume") BigDecimal volume,
                           @Param("externalCreatedAt") OffsetDateTime externalCreatedAt,
                           @Param("externalModifiedAt") OffsetDateTime externalModifiedAt,
                           @Param("writtenAt") OffsetDateTime writtenAt,
                           @Param("writer") String writer);

    @Query("select min(c.openTimestamp) from CandleEntity c where c.candleGroupId = :groupId")
    Long findMinOpenTimestamp(@Param("groupId") Long groupId);

    @Query("select max(c.openTimestamp) from CandleEntity c where c.candleGroupId = :groupId")
    Long findMaxOpenTimestamp(@Param("groupId") Long groupId);
}
