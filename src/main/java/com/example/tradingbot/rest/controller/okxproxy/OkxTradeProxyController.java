package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxAmendOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxAmendOrderResult;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderResult;
import com.example.tradingbot.client.okx.dto.OkxCancelOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelOrderResult;
import com.example.tradingbot.client.okx.dto.OkxClosePositionRequest;
import com.example.tradingbot.client.okx.dto.OkxClosePositionResult;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderResult;
import com.example.tradingbot.client.okx.dto.OkxCreateOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCreateOrderResult;
import com.example.tradingbot.client.okx.dto.OkxFillsArchiveQueryRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsArchiveRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsHistoryRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsRequest;
import com.example.tradingbot.client.okx.dto.OkxOrder;
import com.example.tradingbot.client.okx.dto.OkxOrderDetailsRequest;
import com.example.tradingbot.client.okx.dto.OkxOrdersHistoryArchiveRequest;
import com.example.tradingbot.client.okx.dto.OkxOrdersHistoryRequest;
import com.example.tradingbot.client.okx.dto.OkxOrdersPendingRequest;
import com.example.tradingbot.client.okx.dto.OkxTradeFill;
import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveResult;
import com.example.tradingbot.mapping.AlgoOrderResultMapper;
import com.example.tradingbot.mapping.ClosePositionResultMapper;
import com.example.tradingbot.mapping.OrderAmendResultMapper;
import com.example.tradingbot.mapping.OrderCancelResultMapper;
import com.example.tradingbot.mapping.OrderCreateResultMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.mapping.TradeFillMapper;
import com.example.tradingbot.mapping.TradeFillsArchiveMapper;
import com.example.tradingbot.rest.model.OkxProxyResponse;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/trade")
@RequiredArgsConstructor
public class OkxTradeProxyController {

    private final OkxRestClient okxRestClient;
    private final OrderMapper orderMapper;
    private final TradeFillMapper tradeFillMapper;
    private final OrderCreateResultMapper orderCreateResultMapper;
    private final OrderAmendResultMapper orderAmendResultMapper;
    private final OrderCancelResultMapper orderCancelResultMapper;
    private final AlgoOrderResultMapper algoOrderResultMapper;
    private final ClosePositionResultMapper closePositionResultMapper;
    private final TradeFillsArchiveMapper tradeFillsArchiveMapper;

