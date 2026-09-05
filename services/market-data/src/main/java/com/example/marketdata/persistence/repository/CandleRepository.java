package com.example.marketdata.persistence.repository;

import com.example.marketdata.persistence.model.CandleEntity;
import com.example.marketdata.persistence.model.CandleId;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("select min(c.openTimestamp) from CandleEntity c where c.candleGroupId = :groupId")
    Long findMinOpenTimestamp(@Param("groupId") Long groupId);

    @Query("select max(c.openTimestamp) from CandleEntity c where c.candleGroupId = :groupId")
    Long findMaxOpenTimestamp(@Param("groupId") Long groupId);
}
