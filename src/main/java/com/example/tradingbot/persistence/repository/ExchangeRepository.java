package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.domain.model.entity.ExchangeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRepository extends JpaRepository<ExchangeEntity, Long> {

    Optional<ExchangeEntity> findByName(String name);
}
