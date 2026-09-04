package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.candle.CandleEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    Long countByCandleGroupId(Long candleGroupId);

    /**
     * Ограниченное недавнее окно закрытых свечей группы (по убыванию
     * открытия, LIMIT через Pageable) — вход расчёта производных данных.
     * Грузим окно, а не всю историю: минутные свечи за годы положили бы
     * БД (защита от тяжёлых запросов, codestyle §Выборка данных).
     */
    List<CandleEntity> findByCandleGroupIdOrderByOpenTimestampDesc(Long candleGroupId, Pageable pageable);

    @Query("select min(c.openTimestamp) from CandleEntity c where c.candleGroupId = :groupId")
    Long findMinOpenTimestamp(@Param("groupId") Long candleGroupId);

    @Query("select max(c.openTimestamp) from CandleEntity c where c.candleGroupId = :groupId")
    Long findMaxOpenTimestamp(@Param("groupId") Long candleGroupId);

    @Query("select count(c) from CandleEntity c "
            + "where c.candleGroupId = :groupId and c.openTimestamp between :from and :to")
    Long countInRange(@Param("groupId") Long candleGroupId, @Param("from") Long fromMillis, @Param("to") Long toMillis);

    @Query("select c.openTimestamp from CandleEntity c "
            + "where c.candleGroupId = :groupId and c.openTimestamp between :from and :to")
    List<Long> findOpenTimestampsInRange(@Param("groupId") Long candleGroupId,
                                         @Param("from") Long fromMillis, @Param("to") Long toMillis);
}
