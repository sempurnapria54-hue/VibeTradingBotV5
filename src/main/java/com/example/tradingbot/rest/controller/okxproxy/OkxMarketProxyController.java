package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.model.okx.CandleResponse;
import com.example.tradingbot.client.model.okx.CandlesRequest;
import com.example.tradingbot.client.model.okx.InstrumentResponse;
import com.example.tradingbot.client.model.okx.InstrumentsRequest;
import com.example.tradingbot.client.model.okx.PriceTickerResponse;
import com.example.tradingbot.client.model.okx.TickerRequest;
import com.example.tradingbot.domain.service.OkxMarketProxyService;
import com.example.tradingbot.rest.model.okxproxy.RestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/okx/v5")
@RequiredArgsConstructor
public class OkxMarketProxyController {

    private final OkxMarketProxyService service;

    @GetMapping("/market/candles")
    public RestResponse<CandleResponse> getCandles(CandlesRequest request) {
        return success(service.getCandles(request));
    }

    @GetMapping("/market/history-candles")
    public RestResponse<CandleResponse> getHistoryCandles(CandlesRequest request) {
        return success(service.getHistoryCandles(request));
    }

    @GetMapping("/public/instruments")
    public RestResponse<InstrumentResponse> getInstruments(InstrumentsRequest request) {
        return success(service.getInstruments(request));
    }

    @GetMapping("/market/ticker")
    public RestResponse<PriceTickerResponse> getTicker(TickerRequest request) {
        return success(service.getTicker(request));
    }

    private <T> RestResponse<T> success(List<T> data) {
        RestResponse<T> response = new RestResponse<>();
        response.setCode("0");
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
