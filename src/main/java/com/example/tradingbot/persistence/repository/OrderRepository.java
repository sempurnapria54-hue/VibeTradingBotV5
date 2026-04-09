package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {

    Optional<OrderEntity> findByInternalId(String internalId);

    Optional<OrderEntity> findByExternalId(String externalId);

    @Query(value = """
            select o.*
            from orders o
            join deals d on d.id = o.deal_id
            where d.instrument_id = :instrumentId
            order by o.id desc
            """, nativeQuery = true)
    List<OrderEntity> findAllByInstrumentId(@Param("instrumentId") Long instrumentId);
}
