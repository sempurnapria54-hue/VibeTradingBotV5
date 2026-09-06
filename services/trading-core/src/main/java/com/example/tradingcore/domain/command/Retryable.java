package com.example.tradingcore.domain.command;

import com.example.tradingbot.domain.model.Auditable;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовое состояние повторяемой персистентной операции: счётчик попыток,
 * снимок предела, момент следующей допустимой попытки, последняя ошибка —
 * плюс поля аудита.
 *
 * <p><b>Счётчик — сквозной бюджет отказов ОДНОГО ИСПОЛНЕНИЯ</b>, без
 * обнуления при продвижении стадии. <b>Предел читается живьём</b> по типу
 * текущей команды, а поле здесь — снимок для истории, не операторное
 * значение: правка предела обязана браться сразу везде
 * (docs/components/RetryPolicyService.md §«Авторитет предела — политика,
 * читается живьём»).
 */
@Getter
@Setter
public abstract class Retryable extends Auditable {

    /** Сколько раз операция уже выполнялась: инкремент при отказе. */
    private Integer attemptCount;

    /** Снимок предела попыток на момент записи; авторитет — политика. */
    private Integer maxAttempts;

    /** Момент, не ранее которого допустим следующий повтор (UTC). */
    private OffsetDateTime nextRetryAt;

    /** Последняя зафиксированная ошибка операции. */
    private RetryError lastError;
}
