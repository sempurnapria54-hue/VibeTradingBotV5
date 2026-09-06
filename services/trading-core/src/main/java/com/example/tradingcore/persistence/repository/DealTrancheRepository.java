package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.DealTrancheEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Запросы по строке транша.
 *
 * <p>Выборка идёт по сделке и ограничена ею: число траншей одной сделки
 * задаёт стратегия, поэтому безлимитного чтения здесь нет по построению.
 * Фильтра по статусу у неё нет намеренно — числа сделки считаются и по
 * закрытым траншам (docs/spec/deal-context-load.json).
 */
public interface DealTrancheRepository extends JpaRepository<DealTrancheEntity, Long> {

    List<DealTrancheEntity> findByDealIdOrderByIdAsc(Long dealId);
}
