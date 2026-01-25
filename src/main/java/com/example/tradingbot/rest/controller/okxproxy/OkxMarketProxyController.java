package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxCandle;
import com.example.tradingbot.client.okx.dto.OkxCandlesRequest;
import com.example.tradingbot.client.okx.dto.OkxHistoryCandlesRequest;
import com.example.tradingbot.client.okx.dto.OkxPriceTicker;
import com.example.tradingbot.client.okx.dto.OkxTickerRequest;
import com.example.tradingbot.domain.model.Candle;
import com.example.tradingbot.domain.model.PriceTicker;
import com.example.tradingbot.mapping.CandleMapper;
import com.example.tradingbot.mapping.PriceTickerMapper;
import com.example.tradingbot.rest.model.OkxProxyResponse;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/market")
@RequiredArgsConstructor
public class OkxMarketProxyController {

    private final OkxRestClient okxRestClient;
    private final CandleMapper candleMapper;
    private final PriceTickerMapper priceTickerMapper;

    @GetMapping("/candles")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Candle> getCandles(
        @RequestParam(name = "instId") String instId,
        @RequestParam(name = "bar", required = false) String bar,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxCandlesRequest request = new OkxCandlesRequest();
        request.setInstId(instId);
        request.setBar(bar);
        request.setAfter(after);
        request.setBefore(before);
        request.setLimit(limit);
        OkxApiResponse<OkxCandle> response = okxRestClient.getCandles(request);
        return mapResponse(response, candleMapper::clientToDomain, candleMapper::domainToRest);
    }

    @GetMapping("/history-candles")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Candle> getHistoryCandles(
        @RequestParam(name = "instId") String instId,
        @RequestParam(name = "bar", required = false) String bar,
        @RequestParam(name = "after", required = false) String after,
        @RequestParam(name = "before", required = false) String before,
        @RequestParam(name = "limit", required = false) String limit
    ) {
        OkxHistoryCandlesRequest request = new OkxHistoryCandlesRequest();
        request.setInstId(instId);
        request.setBar(bar);
        request.setAfter(after);
        request.setBefore(before);
        request.setLimit(limit);
        OkxApiResponse<OkxCandle> response = okxRestClient.getHistoryCandles(request);
        return mapResponse(response, candleMapper::clientToDomain, candleMapper::domainToRest);
    }

    @GetMapping("/ticker")
    public OkxProxyResponse<com.example.tradingbot.rest.model.PriceTicker> getTicker(
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "instType", required = false) String instType
    ) {
        OkxTickerRequest request = new OkxTickerRequest();
        request.setInstId(instId);
        request.setInstType(instType);
        OkxApiResponse<OkxPriceTicker> response = okxRestClient.getTicker(request);
        return mapResponse(response, priceTickerMapper::clientToDomain, priceTickerMapper::domainToRest);
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
