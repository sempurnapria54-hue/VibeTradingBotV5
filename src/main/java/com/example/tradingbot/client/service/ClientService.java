package com.example.tradingbot.client.service;

import com.example.tradingbot.domain.model.Candle;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.domain.model.Order;
import com.example.tradingbot.domain.model.PriceTicker;
import com.example.tradingbot.domain.model.TradeFill;
import com.example.tradingbot.domain.model.TradeFillsArchive;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;

import java.util.List;

public interface ClientService {

    String getName();

    /**
     * GET.
     */
    //TODO: тут норм, остальное рефакторим.
    BalanceContainerExternalSnapshot getBalanceContainer(Exchange exchange);

    //TODO: тут норм, остальное рефакторим.
    List<PositionExternalSnapshot> getPositionsByInstrument(Instrument instrument);

    //TODO: тут норм, остальное рефакторим.
    List<PositionExternalSnapshot> getAllPositions();

    List<OrderExternalSnapshot> getActiveOrdersByInstrument(Instrument instrument);

    List<OrderExternalSnapshot> getActiveOrdersByInstrumentType(Instrument instrument);

    OrderExternalSnapshot getOrder(String externalInstrumentId, String externalOrderId, String internalOrderId);

    List<OrderExternalSnapshot> getOrdersHistory(Instrument instrument);

    List<OrderExternalSnapshot> getOrdersHistoryArchive(Instrument instrument);


    //TODO: тут норм, остальное рефакторим.
    List<AlgoOrderExternalSnapshot> getActiveAlgoOrders(Instrument instrument, AlgoOrder algoOrder);

    //TODO: тут норм, остальное рефакторим.
    AlgoOrderExternalSnapshot getAlgoOrder(AlgoOrder algoOrder);

    //TODO: тут норм, остальное рефакторим.
    List<AlgoOrderExternalSnapshot> getAlgoOrdersHistory(Instrument instrument, AlgoOrder algoOrder);

    List<TradeFill> getFills(Object... args);

    List<TradeFill> getFillsHistory(Object... args);

    List<TradeFillsArchive> requestFillsArchive(Object... args);

    List<TradeFillsArchive> getFillsArchiveLink(Object... args);

    List<Candle> getCandles(Object... args);

    List<Candle> getHistoryCandles(Object... args);

    List<Order> createOrder(Object... args);

    List<Order> amendOrder(Object... args);

    List<Order> cancelOrder(Order order, String instrumentExternalId);

    List<AlgoOrder> createAlgoOrder(AlgoOrder algoOrder, Instrument instrument, Position position);

    List<AlgoOrder> cancelAlgoOrder(Object... args);

    List<Position> closePosition(Object... args);

    List<Instrument> getInstruments(Object... args);

    List<PriceTicker> getTicker(Object... args);

}
