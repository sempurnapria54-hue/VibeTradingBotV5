package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.exchange.ExchangeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRepository extends JpaRepository<ExchangeEntity, Long> {

    Optional<ExchangeEntity> findByName(String name);

    Optional<ExchangeEntity> findByInternalId(String internalId);
}
