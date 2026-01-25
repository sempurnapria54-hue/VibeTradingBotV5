package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxCandleDto;
import com.example.tradingbot.client.okx.dto.OkxPriceTickerDto;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.mapping.PriceTickerMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/market")
public class OkxMarketProxyController {
    private final OkxRestClient okxRestClient;
    private final OkxProxyResponseHandler responseHandler;
    private final CandleMapper candleMapper;
    private final PriceTickerMapper priceTickerMapper;

    public OkxMarketProxyController(
            OkxRestClient okxRestClient,
            OkxProxyResponseHandler responseHandler,
            CandleMapper candleMapper,
            PriceTickerMapper priceTickerMapper
    ) {
        this.okxRestClient = okxRestClient;
        this.responseHandler = responseHandler;
        this.candleMapper = candleMapper;
        this.priceTickerMapper = priceTickerMapper;
    }

    @GetMapping("/candles")
    public ResponseEntity<?> getCandles(
            @RequestParam String instId,
            @RequestParam(required = false) String bar,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxCandleDto> response = okxRestClient.getCandles(instId, bar, after, before, limit);
            return responseHandler.handleResponse(response, candleMapper::clientToDomain, candleMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/history-candles")
    public ResponseEntity<?> getHistoryCandles(
            @RequestParam String instId,
            @RequestParam(required = false) String bar,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String limit
    ) {
        try {
            OkxApiResponse<OkxCandleDto> response = okxRestClient.getHistoryCandles(instId, bar, after, before, limit);
            return responseHandler.handleResponse(response, candleMapper::clientToDomain, candleMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }

    @GetMapping("/ticker")
    public ResponseEntity<?> getTicker(@RequestParam String instId) {
        try {
            OkxApiResponse<OkxPriceTickerDto> response = okxRestClient.getTicker(instId);
            return responseHandler.handleResponse(response, priceTickerMapper::clientToDomain, priceTickerMapper::domainToRest);
        } catch (Exception ex) {
            return responseHandler.handleException(ex);
        }
    }
}
