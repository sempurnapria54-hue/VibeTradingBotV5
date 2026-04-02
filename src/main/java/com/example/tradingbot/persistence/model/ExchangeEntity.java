package com.example.tradingbot.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exchanges", uniqueConstraints = {
        @UniqueConstraint(name = "uk_exchange_internal_id", columnNames = "internal_id"),
        @UniqueConstraint(name = "uk_exchange_name", columnNames = "name")
})
public class ExchangeEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор биржи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Межсервисный идентификатор биржи.
     */
    @Column(name = "internal_id", nullable = false, updatable = false)
    private String internalId;

    /**
     * Уникальное имя биржи (например OKX).
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Базовый URL для API биржи.
     */
    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    /**
     * Текущий статус подключения/использования биржи.
     */
    @Column(name = "status", nullable = false)
    private String status;
}
