package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BalanceRepository extends JpaRepository<BalanceEntity, Long> {

    Optional<BalanceEntity> findByExchangeIdAndCurrency(Long exchangeId, String currency);
}
