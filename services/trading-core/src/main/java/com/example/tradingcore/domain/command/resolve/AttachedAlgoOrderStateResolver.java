package com.example.tradingcore.domain.command.resolve;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.Order;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Выводит статус встроенной защиты и кандидата причины закрытия ПО НАБОРУ
 * ФАКТОВ: полноценного статуса источник ей не отдаёт. Дом матриц —
 * docs/lifecycles/Order.md, форма и примеры — docs/spec/order-lifecycle.json
 * ({@code attachedParentStatus}, {@code attachedParentClass}, {@code attachedOutcomeByParent},
 * {@code attachedBecomesActive}, {@code searchExhaustedOutcome},
 * {@code attachedHistoryStatus}, {@code attachedHistoryCloseReason}).
 *
 * <p><b>Живёт у ядра, а не у коннектора.</b> Словаря площадки на входе
 * нет: код отказа постановки читается ПРИСУТСТВИЕМ, а не значением, — а
 * половина операндов (экспозиция транша, наличие отдельной защиты, наше
 * стоящее намерение, исчерпание цикла) вообще существует только в проходе
 * ядра. Критерий стороны — docs/rules/external-status-resolution.md
 * §«Где резолвится — сторона выбирается по словарю источника».
 *
 * <p>Пер-источниковой реализации нет намеренно: она не несла бы ни одного
 * биржевого факта.
 *
 * <p>Сущность не сохраняет и решений FSM не принимает; ценовую базу
 * триггера не сверяет — расхождение эха с объявленным есть нарушение
 * биржевого инварианта на РОДИТЕЛЬСКОЙ заявке, а не статус защиты
 * (docs/components/AttachedAlgoOrderStateResolver.md).
 */
@Component
public class AttachedAlgoOrderStateResolver {

    /**
     * Наблюдённые статусы родителя в {@code ERROR}, которые класс читает
     * вместо пометки ({@code attachedParentStatus}).
     */
    private static final Set<Order.Status> OBSERVABLE_PARENT_STATUSES = EnumSet.of(Order.Status.ACTIVE,
            Order.Status.PARTIALLY_COMPLETED, Order.Status.COMPLETED, Order.Status.CANCELED);

    /** Класс состояния родителя, различимый политикой встроенной защиты. */
    private enum ParentClass {
        UNCONFIRMED, LIVE, PROBLEM, TERMINAL_FILLED, TERMINAL_EMPTY, TERMINAL_FILL_UNKNOWN
    }

    /**
     * Запускается ли цикл добычи материализованной защиты для родителя в
     * таком состоянии. Предикат публичен затем, чтобы добытчик фактов не
     * заводил СВОЮ копию гейта: гейт один, и живёт он здесь — иначе
     * добытчик ходил бы к источнику там, где решение на ответ не смотрит,
     * либо молчал бы там, где ответ решению нужен.
     *
     * <p>Класс читается по тому же статусу, что и в резолве
     * ({@code attachedParentStatus}): нога в {@code ERROR}, наблюдённая
     * исполненной, свою защиту материализовала, и цикл её ищет; наблюдённая
     * живой — класс живого родителя, и цикл не запускается. Нога в
     * {@code ERROR}, которую полный цикл не нашёл, читается терминальной по
     * наливу: при непустом либо недобытом наливе цикл её защиту ищет.
     *
     * @param parentObservedStatus статус снапшота родителя этой добычей;
     *                             пусто — снапшот не получен
     * @param parentExternalLive   наблюдённая живость родителя; ложь у
     *                             родителя в {@code ERROR} без наблюдённого
     *                             статуса — полный цикл его не нашёл
     */
    public Boolean runsSearchCycle(Order.Status parentStatus, Order.Status parentObservedStatus,
                                   Boolean parentExternalLive, BigDecimal parentAccumulatedFillSize) {
        if (isNull(parentStatus)) {
            return false;
        }
        return runsSearchCycle(parentClass(attachedParentStatus(parentStatus, parentObservedStatus),
                parentExternalLive, parentAccumulatedFillSize));
    }

