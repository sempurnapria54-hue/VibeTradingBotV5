package com.example.tradingbot.domain.command;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted операционное состояние исполнения одного StrategyAction в
 * рамках Deal: на каком шаге исполнения находится action и какую
 * runtime-сущность он породил. Единственный держатель связи
 * StrategyAction ↔ Order/AlgoOrder/Position (сами они strategyActionId
 * не хранят). Несёт идемпотентность/recovery/retry command-layer'а;
 * retry-состояние наследует от Retryable.
 *
 * <p>Идемпотентность фиксируется в persistence частичными ключами по
 * живым статусам, и транш входит в ключ ровно настолько, насколько
 * действие потраншевое: у потраншевого ключ — (deal, tranche, action),
 * у агрегатного (шаг уровня сделки) транша нет ни одного, и ключ
 * вырождается в (deal, action). Пустой транш участвует в уникальности
 * как пустой, а не как разделитель
 * (docs/rules/idempotency-via-unique.md).
 *
 * <p>См. docs/models/domain/other/DealActionState.md,
 * docs/lifecycles/DealActionState.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class DealActionState extends Retryable {

    /** Внутренний идентификатор в БД. */
    private Long id;

    /** Сделка, в рамках которой выполняется action. */
    private Long dealId;

    /**
     * Транш, чьё действие исполняется. Пусто у исполнений УРОВНЯ СДЕЛКИ
     * (агрегатная поверхность стратегии — шаги EXIT и FAIL_SAFE): у них
     * транша нет ни одного, и выбирать «носителем» один из N было бы
     * произволом — выход есть утверждение обо всех траншах сразу.
     */
    private Long dealTrancheId;

    /**
     * Номер эпизода транша на момент заведения строки. Операнд области
     * «эпизод»: переоткрытие идёт тем же траншем, поэтому без него строки
     * прошлого эпизода неотличимы от строк текущего. Пусто там же, где
     * пуст транш.
     */
    private Integer trancheEpisodeSeq;

    /** Действие стратегии, чьё исполнение отслеживается. */
    private Long strategyActionId;

    /** Куда нацелено действие; null, пока сущность не создана (PLANNED). Персистится jsonb. */
    private RuntimeTarget target;

    /** Статус исполнения action. */
    private DealActionStateStatus status;
}
