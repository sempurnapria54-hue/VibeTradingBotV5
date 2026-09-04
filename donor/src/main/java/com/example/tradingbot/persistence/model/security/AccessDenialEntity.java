package com.example.tradingbot.persistence.model.security;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link com.example.tradingbot.domain.security.AccessDenial}
 * (таблица access_denials). Енум класса отказа — строкой, как и у прочих
 * моделей. Биржевые поля аудита остаются пустыми навсегда: у отвергнутого
 * вызова биржевого домена нет вовсе.
 */
@Getter
@Setter
@Entity
@Table(name = "access_denials")
public class AccessDenialEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    @Column(name = "surface", nullable = false)
    private String surface;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    /** Принятый принципал; пусто ⟺ outcome = PRINCIPAL_ABSENT. */
    @Column(name = "principal")
    private String principal;
}
