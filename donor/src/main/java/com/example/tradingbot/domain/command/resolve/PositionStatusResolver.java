package com.example.tradingbot.domain.command.resolve;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import com.example.tradingbot.domain.model.core.position.external_snapshot.PositionExternalSnapshot;

/**
 * Определяет доменный {@link Position.Status} по факту наличия позиции
 * (snapshot / null), а не по строковому external status. Успешный null
 * — нормальный closed-on-exchange факт. Per-биржа реализация. См.
 * docs/components/PositionStatusResolver.md.
 */
public interface PositionStatusResolver {

    StatusResolveResult<Position.Status, Position.CloseReason> resolve(PositionExternalSnapshot snapshot);
}
