package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxInstrument;
import com.example.tradingbot.client.okx.dto.OkxInstrumentsRequest;
import com.example.tradingbot.domain.model.Instrument;
import com.example.tradingbot.mapping.InstrumentMapper;
import com.example.tradingbot.rest.model.OkxProxyResponse;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/public")
@RequiredArgsConstructor
public class OkxPublicProxyController {

    private final OkxRestClient okxRestClient;
    private final InstrumentMapper instrumentMapper;

    @GetMapping("/instruments")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Instrument> getInstruments(
        @RequestParam(name = "instType") String instType,
        @RequestParam(name = "instId", required = false) String instId,
        @RequestParam(name = "instFamily", required = false) String instFamily
    ) {
        OkxInstrumentsRequest request = new OkxInstrumentsRequest();
        request.setInstType(instType);
        request.setInstId(instId);
        request.setInstFamily(instFamily);
        OkxApiResponse<OkxInstrument> response = okxRestClient.getInstruments(request);
        return mapResponse(response, instrumentMapper::clientToDomain, instrumentMapper::domainToRest);
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
