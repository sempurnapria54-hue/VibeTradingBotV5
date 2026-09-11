package com.example.auditstatistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Что сервис объявляет о полноте отданных чисел
 * (docs/spec/audit-journal.json, величины {@code journalLowerBound},
 * {@code continuityClaimable}).
 *
 * <p><b>Форма едет с ЛЮБОЙ выдачей чисел этого сервиса</b> — и со
 * строками журнала, и со строками агрегатов: число, показанное без своей
 * достоверности, читается как «сейчас», а является проекцией с лагом
 * (docs/models/domain/other/AuditRecord.md §«Позиция чтения и с какого
 * момента журнал полон»).
 */
@Getter
@Builder
public class JournalCompletenessApiResponse {

    /**
     * Нижняя граница полноты.
     *
     * <p><b>Пусто — значение, а не ноль</b>
     * (docs/rules/absent-value-semantics.md): группа, не наблюдающая ни
     * одной темы, полноты не обещает вовсе. На том же состоянии предикат
     * непрерывности ложен — обе величины говорят об одном состоянии одно
     * и то же.
     */
    @Schema(description = "Журнал полон по событиям, произведённым после этого момента; "
            + "пусто — полнота не обещается вовсе")
    private final OffsetDateTime lowerBound;

    @Schema(description = "Непрерывность журнала после нижней границы утверждаема. "
            + "Ложь означает «не утверждаема» — либо найдена дыра, либо не наблюдается ни одна тема")
    private final Boolean continuityClaimable;
}
