package com.example.tradingbot.client.service.okx;

import com.example.tradingbot.client.model.okx.request.AmendOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CancelOrderRequest;
import com.example.tradingbot.client.model.okx.request.CandlesRequest;
import com.example.tradingbot.client.model.okx.request.ClosePositionRequest;
import com.example.tradingbot.client.model.okx.request.CreateAlgoOrderRequest;
import com.example.tradingbot.client.model.okx.request.CreateOrderRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveLinkRequest;
import com.example.tradingbot.client.model.okx.request.FillsArchiveRequest;
import com.example.tradingbot.client.model.okx.request.FillsRequest;
import com.example.tradingbot.client.model.okx.request.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.request.get.GetAlgoOrdersHistorySearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrderDetailsSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersAlgoPendingSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersHistoryArchiveSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersHistorySearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetOrdersPendingSearchParams;
import com.example.tradingbot.client.model.okx.request.get.GetPositionsSearchParams;
import com.example.tradingbot.client.model.okx.response.AlgoOrderResponse;
import com.example.tradingbot.client.model.okx.response.CandleResponse;
import com.example.tradingbot.client.model.okx.response.InstrumentResponse;
import com.example.tradingbot.client.model.okx.response.OkxApiResponse;
import com.example.tradingbot.client.model.okx.response.OrderResponse;
import com.example.tradingbot.client.model.okx.response.PositionResponse;
import com.example.tradingbot.client.model.okx.response.PriceTickerResponse;
import com.example.tradingbot.client.model.okx.response.TickerRequest;
import com.example.tradingbot.client.model.okx.response.TradeFillResponse;
import com.example.tradingbot.client.model.okx.response.TradeFillsArchiveResponse;
import com.example.tradingbot.client.model.okx.response.balance.BalanceResponse;
import com.example.tradingbot.client.service.ClientService;
import com.example.tradingbot.domain.model.algo_order.AlgoOrder;
import com.example.tradingbot.domain.model.algo_order.external_snapshot.AlgoOrderExternalSnapshot;
import com.example.tradingbot.domain.model.balance.external_snapshot.BalanceContainerExternalSnapshot;
import com.example.tradingbot.domain.model.candle.Candle;
import com.example.tradingbot.domain.model.exchange.Exchange;
import com.example.tradingbot.domain.model.instrument.Instrument;
import com.example.tradingbot.domain.model.instrument.external_snapshot.InstrumentExternalSnapshot;
import com.example.tradingbot.domain.model.market.PriceTicker;
import com.example.tradingbot.domain.model.market.TradeFill;
import com.example.tradingbot.domain.model.market.TradeFillsArchive;
import com.example.tradingbot.domain.model.order.Order;
import com.example.tradingbot.domain.model.order.external_snapshot.OrderExternalSnapshot;
import com.example.tradingbot.domain.model.position.Position;
import com.example.tradingbot.domain.model.position.external_snapshot.PositionExternalSnapshot;
import com.example.tradingbot.domain.model.search_params.CandleSearchParams;
import com.example.tradingbot.domain.model.search_params.InstrumentSearchParams;
import com.example.tradingbot.domain.model.search_params.PriceTickerSearchParams;
import com.example.tradingbot.domain.model.search_params.TradeFillsSearchParams;
import com.example.tradingbot.mapping.AlgoOrderMapper;
import com.example.tradingbot.mapping.BalanceContainerMapper;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.mapping.PriceTickerMapper;
import com.example.tradingbot.mapping.TradeFillMapper;
import com.example.tradingbot.mapping.TradeFillsArchiveMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.tradingbot.util.Constant.ExchangeNames.EXCHANGE_NAME_OKX;

@Service
@RequiredArgsConstructor
public class OkxClientService implements ClientService {

    private final OkxRestClient okxRestClient;
    private final BalanceContainerMapper balanceContainerMapper;
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

