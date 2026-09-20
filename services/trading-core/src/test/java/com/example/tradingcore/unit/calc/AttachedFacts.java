package com.example.tradingcore.unit.calc;

import com.example.tradingbot.domain.model.core.order.AttachedAlgoOrder;
import com.example.tradingcore.domain.command.resolve.AttachedProtectionFacts;

/**
 * Набор фактов встроенной защиты — сборка групп {@code U10} и {@code U11}
 * документа `.claude/tests/cases/trading-core-calc.md`.
 *
 * <p>Полноценного статуса источник защите не отдаёт, и статус
 * ВЫВОДИТСЯ: сборка собирает ровно те операнды, по которым он выводится,
 * — предъявленную защиту, статус и налив родителя, признак
 * самостоятельной записи, экспозицию транша, признак отдельной защиты,
 * ногу разбора истории и стоящее намерение снятия.
 */
final class AttachedFacts {

    private AttachedFacts() {
    }

    /** Пустой набор: ни предъявленной защиты, ни фактов цикла добычи. */
    static AttachedProtectionFacts.AttachedProtectionFactsBuilder facts() {
        return AttachedProtectionFacts.builder();
    }

    /** Предъявленная защита без кода отказа постановки. */
    static AttachedAlgoOrder observed() {
        AttachedAlgoOrder protection = new AttachedAlgoOrder();
        protection.setId(11L);
        protection.setInternalId("ap-0001");
        protection.setType(AttachedAlgoOrder.Type.ATTACHED_STOP_LOSS);
        return protection;
    }

    /** Предъявленная защита с непустым кодом отказа постановки: на бирже она не встала. */
    static AttachedAlgoOrder observedFailingToPlace(String failCode) {
        AttachedAlgoOrder protection = observed();
        protection.setFailCode(failCode);
        return protection;
    }
}
