package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.DealSystemActionStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Строки исполнения СИСТЕМНЫХ действий. Адресуются по сделке: живое
 * исполнение резолвится частичным ключом в памяти прохода (сделка +
 * транш + эпизод + тип действия).
 */
public interface DealSystemActionStateRepository
        extends JpaRepository<DealSystemActionStateEntity, Long> {

    List<DealSystemActionStateEntity> findByDealId(Long dealId);
}
