package com.example.tradingbot.domain.command;

import com.example.tradingbot.domain.model.Auditable;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Базовое состояние persisted-операции: счётчик попыток, лимит, момент
 * следующего допустимого повтора, последняя ошибка — плюс поля аудита от
 * {@link Auditable}. Строка исполнения живёт в БД, поэтому «кто и когда
 * её завёл» она несёт наравне с прочими персистентными моделями
 * (.claude/rules/codestyle.md §«Auditable по слоям»).
 *
 * <p><b>Счётчик — сквозной бюджет отказов одного исполнения</b>, без
 * обнуления при продвижении стадии; предел читается живьём по типу
 * текущей команды. Механика повтора —
 * docs/components/RetryPolicyService.md.
 */
@Getter
@Setter
public abstract class Retryable extends Auditable {

    /** Сколько раз операция уже выполнялась (инкремент при падении executor'а). */
    private Integer attemptCount;

    /** Максимум попыток до перевода в FAILED. */
    private Integer maxAttempts;

    /** Момент, не ранее которого допустим следующий повтор (UTC). */
    private OffsetDateTime nextRetryAt;

    /** Последняя зафиксированная ошибка операции. */
    private RetryError lastError;
}
