package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.command.DealSystemActionStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий строк исполнения СИСТЕМНЫХ действий. Адресуется по сделке:
 * живое исполнение резолвится частичным ключом в памяти прохода
 * (deal + транш + эпизод + тип действия).
 */
public interface DealSystemActionStateRepository extends JpaRepository<DealSystemActionStateEntity, Long> {

    List<DealSystemActionStateEntity> findByDealId(Long dealId);
}
