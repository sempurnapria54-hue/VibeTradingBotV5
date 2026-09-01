package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.position.PositionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    /**
     * Эпизоды сделки в порядке их возникновения. Выборка ограничена
     * сделкой и потому конечна: эпизодов у сделки столько, сколько раз
     * позиция схлопывалась и открывалась заново.
     */
    List<PositionEntity> findByDealIdOrderByExternalCreatedAtAsc(Long dealId);

    /** Живой эпизод сделки — по инварианту он не более одного. */
    Optional<PositionEntity> findFirstByDealIdAndStatus(Long dealId, String status);
}
