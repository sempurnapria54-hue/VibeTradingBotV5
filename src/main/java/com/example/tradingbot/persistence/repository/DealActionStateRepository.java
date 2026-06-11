package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.command.DealActionStateEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealActionStateRepository extends JpaRepository<DealActionStateEntity, Long> {

    Optional<DealActionStateEntity> findByDealIdAndStrategyActionId(Long dealId, Long strategyActionId);
}
