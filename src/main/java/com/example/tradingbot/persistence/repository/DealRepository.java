package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<DealEntity, Long> {

    Optional<DealEntity> findByInternalId(String internalId);
}
