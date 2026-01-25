package com.example.tradingbot.rest.controller.okxproxy;

import com.example.tradingbot.client.okx.OkxRestClient;
import com.example.tradingbot.client.okx.dto.OkxApiResponse;
import com.example.tradingbot.client.okx.dto.OkxBalance;
import com.example.tradingbot.client.okx.dto.OkxBalanceRequest;
import com.example.tradingbot.client.okx.dto.OkxPosition;
import com.example.tradingbot.client.okx.dto.OkxPositionsRequest;
import com.example.tradingbot.domain.model.Balance;
import com.example.tradingbot.domain.model.Position;
import com.example.tradingbot.mapping.BalanceMapper;
import com.example.tradingbot.mapping.PositionMapper;
import com.example.tradingbot.rest.model.OkxProxyResponse;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/okx/v5/account")
@RequiredArgsConstructor
public class OkxAccountProxyController {

    private final OkxRestClient okxRestClient;
    private final BalanceMapper balanceMapper;
    private final PositionMapper positionMapper;

    @GetMapping("/balance")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Balance> getBalance(
        @RequestParam(name = "ccy", required = false) String ccy
    ) {
        OkxBalanceRequest request = new OkxBalanceRequest();
        request.setCcy(ccy);
        OkxApiResponse<OkxBalance> response = okxRestClient.getBalance(request);
        return mapResponse(response, balanceMapper::clientToDomain, balanceMapper::domainToRest);
    }

    @GetMapping("/positions")
    public OkxProxyResponse<com.example.tradingbot.rest.model.Position> getPositions(
        @RequestParam(name = "instId", required = false) String instId
    ) {
        OkxPositionsRequest request = new OkxPositionsRequest();
        request.setInstId(instId);
        OkxApiResponse<OkxPosition> response = okxRestClient.getPositions(request);
        return mapResponse(response, positionMapper::clientToDomain, positionMapper::domainToRest);
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