    /**
     * Статус, по которому читается класс родителя
     * (docs/spec/order-lifecycle.json, {@code attachedParentStatus}).
     *
     * <p><b>У родителя в {@code ERROR} — статус, который добыча показала на
     * площадке</b>: живой, частично исполненный, исполненный либо снятый.
     * Пометка ошибки — наше safety-состояние, поставленное невозможностью
     * интерпретировать факт, а не факт площадки, и судьбы защиты она не
     * несёт: площадка, показавшая налив помеченной ноги, материализовала
     * защиту самостоятельной живой заявкой, а класс проблемного увёл бы её в
     * неживые — и снятие риска её бы не сняло. Статус самого родителя при
     * этом не меняется: рёбер из {@code ERROR} матрица не содержит.
     *
     * <p><b>Наблюдённый живой статус читается тем же правилом</b> — класс
     * живого родителя: защита остаётся в его теле и наблюдается дальше, а не
     * уходит в {@code ERROR}. Иначе, когда снятие риска отменит помеченную
     * ногу с наливом, площадка материализует защиту живой заявкой, а модель
     * держала бы её неживой.
     *
     * <p>Без наблюдённого статуса пометка остаётся, и класс по ней выводит
     * наблюдённая живость родителя (класс родителя ниже). Прочим родителям —
     * локальный статус: у них он и есть последний применённый факт.
     */
    private Order.Status attachedParentStatus(Order.Status parentStatus, Order.Status parentObservedStatus) {
        if (Order.Status.ERROR.equals(parentStatus) && OBSERVABLE_PARENT_STATUSES.contains(parentObservedStatus)) {
            return parentObservedStatus;
        }
        return parentStatus;
    }

    /**
     * Потеряно ли покрытие, когда ни живой записи защиты, ни записи в
     * истории нет: живой риск транша без отдельной защиты того же транша, и
     * исчезновение защиты не объяснено НАШИМ намерением снятия.
     *
     * <p><b>Читается ПОСЛЕ разбора истории, а не вместо него:</b> защита,
     * сработавшая у площадки, из живых уходит так же, как пропавшая, а
     * экспозиция транша до наблюдения срабатывания стоит налитой — без
     * разбора сработавший стоп читался бы потерянным покрытием
     * (docs/lifecycles/Order.md §«Исход ненайденности — вторая ступень»).
     *
     * <p><b>Стоящее намерение ветвь закрывает:</b> защиту, которую сняли мы,
     * пропавшей не читают. Экспозиция транша при этом может ещё стоять
     * налитой: закрытие позиции вне окна атрибуции траншу не приписано.
     *
     * <p>Пустой признак намерения читается ОТСУТСТВИЕМ намерения: пустота
     * не открывает ветви, которая снимает тревогу.
     */
    private Boolean coverageLost(BigDecimal trancheExposure, Boolean standaloneProtectionExists,
                                 Boolean cancelIntentStanding) {
        return nonNull(trancheExposure)
                && trancheExposure.signum() > 0
                && isFalse(standaloneProtectionExists)
                && isNotTrue(cancelIntentStanding);
    }

