package com.example.tradingbot.persistence.model.balance;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "balance_containers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_balance_containers_exchange", columnNames = {"exchange_id"})
})
public class BalanceContainerEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "exchange_id", nullable = false, updatable = false)
    private Long exchangeId;

    @Column(name = "total_equity", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal totalEquity;

    @Column(name = "unrealized_profit", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal unrealizedProfit;

    @Column(name = "external_updated_at")
    private OffsetDateTime externalUpdatedAt;

    @OneToMany(mappedBy = "balanceContainer", fetch = FetchType.LAZY)
    private List<BalanceEntity> balances;
}
