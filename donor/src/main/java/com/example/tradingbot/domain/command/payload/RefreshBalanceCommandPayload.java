package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры REFRESH_BALANCE: settle currency (обязательна в валидном
 * snapshot). exchangeId берётся из DealContext. См.
 * docs/components/RefreshBalanceExecutor.md.
 */
@Value
public class RefreshBalanceCommandPayload implements ServiceCommandPayload {

    /** Settle currency (например, USDT). */
    String settleCurrency;
}
