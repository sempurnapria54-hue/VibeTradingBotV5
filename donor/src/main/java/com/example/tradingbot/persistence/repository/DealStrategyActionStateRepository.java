package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.command.DealStrategyActionStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий строк исполнения СТРАТЕГИЙНЫХ действий. Адресуется по
 * сделке: живое исполнение резолвится частичным ключом в памяти прохода
 * (deal + транш + эпизод + узел), а не запросом на каждый узел — иначе
 * сетка из N траншей дала бы N обращений за проход.
 */
public interface DealStrategyActionStateRepository extends JpaRepository<DealStrategyActionStateEntity, Long> {

    List<DealStrategyActionStateEntity> findByDealId(Long dealId);
}
