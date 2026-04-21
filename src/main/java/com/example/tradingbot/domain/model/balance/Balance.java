package com.example.tradingbot.domain.model.balance;

import com.example.tradingbot.domain.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Баланс аккаунта по одной валюте.
 */
@Getter
@Setter
public class Balance extends Auditable {

    /**
     * Внутренний идентификатор.
     */
    private Long id;

    /**
     * Валюта баланса (например USDT).
     */
    private String currency;

    /**
     * Идентификатор контейнера snapshot аккаунта.
     */
    private Long balanceContainerId;

    /**
     * Доступный баланс.
     */
    private BigDecimal available;

    /**
     * Заблокированный баланс.
     */
    private BigDecimal frozen;

    /**
     * Общий баланс по валюте.
     */
    private BigDecimal total;

    /**
     * Время последнего обновления баланса на бирже.
     */
    private OffsetDateTime externalUpdatedAt;
}