    /**
     * Состояние защиты по предъявленным фактам.
     *
     * <p><b>Пустой статус родителя — отсутствие факта, а не факт</b>, и
     * читается так же, как в публичном гейте цикла: класс родителя не
     * выводится, исход не определён, статус защиты не двигается
     * (docs/components/AttachedAlgoOrderStateResolver.md §Границы). Отказ
     * постановки проверяется раньше — это свой факт, и статус родителя его
     * не отменяет.
     *
     * <p><b>Родитель без наблюдения защиту не двигает</b> — исход «ждать»
     * (docs/spec/order-lifecycle.json, {@code attachedOutcomeByParent}): о
     * родителе площадка не показала ничего, судьба защиты не выводится ни из
     * чего, и защита остаётся в прежнем состоянии — в множестве живых, где
     * снятие риска её держит. Ошибочный исход увёл бы в неживые защиту, чья
     * живость на площадке не исключена, а поля наблюдения у встроенной
     * защиты нет (docs/lifecycles/Order.md §«Судьба встроенной защиты по
     * фактам родителя»).
     */
    public AttachedProtectionResolution resolve(AttachedProtectionFacts facts) {
        if (isTrue(failsToPlace(facts.getObserved()))) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ERROR,
                    AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED);
        }
        if (isNull(facts.getParentStatus())) {
            return AttachedProtectionResolution.undetermined();
        }
        ParentClass parentClass = parentClass(facts);
        if (Objects.equals(ParentClass.PROBLEM, parentClass)) {
            return AttachedProtectionResolution.waiting();
        }
        if (Objects.equals(ParentClass.TERMINAL_EMPTY, parentClass)) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.CANCELED,
                    AttachedAlgoOrder.CloseReason.PARENT_ORDER_CANCELED);
        }
        if (isFalse(runsSearchCycle(parentClass))) {
            return observedLiveness(facts);
        }
        return afterSearchCycle(facts);
    }

    /**
     * Заполненный код отказа означает, что заявка на бирже не встала.
     * Проверяется РАНЬШЕ класса родителя: отказ постановки — свой факт, и
     * состояние родителя его не отменяет.
     */
    private Boolean failsToPlace(AttachedAlgoOrder observed) {
        return nonNull(observed) && isNotBlank(observed.getFailCode());
    }

    /**
     * Различает не статус сам по себе, а ПАРУ «терминален ли родитель» +
     * «каков налив»: до терминала налив исхода не меняет, на терминале он
     * его и определяет. Пустой налив нулём НЕ подменяется. Статус — тот,
     * по которому класс читается ({@code attachedParentStatus}), а не
     * локальный статус родителя.
     */
    private ParentClass parentClass(AttachedProtectionFacts facts) {
        return parentClass(attachedParentStatus(facts.getParentStatus(), facts.getParentObservedStatus()),
                facts.getParentExternalLive(), facts.getParentAccumulatedFillSize());
    }

    /**
     * Класс по статусу, в котором он читается
     * (docs/spec/order-lifecycle.json, {@code attachedParentClass}).
     *
     * <p><b>Родитель в {@code ERROR}, которого полный цикл добычи НЕ НАШЁЛ,
     * читается терминальным классом по наливу</b>: на площадке его нет, и
     * налива он больше не наберёт — та же посылка, что у неотправленной ноги
     * (принятая площадкой заявка видна поиску). Прочитанный проблемным, он
     * уводил бы защиту, возможно материализованную площадкой живой записью,
     * мимо поиска. Класс {@code PROBLEM} остаётся за родителем, о котором
     * площадка не показала ничего: наблюдённая живость пуста либо истинна —
     * пустота нежилостью не читается.
     */
    private ParentClass parentClass(Order.Status parentStatus, Boolean parentExternalLive,
                                    BigDecimal parentAccumulatedFillSize) {
        return switch (parentStatus) {
            case CREATED, PENDING -> ParentClass.UNCONFIRMED;
            case ACTIVE, PARTIALLY_COMPLETED -> ParentClass.LIVE;
            case ERROR -> isFalse(parentExternalLive)
                    ? terminalClass(parentAccumulatedFillSize)
                    : ParentClass.PROBLEM;
            case COMPLETED, CANCELED -> terminalClass(parentAccumulatedFillSize);
        };
    }

    private ParentClass terminalClass(BigDecimal parentAccumulatedFillSize) {
        if (isNull(parentAccumulatedFillSize)) {
            return ParentClass.TERMINAL_FILL_UNKNOWN;
        }
        return parentAccumulatedFillSize.signum() > 0
                ? ParentClass.TERMINAL_FILLED
                : ParentClass.TERMINAL_EMPTY;
    }

    /**
     * Гейт запуска цикла добычи — ТЕРМИНАЛЬНОСТЬ родителя, а не исход
     * первой ступени. У живого родителя «искать дальше» значит «наблюдаем
     * дальше»: защита ещё в его теле, материализовать её нечему, и
     * исчерпание цикла на нём давало бы потерянное покрытие на живой
     * защите.
     */
    private Boolean runsSearchCycle(ParentClass parentClass) {
        return Objects.equals(ParentClass.TERMINAL_FILLED, parentClass)
                || Objects.equals(ParentClass.TERMINAL_FILL_UNKNOWN, parentClass);
    }

    /**
     * Живость по факту МАТЕРИАЛИЗАЦИИ (docs/spec/order-lifecycle.json,
     * {@code attachedBecomesActive}): доказательство одно — самостоятельная
     * запись, найденная циклом добычи. Присутствие элемента в теле родителя
     * живости не доказывает — он стои́т там и у живого без налива, и у
     * отменённого.
     *
     * <p><b>Налив родителя доказательством не является.</b> Площадка ставит
     * встроенную защиту только на терминале родителя с наливом; у живого
     * родителя её нет и при частичном наливе, и прежний дизъюнкт по наливу
     * срабатывал ровно там, где факт его опровергает. Защита живого
     * частично налитого родителя остаётся {@code PENDING}, а её покрытие
     * засчитывается отложенным (docs/rules/live-risk-protection.md;
     * .claude/decisions/attached-protection-deferred-coverage.md). Цикл
     * добычи на нетерминальном родителе не запускается, поэтому здесь
     * исход — постановка по построению.
     */
    private AttachedProtectionResolution observedLiveness(AttachedProtectionFacts facts) {
        boolean materialized = nonNull(facts.getObserved()) && isTrue(facts.getStandaloneRecordFound());
        return AttachedProtectionResolution.of(
                materialized ? AttachedAlgoOrder.Status.ACTIVE : AttachedAlgoOrder.Status.PENDING, null);
    }

    /**
     * Терминальный родитель: цикл добычи материализованной защиты прошёл
     * (docs/spec/order-lifecycle.json, {@code searchExhaustedOutcome}).
     * Предъявленная запись живёт (нога живых) либо несёт терминал по
     * нашедшей её ноге разбора; пустой разбор на терминале, впервые
     * показанном этой добычей, — ожидание; на терминале, наблюдённом
     * раньше, — потерянное покрытие на живом непокрытом риске транша и
     * неопределённый исход на прочих.
     *
     * <p><b>Ожидание на первом наблюдении терминала.</b> Площадка ставит
     * защиту НА терминале родителя, то есть после него, а цикл запускает та
     * же добыча, что терминал впервые увидела: пустота на ней мерит задержку
     * постановки, а не судьбу защиты. Защита не двигается и сигнала нет —
     * она остаётся в постановке, её покрытие засчитывается, и вывод делает
     * следующая добыча того же родителя. Найденная нога разбора и живая
     * запись применяются и на первом наблюдении: запись есть факт, и гонки у
     * неё нет (.claude/decisions/protection-lost-needs-prior-terminal.md).
     *
     * <p>Пустой операнд «терминал наблюдён раньше» ожиданием не читается:
     * пустота не открывает ветви, которая снимает тревогу.
     */
    private AttachedProtectionResolution afterSearchCycle(AttachedProtectionFacts facts) {
        if (nonNull(facts.getHistoryLegFound())) {
            return historyTerminal(facts);
        }
        if (isTrue(facts.getStandaloneRecordFound())) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ACTIVE, null);
        }
        if (isFalse(facts.getParentTerminalObservedBefore())) {
            return AttachedProtectionResolution.waiting();
        }
        if (isTrue(coverageLost(facts.getTrancheExposure(), facts.getStandaloneProtectionExists(),
                facts.getCancelIntentStanding()))) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ERROR,
                    AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
        }
        return AttachedProtectionResolution.undetermined();
    }

    /** Исход кодирует НОГА, нашедшая запись; сырой статус записи — диагностика. */
    private AttachedProtectionResolution historyTerminal(AttachedProtectionFacts facts) {
        return switch (facts.getHistoryLegFound()) {
            case EFFECTIVE -> AttachedProtectionResolution.of(AttachedAlgoOrder.Status.COMPLETED,
                    AttachedAlgoOrder.CloseReason.TRIGGERED);
            case CANCELED -> AttachedProtectionResolution.of(AttachedAlgoOrder.Status.CANCELED,
                    isTrue(facts.getCancelIntentStanding())
                            ? AttachedAlgoOrder.CloseReason.SWITCHED_BY_STRATEGY
                            : AttachedAlgoOrder.CloseReason.UNKNOWN);
            case ORDER_FAILED -> AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ERROR,
                    AttachedAlgoOrder.CloseReason.PROTECTION_TRIGGER_FAILED);
        };
    }
}
