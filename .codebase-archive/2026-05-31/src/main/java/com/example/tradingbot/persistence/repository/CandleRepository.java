package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.candle.CandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    long countByCandleGroupIdAndTimestampBetween(Long candleGroupId, long from, long to);

    @Query("""
            select min(c.timestamp)
            from CandleEntity c
            where c.candleGroupId = :groupId
            """)
    Optional<Long> findMinTimestampByCandleGroupId(@Param("groupId") Long groupId);

    @Query("""
            select max(c.timestamp)
            from CandleEntity c
            where c.candleGroupId = :groupId
            """)
    Optional<Long> findMaxTimestampByCandleGroupId(@Param("groupId") Long groupId);

    @Query("""
            select c.timestamp
            from CandleEntity c
            where c.candleGroupId = :groupId and c.timestamp between :from and :to
            order by c.timestamp asc
            """)
    List<Long> findTimestampsByCandleGroupIdAndTimestampBetweenOrderByTimestampAsc(
            @Param("groupId") Long groupId,
            @Param("from") long from,
            @Param("to") long to
    );
}
