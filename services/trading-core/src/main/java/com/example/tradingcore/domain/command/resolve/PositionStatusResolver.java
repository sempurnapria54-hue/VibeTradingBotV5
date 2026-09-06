package com.example.tradingcore.domain.command.resolve;

import static java.util.Objects.isNull;

import com.example.tradingbot.domain.model.core.position.Position;
import com.example.tradingbot.domain.resolve.StatusResolveResult;
import org.springframework.stereotype.Component;

/**
 * Доменный статус эпизода позиции по факту её наличия у площадки.
 *
 * <p><b>Живёт у ядра, а не у коннектора, и это следствие критерия.</b>
 * Сырого статуса у позиции нет вовсе — словаря площадки на входе тоже, —
 * а «не найдено» трактуется вместе с циклом добычи, то есть операндом
 * исполнителя ({@code docs/rules/external-status-resolution.md} §«Где
 * резолвится — сторона выбирается по словарю источника»).
 *
 * <p><b>Пер-источниковой реализации нет намеренно:</b> различать нечего,
 * и интерфейс с единственной реализацией был бы носителем ради формы
 * ({@code .claude/rules/design-simplicity.md}).
 *
 * <p><b>Пустота здесь — нормальный факт закрытия, а не ненайденность.</b>
 * Терминала {@code MISSING_AFTER_REFRESH} у позиции не бывает: её финал
 * объясняется закрытием на бирже
 * ({@code docs/components/PositionStatusResolver.md}).
 */
@Component
public class PositionStatusResolver {

    /** Резолв по добытой позиции; {@code null} — площадка позиции не отдала. */
    public StatusResolveResult<Position.Status, Position.CloseReason> resolve(Position fetched) {
        if (isNull(fetched)) {
            return StatusResolveResult.of(Position.Status.CLOSED, Position.CloseReason.EXTERNAL_CLOSE);
        }
        return StatusResolveResult.of(Position.Status.ACTIVE, null);
    }
}
