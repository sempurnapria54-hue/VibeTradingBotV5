package com.example.tradingbot.client.service;

import com.example.tradingbot.domain.model.*;

import java.util.List;

public interface ClientService {

    String getName();

    List<Balance> getBalance(Object... args);

    List<Position> getPositions(Object... args);

    List<Order> getOrdersPending(Object... args);

    List<Order> getOrderDetails(Object... args);

    List<AlgoOrder> getOrdersAlgoPending(Object... args);

    List<Order> getOrdersHistory(Object... args);

    List<Order> getOrdersHistoryArchive(Object... args);

    List<TradeFill> getFills(Object... args);

    List<TradeFill> getFillsHistory(Object... args);

    List<TradeFillsArchive> requestFillsArchive(Object... args);

    List<TradeFillsArchive> getFillsArchiveLink(Object... args);

    List<Candle> getCandles(Object... args);

    List<Candle> getHistoryCandles(Object... args);

    List<Order> createOrder(Object... args);

    List<Order> amendOrder(Object... args);

    List<Order> cancelOrder(Object... args);

    List<AlgoOrder> createAlgoOrder(Object... args);

    List<AlgoOrder> cancelAlgoOrder(Object... args);

    List<AlgoOrder> closePosition(Object... args);

    List<Instrument> getInstruments(Object... args);

    List<PriceTicker> getTicker(Object... args);
}
