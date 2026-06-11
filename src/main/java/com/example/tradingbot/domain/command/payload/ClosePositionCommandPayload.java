package com.example.tradingbot.domain.command.payload;

import com.example.tradingbot.domain.command.ServiceCommandPayload;
import com.example.tradingbot.domain.model.core.position.Position;
import lombok.Value;

/**
 * Параметры CLOSE_POSITION: локальный positionId и requested close
 * reason. Всегда full close (no-partial-close); instId/posSide/mgnMode/
 * ccy и autoCxl — из DealContext / adapter, не в payload. См.
 * docs/components/ClosePositionExecutor.md.
 */
@Value
public class ClosePositionCommandPayload implements ServiceCommandPayload {

    /** Локальный id закрываемой позиции. */
    Long positionId;

    /** Requested причина закрытия (CLOSED_BY_STRATEGY / KILL_SWITCH / MANUAL_CLOSE). */
    Position.CloseReason requestedCloseReason;
}
