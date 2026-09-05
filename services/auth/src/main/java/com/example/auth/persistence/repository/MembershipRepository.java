package com.example.auth.persistence.repository;

import com.example.auth.persistence.model.MembershipEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запросы по строке Membership. */
public interface MembershipRepository extends JpaRepository<MembershipEntity, Long> {

    Optional<MembershipEntity> findByInternalId(String internalId);

    /** Членства пользователя — вход резолва контекста тенанта. */
    List<MembershipEntity> findAllByUserId(String userId);

    /**
     * Есть ли у тенанта членство с этой ролью.
     *
     * <p>Инвариант «ровно одно членство `OWNER`» держит уникальный
     * частичный индекс схемы; предикат нужен исполнителю, чтобы отказать
     * ДО вставки внятной причиной, а не ловить нарушение ограничения.
     */
    Boolean existsByTenantIdAndRole(String tenantId, String role);
}
