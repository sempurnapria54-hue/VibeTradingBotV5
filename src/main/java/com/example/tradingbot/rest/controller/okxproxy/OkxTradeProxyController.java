package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxAlgoOrderResultDto;
import com.example.tradingbot.client.okx.dto.OkxAmendOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxCancelAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCancelOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxClosePositionRequest;
import com.example.tradingbot.client.okx.dto.OkxClosePositionResultDto;
import com.example.tradingbot.client.okx.dto.OkxCreateAlgoOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxCreateOrderRequest;
import com.example.tradingbot.client.okx.dto.OkxFillsArchiveRequest;
import com.example.tradingbot.client.okx.dto.OkxOrderActionResultDto;
import com.example.tradingbot.client.okx.dto.OkxOrderDto;
import com.example.tradingbot.client.okx.dto.OkxTradeFillDto;
import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveLinkDto;
import com.example.tradingbot.client.okx.dto.OkxTradeFillsArchiveResultDto;
import com.example.tradingbot.mapping.AlgoOrderResultMapper;
import com.example.tradingbot.mapping.ClosePositionResultMapper;
import com.example.tradingbot.mapping.OrderActionResultMapper;
import com.example.tradingbot.mapping.OrderMapper;
import com.example.tradingbot.mapping.TradeFillMapper;
import com.example.tradingbot.mapping.TradeFillsArchiveLinkMapper;
import com.example.tradingbot.mapping.TradeFillsArchiveResultMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/trade")
public class OkxTradeProxyController {
    private final OkxRestClient okxRestClient;
    private final OkxProxyResponseHandler responseHandler;
    private final OrderMapper orderMapper;
    private final OrderActionResultMapper orderActionResultMapper;
    private final TradeFillMapper tradeFillMapper;
    private final TradeFillsArchiveResultMapper tradeFillsArchiveResultMapper;
    private final TradeFillsArchiveLinkMapper tradeFillsArchiveLinkMapper;
    private final AlgoOrderResultMapper algoOrderResultMapper;
    private final ClosePositionResultMapper closePositionResultMapper;

    public OkxTradeProxyController(
            OkxRestClient okxRestClient,
            OkxProxyResponseHandler responseHandler,
            OrderMapper orderMapper,
            OrderActionResultMapper orderActionResultMapper,
            TradeFillMapper tradeFillMapper,
            TradeFillsArchiveResultMapper tradeFillsArchiveResultMapper,
            TradeFillsArchiveLinkMapper tradeFillsArchiveLinkMapper,
            AlgoOrderResultMapper algoOrderResultMapper,
            ClosePositionResultMapper closePositionResultMapper
    ) {
        this.okxRestClient = okxRestClient;
        this.responseHandler = responseHandler;
        this.orderMapper = orderMapper;
        this.orderActionResultMapper = orderActionResultMapper;
        this.tradeFillMapper = tradeFillMapper;
        this.tradeFillsArchiveResultMapper = tradeFillsArchiveResultMapper;
        this.tradeFillsArchiveLinkMapper = tradeFillsArchiveLinkMapper;
        this.algoOrderResultMapper = algoOrderResultMapper;
        this.closePositionResultMapper = closePositionResultMapper;
    }

