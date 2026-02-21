package com.example.tradingbot.domain.model.entity;

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
@Table(name = "exchange", uniqueConstraints = {
    @UniqueConstraint(name = "uk_exchange_name", columnNames = "name")
})
public class ExchangeEntity extends AuditableEntity {

    public static final int NAME_LENGTH = 100;
    public static final int BASE_URL_LENGTH = 512;
    public static final int STATUS_LENGTH = 50;

    /** Внутренний идентификатор биржи. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /** Уникальное имя биржи (например OKX). */
    @Column(name = "name", nullable = false)
    private String name;

    /** Базовый URL для API биржи. */
    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    /** Текущий статус подключения/использования биржи. */
    @Column(name = "status", nullable = false)
    private String status;
}
