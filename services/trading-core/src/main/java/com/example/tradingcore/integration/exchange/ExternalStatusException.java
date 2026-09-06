package com.example.tradingcore.integration.exchange;

import com.example.tradingbot.domain.resolve.ExternalStatusReason;
import lombok.Getter;

/**
 * Внешний статус получен, но неизвестен либо означает проблемное
 * состояние.
 *
 * <p><b>Причина приезжает отдельным полем, а не внутри текста</b>, и это
 * не оформление: ядро ставит её причиной закрытия сущности
 * ({@code closeReason = reasonCode},
 * docs/rules/controlled-exchange-exceptions.md). Оставь её только в
 * сообщении для человека — и назначение исхода сделке потребовало бы
 * разбора строки.
 */
@Getter
public class ExternalStatusException extends ControlledExchangeException {

    private final ExternalStatusReason reasonCode;

    public ExternalStatusException(ExternalStatusReason reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }
}
