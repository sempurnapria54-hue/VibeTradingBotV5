package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.PositionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Запросы по строке эпизода позиции.
 *
 * <p>Эпизоды читаются ЦЕЛИКОМ, включая закрытые: по ним считается
 * результат сделки и правые операнды пар сверки, и непогруженный эпизод
 * занизил бы число молча (docs/components/DealContextService.md).
 */
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    List<PositionEntity> findByDealIdOrderByExternalCreatedAtAsc(Long dealId);
}
