package com.example.tradingbot.domain.command;

import static java.util.Objects.nonNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted операционная запись ОДНОГО ИСПОЛНЕНИЯ действия в рамках
 * транша (у системных действий уровня сделки — в рамках сделки):
 * на каком шаге это исполнение и какую runtime-сущность оно затрагивает.
 * Несёт идемпотентность, восстановление и бюджет попыток; retry-состояние
 * и поля аудита — от {@link Retryable}.
 *
 * <p><b>Строка = исполнение, не действие.</b> Действие исполняется
 * многократно (переоткрытый эпизод транша, одно объявление на N траншей
 * сетки, циклы добычи); идентичность исполнения — суррогатный ключ.
 *
 * <p><b>Стратегийные и системные исполнения хранятся в двух таблицах</b>,
 * и вид кодируется таблицей, а не колонкой: {@link #actionKind} в схему
 * не персистится. В стратегийной строке обязателен узел стратегии, в
 * системной — тип системного действия.
 *
 * <p>Идемпотентность фиксируется в persistence частичными ключами по
 * живым статусам, и транш входит в ключ ровно настолько, насколько
 * действие потраншевое: у потраншевого ключ — (deal, tranche, episode,
 * действие), у исполнения уровня сделки транша нет ни одного, и ключ
 * вырождается в (deal, действие). Пустой транш участвует в уникальности
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

    /** Сделка, в рамках которой выполняется действие. */
    private Long dealId;

    /**
     * Транш, чьё действие исполняется. Пусто у исполнений УРОВНЯ СДЕЛКИ,
     * и они бывают обоих родов: у системных — агрегатный тип действия, у
     * стратегийных — узел агрегатной поверхности (шаги EXIT и FAIL_SAFE
     * уровня сделки). Выбирать «носителем» один транш из N было бы
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

    /** Вид действия. В схему не персистится — вид кодируется таблицей. */
    private ActionKind actionKind;

    /** Узел стратегии, чьё исполнение отслеживается; обязателен у стратегийных. */
    private Long strategyActionId;

    /** Тип системного действия; обязателен у системных. */
    private SystemActionType systemActionType;

    /**
     * Тип runtime-сущности, которую действие породило/затрагивает.
     * Колонка, а не JSONB: операнд ключа уникальности.
     */
    private TargetEntityType targetEntityType;

    /** Идентификатор этой сущности; пусто, пока она не создана (PLANNED). */
    private Long targetEntityId;

    /** Статус исполнения. */
    private DealActionStateStatus status;

    /** Исполнение системного действия (иначе — стратегийного). */
    public Boolean isSystem() {
        return ActionKind.SYSTEM.equals(actionKind);
    }

    /** Исполнение потраншевое: транш назван, значит уровень объявления — транш. */
    public Boolean isTrancheLevel() {
        return nonNull(dealTrancheId);
    }

    /**
     * Исполнение живо: не терминально и не отменено. Живые строки держат
     * частичный ключ уникальности; завершённые слот не переиспользуют.
     */
    public Boolean isLive() {
        return DealActionStateStatus.PLANNED.equals(status)
                || DealActionStateStatus.CREATED.equals(status)
                || DealActionStateStatus.SUBMITTED.equals(status)
                || DealActionStateStatus.RETRY_PENDING.equals(status);
    }

    /** Цель исполнения заполнена — локальная сущность заведена. */
    public void targetAt(TargetEntityType entityType, Long entityId) {
        this.targetEntityType = entityType;
        this.targetEntityId = entityId;
    }
}
