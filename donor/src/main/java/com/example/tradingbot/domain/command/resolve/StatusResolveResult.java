package com.example.tradingbot.domain.command.resolve;

import lombok.Value;

/**
 * Результат status-resolver'а: доменный статус сущности + candidate
 * причины финализации/problem-state. Executor применяет closeReason
 * write-once (только если текущий == null). RVO. Общий паттерн для
 * Order/AlgoOrder/Position/AttachedAlgoOrder резолверов. См.
 * docs/components/models/PositionStatusResolveResult.md.
 *
 * @param <S> тип доменного статуса сущности
 * @param <C> тип доменного closeReason сущности
 */
@Value
public class StatusResolveResult<S, C> {

    /** Доменный статус сущности. */
    S status;

    /** Candidate причины (nullable); применяется write-once. */
    C closeReason;

    /**
     * Фабрика результата. Явные generics — Lombok-генерация
     * {@code staticConstructor} для generic-класса ломает вывод типа в
     * IDEA при аргументе {@code null} (C не сводится к целевому типу);
     * собственная фабрика этого трения лишена.
     */
    public static <S, C> StatusResolveResult<S, C> of(S status, C closeReason) {
        return new StatusResolveResult<>(status, closeReason);
    }
}
