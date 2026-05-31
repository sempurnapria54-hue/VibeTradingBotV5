package com.example.tradingbot.persistence.model.exchange;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
