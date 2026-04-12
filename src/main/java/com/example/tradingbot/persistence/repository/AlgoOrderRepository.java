package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.algo_order.AlgoOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlgoOrderRepository extends JpaRepository<AlgoOrderEntity, Long>,
        JpaSpecificationExecutor<AlgoOrderEntity> {

    Optional<AlgoOrderEntity> findByInternalId(String internalId);

    @Query(value = """
            select ao.*
            from algo_orders ao
            join deals d on d.id = ao.deal_id
            where d.instrument_id = :instrumentId
            order by ao.id desc
            """, nativeQuery = true)
    List<AlgoOrderEntity> findAllByInstrumentId(@Param("instrumentId") Long instrumentId);

}
