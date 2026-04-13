package com.example.tradingbot.persistence.model.balance;

import com.example.tradingbot.persistence.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static com.example.tradingbot.util.Constant.Service.PRICE_PRECISION;
import static com.example.tradingbot.util.Constant.Service.PRICE_SCALE;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "balances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_balances_exchange_currency", columnNames = {"exchange_id", "currency"})
})
public class BalanceEntity extends AuditableEntity {

    /**
     * Внутренний идентификатор.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * Идентификатор биржи.
     */
    @Column(name = "exchange_id", nullable = false, updatable = false)
    private Long exchangeId;

    /**
     * Валюта баланса.
     */
    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    /**
     * Идентификатор контейнера snapshot аккаунта.
     */
    @Column(name = "balance_container_id", nullable = false)
    private Long balanceContainerId;

    /**
     * Доступный баланс.
     */
    @Column(name = "available", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal available;

    /**
     * Заблокированный баланс.
     */
    @Column(name = "frozen", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal frozen;

    /**
     * Общий баланс.
     */
    @Column(name = "total", nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal total;

    /**
     * Время обновления записи на бирже.
     */
    @Column(name = "external_updated_at")
    private OffsetDateTime externalUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "balance_container_id", nullable = false, insertable = false, updatable = false)
    private BalanceContainerEntity balanceContainer;
}
