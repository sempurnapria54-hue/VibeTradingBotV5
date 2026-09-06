package com.example.tradingcore.domain.command;

import static java.util.Objects.nonNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Операционная запись ОДНОГО ИСПОЛНЕНИЯ действия в рамках транша (у
 * системных действий уровня сделки — в рамках сделки): на каком шаге это
 * исполнение и какую runtime-сущность оно затрагивает. Несёт
 * идемпотентность, восстановление и бюджет попыток.
 *
 * <p><b>Строка = исполнение, не действие.</b> Действие исполняется
 * многократно — переоткрытый эпизод транша, одно объявление на N траншей
 * сетки, циклы добычи; идентичность исполнения — суррогатный ключ, а
 * строки копятся: завершённое состояние жёстко терминально, слот не
 * переиспользуется.
 *
 * <p><b>Единственный держатель связи «действие стратегии —
 * runtime-сущность»:</b> заявки и позиции ссылок на действие не хранят.
 *
 * <p><b>Стратегийные и системные исполнения хранятся в двух таблицах</b>,
 * и вид кодируется таблицей, а не колонкой: {@link #actionKind} в схему
 * не персистится. Вместе с nullable-колонкой рода исчезает и
 * неоднозначность ключа.
 *
 * <p><b>Стадия исполнения выводится из подтверждённых фактов и полем не
 * хранится:</b> явная стадия дублировала бы факты и протухала на
 * рестарте. Курсора пройденных звеньев нет.
 *
 * <p>См. docs/models/domain/other/DealActionState.md,
 * docs/lifecycles/DealActionState.md.
 */
@Getter
@Setter
@NoArgsConstructor
public class DealActionState extends Retryable {

    /** Идентичность исполнения. */
    private Long id;

    /** Сделка-агрегат. */
    private Long dealId;

    /**
     * Транш, чьё действие исполняется. Пусто у исполнений УРОВНЯ СДЕЛКИ,
     * и они бывают обоих родов: у системных — агрегатный тип действия, у
     * стратегийных — узел агрегатной поверхности. Выбирать «носителем»
     * один транш из N нельзя: выход есть утверждение обо всех траншах
     * сразу.
     */
    private Long dealTrancheId;

    /**
     * Номер эпизода транша на момент заведения строки. Операнд области
     * «эпизод»: переоткрытие идёт ТЕМ ЖЕ траншем, поэтому без него строки
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

    /** Тип runtime-сущности, которую исполнение породило либо затрагивает. */
    private TargetEntityType targetEntityType;

    /** Идентификатор этой сущности; пусто, пока она не создана. */
    private Long targetEntityId;

    /** Статус исполнения. */
    private DealActionStateStatus status;

    /** Исполнение системного действия; иначе стратегийного. */
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

    /** Заполнить цель исполнения: локальная сущность заведена. */
    public void targetAt(TargetEntityType entityType, Long entityId) {
        this.targetEntityType = entityType;
        this.targetEntityId = entityId;
    }
}
