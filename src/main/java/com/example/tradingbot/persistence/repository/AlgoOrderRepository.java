package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.algo_order.AlgoOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AlgoOrderRepository extends JpaRepository<AlgoOrderEntity, Long>,
        JpaSpecificationExecutor<AlgoOrderEntity> {

    Optional<AlgoOrderEntity> findByInternalId(String internalId);

}
