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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = NAME_LENGTH)
    private String name;

    @Column(name = "base_url", nullable = false, length = BASE_URL_LENGTH)
    private String baseUrl;

    @Column(name = "status", nullable = false, length = STATUS_LENGTH)
    private String status;
}
