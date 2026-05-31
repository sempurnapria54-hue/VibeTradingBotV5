package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.balance.BalanceContainerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BalanceContainerRepository extends JpaRepository<BalanceContainerEntity, Long> {

    Optional<BalanceContainerEntity> findByExchangeId(Long exchangeId);

    @Query("""
            select distinct bc
            from BalanceContainerEntity bc
            left join fetch bc.balances b
            where bc.exchangeId = :exchangeId
            """)
    Optional<BalanceContainerEntity> findByExchangeIdWithBalances(@Param("exchangeId") Long exchangeId);
}
