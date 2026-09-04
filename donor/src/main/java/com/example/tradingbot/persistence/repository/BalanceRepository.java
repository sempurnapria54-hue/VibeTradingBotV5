package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.balance.BalanceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceRepository extends JpaRepository<BalanceEntity, Long> {

    List<BalanceEntity> findByBalanceContainerId(Long balanceContainerId);

    void deleteByBalanceContainerId(Long balanceContainerId);
}
