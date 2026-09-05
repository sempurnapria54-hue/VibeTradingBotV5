package com.example.auth.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Строка тенанта (docs/models/domain/core/Tenant.md §Персистентность). */
@Getter
@Setter
@Entity
@Table(name = "tenants")
public class TenantEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "internal_id", nullable = false, unique = true)
    private String internalId;

    @Column(name = "name", nullable = false)
    private String name;

    /** Перечень хранится строкой (.claude/rules/codestyle.md §«Слои моделей и enum'ы»). */
    @Column(name = "status", nullable = false)
    private String status;
}
