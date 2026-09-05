package com.example.tradingbot.domain.command.resolve;

import com.example.tradingbot.domain.model.core.order.Order;
import com.example.tradingbot.domain.model.core.order.external_snapshot.AttachedAlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.resolve.ProtectionHistoryLeg;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

/**
 * Набор фактов, по которому выводится состояние встроенной защиты:
 * полноценного статуса источник ей не отдаёт, и статус приходится
 * ВЫВОДИТЬ. Runtime-объект, не хранимая сущность. См.
 * docs/components/models/AttachedProtectionFacts.md, дом матрицы —
 * docs/lifecycles/Order.md, форма и примеры —
 * docs/spec/order-lifecycle.json.
 */
@Value
@Builder
public class AttachedProtectionFacts {

    /**
     * ПРЕДЪЯВЛЕННЫЙ снапшот защиты — либо элементом attachAlgoOrds в теле
     * родителя, либо самостоятельной условной заявкой цикла добычи. Пусто
     * — не предъявлен. Место, откуда снапшот пришёл, живости не решает:
     * решает факт предъявления.
     */
    AttachedAlgoOrderExternalSnapshot snapshot;

    /** Статус РОДИТЕЛЬСКОЙ заявки — операнд класса состояния родителя. */
    Order.Status parentStatus;

    /**
     * Накопленный налив РОДИТЕЛЯ. Дискриминатор материализации: источник
     * разворачивает защиту в самостоятельную живую заявку ровно на
     * налитый объём. ПУСТО достижимо и нулём НЕ ПОДМЕНЯЕТСЯ — подмена
     * увела бы недобытый факт в CANCEL_BY_PARENT, то есть пометила бы
     * снятой возможно живую защиту.
     */
    BigDecimal parentAccumulatedFillSize;

    /**
     * Снапшот предъявлен САМОСТОЯТЕЛЬНОЙ записью цикла добычи. Такая
     * запись — самостоятельное доказательство материализации, и
     * недобытость налива родителя её не гасит.
     */
    Boolean standaloneRecordFound;

    /**
     * Экспозиция ТРАНША, чью защиту ищет цикл. Обе стороны предиката
     * второй ступени траншевые: инвариант покрытия потраншевый целиком, а
     * «живая позиция» — факт уровня сделки, и на транше без собственной
     * экспозиции её держит СОСЕДНИЙ транш.
     */
    BigDecimal trancheExposure;

    /** Отдельная основная защита ТОГО ЖЕ транша существует. */
    Boolean standaloneProtectionExists;

    /**
     * Нога разбора истории, нашедшая запись; пусто — ни одна не дала.
     * Читается ТОЛЬКО на ветви ANALYSE_HISTORY: на ветви PROTECTION_LOST
     * история не опрашивается вовсе.
     */
    ProtectionHistoryLeg historyLegFound;

    /**
     * Стоящее НАШЕ намерение снятия — непустая причина закрытия,
     * записанная командой снятия до наблюдения факта. Причина write-once,
     * поэтому намерение наблюдением не перезаписывается.
     */
    Boolean cancelIntentStanding;
}
