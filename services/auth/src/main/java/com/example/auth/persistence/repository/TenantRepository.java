package com.example.auth.persistence.repository;

import com.example.auth.persistence.model.TenantEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке Tenant. */
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {

    Optional<TenantEntity> findByInternalId(String internalId);
}
