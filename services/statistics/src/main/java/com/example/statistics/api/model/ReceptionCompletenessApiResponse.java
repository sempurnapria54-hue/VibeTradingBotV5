package com.example.statistics.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * Что сервис объявляет о полноте отданных чисел
 * (docs/spec/durable-reception.json, величины {@code receptionLowerBound},
 * {@code continuityClaimable}).
 *
 * <p><b>Форма едет с ЛЮБОЙ выдачей чисел этого сервиса</b>: число,
 * показанное без своей достоверности, читается как «сейчас», а является
 * проекцией с лагом. Величины — СВОИ, группы статистики, а не журнальные
 * (docs/models/domain/other/StatisticsFact.md §«Состояние приёма и полнота
 * чисел статистики»).
 */
@Getter
@Builder
public class ReceptionCompletenessApiResponse {

    /**
     * Нижняя граница полноты.
     *
     * <p><b>Пусто — значение, а не ноль</b>
     * (docs/rules/absent-value-semantics.md): группа, не наблюдающая ни
     * одной темы, полноты не обещает вовсе. На том же состоянии предикат
     * непрерывности ложен — обе величины говорят об одном состоянии одно
     * и то же.
     */
    @Schema(description = "Факты статистики полны по событиям, произведённым после этого момента; "
            + "пусто — полнота не обещается вовсе")
    private final OffsetDateTime lowerBound;

    @Schema(description = "Непрерывность приёма статистики после нижней границы утверждаема. "
            + "Ложь означает «не утверждаема» — либо найдена дыра, либо не наблюдается ни одна тема")
    private final Boolean continuityClaimable;
}
