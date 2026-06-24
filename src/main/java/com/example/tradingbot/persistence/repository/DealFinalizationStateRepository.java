package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.command.DealFinalizationStateEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий персистентного retry-state финализации. Адресуется по ключу
 * идемпотентности (deal_id, type) и по сделке (загрузка в DealContext).
 */
public interface DealFinalizationStateRepository extends JpaRepository<DealFinalizationStateEntity, Long> {

    Optional<DealFinalizationStateEntity> findByDealIdAndType(Long dealId, String type);

    List<DealFinalizationStateEntity> findByDealId(Long dealId);
}
