package com.example.tradingbot.domain.model.snapshot;

import java.util.List;

import com.example.tradingbot.domain.model.AlgoOrder;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.Position;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InstrumentSnapshot {

    /** Идентификатор инструмента (instId), для которого снят срез. */
    private final String externalId;
    /** Количество открытых позиций по инструменту. */
    private final int positionsCount;
    /** Количество активных обычных ордеров по инструменту. */
    private final int ordersCount;
    /** Количество активных algo-ордеров по инструменту. */
    private final int algoOrdersCount;
    /** Список позиций по инструменту. */
    private final List<Position> positions;
    /** Список обычных ордеров по инструменту. */
    private final List<Order> orders;
    /** Список algo-ордеров по инструменту. */
    private final List<AlgoOrder> algoOrders;
}
