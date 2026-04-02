package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    Optional<PositionEntity> findByDealId(Long dealId);
}
