package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.BalanceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Валютные строки снимка средств.
 *
 * <p>Приземление снимка ЗАМЕЩАЕТ набор целиком: источник отдаёт состояние,
 * а не дельту, и строка, исчезнувшая из ответа, означает «валюты больше
 * нет» (docs/models/domain/core/BalanceContainer.md).
 */
public interface BalanceRepository extends JpaRepository<BalanceEntity, Long> {

    List<BalanceEntity> findByBalanceContainerId(Long balanceContainerId);

    void deleteByBalanceContainerId(Long balanceContainerId);
}
