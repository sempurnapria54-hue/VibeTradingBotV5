package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.position.PositionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    Optional<PositionEntity> findByDealId(Long dealId);
}
