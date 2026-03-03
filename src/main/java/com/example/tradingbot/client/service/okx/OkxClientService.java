package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.model.okx.request.*;
import com.example.tradingbot.client.model.okx.response.*;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.*;
import com.example.tradingbot.mapping.*;
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
    private final AlgoOrderMapper algoOrderMapper;
    private final TradeFillMapper tradeFillMapper;
    private final TradeFillsArchiveMapper tradeFillsArchiveMapper;
    private final CandleMapper candleMapper;
    private final InstrumentMapper instrumentMapper;
    private final PriceTickerMapper priceTickerMapper;

    @Override
    public String getName() {
        return EXCHANGE_NAME_OKX;
    }

    @Override
    public List<Balance> getBalance(Object... args) {
        Balance balance = (Balance) args[0];
        BalanceRequest request = balanceMapper.domainToClientOkxRequest(balance);
        OkxApiResponse<BalanceResponse> response = okxRestClient.getBalance(request);
        return balanceMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Position> getPositions(Object... args) {
        Position position = (Position) args[0];
        PositionsRequest request = positionMapper.domainToClientOkxPositionsRequest(position);
        OkxApiResponse<PositionResponse> response = okxRestClient.getPositions(request);
        return positionMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersPending(Object... args) {
        Order order = (Order) args[0];
        OrdersPendingRequest request = orderMapper.domainToClientOkxOrdersPendingRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersPending(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrderDetails(Object... args) {
        Order order = (Order) args[0];
        OrderDetailsRequest request = orderMapper.domainToClientOkxOrderDetailsRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrderDetails(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> getOrdersAlgoPending(Object... args) {
        AlgoOrder algoOrder = (AlgoOrder) args[0];
        OrdersAlgoPendingRequest request = algoOrderMapper.domainToClientOkxOrdersAlgoPendingRequest(algoOrder);
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.getOrdersAlgoPending(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersHistory(Object... args) {
        Order order = (Order) args[0];
        OrdersHistoryRequest request = orderMapper.domainToClientOkxOrdersHistoryRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistory(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersHistoryArchive(Object... args) {
        Order order = (Order) args[0];
        OrdersHistoryRequest request = orderMapper.domainToClientOkxOrdersHistoryRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistoryArchive(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFill> getFills(Object... args) {
        TradeFill tradeFill = (TradeFill) args[0];
        FillsRequest request = tradeFillMapper.domainToClientOkxRequest(tradeFill);
        OkxApiResponse<TradeFillResponse> response = okxRestClient.getFills(request);
        return tradeFillMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFill> getFillsHistory(Object... args) {
        TradeFill tradeFill = (TradeFill) args[0];
        FillsRequest request = tradeFillMapper.domainToClientOkxRequest(tradeFill);
        OkxApiResponse<TradeFillResponse> response = okxRestClient.getFillsHistory(request);
        return tradeFillMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFillsArchive> requestFillsArchive(Object... args) {
        TradeFillsArchive tradeFillsArchive = (TradeFillsArchive) args[0];
        FillsArchiveRequest request = tradeFillsArchiveMapper.domainToClientOkxRequest(tradeFillsArchive);
        OkxApiResponse<TradeFillsArchiveResponse> response = okxRestClient.requestFillsArchive(request);
        return tradeFillsArchiveMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFillsArchive> getFillsArchiveLink(Object... args) {
        TradeFillsArchive tradeFillsArchive = (TradeFillsArchive) args[0];
        FillsArchiveLinkRequest request = tradeFillsArchiveMapper.domainToClientOkxLinkRequest(tradeFillsArchive);
        OkxApiResponse<TradeFillsArchiveResponse> response = okxRestClient.getFillsArchiveLink(request);
        return tradeFillsArchiveMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Candle> getCandles(Object... args) {
        Candle candle = (Candle) args[0];
        CandlesRequest request = candleMapper.domainToClientOkxRequest(candle);
        OkxApiResponse<CandleResponse> response = okxRestClient.getCandles(request);
        return candleMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Candle> getHistoryCandles(Object... args) {
        Candle candle = (Candle) args[0];
        CandlesRequest request = candleMapper.domainToClientOkxRequest(candle);
        OkxApiResponse<CandleResponse> response = okxRestClient.getHistoryCandles(request);
        return candleMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> createOrder(Object... args) {
        Order order = (Order) args[0];
        CreateOrderRequest request = orderMapper.domainToClientOkxRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.createOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> amendOrder(Object... args) {
        Order order = (Order) args[0];
        AmendOrderRequest request = orderMapper.domainToClientOkxAmendRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.amendOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> cancelOrder(Object... args) {
        Order order = (Order) args[0];
        CancelOrderRequest request = orderMapper.domainToClientOkxCancelRequest(order);
        OkxApiResponse<OrderResponse> response = okxRestClient.cancelOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> createAlgoOrder(Object... args) {
        AlgoOrder algoOrder = (AlgoOrder) args[0];
        CreateAlgoOrderRequest request = algoOrderMapper.domainToClientOkxRequest(algoOrder);
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.createAlgoOrder(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> cancelAlgoOrder(Object... args) {
        AlgoOrder algoOrder = (AlgoOrder) args[0];
        CancelAlgoOrderRequest request = algoOrderMapper.domainToClientOkxCancelRequest(algoOrder);
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.cancelAlgoOrder(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Position> closePosition(Object... args) {
        Position position = (Position) args[0];
        ClosePositionRequest request = positionMapper.domainToClientOkxRequest(position);
        OkxApiResponse<PositionResponse> response = okxRestClient.closePosition(request);
        return positionMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Instrument> getInstruments(Object... args) {
        Instrument instrument = (Instrument) args[0];
        InstrumentsRequest request = instrumentMapper.domainToClientOkxRequest(instrument);
        OkxApiResponse<InstrumentResponse> response = okxRestClient.getInstruments(request);
        return instrumentMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<PriceTicker> getTicker(Object... args) {
        PriceTicker priceTicker = (PriceTicker) args[0];
        TickerRequest request = priceTickerMapper.domainToClientOkxRequest(priceTicker);
        OkxApiResponse<PriceTickerResponse> response = okxRestClient.getTicker(request);
        return priceTickerMapper.clientOkxResponseToDomain(response.getData());
    }
}
