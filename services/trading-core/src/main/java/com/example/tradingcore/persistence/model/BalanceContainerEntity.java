package com.example.tradingcore.persistence.model;

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
 * Строка снимка средств биржевого счёта.
 *
 * <p>Снимок один на счёт — он зеркало, а не история; уникальность держит
 * ограничение схемы. Валютные строки — дочерние по
 * {@code balance_container_id}, семантика замещения живёт в границе
 * domain ↔ persistence (docs/models/domain/core/BalanceContainer.md).
 */
@Getter
@Setter
@Entity
@Table(name = "balance_containers")
public class BalanceContainerEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_account_id", nullable = false)
    private Long exchangeAccountId;

    @Column(name = "external_updated_at")
    private OffsetDateTime externalUpdatedAt;

    @Column(name = "external_total_equity", precision = 36, scale = 18)
    private BigDecimal externalTotalEquity;

    @Column(name = "external_adjusted_equity", precision = 36, scale = 18)
    private BigDecimal externalAdjustedEquity;

    @Column(name = "external_available_equity", precision = 36, scale = 18)
    private BigDecimal externalAvailableEquity;
}