    @GetMapping("/orders-pending")
    public ResponseEntity<?> getOrdersPending(
            @RequestParam(required = false) String instType,
            @RequestParam(required = false) String instFamily,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String ordType,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxOrderDto> response = okxRestClient.getOrdersPending(
                    instType,
                    instFamily,
                    instId,
                    ordType,
                    state,
                    after,
                    before,
                    limit
            );
            return responseHandler.handleResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/order")
    public ResponseEntity<?> getOrderDetails(
            @RequestParam String instId,
            @RequestParam(required = false) String ordId,
            @RequestParam(required = false) String clOrdId
    ) {
        try {
            OkxApiResponse<OkxOrderDto> response = okxRestClient.getOrderDetails(instId, ordId, clOrdId);
            return responseHandler.handleResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/orders-history")
    public ResponseEntity<?> getOrdersHistory(
            @RequestParam(required = false) String instType,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxOrderDto> response = okxRestClient.getOrdersHistory(instType, instId, state, after, before, limit);
            return responseHandler.handleResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/orders-history-archive")
    public ResponseEntity<?> getOrdersHistoryArchive(
            @RequestParam(required = false) String instType,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxOrderDto> response = okxRestClient.getOrdersHistoryArchive(instType, instId, state, after, before, limit);
            return responseHandler.handleResponse(response, orderMapper::clientToDomain, orderMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/fills")
    public ResponseEntity<?> getFills(
            @RequestParam(required = false) String instType,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String ordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxTradeFillDto> response = okxRestClient.getFills(instType, instId, ordId, after, before, begin, end, limit);
            return responseHandler.handleResponse(response, tradeFillMapper::clientToDomain, tradeFillMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/fills-history")
    public ResponseEntity<?> getFillsHistory(
            @RequestParam(required = false) String instType,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String ordId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxTradeFillDto> response = okxRestClient.getFillsHistory(instType, instId, ordId, after, before, begin, end, limit);
            return responseHandler.handleResponse(response, tradeFillMapper::clientToDomain, tradeFillMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/fills-archive")
    public ResponseEntity<?> requestFillsArchive(@RequestBody OkxFillsArchiveRequest request) {
        try {
            OkxApiResponse<OkxTradeFillsArchiveResultDto> response = okxRestClient.requestFillsArchive(request);
            return responseHandler.handleResponse(
                    response,
                    tradeFillsArchiveResultMapper::clientToDomain,
                    tradeFillsArchiveResultMapper::domainToRest
            );
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/fills-archive")
    public ResponseEntity<?> getFillsArchiveLink(@RequestParam String year, @RequestParam String quarter) {
        try {
            OkxApiResponse<OkxTradeFillsArchiveLinkDto> response = okxRestClient.getFillsArchiveLink(year, quarter);
            return responseHandler.handleResponse(
                    response,
                    tradeFillsArchiveLinkMapper::clientToDomain,
                    tradeFillsArchiveLinkMapper::domainToRest
            );
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/order")
    public ResponseEntity<?> createOrder(@RequestBody OkxCreateOrderRequest request) {
        try {
            OkxApiResponse<OkxOrderActionResultDto> response = okxRestClient.createOrder(request);
            return responseHandler.handleResponse(response, orderActionResultMapper::clientToDomain, orderActionResultMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/amend-order")
    public ResponseEntity<?> amendOrder(@RequestBody OkxAmendOrderRequest request) {
        try {
            OkxApiResponse<OkxOrderActionResultDto> response = okxRestClient.amendOrder(request);
            return responseHandler.handleResponse(response, orderActionResultMapper::clientToDomain, orderActionResultMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/cancel-order")
    public ResponseEntity<?> cancelOrder(@RequestBody OkxCancelOrderRequest request) {
        try {
            OkxApiResponse<OkxOrderActionResultDto> response = okxRestClient.cancelOrder(request);
            return responseHandler.handleResponse(response, orderActionResultMapper::clientToDomain, orderActionResultMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/order-algo")
    public ResponseEntity<?> createAlgoOrder(@RequestBody OkxCreateAlgoOrderRequest request) {
        try {
            OkxApiResponse<OkxAlgoOrderResultDto> response = okxRestClient.createAlgoOrder(request);
            return responseHandler.handleResponse(response, algoOrderResultMapper::clientToDomain, algoOrderResultMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/cancel-algos")
    public ResponseEntity<?> cancelAlgoOrder(@RequestBody OkxCancelAlgoOrderRequest request) {
        try {
            OkxApiResponse<OkxAlgoOrderResultDto> response = okxRestClient.cancelAlgoOrder(request);
            return responseHandler.handleResponse(response, algoOrderResultMapper::clientToDomain, algoOrderResultMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @PostMapping("/close-position")
    public ResponseEntity<?> closePosition(@RequestBody OkxClosePositionRequest request) {
        try {
            OkxApiResponse<OkxClosePositionResultDto> response = okxRestClient.closePosition(request);
            return responseHandler.handleResponse(
                    response,
                    closePositionResultMapper::clientToDomain,
                    closePositionResultMapper::domainToRest
            );
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }
}
