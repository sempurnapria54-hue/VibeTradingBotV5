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
        PositionsRequest request = (PositionsRequest) args[0];
        OkxApiResponse<PositionResponse> response = okxRestClient.getPositions(request);
        return positionMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersPending(Object... args) {
        OrdersPendingRequest request = (OrdersPendingRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersPending(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrderDetails(Object... args) {
        OrderDetailsRequest request = (OrderDetailsRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrderDetails(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> getOrdersAlgoPending(Object... args) {
        OrdersAlgoPendingRequest request = (OrdersAlgoPendingRequest) args[0];
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.getOrdersAlgoPending(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersHistory(Object... args) {
        OrdersHistoryRequest request = (OrdersHistoryRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistory(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> getOrdersHistoryArchive(Object... args) {
        OrdersHistoryRequest request = (OrdersHistoryRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistoryArchive(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFill> getFills(Object... args) {
        FillsRequest request = (FillsRequest) args[0];
        OkxApiResponse<TradeFillResponse> response = okxRestClient.getFills(request);
        return tradeFillMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFill> getFillsHistory(Object... args) {
        FillsRequest request = (FillsRequest) args[0];
        OkxApiResponse<TradeFillResponse> response = okxRestClient.getFillsHistory(request);
        return tradeFillMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFillsArchive> requestFillsArchive(Object... args) {
        FillsArchiveRequest request = (FillsArchiveRequest) args[0];
        OkxApiResponse<TradeFillsArchiveResponse> response = okxRestClient.requestFillsArchive(request);
        return tradeFillsArchiveMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<TradeFillsArchive> getFillsArchiveLink(Object... args) {
        FillsArchiveLinkRequest request = (FillsArchiveLinkRequest) args[0];
        OkxApiResponse<TradeFillsArchiveResponse> response = okxRestClient.getFillsArchiveLink(request);
        return tradeFillsArchiveMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Candle> getCandles(Object... args) {
        CandlesRequest request = (CandlesRequest) args[0];
        OkxApiResponse<CandleResponse> response = okxRestClient.getCandles(request);
        return candleMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Candle> getHistoryCandles(Object... args) {
        CandlesRequest request = (CandlesRequest) args[0];
        OkxApiResponse<CandleResponse> response = okxRestClient.getHistoryCandles(request);
        return candleMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> createOrder(Object... args) {
        CreateOrderRequest request = (CreateOrderRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.createOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> amendOrder(Object... args) {
        AmendOrderRequest request = (AmendOrderRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.amendOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Order> cancelOrder(Object... args) {
        CancelOrderRequest request = (CancelOrderRequest) args[0];
        OkxApiResponse<OrderResponse> response = okxRestClient.cancelOrder(request);
        return orderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> createAlgoOrder(Object... args) {
        CreateAlgoOrderRequest request = (CreateAlgoOrderRequest) args[0];
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.createAlgoOrder(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> cancelAlgoOrder(Object... args) {
        CancelAlgoOrderRequest request = (CancelAlgoOrderRequest) args[0];
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.cancelAlgoOrder(request);
        return algoOrderMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Position> closePosition(Object... args) {
        ClosePositionRequest request = (ClosePositionRequest) args[0];
        OkxApiResponse<PositionResponse> response = okxRestClient.closePosition(request);
        return positionMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<Instrument> getInstruments(Object... args) {
        InstrumentsRequest request = (InstrumentsRequest) args[0];
        OkxApiResponse<InstrumentResponse> response = okxRestClient.getInstruments(request);
        return instrumentMapper.clientOkxResponseToDomain(response.getData());
    }

    @Override
    public List<PriceTicker> getTicker(Object... args) {
        TickerRequest request = (TickerRequest) args[0];
        OkxApiResponse<PriceTickerResponse> response = okxRestClient.getTicker(request);
        return priceTickerMapper.clientOkxResponseToDomain(response.getData());
    }
}
