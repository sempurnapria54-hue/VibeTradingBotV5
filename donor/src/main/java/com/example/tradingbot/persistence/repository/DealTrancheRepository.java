package com.example.tradingbot.persistence.repository;

import com.example.tradingbot.persistence.model.deal.DealTrancheEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealTrancheRepository extends JpaRepository<DealTrancheEntity, Long> {

    List<DealTrancheEntity> findByDealIdOrderByIdAsc(Long dealId);

    Optional<DealTrancheEntity> findByInternalId(String internalId);
}
