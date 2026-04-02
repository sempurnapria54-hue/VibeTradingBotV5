package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.DealEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DealRepository extends JpaRepository<DealEntity, Long> {

    Optional<DealEntity> findByInternalId(String internalId);

}
