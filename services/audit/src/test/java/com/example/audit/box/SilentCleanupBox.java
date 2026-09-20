package com.example.audit.box;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Общее у трёх клеток группы {@code B7}, которым входом служит САМА ось
 * конфигурации: неограниченный профиль хранения, недоехавшая ось и снятый
 * выключатель (.claude/tests/cases/audit.md §«B7 — Чистка журнала:
 * применимость из оси, глубина из конфигурации»).
 *
 * <p><b>Класс у каждой из трёх свой, и объединить их нельзя:</b> положение
 * осей есть часть ключа кэша контекста, и сдвинуть его внутри класса
 * нечем. Общим остаётся ВХОД — состояние, на котором исправный проход
 * сделал бы обе свои работы.
 *
 * <p><b>Вход обязан быть ПРЕДМЕТОМ прохода, иначе отрицание сходится
 * само.</b> Журнал несёт строку за глубиной и строку внутри неё, момент
 * наблюдения пары лежит позади обоих моментов приёма, а момент разрыва —
 * между старой границей и той, которую создало бы удаление. На таком
 * состоянии исправный проход удалил бы строку и погасил бы разрыв
 * ({@code B7.7}); отрицания «не удалено» и «не погашено» поэтому говорят
 * о прохождении, а не о расстановке.
 *
 * <p><b>Своя группа и свои темы — у каждой</b>
 * ({@link AuditSubstrate#registerOwn}): группа есть состояние на брокере,
 * и контекст со своим именем читал бы чужую тему с начала.
 */
abstract class SilentCleanupBox extends AuditBox {

    /** Насколько глубже назначенной глубины лежит строка, которую уносил бы проход. */
    protected static final Duration BEYOND_DEPTH = Duration.ofDays(60);

    /** Возраст строки, за глубину не выходящей: ею двигалась бы граница. */
    protected static final Duration WITHIN_DEPTH = Duration.ofHours(1);

    /** Возраст события, с которым клетки ходят в тему. */
    protected static final Duration EVENT_AGE = Duration.ofMinutes(5);

    /** Насколько раньше «сейчас» пара наблюдает свою тему. */
    protected static final Duration OBSERVED_AGO = Duration.ofHours(2);

    /** Возраст разрыва: старая граница его не достаёт, новая достала бы. */
    protected static final Duration GAP_AGO = Duration.ofMinutes(90);

    /**
     * Предупреждение о недоехавшей оси — единственный наблюдаемый след, чем
     * дефект развёртывания отличим от назначенного значения.
     *
     * <p><b>Объявлено здесь одно на три клетки:</b> две утверждают о его
     * отсутствии, одна — о наличии, и три записи одного литерала разошлись бы
     * первой же правкой текста (.claude/rules/carrier-levels.md).
     */
    protected static final String AXIS_WARNING = "Journal retention profile is not configured";

    /**
     * Ставит состояние, на котором исправный проход и удалил бы строку, и
     * погасил бы разрыв.
     *
     * @return момент разрыва — им клетка читает обе стороны отрицания
     */
    protected OffsetDateTime givenWorkForThePass() {
        String topic = subscription().getFirst();
        publish(topic, "E-AGED", momentsAgo(BEYOND_DEPTH), Bodies.reference());
        publish(topic, "E-SURVIVING", momentsAgo(EVENT_AGE), Bodies.reference());
        awaitRecordCount(2L);
        awaitConsumed(topic);
        recordedEarlier("E-AGED", momentsAgo(BEYOND_DEPTH));
        recordedEarlier("E-SURVIVING", momentsAgo(WITHIN_DEPTH));
        OffsetDateTime gapAt = momentsAgo(GAP_AGO);
        givenPair(topic, momentsAgo(OBSERVED_AGO), Boolean.TRUE, Boolean.FALSE, gapAt, null);
        return gapAt;
    }
}
