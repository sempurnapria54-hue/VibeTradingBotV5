package com.example.tradingbot.domain.command.resolve;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.BooleanUtils.isFalse;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Выводит статус встроенной защиты и кандидата причины закрытия ПО НАБОРУ
 * ФАКТОВ: полноценного статуса источник ей не отдаёт. Дом матриц —
 * docs/lifecycles/Order.md, форма и примеры —
 * docs/spec/order-lifecycle.json (attachedParentClass,
 * attachedOutcomeByParent, attachedBecomesActive, searchExhaustedOutcome,
 * attachedHistoryStatus, attachedHistoryCloseReason).
 *
 * <p>Резолвер источник-агностичен и живёт в домене, а не в per-биржевом
 * пакете: специфика источника кончается на маппинге в снапшот, а решение
 * стои́т на нормализованных фактах. Per-биржевой реализации у него нет
 * намеренно — она не несла бы ни одного биржевого факта.
 *
 * <p>Сущность не сохраняет и решений FSM не принимает; базу триггера не
 * сверяет — расхождение эха с объявленным есть нарушение биржевого
 * инварианта на РОДИТЕЛЬСКОЙ заявке.
 *
 * <p>См. docs/components/AttachedAlgoOrderStateResolver.md.
 */
@Component
public class AttachedAlgoOrderStateResolver {

    /** Класс состояния родителя, различимый политикой встроенной защиты. */
    private enum ParentClass {
        UNCONFIRMED, LIVE, PROBLEM, TERMINAL_FILLED, TERMINAL_EMPTY, TERMINAL_FILL_UNKNOWN
    }

    public AttachedProtectionResolution resolve(AttachedProtectionFacts facts) {
        if (failsToPlace(facts.getSnapshot())) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ERROR,
                    AttachedAlgoOrder.CloseReason.PROTECTION_PLACEMENT_FAILED);
        }
        ParentClass parentClass = parentClass(facts);
        if (Objects.equals(ParentClass.PROBLEM, parentClass)) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ERROR,
                    AttachedAlgoOrder.CloseReason.UNKNOWN);
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
    private Boolean failsToPlace(AttachedAlgoOrderExternalSnapshot snapshot) {
        return nonNull(snapshot) && isNotBlank(snapshot.getFailCode());
    }

    /**
     * Различает не статус сам по себе, а ПАРА «терминален ли родитель» +
     * «каков налив»: до терминала налив исхода не меняет, на терминале он
     * его и определяет. Пустой налив нулём НЕ подменяется.
     */
    private ParentClass parentClass(AttachedProtectionFacts facts) {
        return switch (facts.getParentStatus()) {
            case CREATED, PENDING -> ParentClass.UNCONFIRMED;
            case ACTIVE, PARTIALLY_COMPLETED -> ParentClass.LIVE;
            case ERROR -> ParentClass.PROBLEM;
            case COMPLETED, CANCELED -> terminalClass(facts.getParentAccumulatedFillSize());
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
     * первой ступени. У живого родителя SEARCH_MORE значит «наблюдаем
     * дальше»: защита ещё в его теле, материализовать её нечему, и
     * исчерпание цикла на нём давало бы PROTECTION_LOST на живой защите.
     */
    private Boolean runsSearchCycle(ParentClass parentClass) {
        return Objects.equals(ParentClass.TERMINAL_FILLED, parentClass)
                || Objects.equals(ParentClass.TERMINAL_FILL_UNKNOWN, parentClass);
    }

    /**
     * Живость по факту МАТЕРИАЛИЗАЦИИ, один предикат на обе тропы
     * предъявления: налив родителя ЛИБО предъявленная самостоятельная
     * запись. Присутствие элемента в теле родителя живости не доказывает —
     * он стои́т там и у живого без налива, и у отменённого.
     */
    private AttachedProtectionResolution observedLiveness(AttachedProtectionFacts facts) {
        if (isNull(facts.getSnapshot())) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.PENDING, null);
        }
        Boolean materialized = isTrue(facts.getStandaloneRecordFound())
                || (nonNull(facts.getParentAccumulatedFillSize())
                        && facts.getParentAccumulatedFillSize().signum() > 0);
        return AttachedProtectionResolution.of(
                isTrue(materialized) ? AttachedAlgoOrder.Status.ACTIVE : AttachedAlgoOrder.Status.PENDING, null);
    }

    /**
     * Терминальный родитель: цикл добычи материализованной защиты прошёл.
     * Предъявленная запись живёт (нога живых) либо несёт терминал по
     * нашедшей её ноге разбора; пустая нога живых открывает вторую
     * ступень.
     */
    private AttachedProtectionResolution afterSearchCycle(AttachedProtectionFacts facts) {
        if (nonNull(facts.getHistoryLegFound())) {
            return historyTerminal(facts);
        }
        if (isTrue(facts.getStandaloneRecordFound())) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ACTIVE, null);
        }
        if (protectionLost(facts)) {
            return AttachedProtectionResolution.of(AttachedAlgoOrder.Status.ERROR,
                    AttachedAlgoOrder.CloseReason.PROTECTION_LOST);
        }
        return AttachedProtectionResolution.undetermined();
    }

    /**
     * Вторая ступень: ОБЕ стороны предиката траншевые. Живой риск транша
     * без покрытия — терминал сразу, разбор истории не ждётся: любой её
     * факт на этой ветви оставляет покрытие потерянным. Иначе —
     * ANALYSE_HISTORY, и терминал даёт найденный факт либо пустой разбор.
     */
    private Boolean protectionLost(AttachedProtectionFacts facts) {
        return nonNull(facts.getTrancheExposure())
                && facts.getTrancheExposure().signum() > 0
                && isFalse(facts.getStandaloneProtectionExists());
    }

    /** Исход кодирует НОГА, нашедшая запись; state записи — диагностика. */
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
