package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.BalanceContainerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Снимок средств счёта. Строка одна на счёт — снимок есть зеркало, а не
 * история (docs/models/domain/core/BalanceContainer.md).
 */
public interface BalanceContainerRepository extends JpaRepository<BalanceContainerEntity, Long> {

    Optional<BalanceContainerEntity> findByExchangeAccountId(Long exchangeAccountId);
}
