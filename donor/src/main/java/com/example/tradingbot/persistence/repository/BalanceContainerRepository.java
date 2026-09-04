package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceContainerRepository extends JpaRepository<BalanceContainerEntity, Long> {

    Optional<BalanceContainerEntity> findByExchangeId(Long exchangeId);
}