    @GetMapping("/orders-pending")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Order> getOrdersPending(
        @RequestParam(name = "instType", required = false) String instType,
        @RequestParam(name = "instFamily", required = false) String instFamily,
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "ordType", required = false) String ordType,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxOrdersPendingRequest request = new OkxOrdersPendingRequest();
        request.setInstType(instType);
        request.setInstFamily(instFamily);
        request.setInstId(instId);
        request.setOrdType(ordType);
        request.setState(state);
        request.setAfter(after);
        request.setBefore(before);
        request.setLimit(limit);
        OkxApiResponse<OkxOrder> response = okxRestClient.getOrdersPending(request);
        return mapResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
    }

    @GetMapping("/order")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Order> getOrderDetails(
        @RequestParam(name = "instId") String instId,
        @RequestParam(name = "ordId", required = false) String ordId,
        @RequestParam(name = "clOrdId", required = false) String clOrdId
    ) {
        OkxOrderDetailsRequest request = new OkxOrderDetailsRequest();
        request.setInstId(instId);
        request.setOrdId(ordId);
        request.setClOrdId(clOrdId);
        OkxApiResponse<OkxOrder> response = okxRestClient.getOrderDetails(request);
        return mapResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
    }

    @GetMapping("/orders-history")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Order> getOrdersHistory(
        @RequestParam(name = "instType") String instType,
        @RequestParam(name = "instFamily", required = false) String instFamily,
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "ordType", required = false) String ordType,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "begin", required = false) String begin,
        @RequestParam(name = "end", required = false) String end,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxOrdersHistoryRequest request = new OkxOrdersHistoryRequest();
        request.setInstType(instType);
        request.setInstFamily(instFamily);
        request.setInstId(instId);
        request.setOrdType(ordType);
        request.setState(state);
        request.setCategory(category);
        request.setAfter(after);
        request.setBefore(before);
        request.setBegin(begin);
        request.setEnd(end);
        request.setLimit(limit);
        OkxApiResponse<OkxOrder> response = okxRestClient.getOrdersHistory(request);
        return mapResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
    }

    @GetMapping("/orders-history-archive")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Order> getOrdersHistoryArchive(
        @RequestParam(name = "instType") String instType,
        @RequestParam(name = "instFamily", required = false) String instFamily,
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "ordType", required = false) String ordType,
        @RequestParam(name = "state", required = false) String state,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "begin", required = false) String begin,
        @RequestParam(name = "end", required = false) String end,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxOrdersHistoryArchiveRequest request = new OkxOrdersHistoryArchiveRequest();
        request.setInstType(instType);
        request.setInstFamily(instFamily);
        request.setInstId(instId);
        request.setOrdType(ordType);
        request.setState(state);
        request.setCategory(category);
        request.setAfter(after);
        request.setBefore(before);
        request.setBegin(begin);
        request.setEnd(end);
        request.setLimit(limit);
        OkxApiResponse<OkxOrder> response = okxRestClient.getOrdersHistoryArchive(request);
        return mapResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
    }

    @GetMapping("/fills")
    public OkxProxyResponse<com.example.tradingbot.rest.model.TradeFill> getFills(
        @RequestParam(name = "instType", required = false) String instType,
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "ordId", required = false) String ordId,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxFillsRequest request = new OkxFillsRequest();
        request.setInstType(instType);
        request.setInstId(instId);
        request.setOrdId(ordId);
        request.setAfter(after);
        request.setBefore(before);
        request.setLimit(limit);
        OkxApiResponse<OkxTradeFill> response = okxRestClient.getFills(request);
        return mapResponse(response, tradeFillMapper::clientToDomain, tradeFillMapper::domainToRest);
    }

    @GetMapping("/fills-history")
    public OkxProxyResponse<com.example.tradingbot.rest.model.TradeFill> getFillsHistory(
        @RequestParam(name = "instType", required = false) String instType,
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "ordId", required = false) String ordId,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "begin", required = false) String begin,
        @RequestParam(name = "end", required = false) String end,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxFillsHistoryRequest request = new OkxFillsHistoryRequest();
        request.setInstType(instType);
        request.setInstId(instId);
        request.setOrdId(ordId);
        request.setAfter(after);
        request.setBefore(before);
        request.setBegin(begin);
        request.setEnd(end);
        request.setLimit(limit);
        OkxApiResponse<OkxTradeFill> response = okxRestClient.getFillsHistory(request);
        return mapResponse(response, tradeFillMapper::clientToDomain, tradeFillMapper::domainToRest);
    }

    @PostMapping("/fills-archive")
    public OkxProxyResponse<com.example.tradingbot.rest.model.TradeFillsArchive> requestFillsArchive(
        @RequestBody OkxFillsArchiveRequest request
    ) {
        OkxApiResponse<OkxTradeFillsArchiveResult> response = okxRestClient.requestFillsArchive(request);
        return mapResponse(response, tradeFillsArchiveMapper::clientToDomain, tradeFillsArchiveMapper::domainToRest);
    }

    @GetMapping("/fills-archive")
    public OkxProxyResponse<com.example.tradingbot.rest.model.TradeFillsArchive> getFillsArchive(
        @RequestParam(name = "year") String year,
        @RequestParam(name = "quarter") String quarter
    ) {
        OkxFillsArchiveQueryRequest request = new OkxFillsArchiveQueryRequest();
        request.setYear(year);
        request.setQuarter(quarter);
        OkxApiResponse<OkxTradeFillsArchiveResult> response = okxRestClient.getFillsArchive(request);
        return mapResponse(response, tradeFillsArchiveMapper::clientToDomain, tradeFillsArchiveMapper::domainToRest);
    }

    @PostMapping("/order")
    public OkxProxyResponse<com.example.tradingbot.rest.model.OrderCreateResult> createOrder(
        @RequestBody OkxCreateOrderRequest request
    ) {
        OkxApiResponse<OkxCreateOrderResult> response = okxRestClient.createOrder(request);
        return mapResponse(response, orderCreateResultMapper::clientToDomain, orderCreateResultMapper::domainToRest);
    }

    @PostMapping("/amend-order")
    public OkxProxyResponse<com.example.tradingbot.rest.model.OrderAmendResult> amendOrder(
        @RequestBody OkxAmendOrderRequest request
    ) {
        OkxApiResponse<OkxAmendOrderResult> response = okxRestClient.amendOrder(request);
        return mapResponse(response, orderAmendResultMapper::clientToDomain, orderAmendResultMapper::domainToRest);
    }

    @PostMapping("/cancel-order")
    public OkxProxyResponse<com.example.tradingbot.rest.model.OrderCancelResult> cancelOrder(
        @RequestBody OkxCancelOrderRequest request
    ) {
        OkxApiResponse<OkxCancelOrderResult> response = okxRestClient.cancelOrder(request);
        return mapResponse(response, orderCancelResultMapper::clientToDomain, orderCancelResultMapper::domainToRest);
    }

    @PostMapping("/order-algo")
    public OkxProxyResponse<com.example.tradingbot.rest.model.AlgoOrderResult> createAlgoOrder(
        @RequestBody OkxCreateAlgoOrderRequest request
    ) {
        OkxApiResponse<OkxCreateAlgoOrderResult> response = okxRestClient.createAlgoOrder(request);
        return mapResponse(response, algoOrderResultMapper::clientToDomain, algoOrderResultMapper::domainToRest);
    }

    @PostMapping("/cancel-algos")
    public OkxProxyResponse<com.example.tradingbot.rest.model.AlgoOrderResult> cancelAlgoOrder(
        @RequestBody OkxCancelAlgoOrderRequest request
    ) {
        OkxApiResponse<OkxCancelAlgoOrderResult> response = okxRestClient.cancelAlgoOrder(request);
        return mapResponse(response, algoOrderResultMapper::clientToDomainCancel, algoOrderResultMapper::domainToRest);
    }

    @PostMapping("/close-position")
    public OkxProxyResponse<com.example.tradingbot.rest.model.ClosePositionResult> closePosition(
        @RequestBody OkxClosePositionRequest request
    ) {
        OkxApiResponse<OkxClosePositionResult> response = okxRestClient.closePosition(request);
        return mapResponse(response, closePositionResultMapper::clientToDomain, closePositionResultMapper::domainToRest);
    }

    private <C, D, R> OkxProxyResponse<R> mapResponse(
        OkxApiResponse<C> response,
        Function<List<C>, List<D>> clientToDomain,
        Function<List<D>, List<R>> domainToRest
    ) {
        OkxProxyResponse<R> proxyResponse = new OkxProxyResponse<>();
        proxyResponse.setCode(response.getCode());
        proxyResponse.setMsg(response.getMsg());
        List<C> data = response.getData();
        if (data == null) {
            proxyResponse.setData(List.of());
            return proxyResponse;
        }
        List<D> domainData = clientToDomain.apply(data);
        proxyResponse.setData(domainToRest.apply(domainData));
        return proxyResponse;
    }
}
