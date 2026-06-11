package com.example.tradingbot.persistence.model.balance;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistence-проекция {@link com.example.tradingbot.domain.model.core.balance.BalanceContainer}
 * (таблица balance_containers). Currency-балансы — дочерние строки
 * balances по balance_container_id (replace semantics в DataService).
 */
@Getter
@Setter
@Entity
@Table(name = "balance_containers")
public class BalanceContainerEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    @Column(name = "external_updated_at")
    private OffsetDateTime externalUpdatedAt;

    @Column(name = "external_total_equity")
    private BigDecimal externalTotalEquity;

    @Column(name = "external_adjusted_equity")
    private BigDecimal externalAdjustedEquity;

    @Column(name = "external_available_equity")
    private BigDecimal externalAvailableEquity;
}