    /**
     * GET.
     */
    //TODO: тут норм, остальное рефакторим.
    @Override
    public BalanceContainerExternalSnapshot getBalanceContainer(Exchange exchange) {
        OkxApiResponse<BalanceResponse> response = okxRestClient.getBalances();
        return balanceContainerMapper.clientToExternalSnapshot(exchange.getId(), response.getData()
                                                                                   .getFirst());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<PositionExternalSnapshot> getPositionsByInstrument(Instrument instrument) {
        GetPositionsSearchParams searchParams = new GetPositionsSearchParams();
        searchParams.setInstrumentExternalId(instrument.getExternalId());
        searchParams.setInstrumentExternalType(instrument.getExternalType());
        OkxApiResponse<PositionResponse> response = okxRestClient.getPositions(searchParams);
        return positionMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<PositionExternalSnapshot> getAllPositions() {
        OkxApiResponse<PositionResponse> response = okxRestClient.getAllPositions();
        return positionMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<OrderExternalSnapshot> getActiveOrdersByInstrument(Instrument instrument) {
        GetOrdersPendingSearchParams searchParams = new GetOrdersPendingSearchParams();
        searchParams.setInstrumentExternalId(instrument.getExternalId());
        searchParams.setInstrumentExternalType(instrument.getExternalType());
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersPending(searchParams);
        return orderMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<OrderExternalSnapshot> getActiveOrdersByInstrumentType(Instrument instrument) {
        GetOrdersPendingSearchParams searchParams = new GetOrdersPendingSearchParams();
        searchParams.setInstrumentExternalType(instrument.getExternalType());
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersPending(searchParams);
        return orderMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public OrderExternalSnapshot getOrder(String externalInstrumentId, String externalOrderId, String internalOrderId) {
        GetOrderDetailsSearchParams searchParams = new GetOrderDetailsSearchParams();
        searchParams.setInstrumentExternalId(externalInstrumentId);
        searchParams.setExternalId(externalOrderId);
        searchParams.setInternalId(internalOrderId);
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrderDetails(searchParams);
        return orderMapper.clientToExternalSnapshot(response.getData()
                                                            .getFirst());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<OrderExternalSnapshot> getOrdersHistory(Instrument instrument) {
        GetOrdersHistorySearchParams searchParams = new GetOrdersHistorySearchParams();
        searchParams.setInstrumentExternalType(instrument.getExternalType());
        searchParams.setInstrumentExternalId(instrument.getExternalId());
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistory(searchParams);
        return orderMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<OrderExternalSnapshot> getOrdersHistoryArchive(Instrument instrument) {
        GetOrdersHistoryArchiveSearchParams searchParams = new GetOrdersHistoryArchiveSearchParams();
        searchParams.setInstrumentExternalType(instrument.getExternalType());
        searchParams.setInstrumentExternalId(instrument.getExternalId());
        OkxApiResponse<OrderResponse> response = okxRestClient.getOrdersHistoryArchive(searchParams);
        return orderMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<AlgoOrderExternalSnapshot> getActiveAlgoOrders(Instrument instrument, AlgoOrder algoOrder) {
        GetOrdersAlgoPendingSearchParams params = new GetOrdersAlgoPendingSearchParams();
        params.setAlgoOrderExternalType(algoOrder.getExternalType());
        params.setInstrumentExternalType(instrument.getExternalType());
        params.setInstrumentExternalId(instrument.getExternalId());
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.getOrdersAlgoPending(params);
        return algoOrderMapper.clientToExternalSnapshot(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public AlgoOrderExternalSnapshot getAlgoOrder(AlgoOrder algoOrder) {
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.getOrderAlgoDetails(
                algoOrder.getInternalId(),
                algoOrder.getExternalId()
        );
        return algoOrderMapper.clientToExternalSnapshot(response.getData()
                                                                .getFirst());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<AlgoOrderExternalSnapshot> getAlgoOrdersHistory(Instrument instrument, AlgoOrder algoOrder) {
        GetAlgoOrdersHistorySearchParams searchParams = new GetAlgoOrdersHistorySearchParams();
        searchParams.setExternalAlgoOrderType(algoOrder.getExternalType());
        searchParams.setExternalStatus(algoOrder.getExternalStatus());
        searchParams.setAlgoOrderExternalId(algoOrder.getExternalId());
        searchParams.setInstrumentExternalId(instrument.getExternalId());

        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.getOrdersAlgoHistory(searchParams);
        return algoOrderMapper.clientToExternalSnapshot(response.getData());
    }


    @Override
    public List<TradeFill> getFills(Object... args) {
        TradeFillsSearchParams searchParams = (TradeFillsSearchParams) args[0];
        FillsRequest request = tradeFillMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<TradeFillResponse> response = okxRestClient.getFills(request);
        return tradeFillMapper.clientToDomain(response.getData());
    }

    @Override
    public List<TradeFill> getFillsHistory(Object... args) {
        TradeFillsSearchParams searchParams = (TradeFillsSearchParams) args[0];
        FillsRequest request = tradeFillMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<TradeFillResponse> response = okxRestClient.getFillsHistory(request);
        return tradeFillMapper.clientToDomain(response.getData());
    }

    @Override
    public List<TradeFillsArchive> requestFillsArchive(Object... args) {
        TradeFillsSearchParams searchParams = (TradeFillsSearchParams) args[0];
        FillsArchiveRequest request = tradeFillsArchiveMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<TradeFillsArchiveResponse> response = okxRestClient.requestFillsArchive(request);
        return tradeFillsArchiveMapper.clientToDomain(response.getData());
    }

    @Override
    public List<TradeFillsArchive> getFillsArchiveLink(Object... args) {
        TradeFillsSearchParams searchParams = (TradeFillsSearchParams) args[0];
        FillsArchiveLinkRequest request = tradeFillsArchiveMapper.domainSearchParamsToClientOkxLinkRequest(
                searchParams);
        OkxApiResponse<TradeFillsArchiveResponse> response = okxRestClient.getFillsArchiveLink(request);
        return tradeFillsArchiveMapper.clientToDomain(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<Candle> getCandles(CandleSearchParams searchParams) {
        CandlesRequest request = candleMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<CandleResponse> response = okxRestClient.getCandles(request);
        return candleMapper.clientToDomain(response.getData());
    }

    //TODO: тут норм, остальное рефакторим.
    @Override
    public List<Candle> getHistoryCandles(CandleSearchParams searchParams) {
        CandlesRequest request = candleMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<CandleResponse> response = okxRestClient.getHistoryCandles(request);
        return candleMapper.clientToDomain(response.getData());
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
        return orderMapper.clientToDomain(response.getData());
    }

    @Override
    public List<Order> amendOrder(Object... args) {
        Order order = (Order) args[0];
        String instrumentExternalId = (String) args[1];

        AmendOrderRequest request = orderMapper.domainToClientOkxAmendRequest(order);
        request.setInstrumentId(instrumentExternalId);

        OkxApiResponse<OrderResponse> response = okxRestClient.amendOrder(request);
        return orderMapper.clientToDomain(response.getData());
    }

    @Override
    public List<Order> cancelOrder(Order order, String instrumentExternalId) {
        CancelOrderRequest request = orderMapper.domainToClientOkxCancelRequest(order);
        request.setInstrumentId(instrumentExternalId);

        OkxApiResponse<OrderResponse> response = okxRestClient.cancelOrder(request);
        return orderMapper.clientToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> createAlgoOrder(AlgoOrder algoOrder, Instrument instrument, Position position) {
        CreateAlgoOrderRequest request = algoOrderMapper.domainToClientOkxRequest(algoOrder);
        request.setInstrumentId(instrument.getExternalId());
        request.setTradeMode(instrument.getExternalMarginMode());
        request.setPositionSide(position.getExternalSide());
        request.setSide(algoOrder.getExternalDirection());
        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.createAlgoOrder(request);
        return algoOrderMapper.clientToDomain(response.getData());
    }

    @Override
    public List<AlgoOrder> cancelAlgoOrder(Object... args) {
        AlgoOrder algoOrder = (AlgoOrder) args[0];
        String instrumentExternalId = (String) args[1];

        CancelAlgoOrderRequest request = algoOrderMapper.domainToClientOkxCancelRequest(algoOrder);
        request.setInstrumentId(instrumentExternalId);

        OkxApiResponse<AlgoOrderResponse> response = okxRestClient.cancelAlgoOrder(request);
        return algoOrderMapper.clientToDomain(response.getData());
    }

    @Override
    public List<PositionExternalSnapshot> closePositions(Instrument instrument) {
        ClosePositionRequest request = positionMapper.domainToClientOkxCloseRequest(instrument);
        OkxApiResponse<PositionResponse> response = okxRestClient.closePosition(request);
        return positionMapper.clientToExternalSnapshot(response.getData());
    }

    @Override
    public List<InstrumentExternalSnapshot> getInstruments(InstrumentSearchParams searchParams) {
        InstrumentsRequest request = instrumentMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<InstrumentResponse> response = okxRestClient.getInstruments(request);
        return instrumentMapper.clientToExternalSnapshot(response.getData());
    }

    @Override
    public List<PriceTicker> getTicker(PriceTickerSearchParams searchParams) {
        TickerRequest request = priceTickerMapper.domainSearchParamsToClientOkxRequest(searchParams);
        OkxApiResponse<PriceTickerResponse> response = okxRestClient.getTicker(request);
        return priceTickerMapper.clientToDomain(response.getData());
    }
}
