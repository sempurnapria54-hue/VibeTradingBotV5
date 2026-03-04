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
        String instrumentExternalId = (String) args[1];
        String instrumentType = (String) args[2];

        PositionsRequest request = positionMapper.domainToClientOkxPositionsRequest(position);
        request.setInstrumentId(instrumentExternalId);
        request.setInstrumentType(instrumentType);

        OkxApiResponse<PositionResponse> response = okxRestClient.getPositions(request);
        return positionMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersPending(Object... args) {
        Order order = (Order) args[0];
        String instrumentExternalId = (String) args[1];
        String instrumentType = (String) args[2];

        OrdersPendingRequest request = orderMapper.domainToClientOkxOrdersPendingRequest(order);
        request.setInstrumentId(instrumentExternalId);
        request.setInstrumentType(instrumentType);

        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersPending(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrderDetails(Object... args) {
        Order order = (Order) args[0];
        String instrumentExternalId = (String) args[1];

        OrderDetailsRequest request = orderMapper.domainToClientOkxOrderDetailsRequest(order);
        request.setInstrumentId(instrumentExternalId);

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
        String instrumentExternalId = (String) args[1];
        String instrumentType = (String) args[2];
        String orderHistoryAfter = (String) args[3];
        String orderHistoryBefore = (String) args[4];
        String orderHistoryLimit = (String) args[5];

        OrdersHistoryRequest request = orderMapper.domainToClientOkxOrdersHistoryRequest(order);
        request.setInstrumentId(instrumentExternalId);
        request.setInstrumentType(instrumentType);
        request.setAfter(orderHistoryAfter);
        request.setBefore(orderHistoryBefore);
        request.setLimit(orderHistoryLimit);

        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistory(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersHistoryArchive(Object... args) {
        Order order = (Order) args[0];
        String instrumentExternalId = (String) args[1];
        String instrumentType = (String) args[2];
        String orderHistoryAfter = (String) args[3];
        String orderHistoryBefore = (String) args[4];
        String orderHistoryLimit = (String) args[5];

        OrdersHistoryRequest request = orderMapper.domainToClientOkxOrdersHistoryRequest(order);
        request.setInstrumentId(instrumentExternalId);
        request.setInstrumentType(instrumentType);
        request.setAfter(orderHistoryAfter);
        request.setBefore(orderHistoryBefore);
        request.setLimit(orderHistoryLimit);

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
        String instrumentExternalId = (String) args[1];
        String tradeMode = (String) args[2];
        String positionSide = (String) args[3];

        CreateOrderRequest request = orderMapper.domainToClientOkxRequest(order);
        request.setInstrumentId(instrumentExternalId);
        request.setTradeMode(tradeMode);
        request.setPositionSide(positionSide);

        OkxApiResponse<OrderResponse> response = okxRestClient.createOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> amendOrder(Object... args) {
        Order order = (Order) args[0];
        String instrumentExternalId = (String) args[1];

        AmendOrderRequest request = orderMapper.domainToClientOkxAmendRequest(order);
        request.setInstrumentId(instrumentExternalId);

        OkxApiResponse<OrderResponse> response = okxRestClient.amendOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> cancelOrder(Object... args) {
        Order order = (Order) args[0];
        String instrumentExternalId = (String) args[1];

        CancelOrderRequest request = orderMapper.domainToClientOkxCancelRequest(order);
        request.setInstrumentId(instrumentExternalId);

        OkxApiResponse<OrderResponse> response = okxRestClient.cancelOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> createAlgoOrder(Object... args) {
        AlgoOrder algoOrder = (AlgoOrder) args[0];
        String instrumentExternalId = (String) args[1];
        String tradeMode = (String) args[2];
        String positionSide = (String) args[3];
        String orderSide = (String) args[4];

        CreateAlgoOrderRequest request = algoOrderMapper.domainToClientOkxRequest(algoOrder);
        request.setInstrumentId(instrumentExternalId);
        request.setTradeMode(tradeMode);
        request.setPositionSide(positionSide);
        request.setSide(orderSide);

        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.createAlgoOrder(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> cancelAlgoOrder(Object... args) {
        AlgoOrder algoOrder = (AlgoOrder) args[0];
        String instrumentExternalId = (String) args[1];

        CancelAlgoOrderRequest request = algoOrderMapper.domainToClientOkxCancelRequest(algoOrder);
        request.setInstrumentId(instrumentExternalId);

        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.cancelAlgoOrder(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Position> closePosition(Object... args) {
        Position position = (Position) args[0];
        String instrumentExternalId = (String) args[1];
        String marginCurrency = (String) args[2];
        String autoCancel = (String) args[3];

        ClosePositionRequest request = positionMapper.domainToClientOkxRequest(position);
        request.setInstrumentId(instrumentExternalId);
        request.setCurrency(marginCurrency);
        request.setAutoCancel(autoCancel);

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
