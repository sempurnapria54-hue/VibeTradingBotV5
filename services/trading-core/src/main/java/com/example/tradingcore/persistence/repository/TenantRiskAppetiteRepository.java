package com.example.tradingcore.persistence.repository;

import com.example.tradingcore.persistence.model.TenantRiskAppetiteEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке чисел риск-аппетита тенанта. */
public interface TenantRiskAppetiteRepository extends JpaRepository<TenantRiskAppetiteEntity, Long> {

    Optional<TenantRiskAppetiteEntity> findByTenantInternalId(String tenantInternalId);
}
