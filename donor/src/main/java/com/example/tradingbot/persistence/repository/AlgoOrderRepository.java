package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.algoorder.AlgoOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlgoOrderRepository extends JpaRepository<AlgoOrderEntity, Long> {

    Optional<AlgoOrderEntity> findByInternalId(String internalId);

    List<AlgoOrderEntity> findByDealId(Long dealId);
}
