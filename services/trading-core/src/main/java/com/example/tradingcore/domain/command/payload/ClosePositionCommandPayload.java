package com.example.tradingcore.domain.command.payload;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingcore.domain.command.ServiceCommandPayload;
import lombok.Value;

/**
 * Параметры закрытия позиции: цель и запрошенная причина.
 *
 * <p><b>Доли закрытия здесь нет</b> — закрытие всегда полное
 * (docs/rules/no-partial-close.md). Инструмент, сторона и режим маржи
 * приезжают контекстом прохода, а флаг автоотмены заявок — специфика
 * площадки и живёт у коннектора (docs/models/mapping/Position.md).
 */
@Value
public class ClosePositionCommandPayload implements ServiceCommandPayload {

    /** Локальный идентификатор закрываемого эпизода. */
    Long positionId;

    /** Запрошенная причина закрытия; ставится write-once. */
    Position.CloseReason requestedCloseReason;
}
