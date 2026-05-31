package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.order.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByInternalId(String internalId);

    Optional<OrderEntity> findByExternalId(String externalId);

    Optional<OrderEntity> findByDealIdAndStrategyActionId(Long dealId, Long strategyActionId);

    boolean existsByDealIdAndStrategyActionId(Long dealId, Long strategyActionId);

    @Query(value = """
            select o.*
            from orders o
            join deals d on d.id = o.deal_id
            where d.instrument_id = :instrumentId
            order by o.id desc
            """, nativeQuery = true)
    List<OrderEntity> findAllByInstrumentId(@Param("instrumentId") Long instrumentId);

    @Query(value = """
            select o.*
            from orders o
            join deals d on d.id = o.deal_id
            where d.instrument_id = :instrumentId
            and o.status IN(:statuses)
            order by o.id desc
            """, nativeQuery = true)
    List<OrderEntity> findAllByInstrumentIdAndStatuses(@Param("instrumentId") Long instrumentId,
                                                       @Param("statuses") Set<String> statuses);
}
