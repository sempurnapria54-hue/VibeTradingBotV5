package com.example.tradingcore.domain.command.resolve;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;

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
 * <p><b>Пустота живой ноги — не ненайденность, но и не закрытие.</b>
 * Терминала {@code MISSING_AFTER_REFRESH} у позиции не бывает, а закрытием
 * пустой ответ становится только с корроборацией: транзиентная пустота
 * неотличима от закрытия, и ложное закрытие снимает живой риск эпизода со
 * счёта (docs/spec/external-status-resolution.json, величина
 * {@code positionCloseCorroborated}; docs/components/RefreshPositionExecutor.md).
 */
@Component
public class PositionStatusResolver {

    /**
     * Резолв эпизода по добытой позиции и корроборации его закрытия.
     *
     * <p><b>Корроборация — факт источника о ЭТОМ эпизоде</b>: запись его
     * закрытия, добытая историей закрытых позиций по паре эпизода (операнд
     * {@code closeRecordFound}), либо живая нога, отдавшая позицию с другой
     * парой, — смена эпизода сама доказывает, что прежний закрыт. Пусто
     * корроборацией не читается: пустота вела бы к закрытию, то есть в
     * благоприятную сторону.
     *
     * @param fetched           позиция, которую площадка отдала по этому
     *                          эпизоду; {@code null} — не отдала
     * @param closeCorroborated закрытие эпизода корроборировано
     */
    public StatusResolveResult<Position.Status, Position.CloseReason> resolve(Position fetched,
                                                                              Boolean closeCorroborated) {
        if (nonNull(fetched) || isNotTrue(closeCorroborated)) {
            return StatusResolveResult.of(Position.Status.ACTIVE, null);
        }
        return StatusResolveResult.of(Position.Status.CLOSED, Position.CloseReason.EXTERNAL_CLOSE);
    }
}
