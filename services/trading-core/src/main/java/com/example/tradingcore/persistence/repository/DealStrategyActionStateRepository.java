package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.DealStrategyActionStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Строки исполнения СТРАТЕГИЙНЫХ действий.
 *
 * <p>Адресуются по сделке: живое исполнение резолвится частичным ключом в
 * памяти прохода (сделка + транш + эпизод + узел), а не запросом на
 * каждый узел — иначе сетка из N траншей дала бы N обращений за проход
 * (.claude/rules/codestyle.md §«Выборка данных»).
 */
public interface DealStrategyActionStateRepository
        extends JpaRepository<DealStrategyActionStateEntity, Long> {

    List<DealStrategyActionStateEntity> findByDealId(Long dealId);
}
