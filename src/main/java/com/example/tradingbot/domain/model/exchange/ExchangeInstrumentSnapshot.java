package com.example.tradingbot.domain.model.exchange;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExchangeInstrumentSnapshot {

    /** Идентификатор инструмента (instId), для которого снят срез. */
    private final String externalId;
    /** Количество открытых позиций по инструменту. */
    private final int positionsCount;
    /** Количество активных обычных ордеров по инструменту. */
    private final int ordersCount;
    /** Количество активных algo-ордеров по инструменту. */
    private final int algoOrdersCount;
    /** Список позиций по инструменту. */
    private final List<ExchangePosition> positions;
    /** Список обычных ордеров по инструменту. */
    private final List<ExchangeOrder> orders;
    /** Список algo-ордеров по инструменту. */
    private final List<ExchangeAlgoOrder> algoOrders;
}
