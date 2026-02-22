package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.model.okx.request.BalanceRequest;
import com.example.tradingbot.client.model.okx.request.OrdersPendingRequest;
import com.example.tradingbot.client.model.okx.request.PositionsRequest;
import com.example.tradingbot.client.model.okx.response.BalanceResponse;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.*;
import com.example.tradingbot.mapping.BalanceMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.mapping.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.tradingbot.util.Constant.ExchangeNames.EXCHANGE_NAME_OKX;

@Service
@RequiredArgsConstructor
public class OkxClientService implements ClientService {

    private final OkxRestClient okxRestClient;
    private final BalanceMapper balanceMapper;
    private final PositionMapper positionMapper;
    private final OrderMapper orderMapper;

    @Override
    public String getName() {
        return EXCHANGE_NAME_OKX;
    }

    @Override
    public List<Balance> getBalance(Object... args) {
        BalanceRequest request = (BalanceRequest) args[0];
        OkxApiResponse<BalanceResponse> response = okxRestClient.getBalance(request);
        return balanceMapper.clientToDomain(response.getData());
    }

    @Override
    public List<Position> getPositions(Object... args) {
        PositionsRequest request = (PositionsRequest) args[0];
        OkxApiResponse<PositionResponse> response = okxRestClient.getPositions(request);
        return positionMapper.clientToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersPending(Object... args) {
        OrdersPendingRequest request = (OrdersPendingRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersPending(request);
        return orderMapper.clientToDomain(response.getData());
    }

    @Override
    public List<Order> getOrderDetails(Object... args) {
        return List.of();
    }

    @Override
    public List<AlgoOrder> getOrdersAlgoPending(Object... args) {
        return List.of();
    }

    @Override
    public List<Order> getOrdersHistory(Object... args) {
        return List.of();
    }

    @Override
    public List<Order> getOrdersHistoryArchive(Object... args) {
        return List.of();
    }

    @Override
    public List<TradeFill> getFills(Object... args) {
        return List.of();
    }

    @Override
    public List<TradeFill> getFillsHistory(Object... args) {
        return List.of();
    }

    @Override
    public List<TradeFillsArchive> requestFillsArchive(Object... args) {
        return List.of();
    }

    @Override
    public List<TradeFillsArchive> getFillsArchiveLink(Object... args) {
        return List.of();
    }

    @Override
    public List<Candle> getCandles(Object... args) {
        return List.of();
    }

    @Override
    public List<Candle> getHistoryCandles(Object... args) {
        return List.of();
    }

    @Override
    public List<Order> createOrder(Object... args) {
        return List.of();
    }

    @Override
    public List<Order> amendOrder(Object... args) {
        return List.of();
    }

    @Override
    public List<Order> cancelOrder(Object... args) {
        return List.of();
    }

    @Override
    public List<AlgoOrder> createAlgoOrder(Object... args) {
        return List.of();
    }

    @Override
    public List<AlgoOrder> cancelAlgoOrder(Object... args) {
        return List.of();
    }

    @Override
    public List<AlgoOrder> closePosition(Object... args) {
        return List.of();
    }

    @Override
    public List<Instrument> getInstruments(Object... args) {
        return List.of();
    }

    @Override
    public List<PriceTicker> getTicker(Object... args) {
        return List.of();
    }
}
