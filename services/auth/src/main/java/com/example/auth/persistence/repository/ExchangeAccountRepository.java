package com.example.auth.persistence.repository;

import com.example.auth.persistence.model.ExchangeAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке ExchangeAccount. */
public interface ExchangeAccountRepository extends JpaRepository<ExchangeAccountEntity, Long> {

    Optional<ExchangeAccountEntity> findByInternalId(String internalId);

    /** Счета тенанта — реестр, который читает владелец. */
    List<ExchangeAccountEntity> findAllByTenantId(String tenantId);
}
